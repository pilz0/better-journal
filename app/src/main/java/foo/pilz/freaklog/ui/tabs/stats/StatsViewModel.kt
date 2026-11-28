/*
 * Copyright (c) 2022-2023. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.ui.tabs.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsAndCompanions
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.DoseUnits
import foo.pilz.freaklog.ui.tabs.settings.funny.UltraFunSkinController
import foo.pilz.freaklog.ui.tabs.settings.funny.UltraFunSkinEligibility
import foo.pilz.freaklog.ui.tabs.stats.excelskin.EXCEL_SKIN_MAX_DELAY_MS
import foo.pilz.freaklog.ui.tabs.stats.excelskin.EXCEL_SKIN_MIN_DELAY_MS
import foo.pilz.freaklog.ui.tabs.stats.excelskin.EXCEL_SKIN_OPEN_PROBABILITY
import foo.pilz.freaklog.ui.tabs.stats.excelskin.ExcelSkinPayload
import foo.pilz.freaklog.ui.tabs.stats.excelskin.randomExcelFileName
import java.time.Instant
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class StatsViewModel
@Inject
constructor(
    experienceRepo: ExperienceRepository,
    private val skinEligibility: UltraFunSkinEligibility,
    private val skinController: UltraFunSkinController,
) : ViewModel() {

  private val _optionFlow = MutableStateFlow(TimePickerOption.WEEKS_26)
  private val optionFlow = _optionFlow.asStateFlow()

  private val consumerFlow = MutableStateFlow<String?>(null)

  private val sortOptionFlow = MutableStateFlow(StatsSortOption.EXPERIENCES)

  fun onTapOption(timePickerOption: TimePickerOption) {
    viewModelScope.launch { _optionFlow.emit(timePickerOption) }
  }

  fun onChangeConsumer(consumerName: String?) {
    viewModelScope.launch { consumerFlow.emit(consumerName) }
  }

  fun onChangeSortOption(sortOption: StatsSortOption) {
    viewModelScope.launch { sortOptionFlow.emit(sortOption) }
  }

  // Shared so the pipelines below don't each re-collect (and re-query) the Room flow.
  private val allExperiencesSortedFlow: SharedFlow<List<ExperienceWithIngestionsAndCompanions>> =
      experienceRepo
          .getSortedExperiencesWithIngestionsAndCustomUnitsFlow()
          .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

  private val companionFlow: SharedFlow<List<SubstanceCompanion>> =
      experienceRepo
          .getAllSubstanceCompanionsFlow()
          .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

  internal fun getTotalDose(
      groupedIngestions: List<IngestionWithCompanionAndCustomUnit>
  ): TotalDose? = StatsCalculator.computeTotalDose(groupedIngestions)

  private val baseStatsModelFlow =
      combine(
              allExperiencesSortedFlow,
              companionFlow,
              optionFlow,
              consumerFlow,
          ) { experiences, companions, option, consumerName ->
            StatsCalculator.build(
                experiences = experiences,
                companions = companions,
                option = option,
                consumerName = consumerName,
                sortOption = StatsSortOption.EXPERIENCES,
                now = Instant.now(),
                zone = ZoneId.systemDefault(),
                locale = Locale.getDefault(),
            )
          }
          .flowOn(Dispatchers.Default)

  val statsModelFlow: StateFlow<StatsModel?> =
      combine(baseStatsModelFlow, sortOptionFlow) { base, sortOption ->
            if (sortOption == base.sortOption) {
              base
            } else {
              base.copy(
                  statItems = StatsCalculator.sortStatItems(base.statItems, sortOption),
                  sortOption = sortOption,
              )
            }
          }
          .flowOn(Dispatchers.Default)
          .stateIn(
              initialValue = null,
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(5000),
          )

  val sortedConsumerNamesFlow =
      allExperiencesSortedFlow
          .map { experiences ->
            experiences
                .flatMap { it.ingestionsWithCompanionAndCustomUnit }
                .mapNotNull { it.ingestion.consumerName }
                .distinct()
          }
          .flowOn(Dispatchers.Default)
          .stateIn(
              initialValue = emptyList(),
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(5000),
          )

  /**
   * Runs while statistics are on screen: while ultra fun mode is eligible, maybe shows the Excel
   * skin right away, then again every 30–90 minutes.
   */
  suspend fun runExcelSkin() {
    skinEligibility.eligibleFlow.collectLatest { eligible ->
      if (!eligible) {
        skinController.dismissIfShowing<ExcelSkinPayload>()
        return@collectLatest
      }
      if (skinController.state.value == null && Random.nextDouble() < EXCEL_SKIN_OPEN_PROBABILITY) {
        showExcelSkin()
      }
      while (true) {
        delay(Random.nextLong(EXCEL_SKIN_MIN_DELAY_MS, EXCEL_SKIN_MAX_DELAY_MS))
        if (skinController.state.value == null) showExcelSkin()
      }
    }
  }

  /** Also opened by hand from the statistics top bar, independent of ultra fun mode. */
  fun showExcelSkin() {
    val fileName = randomExcelFileName()
    skinController.show(
        viewModelScope,
        statsModelFlow.map {
          ExcelSkinPayload(statItems = it?.statItems.orEmpty(), fileName = fileName)
        },
    )
  }
}

