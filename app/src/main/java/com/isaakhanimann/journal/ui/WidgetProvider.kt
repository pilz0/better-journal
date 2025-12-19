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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.height
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
import java.time.Duration
import java.time.Instant

object WidgetKeys {
    val TITLE = stringPreferencesKey("title")
    val INGESTIONS_TEXT = stringPreferencesKey("ingestionsText")
    val IS_LOADING = booleanPreferencesKey("isLoading")
    val HAS_DATA = booleanPreferencesKey("hasData")
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
            val title = prefs[WidgetKeys.TITLE] ?: "Journal"
            val ingestionsText = prefs[WidgetKeys.INGESTIONS_TEXT] ?: ""
            val isLoading = prefs[WidgetKeys.IS_LOADING] ?: false
            val hasData = prefs[WidgetKeys.HAS_DATA] ?: false

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.Vertical.Top
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Button(
                        text = "↻",
                        onClick = actionRunCallback<RefreshAction>()
                    )
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                when {
                    isLoading -> {
                        Text(
                            text = "Loading…",
                            style = TextStyle(fontSize = 14.sp)
                        )
                    }
                    !hasData -> {
                        Text(
                            text = "No ingestions yet.\nAdd your first entry in the app.",
                            style = TextStyle(fontSize = 14.sp)
                        )
                    }
                    else -> {
                        Text(
                            text = ingestionsText,
                            style = TextStyle(fontSize = 13.sp),
                            maxLines = 10
                        )
                    }
                }
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

            // Fetch latest ingestions directly sorted by time
            val ingestions = experienceDao.getSortedIngestionsFlow().first()
            
            val (title, ingestionsText, hasData) = if (ingestions.isEmpty()) {
                Triple("Journal", "", false)
            } else {
                // Take the most recent ingestions (up to 5)
                val recentIngestions = ingestions.take(5)
                val now = Instant.now()
                
                val lines = recentIngestions.map { ingestion ->
                    val timeText = formatRelativeTime(ingestion.time, now)
                    val doseText = ingestion.dose?.let { dose ->
                        val doseFormatted = if (dose == dose.toLong().toDouble()) {
                            dose.toLong().toString()
                        } else {
                            dose.toString()
                        }
                        val units = ingestion.units
                        if (units.isNullOrEmpty()) doseFormatted else "$doseFormatted $units"
                    } ?: "unknown dose"
                    "• ${ingestion.substanceName} ($doseText) - $timeText"
                }
                
                Triple("Recent Ingestions", lines.joinToString("\n"), true)
            }

            val manager = GlanceAppWidgetManager(applicationContext)
            val glanceId = manager.getGlanceIdBy(appWidgetId)

            updateAppWidgetState(
                context = applicationContext,
                definition = PreferencesGlanceStateDefinition,
                glanceId = glanceId
            ) { prefs: Preferences ->
                prefs.toMutablePreferences().apply {
                    this[WidgetKeys.TITLE] = title
                    this[WidgetKeys.INGESTIONS_TEXT] = ingestionsText
                    this[WidgetKeys.HAS_DATA] = hasData
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

    private fun formatRelativeTime(time: Instant, now: Instant): String {
        val duration = Duration.between(time, now)
        return when {
            duration.toDays() > 0 -> "${duration.toDays()}d ago"
            duration.toHours() > 0 -> "${duration.toHours()}h ago"
            duration.toMinutes() > 0 -> "${duration.toMinutes()}m ago"
            else -> "just now"
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