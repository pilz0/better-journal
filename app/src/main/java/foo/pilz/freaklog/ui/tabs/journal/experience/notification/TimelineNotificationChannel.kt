package foo.pilz.freaklog.ui.tabs.journal.experience.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object TimelineNotificationChannel {
    const val CHANNEL_ID = "timeline_live"
    const val NOTIFICATION_ID = 9001

    fun create(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Live Timeline",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows your active experience timeline with real-time phase updates"
            setShowBadge(false)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
