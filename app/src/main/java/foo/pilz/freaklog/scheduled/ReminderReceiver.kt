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
import foo.pilz.freaklog.MainActivity
import foo.pilz.freaklog.R
import dagger.hilt.android.AndroidEntryPoint
import foo.pilz.freaklog.data.room.reminders.RemindersRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationScheduler: NotificationScheduler
    
    @Inject
    lateinit var remindersRepository: RemindersRepository

    override fun onReceive(context: Context, intent: Intent) {
        val isTest = intent.getBooleanExtra("IS_TEST", false)
        val reminderId = intent.getIntExtra("REMINDER_ID", -1)
        
        if (reminderId == -1 && !isTest) return

        val title = intent.getStringExtra("REMINDER_TITLE") ?: "Ingestion Reminder"

        showNotification(context, reminderId, title)
        
        if (isTest) return

        // Reschedule for next day
        CoroutineScope(Dispatchers.IO).launch {
            val reminder = remindersRepository.getReminderById(reminderId)
            if (reminder != null && reminder.isEnabled) {
                notificationScheduler.scheduleReminder(requestExact = false, reminder = reminder)
            }
        }
    }

    private fun showNotification(context: Context, reminderId: Int, title: String) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val channelId = "ingestion_reminders"
        
        // Create an explicit intent for an Activity in your app
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round) // Replace with appropriate icon
            .setContentTitle("Ingestion Reminder")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(reminderId, builder.build())
        }
    }
}
