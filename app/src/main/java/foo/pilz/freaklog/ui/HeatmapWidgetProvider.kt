package foo.pilz.freaklog.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import foo.pilz.freaklog.MainActivity
import foo.pilz.freaklog.data.room.AppDatabase
import foo.pilz.freaklog.ui.theme.md_theme_dark_primary
import foo.pilz.freaklog.ui.theme.md_theme_light_primary
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private object HeatmapWidgetKeys {
    val HAS_DATA = booleanPreferencesKey("heatmap_hasData")
    val HEATMAP_IMAGE_PATH = stringPreferencesKey("heatmap_imagePath")
    val IS_LOADING = booleanPreferencesKey("heatmap_isLoading")
    val INGESTION_COUNT = stringPreferencesKey("heatmap_ingestionCount")
}

private object HeatmapWorkerInput {
    const val APP_WIDGET_ID = "heatmapAppWidgetId"
}

class HeatmapWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HeatmapAppWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Trigger refresh for each widget
        appWidgetIds.forEach { appWidgetId ->
            enqueueHeatmapRefresh(context, appWidgetId)
        }
    }
}

class HeatmapAppWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val hasData = prefs[HeatmapWidgetKeys.HAS_DATA] ?: false
            val isLoading = prefs[HeatmapWidgetKeys.IS_LOADING] ?: true
            val heatmapImagePath = prefs[HeatmapWidgetKeys.HEATMAP_IMAGE_PATH]
            val ingestionCount = prefs[HeatmapWidgetKeys.INGESTION_COUNT] ?: "0"

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                action = "${context.packageName}.JOURNAL_SCREEN"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            Column(
                modifier = GlanceModifier
                    .background(GlanceTheme.colors.background)
                    .fillMaxSize()
                    .clickable(onClick = actionStartActivity(openAppIntent)),
                verticalAlignment = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally
            ) {
                when {
                    isLoading -> {
                        Text(
                            text = "Loading…",
                            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.secondary)
                        )
                    }
                    !hasData -> {
                        Text(
                            text = "No data",
                            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.secondary)
                        )
                    }
                    else -> {
                        if (heatmapImagePath != null) {
                            val file = File(heatmapImagePath)
                            if (file.exists()) {
                                android.graphics.BitmapFactory.decodeFile(heatmapImagePath)?.let { bitmap ->
                                    Image(
                                        provider = ImageProvider(bitmap),
                                        contentDescription = "Activity heatmap",
                                        modifier = GlanceModifier.fillMaxSize(),
                                        contentScale = ContentScale.FillBounds
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

class HeatmapWidgetWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "HeatmapWidgetWorker"
    }

    override suspend fun doWork(): Result {
        val appWidgetId = inputData.getInt(HeatmapWorkerInput.APP_WIDGET_ID, -1)
        if (appWidgetId == -1) return Result.failure()

        return try {
            val database = Room.databaseBuilder(
                applicationContext,
                AppDatabase::class.java,
                "experiences_db"
            ).build()
            val experienceDao = database.experienceDao()

            // Get all ingestions from the last year
            val oneYearAgo = Instant.now().minus(365, ChronoUnit.DAYS)
            val now = Instant.now()

            val ingestions = experienceDao.getIngestionsWithCompanions(
                fromInstant = oneYearAgo,
                toInstant = now,
            )

            val (hasData, imagePath, ingestionCount) = if (ingestions.isNotEmpty()) {
                val path = generateHeatmapGraph(applicationContext, ingestions.map { it.ingestion.time }, appWidgetId)
                Triple(true, path, ingestions.size.toString())
            } else {
                Triple(false, null, "0")
            }

            val manager = GlanceAppWidgetManager(applicationContext)
            val glanceId = manager.getGlanceIdBy(appWidgetId)

            updateAppWidgetState(
                context = applicationContext,
                definition = PreferencesGlanceStateDefinition,
                glanceId = glanceId
            ) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[HeatmapWidgetKeys.HAS_DATA] = hasData
                    this[HeatmapWidgetKeys.IS_LOADING] = false
                    this[HeatmapWidgetKeys.INGESTION_COUNT] = ingestionCount
                    if (imagePath != null) {
                        this[HeatmapWidgetKeys.HEATMAP_IMAGE_PATH] = imagePath
                    }
                }
            }

            HeatmapAppWidget().update(applicationContext, glanceId)
            database.close()
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "Error updating heatmap widget", t)
            Result.retry()
        }
    }

    private fun generateHeatmapGraph(
        context: Context,
        ingestionTimes: List<Instant>,
        appWidgetId: Int
    ): String {
        // Detect dark mode
        val nightModeFlags = context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES

        val width = 650
        val height = 95
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)

        // Calculate date range (52 weeks, starting from the Sunday of the current week going back)
        val today = LocalDate.now()
        val endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
        val startDate = endOfWeek.minusWeeks(52)

        // Count ingestions per day
        val counts = mutableMapOf<LocalDate, Int>()
        ingestionTimes.forEach { instant ->
            val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
            counts[date] = (counts[date] ?: 0) + 1
        }

        val maxCount = counts.values.maxOrNull() ?: 1

        // Colors - green gradient like GitHub
        val emptyColor = if (isDarkMode) Color.rgb(22, 27, 34) else Color.rgb(235, 237, 240)
        val level1 = if (isDarkMode) Color.rgb(14, 68, 41) else Color.rgb(155, 233, 168)
        val level2 = if (isDarkMode) Color.rgb(0, 109, 50) else Color.rgb(64, 196, 99)
        val level3 = if (isDarkMode) Color.rgb(38, 166, 65) else Color.rgb(48, 161, 78)
        val level4 = if (isDarkMode) Color.rgb(57, 211, 83) else Color.rgb(33, 110, 57)

        fun getColorForCount(count: Int): Int {
            if (count == 0) return emptyColor
            val ratio = count.toFloat() / maxCount
            return when {
                ratio <= 0.25f -> level1
                ratio <= 0.5f -> level2
                ratio <= 0.75f -> level3
                else -> level4
            }
        }

        val cellSize = 10f
        val cellGap = 2f
        val leftPadding = 5f
        val topPadding = 5f

        val paint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // Draw cells - 7 rows (days of week), 53 columns (weeks)
        var currentDate = startDate
        var weekIndex = 0
        
        while (!currentDate.isAfter(endOfWeek) && weekIndex < 53) {
            val dayOfWeek = currentDate.dayOfWeek.value % 7 // 0 = Sunday, 6 = Saturday
            
            val x = leftPadding + weekIndex * (cellSize + cellGap)
            val y = topPadding + dayOfWeek * (cellSize + cellGap)
            
            val count = counts[currentDate] ?: 0
            paint.color = getColorForCount(count)
            
            canvas.drawRoundRect(
                RectF(x, y, x + cellSize, y + cellSize),
                2f, 2f,
                paint
            )
            
            currentDate = currentDate.plusDays(1)
            if (currentDate.dayOfWeek == DayOfWeek.SUNDAY) {
                weekIndex++
            }
        }

        // Save bitmap
        val file = File(context.cacheDir, "widget_heatmap_$appWidgetId.png")
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } finally {
            bitmap.recycle()
        }

        return file.absolutePath
    }
}

fun enqueueHeatmapRefresh(context: Context, appWidgetId: Int) {
    val work = OneTimeWorkRequestBuilder<HeatmapWidgetWorker>()
        .setInputData(workDataOf(HeatmapWorkerInput.APP_WIDGET_ID to appWidgetId))
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        "heatmap-widget-refresh-$appWidgetId",
        ExistingWorkPolicy.REPLACE,
        work
    )
}
