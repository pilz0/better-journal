/*
 * Copyright (c) 2022. Isaak Hanimann.
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

package foo.pilz.freaklog.ui.tabs.stats.substancecompanion

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceProfiles
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.room.experiences.entities.CustomUnit
import foo.pilz.freaklog.data.room.experiences.entities.Experience
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.room.experiences.findProfile
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithExperienceAndCustomUnit
import foo.pilz.freaklog.data.room.experiences.toTolerance
import foo.pilz.freaklog.data.substances.classes.Tolerance
import foo.pilz.freaklog.data.substances.classes.roa.Roa
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.ui.main.navigation.graphs.SubstanceCompanionRoute
import foo.pilz.freaklog.ui.tabs.journal.addingestion.search.suggestion.models.CustomUnitDose
import foo.pilz.freaklog.ui.tabs.search.substance.roa.toReadableString
import foo.pilz.freaklog.ui.utils.getTimeDifferenceText
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SubstanceInfoTarget {
  data class BuiltIn(val substanceName: String) : SubstanceInfoTarget

  data class Custom(val customSubstanceId: Int) : SubstanceInfoTarget
}

@HiltViewModel
class SubstanceCompanionViewModel
@Inject
constructor(
    experienceRepo: ExperienceRepository,
    substanceRepo: SubstanceRepository,
    customProfiles: CustomSubstanceProfiles,
    state: SavedStateHandle,
) : ViewModel() {

  // One shared ticker instead of a separate timer loop per collecting pipeline.
  private val currentTimeFlow: Flow<Instant> =
      flow {
            while (true) {
              emit(Instant.now())
              delay(timeMillis = 1000 * 10)
            }
          }
          .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

  // Coarser minute ticker for aggregate stats and frequency (no sub-minute UI counters).
  private val currentMinuteFlow: Flow<Instant> =
      flow {
            while (true) {
              emit(Instant.now())
              delay(timeMillis = 60_000L)
            }
          }
          .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

  private val substanceCompanionRoute = state.toRoute<SubstanceCompanionRoute>()
  val substanceName: String = substanceCompanionRoute.substanceName
  val consumerName = substanceCompanionRoute.consumerName

  private val substance = substanceRepo.getSubstance(substanceName)
  private val customProfileFlow =
      customProfiles.byNameFlow.map { profiles -> findProfile(profiles, substanceName) }

  private val initialCustomProfile = findProfile(customProfiles.byNameFlow.value, substanceName)

  val toleranceFlow: StateFlow<Tolerance?> =
      customProfileFlow
          .map { customProfile ->
            substance?.tolerance ?: customProfile?.substance?.toTolerance()
          }
          .stateIn(
              initialValue = substance?.tolerance ?: initialCustomProfile?.substance?.toTolerance(),
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(5000),
          )

  val crossTolerancesFlow: StateFlow<List<String>> =
      customProfileFlow
          .map { customProfile ->
            if (substance != null) {
              substance.crossTolerances
            } else {
              customProfile?.crossTolerances?.map { it.categoryName }.orEmpty()
            }
          }
          .stateIn(
              initialValue =
                  if (substance != null) {
                    substance.crossTolerances
                  } else {
                    initialCustomProfile?.crossTolerances?.map { it.categoryName }.orEmpty()
                  },
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(5000),
          )

  val substanceInfoTargetFlow: StateFlow<SubstanceInfoTarget?> =
      customProfileFlow
          .map { customProfile ->
            when {
              substance != null -> SubstanceInfoTarget.BuiltIn(substance.name)
              customProfile != null -> SubstanceInfoTarget.Custom(customProfile.substance.id)
              else -> null
            }
          }
          .stateIn(
              initialValue =
                  when {
                    substance != null -> SubstanceInfoTarget.BuiltIn(substance.name)
                    initialCustomProfile != null ->
                        SubstanceInfoTarget.Custom(initialCustomProfile.substance.id)
                    else -> null
                  },
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(5000),
          )

  val thisCompanionFlow: StateFlow<SubstanceCompanion?> =
      experienceRepo
          .getSubstanceCompanionFlow(substanceName)
          .map {
            it ?: SubstanceCompanion(substanceName = substanceName, color = AdaptiveColor.BLUE)
          }
          .stateIn(
              initialValue = null,
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(5000),
          )

  private val _selectedTimeRange = MutableStateFlow(DosageTimeRange.WEEKS_26)
  val selectedTimeRange = _selectedTimeRange.asStateFlow()

  private val _showAverage = MutableStateFlow(false)
  val showAverage = _showAverage.asStateFlow()

  private val _showTrendLine = MutableStateFlow(false)
  val showTrendLine = _showTrendLine.asStateFlow()

  private val _selectedMetric = MutableStateFlow(DosageMetric.TOTAL_DOSE)
  val selectedMetric = _selectedMetric.asStateFlow()

  fun setTimeRange(range: DosageTimeRange) {
    viewModelScope.launch { _selectedTimeRange.emit(range) }
  }

  fun toggleShowAverage(show: Boolean) {
    viewModelScope.launch { _showAverage.emit(show) }
  }

  fun toggleShowTrendLine(show: Boolean) {
    viewModelScope.launch { _showTrendLine.emit(show) }
  }

  fun setMetric(metric: DosageMetric) {
    viewModelScope.launch { _selectedMetric.emit(metric) }
  }

  // Source of all ingestions for this substance/consumer.
  // Shared once via shareIn(replay = 1) so downstream pipelines
  // (`doseThresholdsFlow`, `dosageStatsFlow`, `ingestionBurstsFlow`, `frequencyFlow`)
  // share one Room collection without emitting a bogus emptyList() before Room answers.
  private val allIngestionsFlow =
      experienceRepo
          .getSortedIngestionsWithExperienceAndCustomUnitFlow(substanceName)
          .map { list -> list.filter { it.ingestion.consumerName == consumerName } }
          .flowOn(Dispatchers.Default)
          .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

  /**
   * Dose thresholds from PsychonautWiki or custom substance profile, preferring the user's most
   * frequently logged route that defines dose thresholds.
   */
  val doseThresholdsFlow: StateFlow<DoseThresholds?> =
      combine(allIngestionsFlow, customProfileFlow) { ingestions, customProfile ->
            val roas: List<Roa> =
                substance?.roas ?: customProfile?.roaInfos?.map { it.toRoa() }.orEmpty()
            val roasWithThresholds = roas.filter { roa ->
              val d = roa.roaDose
              d != null &&
                  (d.lightMin != null ||
                      d.commonMin != null ||
                      d.strongMin != null ||
                      d.heavyMin != null)
            }
            if (roasWithThresholds.isEmpty()) return@combine null
            val countsByRoute =
                ingestions.groupingBy { it.ingestion.administrationRoute }.eachCount()
            val chosenRoa =
                roasWithThresholds.maxByOrNull { countsByRoute[it.route] ?: 0 }
                    ?: roasWithThresholds.first()
            chosenRoa.roaDose?.let { d ->
              DoseThresholds(
                  unit = d.units,
                  lightMin = d.lightMin,
                  commonMin = d.commonMin,
                  strongMin = d.strongMin,
                  heavyMin = d.heavyMin,
                  route = chosenRoa.route,
              )
            }
          }
          .stateIn(
              initialValue = null,
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(5000),
          )

  val dosageStatsFlow: StateFlow<SubstanceDosageStatsModel?> =
      combine(allIngestionsFlow, selectedTimeRange, currentMinuteFlow, doseThresholdsFlow) {
              ingestions,
              timeRange,
              currentTime,
              thresholds ->
            SubstanceDosageStatsHelper.build(
                ingestions = ingestions.map { it.ingestion },
                range = timeRange,
                now = currentTime,
                zone = ZoneId.systemDefault(),
                customUnits = ingestions.mapNotNull { it.customUnit }.associateBy { it.id },
                preferredUnit = thresholds?.unit,
            )
          }
          .flowOn(Dispatchers.Default)
          .stateIn(
              initialValue = null,
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(5000),
          )

  val ingestionBurstsFlow: StateFlow<List<IngestionsBurst>?> =
      allIngestionsFlow
          .combine(currentTimeFlow, ::buildIngestionBursts)
          .flowOn(Dispatchers.Default)
          .stateIn(
              initialValue = null,
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(5000),
          )

  private fun buildIngestionBursts(
      sortedIngestionsWithExperiences: List<IngestionWithExperienceAndCustomUnit>,
      currentTime: Instant,
  ): List<IngestionsBurst> {
    val sortedExperiences =
        sortedIngestionsWithExperiences
            .groupBy { it.ingestion.experienceId }
            .values
            .mapNotNull { entries ->
              val experience = entries.firstOrNull()?.experience ?: return@mapNotNull null
              val ingestionsSorted =
                  entries
                      .map { IngestionsBurst.IngestionAndCustomUnit(it.ingestion, it.customUnit) }
                      .sortedBy { it.ingestion.time }
              experience to ingestionsSorted
            }
            .sortedByDescending { (_, ingestionsSorted) -> ingestionsSorted.last().ingestion.time }
    var lastDate = currentTime
    val allIngestionBursts = mutableListOf<IngestionsBurst>()
    for ((experience, ingestionsSorted) in sortedExperiences) {
      val experienceStart = ingestionsSorted.first().ingestion.time
      val experienceEnd = ingestionsSorted.last().ingestion.time
      val diffText =
          if (experienceEnd > currentTime && experienceEnd > lastDate) {
            "in " +
                getTimeDifferenceText(
                    fromInstant = lastDate,
                    toInstant = experienceEnd,
                )
          } else {
            getTimeDifferenceText(
                fromInstant = experienceEnd,
                toInstant = lastDate,
            )
          }
      allIngestionBursts.add(
          IngestionsBurst(
              timeUntil = diffText,
              experience = experience,
              ingestions = ingestionsSorted,
          )
      )
      lastDate = experienceStart
    }
    return allIngestionBursts
  }

  /**
   * Frequency of this substance over three time ranges, normalized to the busiest range. Used by
   * the "Usage frequency" section of the screen.
   */
  val frequencyFlow: StateFlow<SubstanceFrequency?> =
      combine(allIngestionsFlow, currentMinuteFlow) { ingestions, now ->
            SubstanceFrequency.compute(
                ingestions = ingestions.map { it.ingestion },
                now = now,
                zone = ZoneId.systemDefault(),
            )
          }
          .flowOn(Dispatchers.Default)
          .stateIn(
              initialValue = null,
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(5000),
          )
}

