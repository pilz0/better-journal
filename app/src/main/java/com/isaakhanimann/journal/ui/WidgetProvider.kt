package com.isaakhanimann.journal.ui

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.isaakhanimann.journal.data.room.AppDatabase
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.temporal.ChronoUnit

object WidgetKeys {
    val TITLE = stringPreferencesKey("title")
    val TIMELINE_OPTION = stringPreferencesKey("timelineDisplayOption")
    val TIME_OPTION = stringPreferencesKey("timeDisplayOption")
    val IS_LOADING = booleanPreferencesKey("isLoading")
}

private object WorkerInput {
    const val APP_WIDGET_ID = "appWidgetId"
}

class MyWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MyAppWidget()
}

class MyAppWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val title = prefs[WidgetKeys.TITLE] ?: "Timeline"
            val timelineOption = prefs[WidgetKeys.TIMELINE_OPTION] ?: "Hidden"
            val timeOption = prefs[WidgetKeys.TIME_OPTION] ?: "Absolute"
            val isLoading = prefs[WidgetKeys.IS_LOADING] ?: false

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = title,
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)
                )

                when {
                    isLoading || timelineOption == "Loading" -> {
                        Text(text = "Loading…", modifier = GlanceModifier.padding(top = 8.dp))
                    }
                    timelineOption == "Hidden" -> {
                        Text(text = "Timeline hidden", modifier = GlanceModifier.padding(top = 8.dp))
                    }
                    timelineOption == "NotWorthDrawing" -> {
                        Text(text = "Nothing to show", modifier = GlanceModifier.padding(top = 8.dp))
                    }
                    timelineOption == "Shown" -> {
                        Text(
                            text = "Timeline ready ($timeOption)",
                            modifier = GlanceModifier.padding(top = 8.dp)
                        )
                    }
                }

                Button(
                    text = "Refresh",
                    onClick = actionRunCallback<RefreshAction>(),
                    modifier = GlanceModifier.padding(top = 12.dp)
                )
            }
        }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // 1) Immediately show loading for this widget instance
        updateAppWidgetState(
            context = context,
            definition = PreferencesGlanceStateDefinition,
            glanceId = glanceId
        ) { prefs ->
            prefs.toMutablePreferences().apply {
                this[WidgetKeys.IS_LOADING] = true
                this[WidgetKeys.TIMELINE_OPTION] = "Loading"
            }
        }
        MyAppWidget().update(context, glanceId)

        // 2) Get the app widget ID and enqueue worker for this widget instance
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        enqueueRefresh(context, appWidgetId)
    }
}

class TimelineWidgetWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val appWidgetId = inputData.getInt(WorkerInput.APP_WIDGET_ID, -1)
        if (appWidgetId == -1) return Result.failure()

        return try {
            // Get database instance
            val database = Room.databaseBuilder(
                applicationContext,
                AppDatabase::class.java,
                "experiences_db"
            ).build()
            val experienceDao = database.experienceDao()

            // Fetch latest experiences with ingestions
            val experiences = experienceDao.getSortedExperiencesWithIngestionsFlow().first()
            
            // Determine if there's a timeline worth showing
            val (fetchedTitle, fetchedTimelineOption, fetchedTimeOption) = if (experiences.isEmpty()) {
                Triple("Timeline", "NotWorthDrawing", "Absolute")
            } else {
                val latestExperience = experiences.first()
                val hasIngestions = latestExperience.ingestions.isNotEmpty()
                
                if (!hasIngestions) {
                    Triple(
                        latestExperience.experience.title.ifEmpty { "Timeline" },
                        "NotWorthDrawing",
                        "Absolute"
                    )
                } else {
                    // Check if ingestions are recent (within last 48 hours)
                    val now = Instant.now()
                    val recentIngestions = latestExperience.ingestions.filter {
                        ChronoUnit.HOURS.between(it.time, now) <= 48
                    }
                    
                    val timeOption = if (recentIngestions.isNotEmpty()) "Relative" else "Absolute"
                    
                    Triple(
                        latestExperience.experience.title.ifEmpty { "Timeline" },
                        "Shown",
                        timeOption
                    )
                }
            }

            val manager = GlanceAppWidgetManager(applicationContext)
            val glanceId = manager.getGlanceIdBy(appWidgetId)

            updateAppWidgetState(
                context = applicationContext,
                definition = PreferencesGlanceStateDefinition,
                glanceId = glanceId
            ) { prefs: Preferences ->
                prefs.toMutablePreferences().apply {
                    this[WidgetKeys.TITLE] = fetchedTitle
                    this[WidgetKeys.TIMELINE_OPTION] = fetchedTimelineOption
                    this[WidgetKeys.TIME_OPTION] = fetchedTimeOption
                    this[WidgetKeys.IS_LOADING] = false
                }
            }

            MyAppWidget().update(applicationContext, glanceId)
            database.close()
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }
}

fun enqueueRefresh(context: Context, appWidgetId: Int) {
    val work = OneTimeWorkRequestBuilder<TimelineWidgetWorker>()
        .setInputData(workDataOf(WorkerInput.APP_WIDGET_ID to appWidgetId))
        .build()

    // Make the unique name per-widget, otherwise multiple widgets fight each other.
    WorkManager.getInstance(context).enqueueUniqueWork(
        "timeline-widget-refresh-$appWidgetId",
        ExistingWorkPolicy.REPLACE,
        work
    )
}