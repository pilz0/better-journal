package foo.pilz.freaklog.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
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
import foo.pilz.freaklog.MainActivity
import foo.pilz.freaklog.data.room.AppDatabase
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanion
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.RoaDuration
import foo.pilz.freaklog.data.substances.parse.SubstanceParser
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.shapeAlpha
import foo.pilz.freaklog.ui.theme.md_theme_dark_primary
import foo.pilz.freaklog.ui.theme.md_theme_light_primary
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

object WidgetKeys {
    val INGESTIONS_TEXT = stringPreferencesKey("ingestionsText")
    val IS_LOADING = booleanPreferencesKey("isLoading")
    val HAS_DATA = booleanPreferencesKey("hasData")
    val TIMELINE_IMAGE_PATH = stringPreferencesKey("timelineImagePath")
    val SUBSTANCE_COLORS = stringPreferencesKey("substanceColors") // JSON map of substance name to color name
    val MOST_RECENT_EXPERIENCE_ID = androidx.datastore.preferences.core.intPreferencesKey("mostRecentExperienceId")
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

    companion object {
        private val SMALL_RECTANGLE = DpSize(250.dp, 50.dp)
        private val MEDIUM_RECTANGLE = DpSize(250.dp, 200.dp)
        private val BIG_RECTANGLE = DpSize(250.dp, 200.dp)
    }

    override val sizeMode = SizeMode.Responsive(
        setOf(
            SMALL_RECTANGLE,
            MEDIUM_RECTANGLE,
            BIG_RECTANGLE
        )
    )


    override val stateDefinition = PreferencesGlanceStateDefinition

    @SuppressLint("RestrictedApi")
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val size = LocalSize.current
            val prefs = currentState<Preferences>()
            val ingestionsText = prefs[WidgetKeys.INGESTIONS_TEXT] ?: ""
            val isLoading = prefs[WidgetKeys.IS_LOADING] ?: false
            val hasData = prefs[WidgetKeys.HAS_DATA] ?: false
            val timelineImagePath = prefs[WidgetKeys.TIMELINE_IMAGE_PATH]
            val substanceColorsJson = prefs[WidgetKeys.SUBSTANCE_COLORS] ?: "{}"

            // Parse substance colors from JSON
            val substanceColors = parseSubstanceColors(substanceColorsJson)

