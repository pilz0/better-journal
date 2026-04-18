/*
 * Copyright (c) 2026. FreakLog contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package foo.pilz.freaklog.scheduled

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import foo.pilz.freaklog.MainActivity
import foo.pilz.freaklog.R
import foo.pilz.freaklog.data.room.reminders.RemindersRepository
import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import foo.pilz.freaklog.scheduled.NotificationScheduler.Companion.CHANNEL_ID
import foo.pilz.freaklog.scheduled.NotificationScheduler.Companion.EXTRA_IS_TEST
import foo.pilz.freaklog.scheduled.NotificationScheduler.Companion.EXTRA_REMINDER_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives the alarm broadcast for a reminder, posts a Medisafe-style notification with
 * Take / Snooze / Skip actions, then enqueues the next firing.
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationScheduler: NotificationScheduler
    @Inject lateinit var remindersRepository: RemindersRepository

    override fun onReceive(context: Context, intent: Intent) {
        val isTest = intent.getBooleanExtra(EXTRA_IS_TEST, false)
        val reminderId = intent.getIntExtra(EXTRA_REMINDER_ID, -1)
        if (reminderId == -1) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val reminder = remindersRepository.getReminderById(reminderId) ?: return@launch
                showNotification(context, reminder)
                if (!isTest && reminder.isEnabled) {
                    notificationScheduler.scheduleReminder(reminder)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, reminder: Reminder) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationScheduler.ensureNotificationChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openPending = PendingIntent.getActivity(
            context,
            reminder.id,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = reminder.title.ifBlank { "Reminder" }
        val body = buildBody(reminder)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openPending)
            .setAutoCancel(true)

        // Add quick actions. "Take" only makes sense if a substance is configured.
        if (!reminder.substanceName.isNullOrBlank() && !reminder.administrationRoute.isNullOrBlank()) {
            builder.addAction(
                0,
                "Take",
                ReminderActionReceiver.takePendingIntent(context, reminder.id)
            )
        }
        builder.addAction(
            0,
            "Snooze 15 min",
            ReminderActionReceiver.snoozePendingIntent(context, reminder.id, snoozeMinutes = 15)
        )
        builder.addAction(
            0,
            "Skip",
            ReminderActionReceiver.skipPendingIntent(context, reminder.id)
        )

        NotificationManagerCompat.from(context).notify(reminder.id, builder.build())
    }

    private fun buildBody(reminder: Reminder): String {
        val parts = mutableListOf<String>()
        val dose = reminder.dose
        val units = reminder.units
        val substance = reminder.substanceName
        if (dose != null && !substance.isNullOrBlank()) {
            val doseText = if (dose == dose.toLong().toDouble()) dose.toLong().toString() else dose.toString()
            val unitsText = units?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
            parts += "Take $doseText$unitsText $substance"
        } else if (!substance.isNullOrBlank()) {
            parts += "Take $substance"
        }
        if (reminder.notes.isNotBlank()) parts += reminder.notes
        return if (parts.isEmpty()) "Time for your scheduled reminder." else parts.joinToString("\n")
    }
}