internal object StatsCalculator {
  private val yearPatternRegex = Regex("^[^dMyYu]*[yYu]+[^dMyYu]*|[^dMyYu]*[yYu]+[^dMyYu]*$")
  private val formattersCache = ConcurrentHashMap<Pair<String, Locale>, DateTimeFormatter>()
  private val dayMonthPatternCache = ConcurrentHashMap<Locale, String>()

  private fun formatterFor(pattern: String, locale: Locale): DateTimeFormatter =
      formattersCache.getOrPut(pattern to locale) { DateTimeFormatter.ofPattern(pattern, locale) }

  private data class FilteredIngestion(
      val experienceId: Int,
      val data: IngestionWithCompanionAndCustomUnit,
  )

  @Suppress("LongParameterList")
  fun build(
      experiences: List<ExperienceWithIngestionsAndCompanions>,
      companions: List<SubstanceCompanion>,
      option: TimePickerOption,
      consumerName: String?,
      sortOption: StatsSortOption = StatsSortOption.EXPERIENCES,
      now: Instant = Instant.now(),
      zone: ZoneId = ZoneId.systemDefault(),
      locale: Locale = Locale.getDefault(),
  ): StatsModel {
    val areThereAnyIngestions = experiences.any { experience ->
      experience.ingestionsWithCompanionAndCustomUnit.any {
        it.ingestion.consumerName == consumerName
      }
    }
    val endOfToday = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone)
    val rangeStart = endOfToday.minus(option.allBucketSizes)
    val rangeStartInstant = rangeStart.toInstant()
    val endOfTodayInstant = endOfToday.toInstant()
    val startDateText = formatterFor("EEE, dd MMM yyyy", locale).format(rangeStart)

    val filtered = experiences.flatMap { exp ->
      exp.ingestionsWithCompanionAndCustomUnit
          .filter { item ->
            val t = item.ingestion.time
            item.ingestion.consumerName == consumerName &&
                t >= rangeStartInstant &&
                t < endOfTodayInstant
          }
          .map { FilteredIngestion(experienceId = exp.experience.id, data = it) }
    }
    val companionsByName = companions.associateBy { it.substanceName }
    val chartBuckets =
        buildChartBuckets(
            filtered = filtered,
            companionsByName = companionsByName,
            option = option,
            endOfToday = endOfToday,
            locale = locale,
        )
    val statItems = buildStatItems(filtered, companionsByName, sortOption)

