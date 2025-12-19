package com.isaakhanimann.journal.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.layout.ContentScale
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
import androidx.glance.GlanceTheme
import androidx.glance.background
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.isaakhanimann.journal.data.room.AppDatabase
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.relations.IngestionWithCompanion
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import java.time.Instant
import com.isaakhanimann.journal.ui.theme.md_theme_dark_primary
import androidx.core.graphics.createBitmap
import com.isaakhanimann.journal.ui.theme.md_theme_light_primary

object WidgetKeys {
    val TITLE = stringPreferencesKey("title")
    val INGESTIONS_TEXT = stringPreferencesKey("ingestionsText")
    val IS_LOADING = booleanPreferencesKey("isLoading")
    val HAS_DATA = booleanPreferencesKey("hasData")
    val TIMELINE_IMAGE_PATH = stringPreferencesKey("timelineImagePath")
}

private object WorkerInput {
    const val APP_WIDGET_ID = "appWidgetId"
}

class MyWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MyAppWidget()
}
@OptIn(ExperimentalMaterial3Api::class)
class MyAppWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val title = prefs[WidgetKeys.TITLE] ?: "Journal"
            val ingestionsText = prefs[WidgetKeys.INGESTIONS_TEXT] ?: ""
            val isLoading = prefs[WidgetKeys.IS_LOADING] ?: false
            val hasData = prefs[WidgetKeys.HAS_DATA] ?: false
            val timelineImagePath = prefs[WidgetKeys.TIMELINE_IMAGE_PATH]

            Column(
                modifier = GlanceModifier
                    // Fix: Use GlanceTheme instead of MaterialTheme for proper widget support
                    .background(GlanceTheme.colors.background)
                    .fillMaxSize()
                    .padding(12.dp),

                verticalAlignment = Alignment.Vertical.Top
            ) {
                Row(
                    modifier = GlanceModifier .fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = TextStyle(color = GlanceTheme.colors.primary ,fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Button(
                        text = "↻",
                        style = TextStyle(color = GlanceTheme.colors.primary ,fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        onClick = actionRunCallback<RefreshAction>()
                    )
                }

                Spacer(modifier = GlanceModifier.height(4.dp))

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
                            style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.primary)

                        )
                    }
                    else -> {
                        // Display timeline graph image if available
                        // Note: Bitmap memory is managed by Glance internally when passed to ImageProvider
                        if (timelineImagePath != null) {
                            val file = File(timelineImagePath)
                            if (file.exists()) {
                                android.graphics.BitmapFactory.decodeFile(timelineImagePath)?.let { bitmap ->
                                    Image(
                                        provider = ImageProvider(bitmap),
                                        contentDescription = "Timeline graph",
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .height(80.dp),
                                        contentScale = ContentScale.FillBounds
                                    )
                                    Spacer(modifier = GlanceModifier.height(4.dp))
                                }
                            }
                        }

                        Text(
                            text = ingestionsText,
                            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.primary),
                            maxLines = 8
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

            // Also get ingestions with companion for colors (last 24 hours for the graph)
            val now = Instant.now()
            val nowMinusOneHour = now.minus(Duration.ofHours(2))
            val nowPlusThreeHours = now.plus(Duration.ofHours(3))
            val ingestionsWithCompanions = experienceDao.getIngestionsWithCompanions(
                fromInstant = nowMinusOneHour,
                toInstant = nowPlusThreeHours,
            )

            val (title, ingestionsText, hasData, timelineImagePath) = if (ingestions.isEmpty()) {
                WidgetData("Journal", "", false, null)
            } else {
                // Take the most recent ingestions (up to 5)
                val recentIngestions = ingestions.take(5)

                val lines = recentIngestions.map { ingestion ->
                    val timeText = formatRelativeTime(ingestion.time, nowPlusThreeHours)
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

                // Generate timeline graph bitmap
                val imagePath = if (ingestionsWithCompanions.isNotEmpty()) {
                    generateTimelineGraph(
                        context = applicationContext,
                        ingestions = ingestionsWithCompanions,
                        appWidgetId = appWidgetId
                    )
                } else {
                    null
                }
                WidgetData( "Journal",lines.joinToString("\n"), true, imagePath)
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
                    if (timelineImagePath != null) {
                        this[WidgetKeys.TIMELINE_IMAGE_PATH] = timelineImagePath
                    }
                }
            }

            MyAppWidget().update(applicationContext, glanceId)
            database.close()
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    private data class WidgetData(
        val title: String,
        val ingestionsText: String,
        val hasData: Boolean,
        val timelineImagePath: String?
    )

    private fun generateTimelineGraph(
        context: Context,
        ingestions: List<IngestionWithCompanion>,
        appWidgetId: Int
    ): String? {
        if (ingestions.isEmpty()) return null

        val width = 600
        val height = 160
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)

        val now = Instant.now()
        val nowMinusOneHour = now.minus(Duration.ofHours(2))
        val nowPlusThreeHours = now.plus(Duration.ofHours(3))
        val totalSeconds = Duration.ofHours(5).seconds.toFloat()

        val padding = 16f
        val graphWidth = width - 2 * padding
        val graphHeight = height - 2 * padding
        val baselineY = height - padding

        fun getGridColor(context: Context): Int {
            val nightModeFlags = context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val isDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES

            return if (isDarkMode) {
                md_theme_dark_primary.toArgb()
            } else {
                // TODO: Replace with your light theme color if desired
                md_theme_light_primary.toArgb()
            }
        }

        val gridPaint = Paint().apply {
            color = getGridColor(context) // Pass context here
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }


        // Vertical lines for every hour
        for (i in 0..5) {
            val x = padding + (graphWidth * i / 5f)
            canvas.drawLine(x, padding, x, baselineY, gridPaint)
        }

        // Horizontal baseline
        canvas.drawLine(padding, baselineY, width - padding, baselineY, gridPaint)

        // Group ingestions by substance for layered rendering
        val groupedBySubstance = ingestions.groupBy { it.ingestion.substanceName }

        // Draw each substance's effect curve
        groupedBySubstance.forEach { (_, substanceIngestions) ->
            val companion = substanceIngestions.firstOrNull()?.substanceCompanion
            val color = companion?.color ?: AdaptiveColor.BLUE
            val androidColor = getAndroidColor(color, isDarkTheme = true)

            val fillPaint = Paint().apply {
                this.color = Color.argb(
                    80,
                    Color.red(androidColor),
                    Color.green(androidColor),
                    Color.blue(androidColor)
                )
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            val strokePaint = Paint().apply {
                this.color = androidColor
                style = Paint.Style.STROKE
                strokeWidth = 3f
                isAntiAlias = true
            }

            substanceIngestions.forEach { ingestionWithCompanion ->
                val ingestion = ingestionWithCompanion.ingestion
                val ingestionTime = ingestion.time

                // Calculate x position based on time
                val secondsFromStart = Duration.between(nowMinusOneHour, ingestionTime).seconds.toFloat()
                val xPos = padding + (secondsFromStart / totalSeconds) * graphWidth

                // Draw a simplified effect curve for visual representation
                val peakDurationSeconds = Duration.ofHours(2).seconds.toFloat()
                val totalDurationSeconds = Duration.ofHours(6).seconds.toFloat()

                val peakX = xPos + (peakDurationSeconds / totalSeconds) * graphWidth
                val endX = xPos + (totalDurationSeconds / totalSeconds) * graphWidth

                // Clamp to graph bounds
                val clampedPeakX = peakX.coerceIn(padding, width - padding)
                val clampedEndX = endX.coerceIn(padding, width - padding)
                val clampedStartX = xPos.coerceIn(padding, width - padding)

                // Calculate peak height based on dose (normalized)
                val peakHeight = graphHeight * 0.7f
                val peakY = baselineY - peakHeight

                // Create curved path for the effect
                val path = Path().apply {
                    moveTo(clampedStartX, baselineY)

                    // Smooth curve up to peak
                    val cp1x = clampedStartX + (clampedPeakX - clampedStartX) * 0.3f
                    val cp1y = baselineY
                    val cp2x = clampedStartX + (clampedPeakX - clampedStartX) * 0.7f
                    val cp2y = peakY
                    cubicTo(cp1x, cp1y, cp2x, cp2y, clampedPeakX, peakY)

                    // Smooth curve down from peak
                    val cp3x = clampedPeakX + (clampedEndX - clampedPeakX) * 0.3f
                    val cp3y = peakY
                    val cp4x = clampedPeakX + (clampedEndX - clampedPeakX) * 0.7f
                    val cp4y = baselineY
                    cubicTo(cp3x, cp3y, cp4x, cp4y, clampedEndX, baselineY)

                    close()
                }

                canvas.drawPath(path, fillPaint)

                // Draw stroke on top
                val strokePath = Path().apply {
                    moveTo(clampedStartX, baselineY)
                    val cp1x = clampedStartX + (clampedPeakX - clampedStartX) * 0.3f
                    val cp1y = baselineY
                    val cp2x = clampedStartX + (clampedPeakX - clampedStartX) * 0.7f
                    val cp2y = peakY
                    cubicTo(cp1x, cp1y, cp2x, cp2y, clampedPeakX, peakY)

                    val cp3x = clampedPeakX + (clampedEndX - clampedPeakX) * 0.3f
                    val cp3y = peakY
                    val cp4x = clampedPeakX + (clampedEndX - clampedPeakX) * 0.7f
                    val cp4y = baselineY
                    cubicTo(cp3x, cp3y, cp4x, cp4y, clampedEndX, baselineY)
                }
                canvas.drawPath(strokePath, strokePaint)

                // Draw a small dot at ingestion point
                val dotPaint = Paint().apply {
                    this.color = androidColor
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawCircle(clampedStartX, baselineY, 6f, dotPaint)
            }
        }

        // Draw current time indicator
        val currentTimeX = width - padding
        val nowPaint = Paint().apply {
            color = Color.WHITE
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(currentTimeX, padding, currentTimeX, baselineY, nowPaint)

        // Draw time labels
        val textPaint = Paint().apply {
            color = Color.argb(180, 255, 255, 255)
            textSize = 20f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("1h ago", padding + 30, height - 2f, textPaint)
        canvas.drawText("In 3h", width - padding - 15, height - 2f, textPaint)

        // Save bitmap to file, ensuring bitmap is recycled even if an error occurs
        val file = File(context.cacheDir, "widget_timeline_$appWidgetId.png")
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } finally {
            bitmap.recycle()
        }

        return file.absolutePath
    }

    private fun getAndroidColor(color: AdaptiveColor, isDarkTheme: Boolean): Int {
        // Map AdaptiveColor to Android color int
        return when (color) {
            AdaptiveColor.RED -> if (isDarkTheme) android.graphics.Color.rgb(255, 69, 58) else android.graphics.Color.rgb(255, 59, 48)
            AdaptiveColor.ORANGE -> if (isDarkTheme) android.graphics.Color.rgb(255, 159, 10) else android.graphics.Color.rgb(255, 149, 0)
            AdaptiveColor.YELLOW -> if (isDarkTheme) android.graphics.Color.rgb(255, 214, 10) else android.graphics.Color.rgb(255, 204, 0)
            AdaptiveColor.GREEN -> if (isDarkTheme) android.graphics.Color.rgb(48, 209, 88) else android.graphics.Color.rgb(52, 199, 89)
            AdaptiveColor.MINT -> if (isDarkTheme) android.graphics.Color.rgb(102, 212, 207) else android.graphics.Color.rgb(0, 199, 190)
            AdaptiveColor.TEAL -> if (isDarkTheme) android.graphics.Color.rgb(64, 200, 224) else android.graphics.Color.rgb(48, 176, 199)
            AdaptiveColor.CYAN -> if (isDarkTheme) android.graphics.Color.rgb(100, 210, 255) else android.graphics.Color.rgb(50, 173, 230)
            AdaptiveColor.BLUE -> if (isDarkTheme) android.graphics.Color.rgb(10, 132, 255) else android.graphics.Color.rgb(0, 122, 255)
            AdaptiveColor.INDIGO -> if (isDarkTheme) android.graphics.Color.rgb(94, 92, 230) else android.graphics.Color.rgb(88, 86, 214)
            AdaptiveColor.PURPLE -> if (isDarkTheme) android.graphics.Color.rgb(191, 90, 242) else android.graphics.Color.rgb(175, 82, 222)
            AdaptiveColor.PINK -> if (isDarkTheme) android.graphics.Color.rgb(255, 55, 95) else android.graphics.Color.rgb(255, 45, 85)
            AdaptiveColor.BROWN -> if (isDarkTheme) android.graphics.Color.rgb(172, 142, 104) else android.graphics.Color.rgb(162, 132, 94)
            // Default case uses BLUE colors for consistency
            else -> getAndroidColor(AdaptiveColor.BLUE, isDarkTheme)
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