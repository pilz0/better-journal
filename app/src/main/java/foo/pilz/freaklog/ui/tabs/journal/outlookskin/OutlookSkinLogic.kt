package foo.pilz.freaklog.ui.tabs.journal.outlookskin

import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsCompanionsAndRatings
import foo.pilz.freaklog.ui.tabs.journal.experience.teamsskin.germanNameFor
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val OUTLOOK_SKIN_OPEN_PROBABILITY = 0.05
const val OUTLOOK_SKIN_MIN_DELAY_MS = 1_800_000L
const val OUTLOOK_SKIN_MAX_DELAY_MS = 5_400_000L

private val SUBJECT_PREFIXES = listOf("", "RE: ", "FW: ", "RE: RE: ", "RE: FW: ")

data class OutlookMailRow(
    val experienceId: Int,
    val sender: String,
    val subject: String,
    val preview: String,
    val timeText: String,
    val unread: Boolean,
)

fun outlookSubject(title: String, seed: Int): String =
    SUBJECT_PREFIXES[Math.floorMod(seed, SUBJECT_PREFIXES.size)] + title

fun outlookTimeText(time: Instant, now: Instant, zone: ZoneId): String {
    val date = time.atZone(zone).toLocalDate()
    val nowDate = now.atZone(zone).toLocalDate()
    return when {
        date == nowDate -> DateTimeFormatter.ofPattern("HH:mm", Locale.US).format(time.atZone(zone))
        date.year == nowDate.year -> DateTimeFormatter.ofPattern("d MMM", Locale.US).format(date)
        else -> DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US).format(date)
    }
}

fun buildOutlookRows(
    experiences: List<ExperienceWithIngestionsCompanionsAndRatings>,
    now: Instant,
    zone: ZoneId,
): List<OutlookMailRow> = experiences.map { experience ->
    OutlookMailRow(
        experienceId = experience.experience.id,
        sender = germanNameFor(experience.experience.id),
        subject = outlookSubject(experience.experience.title, experience.experience.id),
        preview = experience.ingestionsWithCompanions
            .joinToString { "${it.ingestion.substanceName} ${it.doseDescription}" }
            .ifEmpty { "(no message preview available)" },
        timeText = outlookTimeText(experience.sortInstant, now, zone),
        unread = experience.sortInstant.isAfter(now.minus(Duration.ofHours(24))),
    )
}