    return StatsModel(
        selectedOption = option,
        areThereAnyIngestions = areThereAnyIngestions,
        startDateText = startDateText,
        statItems = statItems,
        chartBuckets = chartBuckets,
        consumerName = consumerName,
        totalIngestions = filtered.size,
        totalExperiences = filtered.mapTo(HashSet()) { it.experienceId }.size,
        sortOption = sortOption,
    )
  }

  private fun buildChartBuckets(
      filtered: List<FilteredIngestion>,
      companionsByName: Map<String, SubstanceCompanion>,
      option: TimePickerOption,
      endOfToday: java.time.ZonedDateTime,
      locale: Locale,
  ): List<StatsChartBucket> {
    val labelPattern =
        when (option) {
          TimePickerOption.DAYS_7 -> "EEE"
          TimePickerOption.DAYS_30,
          TimePickerOption.WEEKS_26 -> dayMonthPattern(locale)
          TimePickerOption.MONTHS_12 -> "MMM"
          TimePickerOption.YEARS_10 -> "yyyy"
        }
    val labelFormat = formatterFor(labelPattern, locale)
    val fullDateFormat = formatterFor("dd MMM yyyy", locale)
    val isSingleDayBucket = option.oneBucketSize == Period.ofDays(1)
    val sortedFiltered = filtered.sortedBy { it.data.ingestion.time }
    val buckets = ArrayDeque<StatsChartBucket>(option.bucketCount)
    var bucketEnd = endOfToday
    var ingestionIndex = sortedFiltered.lastIndex
    repeat(option.bucketCount) {
      val bucketStart = bucketEnd.minus(option.oneBucketSize)
      val bucketStartInstant = bucketStart.toInstant()
      val bucketEndInstant = bucketEnd.toInstant()
      while (
          ingestionIndex >= 0 &&
              sortedFiltered[ingestionIndex].data.ingestion.time >= bucketEndInstant
      ) {
        ingestionIndex--
      }
      val countsBySubstance = HashMap<String, Int>()
      while (
          ingestionIndex >= 0 &&
              sortedFiltered[ingestionIndex].data.ingestion.time >= bucketStartInstant
      ) {
        val name = sortedFiltered[ingestionIndex].data.ingestion.substanceName
        countsBySubstance[name] = (countsBySubstance[name] ?: 0) + 1
        ingestionIndex--
      }
      val items =
          countsBySubstance
              .map { (substanceName, count) ->
                ColorCount(
                    color = companionsByName[substanceName]?.color ?: AdaptiveColor.BLUE,
                    count = count,
                    substanceName = substanceName,
                )
              }
              .sortedWith(compareByDescending<ColorCount> { it.count }.thenBy { it.substanceName })
      val fullDateText =
          if (isSingleDayBucket) {
            fullDateFormat.format(bucketStart)
          } else {
            "${fullDateFormat.format(bucketStart)} – ${fullDateFormat.format(bucketEnd.minusDays(1))}"
          }
      buckets.addFirst(
          StatsChartBucket(
              label = labelFormat.format(bucketStart),
              fullDateText = fullDateText,
              items = items,
          )
      )
      bucketEnd = bucketStart
    }
    return buckets.toList()
  }

  private fun buildStatItems(
      filtered: List<FilteredIngestion>,
      companionsByName: Map<String, SubstanceCompanion>,
      sortOption: StatsSortOption,
  ): List<StatItem> {
    val grouped = filtered.groupBy { it.data.ingestion.substanceName }
    val items = grouped.map { (name, entries) ->
      val groupedIngestions = entries.map { it.data }
      val ingestions = groupedIngestions.map { it.ingestion }
      val color = companionsByName[name]?.color ?: AdaptiveColor.BLUE
      val experienceCount = entries.mapTo(HashSet()) { it.experienceId }.size
      val totalDose = computeTotalDose(groupedIngestions)
      val knownOnly = groupedIngestions.filter { it.pureDose != null }
      val knownTotalDose =
          totalDose ?: knownOnly.takeIf { it.isNotEmpty() }?.let(::computeTotalDose)
      val hasMixedUnits = totalDose == null && knownOnly.isNotEmpty() && knownTotalDose == null
      StatItem(
          substanceName = name,
          color = color,
          experienceCount = experienceCount,
          ingestionCount = groupedIngestions.size,
          routeCounts = getRouteCounts(ingestions),
          totalDose = totalDose,
          lastUsed = ingestions.maxOfOrNull { it.time },
          unknownDoseCount = groupedIngestions.count { it.pureDose == null },
          unitNames = ingestions.mapNotNull { it.units?.takeIf(String::isNotBlank) }.distinct(),
          knownTotalDose = knownTotalDose,
          hasMixedUnits = hasMixedUnits,
      )
    }
    return sortStatItems(items, sortOption)
  }

  fun sortStatItems(items: List<StatItem>, sortOption: StatsSortOption): List<StatItem> =
      when (sortOption) {
        StatsSortOption.EXPERIENCES ->
            items.sortedWith(
                compareByDescending<StatItem> { it.experienceCount }
                    .thenByDescending { it.ingestionCount }
                    .thenBy { it.substanceName }
            )
        StatsSortOption.INGESTIONS ->
            items.sortedWith(
                compareByDescending<StatItem> { it.ingestionCount }
                    .thenByDescending { it.experienceCount }
                    .thenBy { it.substanceName }
            )
        StatsSortOption.RECENT ->
            items.sortedWith(
                compareByDescending<StatItem> { it.lastUsed ?: Instant.MIN }
                    .thenByDescending { it.experienceCount }
                    .thenBy { it.substanceName }
            )
      }

  private fun getRouteCounts(groupedIngestions: List<Ingestion>): List<RouteCount> =
      groupedIngestions
          .groupBy { it.administrationRoute }
          .map { (route, group) -> RouteCount(administrationRoute = route, count = group.size) }
          .sortedWith(
              compareByDescending<RouteCount> { it.count }.thenBy { it.administrationRoute.name }
          )

  private fun dayMonthPattern(locale: Locale): String =
      dayMonthPatternCache.getOrPut(locale) {
        val shortPattern =
            DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                FormatStyle.SHORT,
                null,
                IsoChronology.INSTANCE,
                locale,
            )
        shortPattern.replace(yearPatternRegex, "").trim().ifEmpty { "dd.MM" }
      }

  fun computeTotalDose(groupedIngestions: List<IngestionWithCompanionAndCustomUnit>): TotalDose? {
    // Every dose is converted to its family's base unit (mg or ml) so g, mg, µg and custom units
    // that resolve to them add up.
    val bases = groupedIngestions.map { DoseUnits.toBase(it.originalUnit) ?: return null }
    val baseUnit = bases.firstOrNull()?.first ?: return null
    val hasIncompatibleDose =
        bases.any { it.first != baseUnit } ||
            groupedIngestions.any { it.pureDose?.isFinite() != true }
    if (hasIncompatibleDose) return null
    val scaled = groupedIngestions.zip(bases) { ingestion, base -> ingestion to base.second }
    val sumDose = scaled.sumOf { (ingestion, factor) -> (ingestion.pureDose ?: 0.0) * factor }

    val independentIngestions = mutableListOf<Pair<IngestionWithCompanionAndCustomUnit, Double>>()
    val customUnitEstimates =
        mutableListOf<
            Triple<
                IngestionWithCompanionAndCustomUnit,
                Double,
                foo.pilz.freaklog.data.room.experiences.entities.CustomUnit,
            >
        >()
    for ((ingestion, factor) in scaled) {
      val cu = ingestion.customUnit
      if (cu != null && cu.isEstimate && (cu.dose ?: 0.0) > 0.0) {
        customUnitEstimates += Triple(ingestion, factor, cu)
      } else {
        independentIngestions += ingestion to factor
      }
    }

    var totalVariance = independentIngestions.sumOf { (ingestion, factor) ->
      val sd = (ingestion.pureDoseStandardDeviation ?: 0.0) * factor
      sd * sd
    }

    val byCustomUnit = customUnitEstimates.groupBy { it.third.id }
    for ((_, scaledItems) in byCustomUnit) {
      val factor = scaledItems.first().second
      val customUnit = scaledItems.first().third
      val items = scaledItems.map { it.first }
      val expectationY = customUnit.dose ?: 0.0
      val standardDeviationY = customUnit.estimatedDoseStandardDeviation ?: 0.0
      val sumX = items.sumOf { it.ingestion.dose ?: 0.0 }
      val varianceX = items.sumOf {
        if (it.ingestion.isDoseAnEstimate) {
          val sd = it.ingestion.estimatedDoseStandardDeviation ?: 0.0
          sd * sd
        } else {
          0.0
        }
      }
      val sum1 = varianceX + sumX.pow(2)
      val sum2 = standardDeviationY.pow(2) + expectationY.pow(2)
      val expectations = sumX.pow(2) * expectationY.pow(2)
      val groupVariance = (sum1 * sum2 - expectations).coerceAtLeast(0.0)
      totalVariance += groupVariance * factor * factor
    }

    val (units, divisor) = DoseUnits.displayUnit(baseUnit, sumDose)
    val combinedStandardDeviation = sqrt(totalVariance) / divisor
    val isEstimate = groupedIngestions.any { it.isEstimate }
    return TotalDose(
        dose = sumDose / divisor,
        units = units,
        isEstimate = isEstimate,
        estimatedDoseStandardDeviation = combinedStandardDeviation.takeIf { it > 0.0 },
    )
  }
}

