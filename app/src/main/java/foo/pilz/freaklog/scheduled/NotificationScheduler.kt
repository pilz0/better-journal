package foo.pilz.freaklog.scheduled

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    init {
        createNotificationChannel()
    }

    fun scheduleReminder(requestExact: Boolean = true, reminder: Reminder) {
        if (!reminder.isEnabled) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                if (requestExact) {
                   val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                   intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                   context.startActivity(intent)
                   Toast.makeText(context, "Please allow exact alarms for reminders to work appropriately", Toast.LENGTH_LONG).show()
                }
                return
            }
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("REMINDER_ID", reminder.id)
            putExtra("REMINDER_TITLE", reminder.title)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
        }

        // If time has passed, schedule for next interval
        // We want to find the next time that matches (start_time + n * interval) > now
        // But the requirement is likely just "repeat every X from start time". 
        // If start time is in the past, we should jump to the next valid slot.
        
        val now = System.currentTimeMillis()
        if (calendar.timeInMillis <= now) {
            // Logic to find next interval
            // For now, let's keep it simple: if strict daily was "add 1 day",
            // flexible is "add interval until in future"
            
            val intervalMillis = when (reminder.intervalUnit.uppercase()) {
                "MINUTES" -> reminder.intervalValue * 60 * 1000L
                "HOURS" -> reminder.intervalValue * 60 * 60 * 1000L
                "DAYS" -> reminder.intervalValue * 24 * 60 * 60 * 1000L
                else -> 24 * 60 * 60 * 1000L // Default to daily
            }
            
            // Calculate how many intervals have passed
            val diff = now - calendar.timeInMillis
            val intervalsPassed = (diff / intervalMillis) + 1
            
            calendar.timeInMillis += intervalsPassed * intervalMillis
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }

    fun cancelReminder(reminder: Reminder) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun testNotification(reminder: Reminder) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("REMINDER_ID", reminder.id)
            putExtra("REMINDER_TITLE", reminder.title)
            putExtra("IS_TEST", true)
        }
        context.sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Ingestion Reminders"
            val descriptionText = "Reminders to take ingestion"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("ingestion_reminders", name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