            // Create intent to open the app with add ingestion action
            val addIngestionIntent = Intent(context, MainActivity::class.java).apply {
                action = ".ADD_INGESTION"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val mostRecentExperienceId = prefs[WidgetKeys.MOST_RECENT_EXPERIENCE_ID]
            val addJournalScreenIntent = if (mostRecentExperienceId != null) {
                Intent(context, MainActivity::class.java).apply {
                    action = "${context.packageName}.OPEN_EXPERIENCE"
                    putExtra("experienceId", mostRecentExperienceId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            } else {
                Intent(context, MainActivity::class.java).apply {
                    action = "${context.packageName}.JOURNAL_SCREEN"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
            Column(
                modifier = GlanceModifier
                    .background(GlanceTheme.colors.background)
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Vertical.Top
            ) {
                if (size.width >= SMALL_RECTANGLE.width && size.height >= SMALL_RECTANGLE.height) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Vertical.CenterVertically
                    ) {
                        Text(
                            text = WidgetConstants.ADD_BUTTON_ICON,
                            style = TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = GlanceModifier
                                .padding(horizontal = 8.dp, vertical = 0.dp)
                                .clickable(onClick = actionStartActivity(addIngestionIntent))
                        )
                        // Compact refresh button
                        Text(
                            text = WidgetConstants.REFRESH_BUTTON_ICON,
                            style = TextStyle(
                                color = GlanceTheme.colors.secondary,
                                fontSize = 20.sp
                            ),
                            modifier = GlanceModifier
                                .padding(horizontal = 6.dp, vertical = 0.dp)
                                .clickable(onClick = actionRunCallback<RefreshAction>())
                        )
                    }
                }
                when {
                    isLoading -> {
                        Spacer(modifier = GlanceModifier.height(16.dp))
                        Text(
                            text = "Loading…",
                            style = TextStyle(fontSize = 16.sp, color = GlanceTheme.colors.secondary)
                        )
                    }
                    !hasData -> {
                        Spacer(modifier = GlanceModifier.height(16.dp))
                        Column(
                            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                            modifier = GlanceModifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No active ingestions",
                                style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.secondary)
                            )
                        }
                    }
                    else -> {
                        // Timeline graph
                        if (timelineImagePath != null) {
                            val file = File(timelineImagePath)
                            if (file.exists()) {
                                android.graphics.BitmapFactory.decodeFile(timelineImagePath)?.let { bitmap ->
                                    Image(
                                        provider = ImageProvider(bitmap),
                                        contentDescription = "Timeline graph",
                                        modifier = GlanceModifier
                                            .clickable(onClick = actionStartActivity(addJournalScreenIntent))
                                            .fillMaxWidth()
                                            .height(90.dp),
                                        contentScale = ContentScale.FillBounds,
                                    )
                                }
                            }
                        }

                    if (size.width >= MEDIUM_RECTANGLE.width && size.height >= MEDIUM_RECTANGLE.height) {
                    Spacer(modifier = GlanceModifier.height(4.dp))
                        LazyColumn {
                            ingestionsText.split("\n").take(20).forEach { line ->
                                item {
                                    val substanceName = extractSubstanceName(line)
                                    val color = substanceColors[substanceName]
                                    // Create a condensed version of the line
                                    val condensedLine = condenseIngestionLine(line)
                                    if (color != null) {
                                        Text(
                                            text = condensedLine,
                                            style = TextStyle(
                                                fontSize = 16.sp,
                                                color = androidx.glance.unit.ColorProvider(
                                                    androidx.compose.ui.graphics.Color(color)
                                                )
                                            ),
                                            maxLines = 1
                                        )
                                    } else {
                                        Text(
                                            text = condensedLine,
                                            style = TextStyle(
                                                fontSize = 12.sp,
                                                color = GlanceTheme.colors.onBackground
                                            ),
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
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun extractSubstanceName(line: String): String? {
        // Extract substance name from format: "SubstanceName (dose) - time"
        return try {
            val parenIndex = line.indexOf('(')
            if (parenIndex > 0) {
                line.take(parenIndex).trim()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Condense ingestion line to a shorter format.
     * Input: "SubstanceName (200 mg) - 16h 31m ago"
     * Output: "SubstanceName 200mg · 16h 31m"
     */
    private fun condenseIngestionLine(line: String): String {
        return try {
            val parenStart = line.indexOf('(')
            val parenEnd = line.indexOf(')')
            val dashIndex = line.indexOf(" - ")

            if (parenStart in 1..<parenEnd && dashIndex > parenEnd) {
                val substance = line.take(parenStart).trim()
                val dose = line.substring(parenStart + 1, parenEnd)
                    .replace(" ", "") // Remove spaces in dose
                val time = line.substring(dashIndex + 3)
                    .replace(" ago", "")
                    .trim()
                "$substance $dose · $time"
            } else {
                line
            }
        } catch (_: Exception) {
            line
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

            val (title, ingestionsText, hasData, timelineImagePath, substanceColors, mostRecentExperienceId) = if (ingestions.isEmpty()) {
                WidgetData("", "", false, null, emptyMap(), null)
            } else {
                // Take the most recent ingestions (up to 7)
                val recentIngestions = ingestions.take(40)

                // Get colors for substances from companions
                val colorsMap = mutableMapOf<String, Int>()
                val nightModeFlags = applicationContext.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK
                val isDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES

                // Get colors from ingestions with companions
                allRecentIngestionsWithCompanions.forEach { ingestionWithCompanion ->
                    val name = ingestionWithCompanion.ingestion.substanceName
                    if (!colorsMap.containsKey(name)) {
                        // Use companion color if available, otherwise default to BLUE (matching graph)
                        val color = ingestionWithCompanion.substanceCompanion?.color ?: AdaptiveColor.BLUE
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
                    "${ingestion.substanceName} ($doseText) - $timeText"
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
                // Determine the most recent experience ID from the first recent ingestion
                val mostRecentExperienceId = recentIngestions.firstOrNull()?.experienceId

                WidgetData("Journal", lines.joinToString("\n"), true, imagePath, colorsMap, mostRecentExperienceId)
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
                    this[WidgetKeys.INGESTIONS_TEXT] = ingestionsText
                    this[WidgetKeys.HAS_DATA] = hasData
                    this[WidgetKeys.IS_LOADING] = false
                    this[WidgetKeys.SUBSTANCE_COLORS] = substanceColorsJson
                    if (timelineImagePath != null) {
                        this[WidgetKeys.TIMELINE_IMAGE_PATH] = timelineImagePath
                    }
                    if (mostRecentExperienceId != null) {
                        this[WidgetKeys.MOST_RECENT_EXPERIENCE_ID] = mostRecentExperienceId
                    } else {
                        // Clear it if no experience is available or relevant
                        this.remove(WidgetKeys.MOST_RECENT_EXPERIENCE_ID)
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
        val substanceColors: Map<String, Int>,
        val mostRecentExperienceId: Int?
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
        val totalSeconds = Duration.ofHours(5).seconds.toFloat()

        val padding = 15f
        val labelHeight = 14f
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
        groupedBySubstanceAndRoute.forEach { (_, substanceIngestions) ->
            val companion = substanceIngestions.firstOrNull()?.substanceCompanion
            val color = companion?.color ?: AdaptiveColor.BLUE
            val androidColor = getAndroidColor(color, isDarkMode)

            substanceIngestions.forEach { ingestionWithCompanion ->
                val ingestion = ingestionWithCompanion.ingestion
                val ingestionTime = ingestion.time

                // Get RoaDuration for this substance and route
                val roaDuration = substanceDurations[ingestion.substanceName]?.get(ingestion.administrationRoute)

                // Check which phases have data
                val hasOnset = roaDuration?.onset != null
                val hasComeup = roaDuration?.comeup != null
                val hasPeak = roaDuration?.peak != null
                val hasOffset = roaDuration?.offset != null
                val hasTotal = roaDuration?.total != null
                val hasAllPhases = hasOnset && hasComeup && hasPeak && hasOffset

                // Calculate duration phases in seconds with fallback
                val onsetSec = roaDuration?.onset?.interpolateAtValueInSeconds(0.5f) ?: 1800f
                val comeupSec = roaDuration?.comeup?.interpolateAtValueInSeconds(0.5f) ?: 2700f
                val peakSec = roaDuration?.peak?.interpolateAtValueInSeconds(0.5f) ?: 5400f
                val offsetSec = roaDuration?.offset?.interpolateAtValueInSeconds(0.5f) ?: 5400f
                val totalSec = roaDuration?.total?.interpolateAtValueInSeconds(0.5f)

                // Calculate x positions (in seconds from graph start)
                val secondsFromStart = Duration.between(startTime, ingestionTime).seconds.toFloat()

                // Calculate peak height
                val peakHeight = graphHeight * WidgetConstants.PEAK_HEIGHT_FRACTION

                // Convert seconds to pixels
                fun secToPixel(sec: Float): Float = padding + (sec / totalSeconds) * graphWidth

                // Determine if we should use dotted lines (missing some phases)
                val useDottedLine = !hasAllPhases && hasTotal

                // Create stroke paint
                val strokePaint = Paint().apply {
                    this.color = androidColor
                    style = Paint.Style.STROKE
                    strokeWidth = WidgetConstants.STROKE_WIDTH
                    isAntiAlias = true
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    if (useDottedLine) {
                        // Dotted line for missing phases
                        val dashLength = WidgetConstants.STROKE_WIDTH * 4
                        val gapLength = WidgetConstants.STROKE_WIDTH * 3
                        pathEffect = android.graphics.DashPathEffect(floatArrayOf(dashLength, gapLength), 0f)
                    } else {
                        pathEffect = android.graphics.CornerPathEffect(WidgetConstants.CORNER_PATH_EFFECT_RADIUS)
                    }
                }

                val fillPaint = Paint().apply {
                    this.color = Color.argb(
                        (shapeAlpha * 255).toInt(),
                        Color.red(androidColor),
                        Color.green(androidColor),
                        Color.blue(androidColor)
                    )
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }

                // Build the timeline points based on available data
                val timelinePoints = mutableListOf<Pair<Float, Float>>()
                val ingestionX = secondsFromStart

                if (useDottedLine && totalSec != null) {
                    // Use simplified curve when we only have total duration
                    val peakX = ingestionX + totalSec / 2
                    val endX = ingestionX + totalSec
                    timelinePoints.add(Pair(ingestionX, 0f))
                    timelinePoints.add(Pair(peakX, 1f))
                    timelinePoints.add(Pair(endX, 0f))
                } else {
                    // Use full timeline shape
                    val onsetEndX = ingestionX + onsetSec
                    val comeupEndX = onsetEndX + comeupSec
                    val peakEndX = comeupEndX + peakSec
                    val offsetEndX = peakEndX + offsetSec
                    timelinePoints.add(Pair(ingestionX, 0f))
                    timelinePoints.add(Pair(onsetEndX, 0f))
                    timelinePoints.add(Pair(comeupEndX, 1f))
                    timelinePoints.add(Pair(peakEndX, 1f))
                    timelinePoints.add(Pair(offsetEndX, 0f))
                }

                // Filter to visible window and build path
                val visibleStartSec = 0f
                val visibleEndSec = totalSeconds

                val path = Path()
                var pathStarted = false
                var firstVisibleX = padding
                var lastVisibleX = padding

                for (i in 0 until timelinePoints.size) {
                    val (currentSec, currentHeight) = timelinePoints[i]
                    val currentPixelX = secToPixel(currentSec)
                    val currentY = baselineY - (currentHeight * peakHeight)

                    if (currentSec >= visibleStartSec && currentSec <= visibleEndSec) {
                        if (!pathStarted) {
                            // Check if we need to interpolate entry point
                            if (i > 0) {
                                val (prevSec, prevHeight) = timelinePoints[i - 1]
                                if (prevSec < visibleStartSec) {
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
                        // Interpolate exit point
                        if (i > 0) {
                            val (prevSec, prevHeight) = timelinePoints[i - 1]
                            if (prevSec < visibleEndSec) {
                                val t = (visibleEndSec - prevSec) / (currentSec - prevSec)
                                val exitHeight = prevHeight + t * (currentHeight - prevHeight)
                                val exitY = baselineY - (exitHeight * peakHeight)
                                path.lineTo(width - padding, exitY)
                                lastVisibleX = width - padding
                            }
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
            textSize = 20f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("-2h", padding + 20, height - 3f, textPaint)
        canvas.drawText("now", currentTimeX, height - 3f, textPaint)
        canvas.drawText("+3h", width - padding - 15, height - 3f, textPaint)

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
            AdaptiveColor.RED -> if (isDarkTheme) Color.rgb(255, 69, 58) else Color.rgb(255, 59, 48)
            AdaptiveColor.ORANGE -> if (isDarkTheme) Color.rgb(255, 159, 10) else Color.rgb(255, 149, 0)
            AdaptiveColor.YELLOW -> if (isDarkTheme) Color.rgb(255, 214, 10) else Color.rgb(255, 204, 0)
            AdaptiveColor.GREEN -> if (isDarkTheme) Color.rgb(48, 209, 88) else Color.rgb(52, 199, 89)
            AdaptiveColor.MINT -> if (isDarkTheme) Color.rgb(102, 212, 207) else Color.rgb(0, 199, 190)
            AdaptiveColor.TEAL -> if (isDarkTheme) Color.rgb(64, 200, 224) else Color.rgb(48, 176, 199)
            AdaptiveColor.CYAN -> if (isDarkTheme) Color.rgb(100, 210, 255) else Color.rgb(50, 173, 230)
            AdaptiveColor.BLUE -> if (isDarkTheme) Color.rgb(10, 132, 255) else Color.rgb(0, 122, 255)
            AdaptiveColor.INDIGO -> if (isDarkTheme) Color.rgb(94, 92, 230) else Color.rgb(88, 86, 214)
            AdaptiveColor.PURPLE -> if (isDarkTheme) Color.rgb(191, 90, 242) else Color.rgb(175, 82, 222)
            AdaptiveColor.PINK -> if (isDarkTheme) Color.rgb(255, 55, 95) else Color.rgb(255, 45, 85)
            AdaptiveColor.BROWN -> if (isDarkTheme) Color.rgb(172, 142, 104) else Color.rgb(162, 132, 94)

            // Extended colors
            AdaptiveColor.FIRE_ENGINE_RED -> if (isDarkTheme) Color.rgb(237, 43, 42) else Color.rgb(237, 14, 6)
            AdaptiveColor.CORAL -> if (isDarkTheme) Color.rgb(255, 131, 121) else Color.rgb(180, 92, 85)
            AdaptiveColor.TOMATO -> if (isDarkTheme) Color.rgb(255, 99, 71) else Color.rgb(180, 69, 50)
            AdaptiveColor.CINNABAR -> Color.rgb(227, 36, 0)
            AdaptiveColor.RUST -> Color.rgb(199, 81, 58)
            AdaptiveColor.ORANGE_RED -> if (isDarkTheme) Color.rgb(255, 69, 0) else Color.rgb(205, 55, 0)
            AdaptiveColor.AUBURN -> if (isDarkTheme) Color.rgb(217, 80, 0) else Color.rgb(173, 62, 0)
            AdaptiveColor.SADDLE_BROWN -> if (isDarkTheme) Color.rgb(191, 95, 25) else Color.rgb(139, 69, 19)
            AdaptiveColor.DARK_ORANGE -> if (isDarkTheme) Color.rgb(255, 140, 0) else Color.rgb(155, 84, 0)
            AdaptiveColor.DARK_GOLD -> Color.rgb(169, 104, 0)
            AdaptiveColor.KHAKI -> if (isDarkTheme) Color.rgb(203, 183, 137) else Color.rgb(128, 114, 86)
            AdaptiveColor.BRONZE -> if (isDarkTheme) Color.rgb(167, 123, 0) else Color.rgb(120, 87, 0)
            AdaptiveColor.GOLD -> if (isDarkTheme) Color.rgb(255, 215, 0) else Color.rgb(130, 109, 0)
            AdaptiveColor.OLIVE -> if (isDarkTheme) Color.rgb(141, 134, 0) else Color.rgb(102, 97, 0)
            AdaptiveColor.OLIVE_DRAB -> if (isDarkTheme) Color.rgb(154, 166, 14) else Color.rgb(111, 118, 8)
            AdaptiveColor.DARK_OLIVE_GREEN -> if (isDarkTheme) Color.rgb(105, 133, 58) else Color.rgb(85, 107, 47)
            AdaptiveColor.MOSS_GREEN -> if (isDarkTheme) Color.rgb(102, 156, 53) else Color.rgb(79, 122, 40)
            AdaptiveColor.LIME_GREEN -> if (isDarkTheme) Color.rgb(0, 255, 0) else Color.rgb(0, 130, 0)
            AdaptiveColor.LIME -> if (isDarkTheme) Color.rgb(50, 205, 50) else Color.rgb(32, 130, 32)
            AdaptiveColor.FOREST_GREEN -> if (isDarkTheme) Color.rgb(34, 139, 34) else Color.rgb(28, 114, 28)
            AdaptiveColor.SEA_GREEN -> if (isDarkTheme) Color.rgb(46, 139, 87) else Color.rgb(38, 114, 71)
            AdaptiveColor.JUNGLE_GREEN -> Color.rgb(3, 136, 88)
            AdaptiveColor.LIGHT_SEA_GREEN -> if (isDarkTheme) Color.rgb(32, 178, 170) else Color.rgb(22, 128, 122)
            AdaptiveColor.DARK_TURQUOISE -> if (isDarkTheme) Color.rgb(0, 206, 209) else Color.rgb(0, 131, 134)
            AdaptiveColor.DODGER_BLUE -> if (isDarkTheme) Color.rgb(30, 144, 255) else Color.rgb(24, 116, 205)
            AdaptiveColor.ROYAL_BLUE -> if (isDarkTheme) Color.rgb(72, 117, 251) else Color.rgb(65, 105, 225)
            AdaptiveColor.DEEP_LAVENDER -> Color.rgb(135, 78, 254)
            AdaptiveColor.BLUE_VIOLET -> if (isDarkTheme) Color.rgb(166, 73, 252) else Color.rgb(138, 43, 226)
            AdaptiveColor.DARK_VIOLET -> if (isDarkTheme) Color.rgb(162, 76, 210) else Color.rgb(148, 0, 211)
            AdaptiveColor.HELIOTROPE -> Color.rgb(151, 93, 175)
            AdaptiveColor.BYZANTIUM -> if (isDarkTheme) Color.rgb(190, 56, 243) else Color.rgb(153, 41, 189)
            AdaptiveColor.MAGENTA -> if (isDarkTheme) Color.rgb(255, 0, 255) else Color.rgb(205, 0, 205)
            AdaptiveColor.DARK_MAGENTA -> if (isDarkTheme) Color.rgb(217, 0, 217) else Color.rgb(139, 0, 139)
            AdaptiveColor.FUCHSIA -> if (isDarkTheme) Color.rgb(214, 68, 146) else Color.rgb(189, 60, 129)
            AdaptiveColor.DEEP_PINK -> if (isDarkTheme) Color.rgb(255, 20, 147) else Color.rgb(205, 16, 117)
            AdaptiveColor.GRAYISH_MAGENTA -> Color.rgb(161, 96, 128)
            AdaptiveColor.HOT_PINK -> if (isDarkTheme) Color.rgb(255, 105, 180) else Color.rgb(180, 74, 126)
            AdaptiveColor.JAZZBERRY_JAM -> if (isDarkTheme) Color.rgb(230, 59, 122) else Color.rgb(185, 45, 93)
            AdaptiveColor.MAROON -> if (isDarkTheme) Color.rgb(187, 82, 99) else Color.rgb(190, 49, 68)
        }
    }

    private fun formatRelativeTime(time: Instant, now: Instant): String {
        val duration = Duration.between(time, now)
        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60
        
        return when {
            days > 0 -> "${days}d ${hours}h ago"
            hours > 0 -> "${hours}h ${minutes}m ago"
            minutes > 0 -> "${minutes}m ${seconds}s ago"
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