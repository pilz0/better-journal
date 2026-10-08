package foo.pilz.freaklog.ui.tabs.journal.experience.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import foo.pilz.freaklog.MainActivity
import foo.pilz.freaklog.R
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanion
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.ui.tabs.journal.experience.components.DataForOneEffectLine
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.AllTimelinesModel
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Builds the ongoing "active experience" notification: a mini timeline plus one status row per ingestion. */
class TimelineNotificationRenderer(
    private val context: Context,
    private val experienceRepo: ExperienceRepository,
    private val substanceRepo: SubstanceRepository,
    private val userPreferences: UserPreferences,
    private val customProfiles: foo.pilz.freaklog.data.room.experiences.CustomSubstanceProfiles,
) {

    data class RenderResult(
        val notification: Notification,
        val nextUpdateIntervalMs: Long,
        val isExperienceFinished: Boolean
    )

    private data class NotificationLine(
        val name: String,
        val phase: String,
        val title: String,
        val status: String,
        val colorArgb: Int,
    )

    /** @return null when there is nothing to show (no own ingestions for this target). */
    suspend fun render(experienceId: Int): RenderResult? {
        val now = Instant.now()
        val sorted = loadIngestions(experienceId, now)
            .filter { it.ingestion.consumerName == null }
            .sortedBy { it.ingestion.time }
        if (sorted.isEmpty()) return null

        val dataForEffectLines = sorted.map(::toEffectLine)
        val model = AllTimelinesModel(
            dataForLines = dataForEffectLines,
            dataForRatings = emptyList(),
            timedNotes = emptyList(),
            areSubstanceHeightsIndependent = userPreferences.areSubstanceHeightsIndependentFlow.first()
        )
        val phaseResults = dataForEffectLines.map {
            PhaseCalculator.currentPhase(it.startTime, it.roaDuration, it.endTime)
        }
        val displayMetrics = context.resources.displayMetrics
        // Drawn straight onto a Canvas: a service has no window, so a ComposeView cannot be measured here.
        val isDarkTheme = context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val bitmap = TimelineBitmapRenderer.render(
            model = model,
            widthPx = (BITMAP_WIDTH_DP * displayMetrics.density).toInt(),
            heightPx = (BITMAP_HEIGHT_DP * displayMetrics.density).toInt(),
            currentTime = now,
            density = displayMetrics.density,
            isDarkTheme = isDarkTheme,
        )
        val lines = dataForEffectLines.indices.map {
            toNotificationLine(dataForEffectLines[it], phaseResults[it], sorted[it].ingestion)
        }
        val title = if (experienceId == TimelineNotificationService.GLOBAL_TIMELINE_ID) {
            "Last 24 hours"
        } else {
            experienceRepo.getExperience(experienceId)?.title ?: "Active Timeline"
        }
        val notification = buildNotification(
            experienceId = experienceId,
            title = title,
            collapsedText = collapsedText(lines),
            expandedViews = buildExpandedViews(bitmap, lines),
            startedAt = sorted.first().ingestion.time,
        )
        return RenderResult(
            notification = notification,
            nextUpdateIntervalMs = PhaseCalculator.combinedInterval(phaseResults) * MILLIS_PER_SECOND,
            isExperienceFinished = phaseResults.all { it.phase == Phase.FINISHED },
        )
    }

    private suspend fun loadIngestions(experienceId: Int, now: Instant): List<IngestionWithCompanion> =
        if (experienceId == TimelineNotificationService.GLOBAL_TIMELINE_ID) {
            experienceRepo.getIngestionsWithCompanions(now.minus(GLOBAL_WINDOW_HOURS, ChronoUnit.HOURS), now)
        } else {
            experienceRepo.getIngestionsWithCompanions(experienceId)
                .map { IngestionWithCompanion(it.ingestion, it.substanceCompanion) }
        }

    private fun toEffectLine(ingestionWithCompanion: IngestionWithCompanion): DataForOneEffectLine {
        val ingestion = ingestionWithCompanion.ingestion
        return DataForOneEffectLine(
            substanceName = ingestion.substanceName,
            route = ingestion.administrationRoute,
            roaDuration = (
                substanceRepo.getSubstance(ingestion.substanceName)?.getRoa(ingestion.administrationRoute)
                    ?: customProfiles.getRoa(ingestion.substanceName, ingestion.administrationRoute)
                )?.roaDuration,
            height = 1f,
            horizontalWeight = 0.5f,
            color = ingestionWithCompanion.substanceCompanion?.color ?: AdaptiveColor.RED,
            startTime = ingestion.time,
            endTime = ingestion.endTime,
        )
    }

    private fun toNotificationLine(
        line: DataForOneEffectLine,
        result: PhaseResult,
        ingestion: Ingestion,
    ): NotificationLine {
        val dose = ingestion.dose?.let { amount ->
            val number =
                if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()
            number + ingestion.units.orEmpty()
        }
        val status = buildList {
            add(result.phase.displayName)
            add("${formatDuration(result.elapsedSeconds)} in")
            result.remainingSeconds?.let { add("~${formatDuration(it)} left") }
        }.joinToString(" · ")
        return NotificationLine(
            name = line.substanceName,
            phase = result.phase.displayName,
            title = listOfNotNull(line.substanceName, dose, line.route.displayText.lowercase())
                .joinToString(" "),
            status = status,
            colorArgb = line.color.getComposeColor(isDarkTheme = false).toArgb(),
        )
    }

    private fun collapsedText(lines: List<NotificationLine>): String =
        if (lines.size == 1) {
            "${lines.single().title} · ${lines.single().status}"
        } else {
            lines.joinToString(" · ") { "${it.name} ${it.phase}" }
        }

    private fun buildExpandedViews(
        bitmap: Bitmap,
        lines: List<NotificationLine>,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.notification_timeline_expanded)
        views.setImageViewBitmap(R.id.timeline_bitmap, bitmap)
        views.removeAllViews(R.id.timeline_rows)
        lines.forEach { line ->
            val row = RemoteViews(context.packageName, R.layout.notification_timeline_row)
            row.setInt(R.id.timeline_row_dot, "setColorFilter", line.colorArgb)
            row.setTextViewText(R.id.timeline_row_title, line.title)
            row.setTextViewText(R.id.timeline_row_status, line.status)
            views.addView(R.id.timeline_rows, row)
        }
        return views
    }

    private fun buildNotification(
        experienceId: Int,
        title: String,
        collapsedText: String,
        expandedViews: RemoteViews,
        startedAt: Instant,
    ): Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            if (experienceId != TimelineNotificationService.GLOBAL_TIMELINE_ID) {
                putExtra("experienceId", experienceId)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, experienceId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, TimelineNotificationService::class.java).apply {
            action = TimelineNotificationService.ACTION_STOP
        }
        val dismissPendingIntent = PendingIntent.getService(
            context, 0, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, TimelineNotificationChannel.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(title).setContentText(collapsedText)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle()).setCustomBigContentView(expandedViews)
            .setOngoing(true).setSilent(true)
            .setContentIntent(openPendingIntent)
            .addAction(0, "Open", openPendingIntent)
            .addAction(0, "Dismiss", dismissPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH).setUsesChronometer(true)
            .setWhen(startedAt.toEpochMilli())
            .build()
    }

    private fun formatDuration(seconds: Float): String {
        val totalMinutes = (seconds / 60).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private companion object {
        const val BITMAP_WIDTH_DP = 360
        const val BITMAP_HEIGHT_DP = 80
        const val GLOBAL_WINDOW_HOURS = 24L
        const val MILLIS_PER_SECOND = 1000L
    }
}
