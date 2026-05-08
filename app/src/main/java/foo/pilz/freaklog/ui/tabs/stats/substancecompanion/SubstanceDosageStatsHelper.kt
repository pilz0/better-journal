package foo.pilz.freaklog.ui.tabs.stats.substancecompanion

import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class SubstanceDosageStatsModel(
    val buckets: List<DosageBucket>,
    val summary: SubstanceDosageSummary,
    val hasMixedUnits: Boolean,
)

data class SubstanceDosageSummary(
    val totalSessions: Int,
    val totalKnownDose: Double?,
    val averageKnownDosePerSession: Double?,
    val peakKnownDose: Double?,
    val unknownDoseCount: Int,
    val unitsUsed: List<String>,
    val longestGapDays: Int?,
    val currentStreakWeeks: Int,
)

object SubstanceDosageStatsHelper {
    fun build(
        ingestions: List<Ingestion>,
        range: DosageTimeRange,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): SubstanceDosageStatsModel {
        val rangeStart = when (range) {
            DosageTimeRange.ALL -> ingestions.minOfOrNull { it.time } ?: now
            else -> now.atZone(zone).minus(range.period ?: Period.ZERO).toInstant()
        }
        val unsorted = ingestions.filter { it.time >= rangeStart && it.time <= now }
        val visible = unsorted.sortedBy { it.time }
        val unitsUsed = unsorted.mapNotNull { it.units?.takeIf(String::isNotBlank) }.distinct()
        val buckets = buildBuckets(visible, range, rangeStart, now, zone, unitsUsed)
        val sessions = visible.map { it.experienceId }.toSet()
        val singleUnit = unitsUsed.singleOrNull()
        val knownDoses = visible
            .filter { singleUnit != null && it.units == singleUnit }
            .mapNotNull { it.dose }
        val totalKnownDose = singleUnit?.let { knownDoses.sum() }
        val summary = SubstanceDosageSummary(
            totalSessions = sessions.size,
            totalKnownDose = totalKnownDose,
            averageKnownDosePerSession = totalKnownDose?.let {
                if (sessions.isEmpty()) 0.0 else it / sessions.size
            },
            peakKnownDose = singleUnit?.let { knownDoses.maxOrNull() ?: 0.0 },
            unknownDoseCount = visible.count { it.dose == null },
            unitsUsed = unitsUsed,
            longestGapDays = longestGapDays(visible, zone),
            currentStreakWeeks = currentStreakWeeks(visible, now),
        )
        return SubstanceDosageStatsModel(
            buckets = buckets,
            summary = summary,
            hasMixedUnits = unitsUsed.size > 1,
        )
    }

    private fun buildBuckets(
        ingestions: List<Ingestion>,
        range: DosageTimeRange,
        start: Instant,
        end: Instant,
        zone: ZoneId,
        unitsUsed: List<String>,
    ): List<DosageBucket> {
        val config = bucketConfig(range, start, end, zone)
        val count = bucketCount(range, start, end, zone, config.step)
        val labelFormat = DateTimeFormatter.ofPattern(config.labelPattern)
        val fullDateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy")
        val singleUnit = unitsUsed.singleOrNull()
        val buckets = ArrayDeque<DosageBucket>()
        var bucketEnd = end.atZone(zone)
        repeat(count) {
            val bucketStart = bucketEnd.minus(config.step)
            val inBucket = ingestions.filter {
                it.time >= bucketStart.toInstant() && it.time < bucketEnd.toInstant()
            }
            buckets.addFirst(
                DosageBucket(
                    label = labelFormat.format(bucketStart),
                    fullDateText = fullDateFormat.format(bucketStart),
                    totalDose = if (singleUnit == null) {
                        0.0
                    } else {
                        inBucket.filter { it.units == singleUnit }.sumOf { it.dose ?: 0.0 }
                    },
                    sessionCount = inBucket.map { it.experienceId }.toSet().size,
                    unit = singleUnit.orEmpty(),
                )
            )
            bucketEnd = bucketStart
        }
        return buckets.toList()
    }

    private data class BucketConfig(val step: Period, val labelPattern: String)

    private fun bucketConfig(
        range: DosageTimeRange,
        start: Instant,
        end: Instant,
        zone: ZoneId,
    ): BucketConfig = when (range) {
        DosageTimeRange.DAYS_30 -> BucketConfig(Period.ofDays(1), "dd")
        DosageTimeRange.WEEKS_26 -> BucketConfig(Period.ofWeeks(1), "dd.MM")
        DosageTimeRange.MONTHS_12 -> BucketConfig(Period.ofMonths(1), "MMM")
        DosageTimeRange.ALL -> {
            val days = ChronoUnit.DAYS.between(start.atZone(zone), end.atZone(zone)).coerceAtLeast(1)
            val months = ChronoUnit.MONTHS.between(start.atZone(zone), end.atZone(zone))
            when {
                days <= 60 -> BucketConfig(Period.ofDays(1), "dd.MM")
                months <= 18 -> BucketConfig(Period.ofWeeks(1), "dd.MM")
                else -> BucketConfig(Period.ofMonths(1), "MMM yy")
            }
        }
    }

    private fun bucketCount(
        range: DosageTimeRange,
        start: Instant,
        end: Instant,
        zone: ZoneId,
        step: Period,
    ): Int = when (range) {
        DosageTimeRange.DAYS_30 -> 30
        DosageTimeRange.WEEKS_26 -> 26
        DosageTimeRange.MONTHS_12 -> 12
        DosageTimeRange.ALL -> {
            var count = 0
            var cursor = end.atZone(zone)
            while (cursor.toInstant().isAfter(start) && count < 200) {
                cursor = cursor.minus(step)
                count++
            }
            count.coerceAtLeast(1)
        }
    }

    private fun longestGapDays(ingestions: List<Ingestion>, zone: ZoneId): Int? {
        val sessionTimes = ingestions
            .groupBy { it.experienceId }
            .values
            .map { group -> group.minOf { it.time } }
            .sorted()
        if (sessionTimes.size < 2) return null
        return sessionTimes.zipWithNext { previous, next ->
            ChronoUnit.DAYS.between(previous.atZone(zone), next.atZone(zone)).toInt()
        }.maxOrNull()
    }

    private fun currentStreakWeeks(ingestions: List<Ingestion>, now: Instant): Int {
        val currentEpochMs = now.toEpochMilli()
        val weekMs = 7L * 24 * 60 * 60 * 1000
        val sessionWeekIndices = ingestions
            .map { it.time.toEpochMilli() }
            .filter { it <= currentEpochMs }
            .map { ((currentEpochMs - it) / weekMs).toInt() }
            .toHashSet()
        var streak = 0
        for (weekIndex in 0 until 104) {
            if (weekIndex in sessionWeekIndices) streak++ else break
        }
        return streak
    }
}