enum class DosageTimeRange(val displayText: String, val title: String, val period: Period?) {
  DAYS_30("30D", "Last 30 days", Period.ofDays(30)),
  WEEKS_26("26W", "Last 26 weeks", Period.ofWeeks(26)),
  MONTHS_12("12M", "Last 12 months", Period.ofMonths(12)),
  ALL("All", "All time", null),
}

/**
 * Counts of distinct experiences containing this substance, broken down by time range. Used by the
 * SubstanceCompanionScreen "Usage frequency" section.
 */
data class SubstanceFrequency(
    val last30Days: Int,
    val last12Months: Int,
    val allTime: Int,
) {
  companion object {
    /**
     * Counts distinct experiences in a single pass. The ranges are nested, so the narrower cutoffs
     * are only checked once the wider one matched. Future-dated ingestions count toward all time
     * but not toward the past periods.
     */
    fun compute(ingestions: List<Ingestion>, now: Instant, zone: ZoneId): SubstanceFrequency {
      val zonedNow = now.atZone(zone)
      val last12MonthsFrom = zonedNow.minusMonths(12).toInstant()
      val last30DaysFrom = zonedNow.minusDays(30).toInstant()
      val allTime = HashSet<Int>()
      val last12Months = HashSet<Int>()
      val last30Days = HashSet<Int>()
      for (ingestion in ingestions) {
        val experienceId = ingestion.experienceId
        allTime.add(experienceId)
        if (ingestion.time <= now && ingestion.time >= last12MonthsFrom) {
          last12Months.add(experienceId)
          if (ingestion.time >= last30DaysFrom) {
            last30Days.add(experienceId)
          }
        }
      }
      return SubstanceFrequency(
          last30Days = last30Days.size,
          last12Months = last12Months.size,
          allTime = allTime.size,
      )
    }
  }
}

