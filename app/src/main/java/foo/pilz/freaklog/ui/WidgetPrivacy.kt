package foo.pilz.freaklog.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import java.io.File

internal fun isRegisteredWidget(context: Context, provider: Class<*>, id: Int): Boolean =
    id > 0 && id in AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, provider))

internal suspend fun GlanceAppWidget.updateAllPrivate(context: Context) {
    val manager = GlanceAppWidgetManager(context)
    for (id in manager.getGlanceIds(javaClass)) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) {
            it.toMutablePreferences().apply { clear() }
        }
        val widgetId = manager.getAppWidgetId(id)
        File(context.cacheDir, "widget_heatmap_$widgetId.png").delete()
        File(context.cacheDir, "widget_timeline_$widgetId.png").delete()
        update(context, id)
    }
}

suspend fun refreshPrivateJournalPreviews(context: Context) {
    MyAppWidget().updateAllPrivate(context)
    HeatmapAppWidget().updateAllPrivate(context)
}
