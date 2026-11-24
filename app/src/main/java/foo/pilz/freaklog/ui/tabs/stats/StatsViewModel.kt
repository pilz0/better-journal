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
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
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

  fun onTapOption(timePickerOption: TimePickerOption) {
    viewModelScope.launch {
      _optionFlow.emit(timePickerOption)
    }
  }

  fun onChangeConsumer(consumerName: String?) {
    viewModelScope.launch {
      consumerFlow.emit(consumerName)
    }
  }

  private val startDateFlow = _optionFlow.map {
    return@map Instant.now().getEndOfDay().minus(it.allBucketSizes)
  }

  private val startDateTextFlow = startDateFlow.map {
    val dateTime = LocalDateTime.ofInstant(it, ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy")
    return@map dateTime.format(formatter)
  }

  // Shared so the pipelines below don't each re-collect (and re-query) the Room flow.
  private val allExperiencesSortedFlow: StateFlow<List<ExperienceWithIngestionsAndCompanions>> =
      experienceRepo
          .getSortedExperiencesWithIngestionsAndCustomUnitsFlow()
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val areThereAnyIngestionsFlow =
      allExperiencesSortedFlow.combine(consumerFlow) { experiences, consumerName ->
        experiences.any { experience ->
          experience.ingestionsWithCompanionAndCustomUnit.any {
            it.ingestion.consumerName == consumerName
          }
        }
      }

  private val relevantExperiencesSortedFlow: Flow<List<ExperienceWithIngestionsAndCompanions>> =
      allExperiencesSortedFlow.combine(startDateFlow) { experiences, startDate ->
        return@combine experiences
            .dropWhile { it.sortInstant > Instant.now().getEndOfDay() }
            .takeWhile { it.sortInstant > startDate }
      }

  private val companionFlow: StateFlow<List<SubstanceCompanion>> =
      experienceRepo
          .getAllSubstanceCompanionsFlow()
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val experienceChartBucketsFlow: Flow<List<List<ColorCount>>> =
      relevantExperiencesSortedFlow
          .combine(optionFlow) { sortedExperiences, option ->
            var remainingExperiences = sortedExperiences
            val buckets = mutableListOf<List<ExperienceWithIngestionsAndCompanions>>()
            var startInstant = Instant.now().getEndOfDay()
            repeat(option.bucketCount) {
              startInstant = startInstant.minus(option.oneBucketSize)
              val experiencesForBucket = remainingExperiences.takeWhile {
                it.sortInstant > startInstant
              }
              buckets.add(experiencesForBucket)
              val numExperiences = experiencesForBucket.size
              remainingExperiences =
                  remainingExperiences.takeLast(remainingExperiences.size - numExperiences)
            }
            return@combine buckets
          }
          .combine(consumerFlow) { buckets, consumerName ->
            Pair(buckets, consumerName)
          }
          .combine(companionFlow) { pair, companions ->
            return@combine pair.first
                .map { experiencesInBucket ->
                  return@map getIngestionColorCounts(
                      experiencesInBucket,
                      companions,
                      pair.second,
                  )
                }
                .reversed()
          }

  private fun getIngestionColorCounts(
      experiences: List<ExperienceWithIngestionsAndCompanions>,
      companions: List<SubstanceCompanion>,
      consumerName: String?,
  ): List<ColorCount> {
    val ingestions = experiences.flatMap { experience ->
      experience.ingestionsWithCompanionAndCustomUnit.filter {
        it.ingestion.consumerName == consumerName
      }
    }
    val companionsByName = companions.associateBy { it.substanceName }
    return ingestions
        .groupBy { it.ingestion.substanceName }
        .mapNotNull { (substanceName, ingestionsForSubstance) ->
          val companion = companionsByName[substanceName] ?: return@mapNotNull null
          ColorCount(
              color = companion.color,
              count = ingestionsForSubstance.size,
          )
        }
        .sortedByDescending { it.count }
  }

  private val statsFlowItem: Flow<List<StatItem>> =
      combine(
          relevantExperiencesSortedFlow,
          consumerFlow,
          companionFlow,
      ) { relevantExperiencesSorted, consumerName, companions ->
        val allIngestions = relevantExperiencesSorted.flatMap { experience ->
          experience.ingestionsWithCompanionAndCustomUnit.filter {
            it.ingestion.consumerName == consumerName
          }
        }
        val experienceCountsBySubstance =
            relevantExperiencesSorted
                .flatMap { experience ->
                  experience.ingestionsWithCompanionAndCustomUnit
                      .filter { it.ingestion.consumerName == consumerName }
                      .map { it.ingestion.substanceName }
                      .toSet()
                }
                .groupingBy { it }
                .eachCount()
        val companionsByName = companions.associateBy { it.substanceName }
        val map = allIngestions.groupBy { it.ingestion.substanceName }
        return@combine map.values
            .mapNotNull { groupedIngestions ->
              val name =
                  groupedIngestions.firstOrNull()?.ingestion?.substanceName
                      ?: return@mapNotNull null
              val oneCompanion = companionsByName[name] ?: return@mapNotNull null
              val experienceCounts = experienceCountsBySubstance[name] ?: 0
              val ingestions = groupedIngestions.map { it.ingestion }
              StatItem(
                  substanceName = name,
                  color = oneCompanion.color,
                  experienceCount = experienceCounts,
                  ingestionCount = groupedIngestions.size,
                  routeCounts = getRouteCounts(ingestions),
                  totalDose = getTotalDose(groupedIngestions),
                  lastUsed = ingestions.maxOfOrNull { it.time },
                  unknownDoseCount =
                      ingestions.count { it.dose == null && it.customUnitId == null },
                  unitNames =
                      ingestions.mapNotNull { it.units?.takeIf(String::isNotBlank) }.distinct(),
              )
            }
            .sortedByDescending { it.experienceCount }
      }

  private fun getRouteCounts(groupedIngestions: List<Ingestion>): List<RouteCount> {
    val routeMap = groupedIngestions.groupBy { it.administrationRoute }
    return routeMap.values.mapNotNull {
      val route = it.firstOrNull()?.administrationRoute ?: return@mapNotNull null
      RouteCount(administrationRoute = route, count = it.size)
    }
  }

  internal fun getTotalDose(
      groupedIngestions: List<IngestionWithCompanionAndCustomUnit>
  ): TotalDose? {
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

    val (customUnitEstimates, independentIngestions) =
        scaled.partition { (ingestion, _) ->
          ingestion.customUnit != null &&
              ingestion.customUnit?.isEstimate == true &&
              (ingestion.customUnit?.dose ?: 0.0) > 0.0
        }

    var totalVariance = independentIngestions.sumOf { (ingestion, factor) ->
      val sd = (ingestion.pureDoseStandardDeviation ?: 0.0) * factor
      sd * sd
    }

    val byCustomUnit = customUnitEstimates.groupBy { it.first.customUnit!!.id }
    for ((_, scaledItems) in byCustomUnit) {
      val factor = scaledItems.first().second
      val items = scaledItems.map { it.first }
      val customUnit = items.first().customUnit!!
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

  val statsModelFlow: StateFlow<StatsModel> =
      combine(
              combine(optionFlow, startDateTextFlow, ::Pair),
              statsFlowItem,
              areThereAnyIngestionsFlow,
              consumerFlow,
              experienceChartBucketsFlow,
          ) { (option, startDateText), substanceStats, areThere, consumerName, chartBuckets ->
            StatsModel(
                selectedOption = option,
                startDateText = startDateText,
                statItems = substanceStats,
                areThereAnyIngestions = areThere,
                chartBuckets = chartBuckets,
                consumerName = consumerName,
            )
          }
          .flowOn(Dispatchers.Default)
          .stateIn(
              initialValue =
                  StatsModel(
                      selectedOption = TimePickerOption.WEEKS_26,
                      areThereAnyIngestions = false,
                      startDateText = "",
                      statItems = emptyList(),
                      chartBuckets = emptyList(),
                      consumerName = null,
                  ),
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(5000),
          )

  val sortedConsumerNamesFlow =
      experienceRepo
          .getSortedIngestions(limit = 200)
          .map { ingestions ->
            return@map ingestions.mapNotNull { it.consumerName }.distinct()
          }
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
        statsModelFlow.map { ExcelSkinPayload(statItems = it.statItems, fileName = fileName) },
    )
  }
}