enum class StatsSortOption(val displayText: String) {
  EXPERIENCES("Experiences"),
  INGESTIONS("Ingestions"),
  RECENT("Recent"),
}

data class StatsScreenCallbacks(
    val navigateToSubstanceCompanion: (substanceName: String, consumerName: String?) -> Unit =
        { _, _ ->
        },
    val navigateToToleranceChart: () -> Unit = {},
    val onTapOption: (option: TimePickerOption) -> Unit = {},
    val onChangeConsumerName: (String?) -> Unit = {},
    val onChangeSortOption: (StatsSortOption) -> Unit = {},
    val onOpenSpreadsheet: () -> Unit = {},
)

data class StatsChartBucket(
    val label: String,
    val fullDateText: String,
    val items: List<ColorCount>,
) {
  val totalCount: Int
    get() = items.sumOf { it.count }
}

data class StatsModel(
    val selectedOption: TimePickerOption,
    val areThereAnyIngestions: Boolean,
    val startDateText: String,
    val statItems: List<StatItem>,
    val chartBuckets: List<StatsChartBucket>,
    val consumerName: String?,
    val totalIngestions: Int = statItems.sumOf { it.ingestionCount },
    val totalExperiences: Int = 0,
    val sortOption: StatsSortOption = StatsSortOption.EXPERIENCES,
)

