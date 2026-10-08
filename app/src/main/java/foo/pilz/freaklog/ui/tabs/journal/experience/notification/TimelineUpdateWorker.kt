package foo.pilz.freaklog.ui.tabs.journal.experience.notification

import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import java.util.concurrent.TimeUnit

class TimelineUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    /** Lets the default WorkManager factory build this worker without a Hilt worker factory. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun experienceRepository(): ExperienceRepository
        fun substanceRepository(): SubstanceRepository
        fun userPreferences(): UserPreferences
        fun customSubstanceProfiles(): foo.pilz.freaklog.data.room.experiences.CustomSubstanceProfiles
    }

    companion object {
        private const val WORK_NAME = "timeline_notification_update"
        private const val KEY_EXPERIENCE_ID = "experience_id"

        fun schedule(context: Context, experienceId: Int) {
            val request = PeriodicWorkRequestBuilder<TimelineUpdateWorker>(
                15, TimeUnit.MINUTES
            )
                .setInputData(workDataOf(KEY_EXPERIENCE_ID to experienceId))
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        val experienceId = inputData.getInt(KEY_EXPERIENCE_ID, -1)
        if (experienceId == -1) return Result.failure()

        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val userPreferences = deps.userPreferences()
        val renderer = TimelineNotificationRenderer(
            applicationContext,
            deps.experienceRepository(),
            deps.substanceRepository(),
            userPreferences,
            deps.customSubstanceProfiles()
        )
        val result = renderer.render(experienceId)

        if (result == null || result.isExperienceFinished) {
            userPreferences.saveActiveNotificationExperienceId(null)
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.cancel(TimelineNotificationChannel.NOTIFICATION_ID)
            cancel(applicationContext)
            return Result.success()
        }

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.notify(TimelineNotificationChannel.NOTIFICATION_ID, result.notification)

        return Result.success()
    }
}
