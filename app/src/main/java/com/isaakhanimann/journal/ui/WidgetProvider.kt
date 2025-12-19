package com.isaakhanimann.journal.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.isaakhanimann.journal.MainActivity
import com.isaakhanimann.journal.data.room.AppDatabase
import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.relations.IngestionWithCompanion
import com.isaakhanimann.journal.data.substances.AdministrationRoute
import com.isaakhanimann.journal.data.substances.classes.roa.RoaDuration
import com.isaakhanimann.journal.data.substances.parse.SubstanceParser
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.shapeAlpha
import com.isaakhanimann.journal.ui.theme.md_theme_dark_primary
import com.isaakhanimann.journal.ui.theme.md_theme_light_primary
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

object WidgetKeys {
    val TITLE = stringPreferencesKey("title")
    val INGESTIONS_TEXT = stringPreferencesKey("ingestionsText")
    val IS_LOADING = booleanPreferencesKey("isLoading")
    val HAS_DATA = booleanPreferencesKey("hasData")
    val TIMELINE_IMAGE_PATH = stringPreferencesKey("timelineImagePath")
    val SUBSTANCE_COLORS = stringPreferencesKey("substanceColors") // JSON map of substance name to color name
}

private object WidgetConstants {
    const val REFRESH_BUTTON_ICON = "↻"
    const val ADD_BUTTON_ICON = "+"
    const val PEAK_HEIGHT_FRACTION = 0.85f
    const val STROKE_WIDTH = 5f
    const val CORNER_PATH_EFFECT_RADIUS = 15f
    const val INGESTION_DOT_RADIUS = 7f
}

private object WorkerInput {
    const val APP_WIDGET_ID = "appWidgetId"
}

class MyWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MyAppWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Start periodic widget updates when widget is enabled
        schedulePeriodicWidgetUpdates(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Cancel periodic updates when all widgets are removed
        WorkManager.getInstance(context).cancelUniqueWork("timeline-widget-periodic-refresh")
    }
}

class MyAppWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val title = prefs[WidgetKeys.TITLE] ?: "Psychonautwiki Journal"
            val ingestionsText = prefs[WidgetKeys.INGESTIONS_TEXT] ?: ""
            val isLoading = prefs[WidgetKeys.IS_LOADING] ?: false
            val hasData = prefs[WidgetKeys.HAS_DATA] ?: false
            val timelineImagePath = prefs[WidgetKeys.TIMELINE_IMAGE_PATH]
            val substanceColorsJson = prefs[WidgetKeys.SUBSTANCE_COLORS] ?: "{}"

            // Parse substance colors from JSON
            val substanceColors = parseSubstanceColors(substanceColorsJson)

            // Create intent to open the app with add ingestion action
            val addIngestionIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.isaakhanimann.journal.ADD_INGESTION"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            Column(
                modifier = GlanceModifier
                    // Fix: Use GlanceTheme instead of MaterialTheme for proper widget support
                    .background(GlanceTheme.colors.background)
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
                        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Button(
                        text = WidgetConstants.ADD_BUTTON_ICON,
                        onClick = actionStartActivity(addIngestionIntent)
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Button(
                        text = WidgetConstants.REFRESH_BUTTON_ICON,
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
                        Column {
                            Text(
                                text = "No current ingestions",
                                style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.primary)
                            )
                            Spacer(modifier = GlanceModifier.height(8.dp))
                            Button(
                                text = "Add Ingestion",
                                onClick = actionStartActivity(addIngestionIntent)
                            )
                        }
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
                                            .height(120.dp),
                                        contentScale = ContentScale.FillBounds
                                    )
                                    Spacer(modifier = GlanceModifier.height(4.dp))
                                }
                            }
                        }
                        // Display ingestions with colored substance names
                        Column {
                            ingestionsText.split("\n").take(7).forEach { line ->
                                val substanceName = extractSubstanceName(line)
                                val color = substanceColors[substanceName]
                                if (color != null) {
                                    Text(
                                        text = line,
                                        style = TextStyle(
                                            fontSize = 12.sp,
                                            color = androidx.glance.unit.ColorProvider(
                                                androidx.compose.ui.graphics.Color(color)
                                            )
                                        ),
                                        maxLines = 1
                                    )
                                } else {
                                    Text(
                                        text = line,
                                        style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.primary),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Simple JSON parser for substance colors format: {"substance1":color1,"substance2":color2}
     * Handles escaped characters in substance names.
     * Note: Uses simple regex-based parsing - substance names in PsychonautWiki 
     * typically don't contain problematic characters, but escaping is supported.
     */
    private fun parseSubstanceColors(json: String): Map<String, Int> {
        return try {
            val result = mutableMapOf<String, Int>()
            // Use regex to find "name":value pairs more robustly
            val pattern = "\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:\\s*(-?\\d+)".toRegex()
            pattern.findAll(json).forEach { match ->
                val escapedName = match.groupValues[1]
                val colorInt = match.groupValues[2].toIntOrNull()
                if (colorInt != null) {
                    // Unescape the name
                    val name = escapedName
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                        .replace("\\n", "\n")
                        .replace("\\r", "\r")
                        .replace("\\t", "\t")
                    result[name] = colorInt
                }
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun extractSubstanceName(line: String): String? {
        // Extract substance name from format: "• SubstanceName (dose) - time"
        return try {
            val withoutBullet = line.removePrefix("• ").trim()
            val parenIndex = withoutBullet.indexOf('(')
            if (parenIndex > 0) {
                withoutBullet.substring(0, parenIndex).trim()
            } else {
                null
            }
        } catch (e: Exception) {
            null
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

    companion object {
        private const val TAG = "TimelineWidgetWorker"
    }

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

            // Load substance data for accurate timeline durations
            val substanceDurations = loadSubstanceDurations(applicationContext)

            // Fetch latest ingestions directly sorted by time
            val ingestions = experienceDao.getSortedIngestionsFlow().first()

            val now = Instant.now()
            
            // For the graph, we need to fetch ingestions that could still be active
            // Use a longer time window (24h back) to catch long-acting substances
            val graphStartTime = now.minus(Duration.ofHours(2))
            val graphEndTime = now.plus(Duration.ofHours(3))
            val fetchStartTime = now.minus(Duration.ofHours(24)) // Fetch 24h back for long-acting substances
            
            val allRecentIngestionsWithCompanions = experienceDao.getIngestionsWithCompanions(
                fromInstant = fetchStartTime,
                toInstant = graphEndTime,
            )
            
            // Filter to only include ingestions that are still active during the graph window
            val ingestionsWithCompanions = allRecentIngestionsWithCompanions.filter { ingestionWithCompanion ->
                val ingestion = ingestionWithCompanion.ingestion
                val ingestionTime = ingestion.time
                
                // Get the total duration for this substance
                val roaDuration = substanceDurations[ingestion.substanceName]?.get(ingestion.administrationRoute)
                
                // Calculate the end time of the effect
                val totalDurationSec = if (roaDuration != null) {
                    val onset = roaDuration.onset?.interpolateAtValueInSeconds(0.5f) ?: 1800f
                    val comeup = roaDuration.comeup?.interpolateAtValueInSeconds(0.5f) ?: 2700f
                    val peak = roaDuration.peak?.interpolateAtValueInSeconds(0.5f) ?: 5400f
                    val offset = roaDuration.offset?.interpolateAtValueInSeconds(0.5f) ?: 5400f
                    onset + comeup + peak + offset
                } else {
                    // Default total duration of 6 hours if no data available
                    6 * 3600f
                }
                
                val effectEndTime = ingestionTime.plusSeconds(totalDurationSec.toLong())
                
                // Include if the effect overlaps with the graph window
                ingestionTime.isBefore(graphEndTime) && effectEndTime.isAfter(graphStartTime)
            }

            val (title, ingestionsText, hasData, timelineImagePath, substanceColors) = if (ingestions.isEmpty()) {
                WidgetData("Journal", "", false, null, emptyMap())
            } else {
                // Take the most recent ingestions (up to 7)
                val recentIngestions = ingestions.take(7)
                
                // Get colors for substances from companions
                val colorsMap = mutableMapOf<String, Int>()
                val nightModeFlags = applicationContext.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK
                val isDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
                
                // Get colors from ingestions with companions
                allRecentIngestionsWithCompanions.forEach { ingestionWithCompanion ->
                    val name = ingestionWithCompanion.ingestion.substanceName
                    val color = ingestionWithCompanion.substanceCompanion?.color
                    if (color != null && !colorsMap.containsKey(name)) {
                        colorsMap[name] = getAndroidColor(color, isDarkMode)
                    }
                }

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

                // Generate timeline graph bitmap with accurate substance durations
                val imagePath = if (ingestionsWithCompanions.isNotEmpty()) {
                    generateTimelineGraph(
                        context = applicationContext,
                        ingestions = ingestionsWithCompanions,
                        appWidgetId = appWidgetId,
                        substanceDurations = substanceDurations
                    )
                } else {
                    null
                }
                WidgetData("Journal", lines.joinToString("\n"), true, imagePath, colorsMap)
            }

            // Serialize substance colors to JSON with proper escaping
            val substanceColorsJson = substanceColors.entries.joinToString(",", "{", "}") { (name, color) ->
                val escapedName = name
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
                "\"$escapedName\":$color"
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
                    this[WidgetKeys.SUBSTANCE_COLORS] = substanceColorsJson
                    if (timelineImagePath != null) {
                        this[WidgetKeys.TIMELINE_IMAGE_PATH] = timelineImagePath
                    }
                }
            }

            MyAppWidget().update(applicationContext, glanceId)
            database.close()
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "Error updating widget", t)
            Result.retry()
        }
    }

    private data class WidgetData(
        val title: String,
        val ingestionsText: String,
        val hasData: Boolean,
        val timelineImagePath: String?,
        val substanceColors: Map<String, Int>
    )

    private fun generateTimelineGraph(
        context: Context,
        ingestions: List<IngestionWithCompanion>,
        appWidgetId: Int,
        substanceDurations: Map<String, Map<AdministrationRoute, RoaDuration?>>
    ): String? {
        if (ingestions.isEmpty()) return null

        // Detect dark mode
        val nightModeFlags = context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES

        val width = 600
        val height = 120
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)

        val now = Instant.now()
        val startTime = now.minus(Duration.ofHours(2))
        val endTime = now.plus(Duration.ofHours(3))
        val totalSeconds = Duration.ofHours(5).seconds.toFloat()

        val padding = 16f
        val labelHeight = 18f
        val graphWidth = width - 2 * padding
        val graphHeight = height - 2 * padding - labelHeight
        val baselineY = height - padding - labelHeight

        val gridColor = if (isDarkMode) {
            md_theme_dark_primary.toArgb()
        } else {
            md_theme_light_primary.toArgb()
        }

        val gridPaint = Paint().apply {
            color = Color.argb(60, Color.red(gridColor), Color.green(gridColor), Color.blue(gridColor))
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        // Draw vertical hour markers
        for (i in 0..5) {
            val x = padding + (graphWidth * i / 5f)
            canvas.drawLine(x, padding, x, baselineY, gridPaint)
        }

        // Group ingestions by substance and route for layered rendering
        val groupedBySubstanceAndRoute = ingestions.groupBy { 
            "${it.ingestion.substanceName}|${it.ingestion.administrationRoute}" 
        }

        // Draw each substance's effect curve using actual duration data
        groupedBySubstanceAndRoute.forEach { (key, substanceIngestions) ->
            val companion = substanceIngestions.firstOrNull()?.substanceCompanion
            val color = companion?.color ?: AdaptiveColor.BLUE
            val androidColor = getAndroidColor(color, isDarkMode)

            val fillPaint = Paint().apply {
                this.color = Color.argb(
                    (shapeAlpha * 255).toInt(), // Use app's shapeAlpha constant
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
                strokeWidth = WidgetConstants.STROKE_WIDTH
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                // Add corner path effect for smoother transitions (matches app's normalStroke)
                pathEffect = android.graphics.CornerPathEffect(WidgetConstants.CORNER_PATH_EFFECT_RADIUS)
            }

            substanceIngestions.forEach { ingestionWithCompanion ->
                val ingestion = ingestionWithCompanion.ingestion
                val ingestionTime = ingestion.time

                // Get RoaDuration for this substance and route
                val roaDuration = substanceDurations[ingestion.substanceName]?.get(ingestion.administrationRoute)

                // Calculate duration phases in seconds with fallback to default values
                val onsetSec = roaDuration?.onset?.interpolateAtValueInSeconds(0.5f) ?: 1800f // 30 min default
                val comeupSec = roaDuration?.comeup?.interpolateAtValueInSeconds(0.5f) ?: 2700f // 45 min default
                val peakSec = roaDuration?.peak?.interpolateAtValueInSeconds(0.5f) ?: 5400f // 1.5 hr default
                val offsetSec = roaDuration?.offset?.interpolateAtValueInSeconds(0.5f) ?: 5400f // 1.5 hr default

                // Calculate x positions (in seconds from graph start)
                val secondsFromStart = Duration.between(startTime, ingestionTime).seconds.toFloat()
                
                // These are the actual timeline phase positions in seconds from graph start
                val ingestionX = secondsFromStart
                val onsetEndX = ingestionX + onsetSec
                val comeupEndX = onsetEndX + comeupSec
                val peakEndX = comeupEndX + peakSec
                val offsetEndX = peakEndX + offsetSec

                // Calculate peak height
                val peakHeight = graphHeight * WidgetConstants.PEAK_HEIGHT_FRACTION
                val peakY = baselineY - peakHeight

                // Convert seconds to pixels
                fun secToPixel(sec: Float): Float = padding + (sec / totalSeconds) * graphWidth

                // Create a list of points that define the timeline shape
                // Format: Pair(seconds from graph start, height fraction 0-1)
                val timelinePoints = mutableListOf<Pair<Float, Float>>()
                
                // Ingestion point (start, at baseline)
                timelinePoints.add(Pair(ingestionX, 0f))
                // End of onset (still at baseline)
                timelinePoints.add(Pair(onsetEndX, 0f))
                // End of comeup (at peak)
                timelinePoints.add(Pair(comeupEndX, 1f))
                // End of peak (still at peak)
                timelinePoints.add(Pair(peakEndX, 1f))
                // End of offset (back to baseline)
                timelinePoints.add(Pair(offsetEndX, 0f))

                // Filter to only include points that are within or adjacent to visible window
                // and interpolate for edge cases
                val visibleStartSec = 0f
                val visibleEndSec = totalSeconds

                // Build the visible path
                val path = Path()
                var pathStarted = false
                var firstVisibleX = padding
                var lastVisibleX = padding

                for (i in 0 until timelinePoints.size) {
                    val (currentSec, currentHeight) = timelinePoints[i]
                    val currentPixelX = secToPixel(currentSec)
                    val currentY = baselineY - (currentHeight * peakHeight)

                    if (currentSec >= visibleStartSec && currentSec <= visibleEndSec) {
                        // Point is visible
                        if (!pathStarted) {
                            // Check if we need to interpolate entry point
                            if (i > 0) {
                                val (prevSec, prevHeight) = timelinePoints[i - 1]
                                if (prevSec < visibleStartSec) {
                                    // Interpolate entry point
                                    val t = (visibleStartSec - prevSec) / (currentSec - prevSec)
                                    val entryHeight = prevHeight + t * (currentHeight - prevHeight)
                                    val entryY = baselineY - (entryHeight * peakHeight)
                                    path.moveTo(padding, entryY)
                                    firstVisibleX = padding
                                } else {
                                    path.moveTo(currentPixelX.coerceIn(padding, width - padding), currentY)
                                    firstVisibleX = currentPixelX.coerceIn(padding, width - padding)
                                }
                            } else {
                                path.moveTo(currentPixelX.coerceIn(padding, width - padding), currentY)
                                firstVisibleX = currentPixelX.coerceIn(padding, width - padding)
                            }
                            pathStarted = true
                        } else {
                            path.lineTo(currentPixelX.coerceIn(padding, width - padding), currentY)
                        }
                        lastVisibleX = currentPixelX.coerceIn(padding, width - padding)
                    } else if (currentSec > visibleEndSec && pathStarted) {
                        // Point is after visible window, interpolate exit
                        val (prevSec, prevHeight) = timelinePoints[i - 1]
                        if (prevSec < visibleEndSec) {
                            val t = (visibleEndSec - prevSec) / (currentSec - prevSec)
                            val exitHeight = prevHeight + t * (currentHeight - prevHeight)
                            val exitY = baselineY - (exitHeight * peakHeight)
                            path.lineTo(width - padding, exitY)
                            lastVisibleX = width - padding
                        }
                        break
                    }
                }

                if (pathStarted) {
                    // Draw stroke
                    canvas.drawPath(path, strokePaint)

                    // Create filled path (close it for fill)
                    val fillPath = Path(path)
                    fillPath.lineTo(lastVisibleX, baselineY + strokePaint.strokeWidth / 2)
                    fillPath.lineTo(firstVisibleX, baselineY + strokePaint.strokeWidth / 2)
                    fillPath.close()
                    canvas.drawPath(fillPath, fillPaint)

                    // Draw ingestion dot at the start point if visible
                    if (secondsFromStart >= 0 && secondsFromStart <= totalSeconds) {
                        val dotPaint = Paint().apply {
                            this.color = androidColor
                            style = Paint.Style.FILL
                            isAntiAlias = true
                        }
                        val dotX = secToPixel(secondsFromStart).coerceIn(padding, width - padding)
                        canvas.drawCircle(dotX, baselineY, WidgetConstants.INGESTION_DOT_RADIUS, dotPaint)
                    }
                }
            }
        }

        // Draw current time indicator at the correct position
        val nowSecondsFromStart = Duration.between(startTime, now).seconds.toFloat()
        val currentTimeX = padding + (nowSecondsFromStart / totalSeconds) * graphWidth
        val nowPaint = Paint().apply {
            color = if (isDarkMode) Color.WHITE else Color.BLACK
            strokeWidth = 3f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(currentTimeX, padding, currentTimeX, baselineY, nowPaint)

        // Draw time labels
        val textPaint = Paint().apply {
            color = if (isDarkMode) Color.argb(180, 255, 255, 255) else Color.argb(180, 0, 0, 0)
            textSize = 18f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("-2h", padding + 20, height - 4f, textPaint)
        canvas.drawText("now", currentTimeX, height - 4f, textPaint)
        canvas.drawText("+3h", width - padding - 15, height - 4f, textPaint)

        // Save bitmap to file
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

    private fun loadSubstanceDurations(context: Context): Map<String, Map<AdministrationRoute, RoaDuration?>> {
        return try {
            val fileContent = context.assets.open("Substances.json").bufferedReader().use { it.readText() }
            val parser = SubstanceParser()
            val substanceFile = parser.parseSubstanceFile(fileContent)
            substanceFile.substances.associate { substance ->
                substance.name to substance.roas.associate { roa ->
                    roa.route to roa.roaDuration
                }
            }
        } catch (e: Exception) {
            Log.w("WidgetProvider", "Failed to load substance durations", e)
            emptyMap()
        }
    }

    private fun getAndroidColor(color: AdaptiveColor, isDarkTheme: Boolean): Int {
        // Map AdaptiveColor to Android color int - matches the values in AdaptiveColor.kt
        return when (color) {
            // Preferred colors
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
            
            // Extended colors
            AdaptiveColor.FIRE_ENGINE_RED -> if (isDarkTheme) android.graphics.Color.rgb(237, 43, 42) else android.graphics.Color.rgb(237, 14, 6)
            AdaptiveColor.CORAL -> if (isDarkTheme) android.graphics.Color.rgb(255, 131, 121) else android.graphics.Color.rgb(180, 92, 85)
            AdaptiveColor.TOMATO -> if (isDarkTheme) android.graphics.Color.rgb(255, 99, 71) else android.graphics.Color.rgb(180, 69, 50)
            AdaptiveColor.CINNABAR -> android.graphics.Color.rgb(227, 36, 0)
            AdaptiveColor.RUST -> android.graphics.Color.rgb(199, 81, 58)
            AdaptiveColor.ORANGE_RED -> if (isDarkTheme) android.graphics.Color.rgb(255, 69, 0) else android.graphics.Color.rgb(205, 55, 0)
            AdaptiveColor.AUBURN -> if (isDarkTheme) android.graphics.Color.rgb(217, 80, 0) else android.graphics.Color.rgb(173, 62, 0)
            AdaptiveColor.SADDLE_BROWN -> if (isDarkTheme) android.graphics.Color.rgb(191, 95, 25) else android.graphics.Color.rgb(139, 69, 19)
            AdaptiveColor.DARK_ORANGE -> if (isDarkTheme) android.graphics.Color.rgb(255, 140, 0) else android.graphics.Color.rgb(155, 84, 0)
            AdaptiveColor.DARK_GOLD -> android.graphics.Color.rgb(169, 104, 0)
            AdaptiveColor.KHAKI -> if (isDarkTheme) android.graphics.Color.rgb(203, 183, 137) else android.graphics.Color.rgb(128, 114, 86)
            AdaptiveColor.BRONZE -> if (isDarkTheme) android.graphics.Color.rgb(167, 123, 0) else android.graphics.Color.rgb(120, 87, 0)
            AdaptiveColor.GOLD -> if (isDarkTheme) android.graphics.Color.rgb(255, 215, 0) else android.graphics.Color.rgb(130, 109, 0)
            AdaptiveColor.OLIVE -> if (isDarkTheme) android.graphics.Color.rgb(141, 134, 0) else android.graphics.Color.rgb(102, 97, 0)
            AdaptiveColor.OLIVE_DRAB -> if (isDarkTheme) android.graphics.Color.rgb(154, 166, 14) else android.graphics.Color.rgb(111, 118, 8)
            AdaptiveColor.DARK_OLIVE_GREEN -> if (isDarkTheme) android.graphics.Color.rgb(105, 133, 58) else android.graphics.Color.rgb(85, 107, 47)
            AdaptiveColor.MOSS_GREEN -> if (isDarkTheme) android.graphics.Color.rgb(102, 156, 53) else android.graphics.Color.rgb(79, 122, 40)
            AdaptiveColor.LIME_GREEN -> if (isDarkTheme) android.graphics.Color.rgb(0, 255, 0) else android.graphics.Color.rgb(0, 130, 0)
            AdaptiveColor.LIME -> if (isDarkTheme) android.graphics.Color.rgb(50, 205, 50) else android.graphics.Color.rgb(32, 130, 32)
            AdaptiveColor.FOREST_GREEN -> if (isDarkTheme) android.graphics.Color.rgb(34, 139, 34) else android.graphics.Color.rgb(28, 114, 28)
            AdaptiveColor.SEA_GREEN -> if (isDarkTheme) android.graphics.Color.rgb(46, 139, 87) else android.graphics.Color.rgb(38, 114, 71)
            AdaptiveColor.JUNGLE_GREEN -> android.graphics.Color.rgb(3, 136, 88)
            AdaptiveColor.LIGHT_SEA_GREEN -> if (isDarkTheme) android.graphics.Color.rgb(32, 178, 170) else android.graphics.Color.rgb(22, 128, 122)
            AdaptiveColor.DARK_TURQUOISE -> if (isDarkTheme) android.graphics.Color.rgb(0, 206, 209) else android.graphics.Color.rgb(0, 131, 134)
            AdaptiveColor.DODGER_BLUE -> if (isDarkTheme) android.graphics.Color.rgb(30, 144, 255) else android.graphics.Color.rgb(24, 116, 205)
            AdaptiveColor.ROYAL_BLUE -> if (isDarkTheme) android.graphics.Color.rgb(72, 117, 251) else android.graphics.Color.rgb(65, 105, 225)
            AdaptiveColor.DEEP_LAVENDER -> android.graphics.Color.rgb(135, 78, 254)
            AdaptiveColor.BLUE_VIOLET -> if (isDarkTheme) android.graphics.Color.rgb(166, 73, 252) else android.graphics.Color.rgb(138, 43, 226)
            AdaptiveColor.DARK_VIOLET -> if (isDarkTheme) android.graphics.Color.rgb(162, 76, 210) else android.graphics.Color.rgb(148, 0, 211)
            AdaptiveColor.HELIOTROPE -> android.graphics.Color.rgb(151, 93, 175)
            AdaptiveColor.BYZANTIUM -> if (isDarkTheme) android.graphics.Color.rgb(190, 56, 243) else android.graphics.Color.rgb(153, 41, 189)
            AdaptiveColor.MAGENTA -> if (isDarkTheme) android.graphics.Color.rgb(255, 0, 255) else android.graphics.Color.rgb(205, 0, 205)
            AdaptiveColor.DARK_MAGENTA -> if (isDarkTheme) android.graphics.Color.rgb(217, 0, 217) else android.graphics.Color.rgb(139, 0, 139)
            AdaptiveColor.FUCHSIA -> if (isDarkTheme) android.graphics.Color.rgb(214, 68, 146) else android.graphics.Color.rgb(189, 60, 129)
            AdaptiveColor.DEEP_PINK -> if (isDarkTheme) android.graphics.Color.rgb(255, 20, 147) else android.graphics.Color.rgb(205, 16, 117)
            AdaptiveColor.GRAYISH_MAGENTA -> android.graphics.Color.rgb(161, 96, 128)
            AdaptiveColor.HOT_PINK -> if (isDarkTheme) android.graphics.Color.rgb(255, 105, 180) else android.graphics.Color.rgb(180, 74, 126)
            AdaptiveColor.JAZZBERRY_JAM -> if (isDarkTheme) android.graphics.Color.rgb(230, 59, 122) else android.graphics.Color.rgb(185, 45, 93)
            AdaptiveColor.MAROON -> if (isDarkTheme) android.graphics.Color.rgb(187, 82, 99) else android.graphics.Color.rgb(190, 49, 68)
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

/**
 * Schedule periodic widget updates every 15 minutes (minimum interval for WorkManager).
 * This ensures the widget automatically refreshes without user interaction.
 */
fun schedulePeriodicWidgetUpdates(context: Context) {
    val periodicWork = PeriodicWorkRequestBuilder<PeriodicWidgetRefreshWorker>(
        15, TimeUnit.MINUTES
    ).build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "timeline-widget-periodic-refresh",
        ExistingPeriodicWorkPolicy.KEEP,
        periodicWork
    )
}

/**
 * Worker that refreshes all widget instances periodically.
 */
class PeriodicWidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val manager = GlanceAppWidgetManager(applicationContext)
            val glanceIds = manager.getGlanceIds(MyAppWidget::class.java)

            for (glanceId in glanceIds) {
                val appWidgetId = manager.getAppWidgetId(glanceId)
                enqueueRefresh(applicationContext, appWidgetId)
            }
            Result.success()
        } catch (e: Exception) {
            Log.w("PeriodicWidgetRefreshWorker", "Widget refresh failed", e)
            Result.retry()
        }
    }
}