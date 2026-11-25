package foo.pilz.freaklog.ui.tabs.stats.substancecompanion

import foo.pilz.freaklog.data.room.experiences.entities.CustomUnit
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.substances.classes.roa.DoseUnits
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

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
  private val yearPatternRegex = Regex("^[^dMyYu]*[yYu]+[^dMyYu]*|[^dMyYu]*[yYu]+[^dMyYu]*$")

  fun build(
      ingestions: List<Ingestion>,
      range: DosageTimeRange,
      now: Instant = Instant.now(),
      zone: ZoneId = ZoneId.systemDefault(),
      customUnits: Map<Int, CustomUnit> = emptyMap(),
      preferredUnit: String? = null,
      locale: Locale = Locale.getDefault(),
  ): SubstanceDosageStatsModel {
    val rangeStart =
        when (range) {
          DosageTimeRange.ALL -> ingestions.minOfOrNull { it.time } ?: now
          else -> now.atZone(zone).minus(range.period ?: Period.ZERO).toInstant()
        }
    val visible = ingestions.filter { it.time >= rangeStart && it.time < now }.sortedBy { it.time }
    // Doses converted to their family's base unit (mg / ml), custom units resolved first.
    val baseDoses = visible.map { baseDose(it, customUnits) }
    val baseUnits = baseDoses.mapNotNull { it?.first }.distinct()
    val singleBase = baseUnits.singleOrNull()
    val knownBaseDoses =
        if (singleBase == null) emptyList() else baseDoses.mapNotNull { it?.second }
    val (unit, divisor) =
        singleBase?.let { DoseUnits.displayUnit(it, knownBaseDoses.sum(), preferredUnit) }
            ?: (null to 1.0)
    val unitsUsed = unit?.let(::listOf) ?: baseUnits
    val doses = baseDoses.map { dose -> dose?.second?.takeIf { unit != null }?.div(divisor) }
    val buckets = buildBuckets(visible, doses, range, rangeStart, now, zone, unit, locale)
    val sessions = visible.map { it.experienceId }.toSet()
    val knownDoses = doses.filterNotNull()
    val totalKnownDose = unit?.let { knownDoses.sum() }
    val summary =
        SubstanceDosageSummary(
            totalSessions = sessions.size,
            totalKnownDose = totalKnownDose,
            averageKnownDosePerSession =
                totalKnownDose?.let {
                  if (sessions.isEmpty()) 0.0 else it / sessions.size
                },
            peakKnownDose = unit?.let { knownDoses.maxOrNull() ?: 0.0 },
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

  /** Base unit and the dose in it, or null when the ingestion has no unit. */
  private fun baseDose(
      ingestion: Ingestion,
      customUnits: Map<Int, CustomUnit>,
  ): Pair<String, Double?>? {
    val customUnit = ingestion.customUnitId?.let(customUnits::get)
    val (base, factor) =
        DoseUnits.toBase(customUnit?.originalUnit ?: ingestion.units) ?: return null
    val perUnit = if (customUnit == null) 1.0 else customUnit.dose
    val dose = perUnit?.let { ingestion.dose?.times(it) }
    return base to dose?.takeIf { it.isFinite() }?.times(factor)
  }

  @Suppress("LongParameterList")
  private fun buildBuckets(
      ingestions: List<Ingestion>,
      doses: List<Double?>,
      range: DosageTimeRange,
      start: Instant,
      end: Instant,
      zone: ZoneId,
      singleUnit: String?,
      locale: Locale,
  ): List<DosageBucket> {
    val config = bucketConfig(range, start, end, zone, locale)
    val count = bucketCount(range, start, end, zone, config.step)
    val labelFormat = DateTimeFormatter.ofPattern(config.labelPattern, locale)
    val fullDateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy")
    val buckets = ArrayDeque<DosageBucket>()
    var bucketEnd = end.atZone(zone)
    var ingestionIndex = ingestions.lastIndex
    repeat(count) {
      val bucketStart = bucketEnd.minus(config.step)
      val bucketStartInstant = bucketStart.toInstant()
      val bucketEndInstant = bucketEnd.toInstant()
      val sessionIds = mutableSetOf<Int>()
      var bucketKnownDose = 0.0
      while (ingestionIndex >= 0) {
        val ingestion = ingestions[ingestionIndex]
        val ingestionTime = ingestion.time
        if (ingestionTime < bucketStartInstant) break
        if (ingestionTime < bucketEndInstant) {
          sessionIds += ingestion.experienceId
          bucketKnownDose += doses[ingestionIndex] ?: 0.0
        }
        ingestionIndex--
      }
      buckets.addFirst(
          DosageBucket(
              label = labelFormat.format(bucketStart),
              fullDateText = fullDateFormat.format(bucketStart),
              totalDose = if (singleUnit == null) 0.0 else bucketKnownDose,
              sessionCount = sessionIds.size,
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
      locale: Locale,
  ): BucketConfig =
      when (range) {
        DosageTimeRange.DAYS_30 -> BucketConfig(Period.ofDays(1), "dd")
        DosageTimeRange.WEEKS_26 -> BucketConfig(Period.ofWeeks(1), dayMonthPattern(locale))
        DosageTimeRange.MONTHS_12 -> BucketConfig(Period.ofMonths(1), "MMM")
        DosageTimeRange.ALL -> {
          val days = ChronoUnit.DAYS.between(start.atZone(zone), end.atZone(zone)).coerceAtLeast(1)
          val months = ChronoUnit.MONTHS.between(start.atZone(zone), end.atZone(zone))
          when {
            days <= 60 -> BucketConfig(Period.ofDays(1), dayMonthPattern(locale))
            months <= 18 -> BucketConfig(Period.ofWeeks(1), dayMonthPattern(locale))
            else -> BucketConfig(Period.ofMonths(1), "MMM yy")
          }
        }
      }

  private fun dayMonthPattern(locale: Locale): String {
    val shortPattern =
        DateTimeFormatterBuilder.getLocalizedDateTimePattern(
            FormatStyle.SHORT,
            null,
            IsoChronology.INSTANCE,
            locale,
        )
    return shortPattern.replace(yearPatternRegex, "").trim().ifEmpty { "dd.MM" }
  }

  private fun bucketCount(
      range: DosageTimeRange,
      start: Instant,
      end: Instant,
      zone: ZoneId,
      step: Period,
  ): Int =
      when (range) {
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
    val sessionTimes =
        ingestions
            .groupBy { it.experienceId }
            .values
            .map { group -> group.minOf { it.time } }
            .sorted()
    if (sessionTimes.size < 2) return null
    return sessionTimes
        .zipWithNext { previous, next ->
          ChronoUnit.DAYS.between(previous.atZone(zone), next.atZone(zone)).toInt()
        }
        .maxOrNull()
  }

  private fun currentStreakWeeks(ingestions: List<Ingestion>, now: Instant): Int {
    val currentEpochMs = now.toEpochMilli()
    val weekMs = 7L * 24 * 60 * 60 * 1000
    val sessionWeekIndices =
        ingestions
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