data class ColorCount(
    val color: AdaptiveColor,
    val count: Int,
    val substanceName: String = "",
)

data class StatItem(
    val substanceName: String,
    val color: AdaptiveColor,
    val experienceCount: Int,
    val ingestionCount: Int,
    val routeCounts: List<RouteCount>,
    val totalDose: TotalDose?,
    val lastUsed: Instant?,
    val unknownDoseCount: Int,
    val unitNames: List<String>,
    val knownTotalDose: TotalDose? = totalDose,
    val hasMixedUnits: Boolean = false,
)

data class RouteCount(
    val administrationRoute: AdministrationRoute,
    val count: Int,
)

data class TotalDose(
    val dose: Double,
    val units: String,
    val isEstimate: Boolean,
    val estimatedDoseStandardDeviation: Double?,
)

enum class TimePickerOption {
  DAYS_7 {
    override val displayText = "7D"
    override val longDisplayText = "Last week"
    override val emptyRangeText = "the last 7 days"
    override val tabIndex = 0
    override val bucketCount = 7
    override val oneBucketSize: Period = Period.ofDays(1)
    override val allBucketSizes: Period = Period.ofDays(7)
  },
  DAYS_30 {
    override val displayText = "30D"
    override val longDisplayText = "Last month"
    override val emptyRangeText = "the last 30 days"
    override val tabIndex = 1
    override val bucketCount = 30
    override val oneBucketSize: Period = Period.ofDays(1)
    override val allBucketSizes: Period = Period.ofDays(30)
  },
  WEEKS_26 {
    override val displayText = "26W"
    override val longDisplayText = "Half year"
    override val emptyRangeText = "the last 26 weeks"
    override val tabIndex = 2
    override val bucketCount = 26
    override val oneBucketSize: Period = Period.ofDays(7)
    override val allBucketSizes: Period = Period.ofDays(7 * bucketCount)
  },
  MONTHS_12 {
    override val displayText = "12M"
    override val longDisplayText = "Last year"
    override val emptyRangeText = "the last 12 months"
    override val tabIndex = 3
    override val bucketCount = 12
    override val oneBucketSize: Period = Period.ofMonths(1)
    override val allBucketSizes: Period = Period.ofMonths(12)
  },
  YEARS_10 {
    override val displayText = "10Y"
    override val longDisplayText = "10 years"
    override val emptyRangeText = "the last 10 years"
    override val tabIndex = 4
    override val bucketCount = 10
    override val oneBucketSize: Period = Period.ofYears(1)
    override val allBucketSizes: Period = Period.ofYears(10)
  };

  abstract val displayText: String
  abstract val longDisplayText: String
  abstract val emptyRangeText: String
  abstract val tabIndex: Int
  abstract val bucketCount: Int
  abstract val oneBucketSize: Period
  abstract val allBucketSizes: Period
}

fun Instant.getEndOfDay(): Instant {
  return this.atOffset(ZoneOffset.UTC).with(LocalTime.of(23, 59, 59, this.nano)).toInstant()
}
