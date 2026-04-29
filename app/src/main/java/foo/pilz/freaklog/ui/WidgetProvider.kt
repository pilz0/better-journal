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
        private val BIG_RECTANGLE = DpSize(350.dp, 300.dp)
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
                action = "${context.packageName}.ADD_INGESTION"
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
                                            .height(100.dp),
                                        contentScale = ContentScale.Fit,
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
                                                    fontSize = 16.sp,
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
                    // Unescape the name - reverse the escaping order
                    val name = escapedName
                        .replace("\\n", "\n")
                        .replace("\\r", "\r")
                        .replace("\\t", "\t")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                    result[name] = colorInt
                }
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }
    /**
     * Extract substance name from line format: "SubstanceName • elapsed • ..."
     */
    private fun extractSubstanceName(line: String): String? {
        return try {
            // Look for bullet separator (•) used in new format
            val bulletIndex = line.indexOf(" • ")
            if (bulletIndex > 0) {
                line.take(bulletIndex).trim()
            } else {
                // Fallback to asterisk separator
                val starIndex = line.indexOf(" * ")
                if (starIndex > 0) {
                    line.take(starIndex).trim()
                } else {
                    // Fallback to old format with parentheses
                    val parenIndex = line.indexOf('(')
                    if (parenIndex > 0) {
                        line.take(parenIndex).trim()
                    } else {
                        null
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Condense ingestion line by keeping only key information.
     * Returns the line as-is since it's already formatted by the worker.
     */
    private fun condenseIngestionLine(line: String): String {
        return line
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

            val now = Instant.now()

            // For the graph, fetch enough history (48h back, 12h forward) so even
            // long-acting substances (LSD, DMT/2C-x analogues, mushroom retreats…)
            // are not silently dropped by the worker before the model can decide.
            val fetchStartTime = now.minus(Duration.ofHours(48))
            val fetchEndTime = now.plus(Duration.ofHours(12))

            val allRecentIngestionsWithCompanions = experienceDao.getIngestionsWithCompanions(
                fromInstant = fetchStartTime,
                toInstant = fetchEndTime,
            )

            // Detect dark mode once for both colour resolution and graph rendering.
            val nightModeFlags = applicationContext.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val isDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES

            // Build the pure-Kotlin timeline model from these ingestions.
            val modelInputs = allRecentIngestionsWithCompanions.map { iwc ->
                WidgetTimelineModel.IngestionInput(
                    substanceName = iwc.ingestion.substanceName,
                    route = iwc.ingestion.administrationRoute,
                    time = iwc.ingestion.time,
                    color = iwc.substanceCompanion?.color ?: AdaptiveColor.BLUE,
                )
            }
            val timelineModel = WidgetTimelineModel.build(
                now = now,
                ingestions = modelInputs,
                durationsBySubstance = substanceDurations,
            )

            // Build text lines from the same recent window, ordered newest first,
            // filtering out any ingestion whose effect has already finished (phase
            // == Finished).  This prevents stale "10d ago • finished" lines from
            // appearing while no active ingestions are present.
            val sortedRecent = allRecentIngestionsWithCompanions
                .sortedByDescending { it.ingestion.time }

            val colorsMap = mutableMapOf<String, Int>()
            val lines = mutableListOf<String>()

            for (iwc in sortedRecent) {
                val ingestion = iwc.ingestion
                val elapsedSeconds = Duration.between(ingestion.time, now).seconds.toFloat()
                val roa = substanceDurations[ingestion.substanceName]?.get(ingestion.administrationRoute)
                val phase = WidgetTimelineModel.resolveIngestionPhase(elapsedSeconds, roa)
                if (phase is IngestionPhase.Finished) continue   // skip inactive entries

                val timeText = formatElapsedTime(ingestion.time, now)
                val phaseText = when (phase) {
                    is IngestionPhase.NotStarted -> "not started"
                    is IngestionPhase.Onset      -> "onset • peak in ${formatSecondsToTime(phase.peakInSeconds.toLong())}"
                    is IngestionPhase.Comeup     -> "↑ comeup • peak in ${formatSecondsToTime(phase.peakInSeconds.toLong())}"
                    is IngestionPhase.Peak       -> "peak • ${formatSecondsToTime(phase.remainingSeconds.toLong())} left"
                    is IngestionPhase.Offset     -> "↓ offset • ${formatSecondsToTime(phase.remainingSeconds.toLong())} left"
                    is IngestionPhase.Active     -> if (phase.remainingSeconds > 0) "${formatSecondsToTime(phase.remainingSeconds.toLong())} left" else "finishing"
                    is IngestionPhase.Finished   -> "finished"  // unreachable; filtered above
                }
                lines += "${ingestion.substanceName} • $timeText • $phaseText"

                // Collect colors (first occurrence wins).
                val name = ingestion.substanceName
                if (!colorsMap.containsKey(name)) {
                    val color = iwc.substanceCompanion?.color ?: AdaptiveColor.BLUE
                    colorsMap[name] = getAndroidColor(color, isDarkMode)
                }
            }

            // Generate timeline graph bitmap from the new model.
            val imagePath = if (timelineModel.hasContent) {
                generateTimelineGraph(
                    context = applicationContext,
                    model = timelineModel,
                    appWidgetId = appWidgetId,
                    isDarkMode = isDarkMode,
                )
            } else {
                // No active ingestions: delete any previously cached PNG so the
                // widget can't accidentally display a stale graph.
                val staleFile = File(applicationContext.cacheDir, "widget_timeline_$appWidgetId.png")
                if (staleFile.exists()) staleFile.delete()
                null
            }

            // hasData is true only when there is something current to display.
            val hasData = timelineModel.hasContent || lines.isNotEmpty()
            // The most recent experience from the active list (first = newest).
            val mostRecentExperienceId = sortedRecent.firstOrNull()?.ingestion?.experienceId

            val ingestionsText = lines.joinToString("\n")

            // Serialize substance colors to JSON with proper escaping
            val substanceColorsJson = colorsMap.entries.joinToString(",", "{", "}") { (name, color) ->
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
                    // Always update the image path: set it to the new value or remove it
                    // entirely so a stale graph from a previous session is never shown.
                    if (imagePath != null) {
                        this[WidgetKeys.TIMELINE_IMAGE_PATH] = imagePath
                    } else {
                        this.remove(WidgetKeys.TIMELINE_IMAGE_PATH)
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

    private fun generateTimelineGraph(
        context: Context,
        model: WidgetTimelineModel,
        appWidgetId: Int,
        isDarkMode: Boolean,
    ): String? {
        if (!model.hasContent) return null

        // Render at high resolution; Glance scales the bitmap to the widget size.
        val width = 1200
        val height = 320
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)

        val paddingLeft = 24f
        val paddingRight = 24f
        val paddingTop = 18f
        val labelHeight = 36f
        val baselineY = height - paddingTop - labelHeight
        val graphTop = paddingTop
        val graphHeight = baselineY - graphTop
        val graphLeft = paddingLeft
        val graphRight = width - paddingRight
        val graphWidth = graphRight - graphLeft

        val widthInSeconds = model.widthInSeconds
        fun secToPixel(sec: Float): Float = graphLeft + (sec / widthInSeconds) * graphWidth

        // ---- background grid + hour ticks ----
        val gridColor = if (isDarkMode) {
            md_theme_dark_primary.toArgb()
        } else {
            md_theme_light_primary.toArgb()
        }
        val gridPaint = Paint().apply {
            color = Color.argb(45, Color.red(gridColor), Color.green(gridColor), Color.blue(gridColor))
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }
        val baselinePaint = Paint().apply {
            color = Color.argb(120, Color.red(gridColor), Color.green(gridColor), Color.blue(gridColor))
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        val axis = foo.pilz.freaklog.ui.tabs.journal.experience.timeline.drawables.AxisDrawable(
            startTime = model.windowStart,
            widthInSeconds = widthInSeconds,
        )
        val pixelsPerSec = graphWidth / widthInSeconds
        val fullHours = axis.getFullHours(pixelsPerSec = pixelsPerSec, widthInPixels = graphWidth)

        // Draw vertical grid lines at every hour tick.
        for (fullHour in fullHours) {
            val x = graphLeft + fullHour.distanceFromStart
            canvas.drawLine(x, graphTop, x, baselineY, gridPaint)
        }
        // Always draw the baseline.
        canvas.drawLine(graphLeft, baselineY, graphRight, baselineY, baselinePaint)

        // ---- effect curves ----
        // Render translucent fill first so strokes stay crisp on top.
        val peakHeight = graphHeight * WidgetConstants.PEAK_HEIGHT_FRACTION

        for (group in model.groups) {
            if (group.points.size < 2) continue
            val androidColor = getAndroidColor(group.color, isDarkMode)

            val path = Path()
            var started = false
            var firstX = graphLeft
            var lastX = graphLeft
            for (p in group.points) {
                val x = secToPixel(p.secondsFromStart).coerceIn(graphLeft, graphRight)
                val y = baselineY - p.height * peakHeight
                if (!started) {
                    path.moveTo(x, y)
                    firstX = x
                    started = true
                } else {
                    path.lineTo(x, y)
                }
                lastX = x
            }
            if (!started) continue

            val strokePaint = Paint().apply {
                this.color = androidColor
                style = Paint.Style.STROKE
                strokeWidth = WidgetConstants.STROKE_WIDTH
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                pathEffect = if (group.isComplete) {
                    android.graphics.CornerPathEffect(WidgetConstants.CORNER_PATH_EFFECT_RADIUS)
                } else {
                    val dashLength = WidgetConstants.STROKE_WIDTH * 4
                    val gapLength = WidgetConstants.STROKE_WIDTH * 3
                    android.graphics.DashPathEffect(floatArrayOf(dashLength, gapLength), 0f)
                }
            }

            // Fill a translucent area beneath the stroke.
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
            val fillPath = Path(path)
            fillPath.lineTo(lastX, baselineY + strokePaint.strokeWidth / 2f)
            fillPath.lineTo(firstX, baselineY + strokePaint.strokeWidth / 2f)
            fillPath.close()
            canvas.drawPath(fillPath, fillPaint)
            canvas.drawPath(path, strokePaint)

            // Ingestion dots on the baseline.
            val dotPaint = Paint().apply {
                this.color = androidColor
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val dotOutlinePaint = Paint().apply {
                color = if (isDarkMode) Color.argb(180, 0, 0, 0) else Color.argb(180, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = 2f
                isAntiAlias = true
            }
            for (sec in group.ingestionDotsSecondsFromStart) {
                if (sec < 0f || sec > widthInSeconds) continue
                val dx = secToPixel(sec).coerceIn(graphLeft, graphRight)
                canvas.drawCircle(dx, baselineY, WidgetConstants.INGESTION_DOT_RADIUS, dotPaint)
                canvas.drawCircle(dx, baselineY, WidgetConstants.INGESTION_DOT_RADIUS, dotOutlinePaint)
            }
        }

        // ---- "now" indicator ----
        val nowSec = model.nowSecondsFromStart
        if (nowSec in 0f..widthInSeconds) {
            val nowX = secToPixel(nowSec)
            val nowPaint = Paint().apply {
                color = if (isDarkMode) Color.WHITE else Color.BLACK
                strokeWidth = 3.5f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                isAntiAlias = true
            }
            canvas.drawLine(nowX, graphTop, nowX, baselineY, nowPaint)
        }

        // ---- labels ----
        val textPaint = Paint().apply {
            color = if (isDarkMode) Color.argb(200, 255, 255, 255) else Color.argb(200, 0, 0, 0)
            textSize = 26f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val labelY = (height - paddingTop / 2f).coerceAtMost(height - 6f)
        val nowSec2 = model.nowSecondsFromStart
        val nowX2 = if (nowSec2 in 0f..widthInSeconds) secToPixel(nowSec2) else null
        // Minimum pixel gap between an hour-tick label and the "now" label.
        val nowLabelClearance = textPaint.measureText("now") * 0.75f

        // Hour labels under each grid tick; skip any that would collide with "now".
        for (fullHour in fullHours) {
            val x = graphLeft + fullHour.distanceFromStart
            if (nowX2 != null && kotlin.math.abs(x - nowX2) < nowLabelClearance) continue
            canvas.drawText(fullHour.label, x, labelY, textPaint)
        }
        // "now" label.
        if (nowX2 != null) {
            val nowLabelPaint = Paint(textPaint).apply {
                color = if (isDarkMode) Color.WHITE else Color.BLACK
                isFakeBoldText = true
            }
            canvas.drawText("now", nowX2, labelY, nowLabelPaint)
        }

        // ---- save ----
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
        // Delegate to the single source of truth in AdaptiveColor (also handles AdaptiveColor.Custom)
        return color.getComposeColor(isDarkTheme).toArgb()
    }

    /**
     * Formats elapsed time since ingestion (without "ago" suffix)
     * Format: "23m 21s" or "1h 5m"
     */
    private fun formatElapsedTime(time: Instant, now: Instant): String {
        val duration = Duration.between(time, now)
        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60
        
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    /**
     * Formats seconds to a compact time string (e.g., "23m" or "1h 30m")
     */
    private fun formatSecondsToTime(totalSeconds: Long): String {
        if (totalSeconds <= 0) return "<1m"

        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
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