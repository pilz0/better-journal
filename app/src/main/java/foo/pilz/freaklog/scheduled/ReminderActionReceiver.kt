/*
 * Copyright (c) 2026. FreakLog contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package foo.pilz.freaklog.scheduled

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.room.experiences.entities.Experience
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.room.reminders.RemindersRepository
import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import foo.pilz.freaklog.data.substances.AdministrationRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Handles user-initiated actions on a posted reminder notification:
 *  - [ACTION_TAKE]  → log an [Ingestion] using the reminder's preset substance/dose/route
 *  - [ACTION_SNOOZE] → re-schedule a one-shot fire N minutes out
 *  - [ACTION_SKIP]   → just dismiss the notification
 */
@AndroidEntryPoint
class ReminderActionReceiver : BroadcastReceiver() {

    @Inject lateinit var remindersRepository: RemindersRepository
    @Inject lateinit var experienceRepository: ExperienceRepository
    @Inject lateinit var notificationScheduler: NotificationScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra(EXTRA_REMINDER_ID, -1)
        if (reminderId == -1) return
        val action = intent.action ?: return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val reminder = remindersRepository.getReminderById(reminderId)
                if (reminder == null) {
                    NotificationManagerCompat.from(context).cancel(reminderId)
                    return@launch
                }
                when (action) {
                    ACTION_TAKE -> handleTake(reminder)
                    ACTION_SNOOZE -> {
                        val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 15)
                        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
                        notificationScheduler.scheduleAt(reminder, triggerAt)
                    }
                    ACTION_SKIP -> { /* no-op; just dismiss */ }
                }
                NotificationManagerCompat.from(context).cancel(reminderId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleTake(reminder: Reminder) {
        val substanceName = reminder.substanceName?.takeIf { it.isNotBlank() } ?: return
        val routeName = reminder.administrationRoute?.takeIf { it.isNotBlank() } ?: return
        val route = runCatching { AdministrationRoute.valueOf(routeName) }.getOrNull() ?: return

        val now = Instant.now()
        // Reuse the most recent experience whose first ingestion is within the last 15 hours,
        // mirroring the heuristic used by FinishIngestionScreenViewModel.
        val recent = experienceRepository.getSortedExperiencesWithIngestionsWithSortDateBetween(
            fromInstant = now.minus(2, ChronoUnit.DAYS),
            toInstant = now.plus(1, ChronoUnit.HOURS),
        )
        val attachTo = recent.firstOrNull { exp ->
            val firstIngestionTime = exp.ingestions.minByOrNull { it.time }?.time
                ?: return@firstOrNull false
            now.isAfter(firstIngestionTime.minus(3, ChronoUnit.HOURS)) &&
                    now.isBefore(firstIngestionTime.plus(15, ChronoUnit.HOURS))
        }

        val companion = SubstanceCompanion(
            substanceName = substanceName,
            color = AdaptiveColor.BLUE,
        )
        if (attachTo != null) {
            val ingestion = newIngestion(reminder, route, attachTo.experience.id, now)
            experienceRepository.insertIngestionAndCompanion(ingestion, companion)
        } else {
            val newId = (recent.maxOfOrNull { it.experience.id } ?: 0) + 1
            val experience = Experience(
                id = newId,
                title = reminder.title.ifBlank { substanceName },
                text = "",
                creationDate = now,
                sortDate = now,
                location = null,
            )
            val ingestion = newIngestion(reminder, route, newId, now)
            experienceRepository.insertIngestionExperienceAndCompanion(
                ingestion = ingestion,
                experience = experience,
                substanceCompanion = companion,
            )
        }
    }

    private fun newIngestion(
        reminder: Reminder,
        route: AdministrationRoute,
        experienceId: Int,
        now: Instant,
    ) = Ingestion(
        substanceName = reminder.substanceName!!,
        time = now,
        endTime = null,
        creationDate = now,
        administrationRoute = route,
        dose = reminder.dose,
        isDoseAnEstimate = false,
        estimatedDoseStandardDeviation = null,
        units = reminder.units,
        experienceId = experienceId,
        notes = reminder.notes.takeIf { it.isNotBlank() }?.let { "From reminder: $it" }
            ?: "Logged from reminder",
        stomachFullness = null,
        consumerName = null,
        customUnitId = null,
    )

    companion object {
        const val ACTION_TAKE = "foo.pilz.freaklog.action.REMINDER_TAKE"
        const val ACTION_SNOOZE = "foo.pilz.freaklog.action.REMINDER_SNOOZE"
        const val ACTION_SKIP = "foo.pilz.freaklog.action.REMINDER_SKIP"
        const val EXTRA_REMINDER_ID = "REMINDER_ID"
        const val EXTRA_SNOOZE_MINUTES = "SNOOZE_MINUTES"

        private const val REQ_BASE_TAKE = 1_000_000
        private const val REQ_BASE_SNOOZE = 2_000_000
        private const val REQ_BASE_SKIP = 3_000_000

        fun takePendingIntent(context: Context, reminderId: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQ_BASE_TAKE + reminderId,
                Intent(context, ReminderActionReceiver::class.java).apply {
                    action = ACTION_TAKE
                    putExtra(EXTRA_REMINDER_ID, reminderId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        fun snoozePendingIntent(context: Context, reminderId: Int, snoozeMinutes: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQ_BASE_SNOOZE + reminderId,
                Intent(context, ReminderActionReceiver::class.java).apply {
                    action = ACTION_SNOOZE
                    putExtra(EXTRA_REMINDER_ID, reminderId)
                    putExtra(EXTRA_SNOOZE_MINUTES, snoozeMinutes)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        fun skipPendingIntent(context: Context, reminderId: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQ_BASE_SKIP + reminderId,
                Intent(context, ReminderActionReceiver::class.java).apply {
                    action = ACTION_SKIP
                    putExtra(EXTRA_REMINDER_ID, reminderId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
