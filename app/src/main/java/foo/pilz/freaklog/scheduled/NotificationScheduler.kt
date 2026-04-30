/*
 * Copyright (c) 2026. FreakLog contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package foo.pilz.freaklog.scheduled

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import foo.pilz.freaklog.MainActivity
import foo.pilz.freaklog.R
import foo.pilz.freaklog.data.room.reminders.RemindersRepository
import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules and cancels reminder alarms.
 *
 * Scheduling math is delegated to [ReminderSchedule]; this class is the thin Android-facing
 * layer that owns the [AlarmManager], the notification channel, and the request-code policy.
 */
@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remindersRepository: RemindersRepository,
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    init {
        ensureNotificationChannel(context)
    }

    /**
     * Returns true if the OS allows this app to schedule exact alarms. On API < 31 this is
     * always true; on API 31+ it depends on the SCHEDULE_EXACT_ALARM/USE_EXACT_ALARM grant.
     */
    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /**
     * Schedules the next firing of [reminder]. If the reminder is disabled or has no future
     * fire time the previously scheduled alarm is cancelled.
     */
    fun scheduleReminder(reminder: Reminder) {
        // Always cancel any pending alarm first so an updated schedule replaces the old one.
        cancelReminder(reminder.id)
        if (!reminder.isEnabled) return
        val nextFireAt = ReminderSchedule.nextFireAt(reminder, System.currentTimeMillis())
            ?: return
        scheduleAt(reminder, nextFireAt)
    }

    /** Schedules a single one-shot fire at [triggerAtMillis] for the given reminder. */
    fun scheduleAt(reminder: Reminder, triggerAtMillis: Long) {
        val pendingIntent = makeFirePendingIntent(reminder)
        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            // Fall back to inexact alarms if the user denied SCHEDULE_EXACT_ALARM. Better
            // late than never.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    fun cancelReminder(reminder: Reminder) = cancelReminder(reminder.id)

    fun cancelReminder(reminderId: Int) {
        if (reminderId == 0) return
        val intent = makeFireIntent(reminderId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /** Reschedules all enabled reminders. Safe to call from app start and after reboot. */
    suspend fun rescheduleAll() {
        remindersRepository.getAllReminders().forEach { scheduleReminder(it) }
    }

    /**
     * Sends an immediate notification for [reminder] without persisting anything or going
     * through AlarmManager / a broadcast. Used by the "Test" button in the Edit screen.
     * The action buttons are omitted because, for an unsaved reminder, there is no row for
     * `ReminderActionReceiver` to act on.
     */
    fun testNotification(reminder: Reminder) {
        postReminderNotification(reminder, includeActions = false, notificationId = TEST_NOTIFICATION_ID)
    }

    /**
     * Posts the user-visible notification for [reminder]. Shared between the alarm path
     * (via [ReminderReceiver]) and the synchronous test path.
     */
    fun postReminderNotification(
        reminder: Reminder,
        includeActions: Boolean = true,
        notificationId: Int = reminder.id,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureNotificationChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            // Explicit package + component avoids any chance of this PendingIntent being
            // resolved against another app (CodeQL: implicit PendingIntent).
            setPackage(context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openPending = PendingIntent.getActivity(
            context,
            notificationId,
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

        if (includeActions) {
            // "Take" only makes sense if a substance + route are configured.
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
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
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

    private fun makeFirePendingIntent(reminder: Reminder): PendingIntent {
        val intent = makeFireIntent(reminder.id)
        return PendingIntent.getBroadcast(
            context,
            reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun makeFireIntent(reminderId: Int): Intent =
        Intent(context, ReminderReceiver::class.java).apply {
            // Explicit component + package: prevents the broadcast from being resolved
            // by any receiver outside this app, even though we also set a custom action
            // string for per-reminder PendingIntent equality (CodeQL: implicit
            // PendingIntent).
            setPackage(context.packageName)
            // Set a unique action so PendingIntent equality is per-reminder rather than per-class.
            action = "$ACTION_FIRE_REMINDER.$reminderId"
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }

    companion object {
        const val CHANNEL_ID = "ingestion_reminders"
        const val ACTION_FIRE_REMINDER = "foo.pilz.freaklog.action.FIRE_REMINDER"
        const val EXTRA_REMINDER_ID = "REMINDER_ID"
        /** Stable id for the notification posted by [testNotification]. */
        private const val TEST_NOTIFICATION_ID = -1

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ingestion reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminds you to take a substance at the scheduled time."
            }
            nm.createNotificationChannel(channel)
        }
    }
}
