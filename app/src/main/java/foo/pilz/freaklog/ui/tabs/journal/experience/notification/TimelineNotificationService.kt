package foo.pilz.freaklog.ui.tabs.journal.experience.notification

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import foo.pilz.freaklog.R
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.di.ApplicationScope
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TimelineNotificationService : Service() {

    companion object {
        const val EXTRA_EXPERIENCE_ID = "experience_id"
        const val ACTION_STOP = "foo.pilz.freaklog.STOP_TIMELINE_NOTIFICATION"
        const val ACTION_REFRESH = "foo.pilz.freaklog.REFRESH_TIMELINE_NOTIFICATION"
        const val GLOBAL_TIMELINE_ID = -2

        fun start(context: Context, experienceId: Int) {
            val intent = Intent(context, TimelineNotificationService::class.java).apply {
                putExtra(EXTRA_EXPERIENCE_ID, experienceId)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TimelineNotificationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun refresh(context: Context) {
            val intent = Intent(context, TimelineNotificationService::class.java).apply {
                action = ACTION_REFRESH
            }
            context.startService(intent)
        }

        suspend fun refreshOrAutoStart(
            context: Context,
            userPreferences: UserPreferences,
            savedExperienceId: Int?,
        ) {
            val activeId = userPreferences.activeNotificationExperienceIdFlow.firstOrNull()
                ?: (savedExperienceId ?: GLOBAL_TIMELINE_ID).takeIf {
                    userPreferences.isTimelineNotificationAutoStartEnabledFlow.first() &&
                            NotificationManagerCompat.from(context).areNotificationsEnabled()
                }
            if (activeId != null) {
                start(context, activeId)
            }
        }
    }

    @Inject
    lateinit var experienceRepo: ExperienceRepository
    @Inject
    lateinit var substanceRepo: SubstanceRepository
    @Inject
    lateinit var userPreferences: UserPreferences
    @Inject
    lateinit var customProfiles: foo.pilz.freaklog.data.room.experiences.CustomSubstanceProfiles
    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var updateJob: Job? = null
    private var experienceId: Int = -1
    private var isExplicitStop = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfAndCleanup()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_REFRESH) {
            if (experienceId != -1) {
                serviceScope.launch {
                    val renderer = TimelineNotificationRenderer(
                        this@TimelineNotificationService,
                        experienceRepo,
                        substanceRepo,
                        userPreferences,
                        customProfiles,
                    )
                    val result = renderer.render(experienceId) ?: return@launch
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(TimelineNotificationChannel.NOTIFICATION_ID, result.notification)
                }
            }
            return START_NOT_STICKY
        }

        experienceId = intent?.getIntExtra(EXTRA_EXPERIENCE_ID, -1) ?: -1
        if (experienceId == -1) {
            stopSelf()
            return START_NOT_STICKY
        }

        val initialNotification =
            NotificationCompat.Builder(this, TimelineNotificationChannel.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setContentTitle("Active Timeline")
                .setContentText("Loading...")
                .setSilent(true)
                .build()

        ServiceCompat.startForeground(
            this,
            TimelineNotificationChannel.NOTIFICATION_ID,
            initialNotification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else 0
        )

        serviceScope.launch {
            userPreferences.saveActiveNotificationExperienceId(experienceId)
        }

        updateJob?.cancel()
        updateJob = serviceScope.launch {
            updateLoop()
        }

        return START_NOT_STICKY
    }

    private suspend fun updateLoop() {
        val renderer = TimelineNotificationRenderer(this, experienceRepo, substanceRepo, userPreferences, customProfiles)

        while (true) {
            val result = renderer.render(experienceId)
            if (result == null || result.isExperienceFinished) {
                stopSelfAndCleanup()
                return
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(TimelineNotificationChannel.NOTIFICATION_ID, result.notification)

            delay(result.nextUpdateIntervalMs)
        }
    }

    private fun stopSelfAndCleanup() {
        isExplicitStop = true
        appScope.launch(Dispatchers.IO) {
            userPreferences.saveActiveNotificationExperienceId(null)
        }
        TimelineUpdateWorker.cancel(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (!isExplicitStop && experienceId != -1) {
            TimelineUpdateWorker.schedule(this, experienceId)
        }
        serviceScope.cancel()
        super.onDestroy()
    }
}
