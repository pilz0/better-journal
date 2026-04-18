/*
 * Copyright (c) 2026. FreakLog contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package foo.pilz.freaklog.scheduled

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
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
     * Sends an immediate broadcast that mimics a real reminder firing, used by the "Test" button
     * in the Edit screen.
     */
    fun testNotification(reminder: Reminder) {
        val intent = makeFireIntent(reminder.id).apply {
            putExtra(EXTRA_IS_TEST, true)
        }
        context.sendBroadcast(intent)
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
            // Set a unique action so PendingIntent equality is per-reminder rather than per-class.
            action = "$ACTION_FIRE_REMINDER.$reminderId"
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }

    companion object {
        const val CHANNEL_ID = "ingestion_reminders"
        const val ACTION_FIRE_REMINDER = "foo.pilz.freaklog.action.FIRE_REMINDER"
        const val EXTRA_REMINDER_ID = "REMINDER_ID"
        const val EXTRA_IS_TEST = "IS_TEST"

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