data class IngestionsBurst(
    val timeUntil: String,
    val experience: Experience,
    val ingestions: List<IngestionAndCustomUnit>,
) {
  data class IngestionAndCustomUnit(
      val ingestion: Ingestion,
      val customUnit: CustomUnit?,
  ) {
    val customUnitDose: CustomUnitDose?
      get() =
          ingestion.dose?.let { doseUnwrapped ->
            customUnit?.let { customUnitUnwrapped ->
              CustomUnitDose(
                  dose = doseUnwrapped,
                  isEstimate = ingestion.isDoseAnEstimate,
                  estimatedDoseStandardDeviation = ingestion.estimatedDoseStandardDeviation,
                  customUnit = customUnitUnwrapped,
              )
            }
          }

    val doseDescription: String
      get() = customUnitDose?.doseDescription ?: ingestionDoseDescription

    private val ingestionDoseDescription
      get() =
          ingestion.dose?.let { dose ->
            val unitSuffix = ingestion.units?.takeIf(String::isNotBlank)?.let { " $it" }.orEmpty()
            ingestion.estimatedDoseStandardDeviation?.let { estimatedDoseStandardDeviation ->
              "${dose.toReadableString()}±${estimatedDoseStandardDeviation.toReadableString()}$unitSuffix"
            }
                ?: run {
                  val description = "${dose.toReadableString()}$unitSuffix"
                  if (ingestion.isDoseAnEstimate) {
                    "~$description"
                  } else {
                    description
                  }
                }
          } ?: "Unknown dose"
  }
}
