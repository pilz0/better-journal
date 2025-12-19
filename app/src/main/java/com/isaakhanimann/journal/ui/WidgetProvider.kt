package com.isaakhanimann.journal.ui

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.update
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
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf

object WidgetKeys {
    val TITLE = stringPreferencesKey("title")
    val TIMELINE_OPTION = stringPreferencesKey("timelineDisplayOption")
    val TIME_OPTION = stringPreferencesKey("timeDisplayOption")
    val IS_LOADING = booleanPreferencesKey("isLoading")
}

private object RefreshParams {
    val GLANCE_ID = ActionParameters.Key<String>("glanceId")
}
private object WorkerInput {
    const val GLANCE_ID = "glanceId"
}

class MyWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MyAppWidget()
}

class MyAppWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    companion object {
        fun refreshAction(glanceId: GlanceId) = actionRunCallback<RefreshAction>(
            parameters = actionParametersOf(
                RefreshParams.GLANCE_ID to glanceId.toString()
            )
        )
    }

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
                    onClick = refreshAction(id),
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

        // 2) Enqueue worker for this widget instance
        val glanceIdStr = parameters[RefreshParams.GLANCE_ID] ?: glanceId.toString()
        enqueueRefresh(context, glanceIdStr)
    }
}

class TimelineWidgetWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val glanceIdStr = inputData.getString(WorkerInput.GLANCE_ID)
            ?: return Result.failure()

        return try {
            // TODO: replace with your real fetch logic
            val fetchedTitle = "My Timeline"
            val fetchedTimelineOption = "Shown"
            val fetchedTimeOption = "Relative"

            val manager = GlanceAppWidgetManager(applicationContext)
            val glanceId = manager.getGlanceIdBy(glanceIdStr)

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
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }
}

fun enqueueRefresh(context: Context, glanceIdStr: String) {
    val work = OneTimeWorkRequestBuilder<TimelineWidgetWorker>()
        .setInputData(workDataOf(WorkerInput.GLANCE_ID to glanceIdStr))
        .build()

    // Make the unique name per-widget, otherwise multiple widgets fight each other.
    WorkManager.getInstance(context).enqueueUniqueWork(
        "timeline-widget-refresh-$glanceIdStr",
        ExistingWorkPolicy.REPLACE,
        work
    )
}