data class StatsModel(
    val selectedOption: TimePickerOption,
    val areThereAnyIngestions: Boolean,
    val startDateText: String,
    val statItems: List<StatItem>,
    val chartBuckets: List<List<ColorCount>>,
    val consumerName: String?,
)

data class ColorCount(
    val color: AdaptiveColor,
    val count: Int,
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
    override val tabIndex = 0
    override val bucketCount = 7
    override val oneBucketSize: Period = Period.ofDays(1)
    override val allBucketSizes: Period = Period.ofDays(7)
  },
  DAYS_30 {
    override val displayText = "30D"
    override val longDisplayText = "Last month"
    override val tabIndex = 1
    override val bucketCount = 30
    override val oneBucketSize: Period = Period.ofDays(1)
    override val allBucketSizes: Period = Period.ofDays(30)
  },
  WEEKS_26 {
    override val displayText = "26W"
    override val longDisplayText = "Half year"
    override val tabIndex = 2
    override val bucketCount = 26
    override val oneBucketSize: Period =
        Period.ofDays(7) // the max time unit that can be used for subtraction is days
    override val allBucketSizes: Period = Period.ofDays(7 * bucketCount)
  },
  MONTHS_12 {
    override val displayText = "12M"
    override val longDisplayText = "Last year"
    override val tabIndex = 3
    override val bucketCount = 12
    override val oneBucketSize: Period = Period.ofDays(30)
    override val allBucketSizes: Period = Period.ofDays(30 * bucketCount)
  },
  YEARS_10 {
    override val displayText = "10Y"
    override val longDisplayText = "10 years"
    override val tabIndex = 4
    override val bucketCount = 10
    override val oneBucketSize: Period = Period.ofDays(365)
    override val allBucketSizes: Period = Period.ofDays(365 * bucketCount)
  };

  abstract val displayText: String
  abstract val longDisplayText: String
  abstract val tabIndex: Int
  abstract val bucketCount: Int
  abstract val oneBucketSize: Period
  abstract val allBucketSizes: Period
}

fun Instant.getEndOfDay(): Instant {
  return this.atOffset(ZoneOffset.UTC).with(LocalTime.of(23, 59, 59, this.nano)).toInstant()
}
