/*
 * Copyright (c) 2026. FreakLog contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package foo.pilz.freaklog.scheduled

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import foo.pilz.freaklog.data.room.reminders.RemindersRepository
import foo.pilz.freaklog.scheduled.NotificationScheduler.Companion.EXTRA_REMINDER_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives the alarm broadcast for a reminder, posts the user-visible notification via
 * [NotificationScheduler], then enqueues the next firing.
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationScheduler: NotificationScheduler
    @Inject lateinit var remindersRepository: RemindersRepository

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra(EXTRA_REMINDER_ID, -1)
        if (reminderId == -1) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val reminder = remindersRepository.getReminderById(reminderId) ?: return@launch
                notificationScheduler.postReminderNotification(reminder)
                if (reminder.isEnabled) {
                    notificationScheduler.scheduleReminder(reminder)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
