/*
 * Copyright (c) 2026. FreakLog contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package foo.pilz.freaklog.scheduled

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Reschedules every enabled reminder when:
 * - the device boots (BOOT_COMPLETED)
 * - the app is updated (MY_PACKAGE_REPLACED)
 * - the system clock or timezone changes
 *
 * AlarmManager forgets all alarms on reboot, so without this receiver the user's reminders silently
 * stop firing after restarting their phone.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

  @Inject lateinit var notificationScheduler: NotificationScheduler

  override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
      Intent.ACTION_BOOT_COMPLETED,
      Intent.ACTION_MY_PACKAGE_REPLACED,
      Intent.ACTION_LOCKED_BOOT_COMPLETED,
      Intent.ACTION_TIME_CHANGED,
      Intent.ACTION_TIMEZONE_CHANGED -> Unit
      else -> return
    }
    val pendingResult = goAsync()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    scope.launch {
      try {
        NotificationScheduler.ensureNotificationChannel(context)
        notificationScheduler.rescheduleAll()
      } catch (ce: CancellationException) {
        throw ce
      } catch (e: Exception) {
        Log.e("BootCompletedReceiver", "Failed to reschedule reminders on boot/time change", e)
      } finally {
        scope.cancel()
        pendingResult.finish()
      }
    }
  }
}
