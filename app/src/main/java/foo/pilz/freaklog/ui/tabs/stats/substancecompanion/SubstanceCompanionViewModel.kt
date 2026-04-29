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
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomUnit
import foo.pilz.freaklog.data.room.experiences.entities.Experience
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.ui.main.navigation.graphs.SubstanceCompanionRoute
import foo.pilz.freaklog.ui.tabs.journal.addingestion.search.suggestion.models.CustomUnitDose
import foo.pilz.freaklog.ui.tabs.search.substance.roa.toReadableString
import foo.pilz.freaklog.ui.utils.getTimeDifferenceText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class SubstanceCompanionViewModel @Inject constructor(
    experienceRepo: ExperienceRepository,
    substanceRepo: SubstanceRepository,
    state: SavedStateHandle
) : ViewModel() {

    private val currentTimeFlow: Flow<Instant> = flow {
        while (true) {
            emit(Instant.now())
            delay(timeMillis = 1000 * 10)
        }
    }

    private val substanceCompanionRoute = state.toRoute<SubstanceCompanionRoute>()
    private val substanceName = substanceCompanionRoute.substanceName
    val consumerName = substanceCompanionRoute.consumerName

    private val substance = substanceRepo.getSubstance(substanceName)
    val tolerance = substance?.tolerance
    val crossTolerances = substance?.crossTolerances ?: emptyList()

    /** Dose thresholds from PsychonautWiki for the first ROA that has dose data. */
    val doseThresholds: DoseThresholds? = substance?.roas
        ?.firstOrNull { it.roaDose != null }
        ?.roaDose
        ?.let { d -> DoseThresholds(d.units, d.lightMin, d.commonMin, d.strongMin, d.heavyMin) }

    val thisCompanionFlow: StateFlow<SubstanceCompanion?> =
        experienceRepo.getSubstanceCompanionFlow(substanceName).stateIn(
            initialValue = null,
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000)
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

    // Source of all ingestions for this substance/consumer
    private val allIngestionsFlow = experienceRepo.getSortedIngestionsWithExperienceAndCustomUnitFlow(substanceName)
        .map { list -> list.filter { it.ingestion.consumerName == consumerName } }

    val dosageChartDataFlow: StateFlow<List<DosageBucket>> =
        combine(allIngestionsFlow, selectedTimeRange, currentTimeFlow) { ingestions, timeRange, currentTime ->
            val mapped = ingestions.map { IngestionsBurst.IngestionAndCustomUnit(it.ingestion, it.customUnit) }
            val zoneId = ZoneId.systemDefault()
            val start = if (timeRange == DosageTimeRange.ALL) {
                ingestions.minOfOrNull { it.ingestion.time } ?: currentTime
            } else {
                currentTime.atZone(zoneId).minus(timeRange.period!!).toInstant()
            }
            getDosageBuckets(mapped, timeRange, start, currentTime)
        }.stateIn(
            initialValue = emptyList(),
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000)
        )

    val chartSummaryFlow: StateFlow<ChartSummary?> =
        combine(allIngestionsFlow, selectedTimeRange, currentTimeFlow) { ingestions, timeRange, currentTime ->
            val zoneId = ZoneId.systemDefault()

            // Determine the range start
            val rangeStart: Instant? = when (timeRange) {
                DosageTimeRange.ALL -> ingestions.minOfOrNull { it.ingestion.time }
                else -> currentTime.atZone(zoneId).minus(timeRange.period!!).toInstant()
            }

            // Ingestions within the selected range
            val inRange = if (rangeStart != null) {
                ingestions.filter { it.ingestion.time >= rangeStart && it.ingestion.time <= currentTime }
            } else ingestions

            if (inRange.isEmpty()) return@combine null

            // Total distinct sessions in range
            val totalSessions = inRange.map { it.ingestion.experienceId }.distinct().size

            // Longest gap between consecutive session starts (in range)
            val sessionTimes = inRange
                .groupBy { it.ingestion.experienceId }
                .values
                .map { group -> group.minOf { it.ingestion.time } }
                .sorted()
            val longestGapDays: Int? = if (sessionTimes.size >= 2) {
                sessionTimes.zipWithNext { a, b ->
                    ChronoUnit.DAYS.between(a.atZone(zoneId), b.atZone(zoneId)).toInt()
                }.maxOrNull()
            } else null

            // Current streak: consecutive weeks going backwards from now with ≥1 session
            var streakWeeks = 0
            var weekEnd = currentTime.atZone(zoneId)
            var keepGoing = true
            var iterations = 0
            // Cap at 104 weeks (2 years) to prevent excessive computation
            while (keepGoing && iterations < 104) {
                val weekStart = weekEnd.minusWeeks(1)
                val ws = weekStart.toInstant()
                val we = weekEnd.toInstant()
                if (ingestions.any { it.ingestion.time >= ws && it.ingestion.time < we }) {
                    streakWeeks++
                    weekEnd = weekStart
                } else {
                    keepGoing = false
                }
                iterations++
            }

            ChartSummary(totalSessions, longestGapDays, streakWeeks)
        }.stateIn(
            initialValue = null,
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000)
        )

    val ingestionBurstsFlow: StateFlow<List<IngestionsBurst>> =
        allIngestionsFlow
            .combine(currentTimeFlow) { sortedIngestionsWithExperiences, currentTime ->
                val experiencesWithIngestions =
                    sortedIngestionsWithExperiences.groupBy { it.ingestion.experienceId }
                var lastDate = currentTime
                val allIngestionBursts: MutableList<IngestionsBurst> = mutableListOf()
                for (oneExperience in experiencesWithIngestions) {
                    val experience = oneExperience.value.firstOrNull()?.experience ?: continue
                    val ingestionsSorted = oneExperience.value.map {
                        IngestionsBurst.IngestionAndCustomUnit(it.ingestion, it.customUnit)
                    }.sortedBy { it.ingestion.time }
                    val experienceStart = ingestionsSorted.first().ingestion.time
                    val experienceEnd = ingestionsSorted.last().ingestion.time
                    val diffText = getTimeDifferenceText(
                        fromInstant = experienceEnd,
                        toInstant = lastDate
                    )
                    allIngestionBursts.add(
                        IngestionsBurst(
                            timeUntil = diffText,
                            experience = experience,
                            ingestions = ingestionsSorted
                        )
                    )
                    lastDate = experienceStart
                }
                allIngestionBursts
            }.stateIn(
                initialValue = emptyList(),
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000)
            )

    private fun getDosageBuckets(
        ingestions: List<IngestionsBurst.IngestionAndCustomUnit>,
        timeRange: DosageTimeRange,
        start: Instant,
        end: Instant
    ): List<DosageBucket> {
        if (!start.isBefore(end)) return emptyList()

        val zoneId = ZoneId.systemDefault()

        // Determine step and label pattern
        data class BucketConfig(val step: Period, val labelPattern: String)

        val spanDays = ChronoUnit.DAYS.between(start.atZone(zoneId), end.atZone(zoneId)).coerceAtLeast(1)

        val config: BucketConfig = when (timeRange) {
            DosageTimeRange.DAYS_30  -> BucketConfig(Period.ofDays(1),    "dd")
            DosageTimeRange.WEEKS_26 -> BucketConfig(Period.ofWeeks(1),   "dd.MM")
            DosageTimeRange.MONTHS_12 -> BucketConfig(Period.ofMonths(1), "MMM")
            DosageTimeRange.ALL -> when {
                spanDays <= 60  -> BucketConfig(Period.ofDays(1),    "dd.MM")
                spanDays <= 546 -> BucketConfig(Period.ofWeeks(1),   "dd.MM")
                else            -> BucketConfig(Period.ofMonths(1),  "MMM yy")
            }
        }

        // Determine bucket count
        val bucketCount: Int = when (timeRange) {
            DosageTimeRange.DAYS_30   -> 30
            DosageTimeRange.WEEKS_26  -> 26
            DosageTimeRange.MONTHS_12 -> 12
            DosageTimeRange.ALL -> {
                // Count steps needed to cover start→end; capped at 200 to avoid rendering
                // an unreasonable number of bars if the dataset is extremely long.
                var count = 0
                var t = end.atZone(zoneId)
                while (t.toInstant().isAfter(start) && count < 200) {
                    t = t.minus(config.step)
                    count++
                }
                count.coerceAtLeast(1)
            }
        }

        val labelFmt    = DateTimeFormatter.ofPattern(config.labelPattern)
        val fullDateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy")
        val initialUnit = ingestions.firstOrNull()?.ingestion?.units ?: "mg"

        val result = mutableListOf<DosageBucket>()
        var bucketEnd = end.atZone(zoneId)

        for (i in 0 until bucketCount) {
            val bucketStart = bucketEnd.minus(config.step)
            val bsi = bucketStart.toInstant()
            val bei = bucketEnd.toInstant()

            val inBucket = ingestions.filter { it.ingestion.time >= bsi && it.ingestion.time < bei }

            val totalDose    = inBucket.sumOf { it.ingestion.dose ?: 0.0 }
            val sessionCount = inBucket.map { it.ingestion.experienceId }.distinct().size

            result.add(
                0,
                DosageBucket(
                    label        = labelFmt.format(bucketStart),
                    fullDateText = fullDateFmt.format(bucketStart),
                    totalDose    = totalDose,
                    sessionCount = sessionCount,
                    unit         = initialUnit
                )
            )

            bucketEnd = bucketStart
        }

        return result
    }
}

enum class DosageTimeRange(val displayText: String, val title: String, val period: Period?) {
    DAYS_30("30D",  "Last 30 Days",    Period.ofDays(30)),
    WEEKS_26("26W", "Last 26 Weeks",   Period.ofWeeks(26)),
    MONTHS_12("12M","Last 12 Months",  Period.ofMonths(12)),
    ALL("All",      "All Time",        null)
}

data class IngestionsBurst(
    val timeUntil: String,
    val experience: Experience,
    val ingestions: List<IngestionAndCustomUnit>
) {
    data class IngestionAndCustomUnit(
        val ingestion: Ingestion,
        val customUnit: CustomUnit?
    ) {
        val customUnitDose: CustomUnitDose?
            get() = ingestion.dose?.let { doseUnwrapped ->
                customUnit?.let { customUnitUnwrapped ->
                    CustomUnitDose(
                        dose = doseUnwrapped,
                        isEstimate = ingestion.isDoseAnEstimate,
                        estimatedDoseStandardDeviation = ingestion.estimatedDoseStandardDeviation,
                        customUnit = customUnitUnwrapped
                    )
                }
            }
        val doseDescription: String
            get() = customUnitDose?.doseDescription ?: ingestionDoseDescription

        private val ingestionDoseDescription get() = ingestion.dose?.let { dose ->
            ingestion.estimatedDoseStandardDeviation?.let { estimatedDoseStandardDeviation ->
                "${dose.toReadableString()}±${estimatedDoseStandardDeviation.toReadableString()} ${ingestion.units}"
            } ?: run {
                val description = "${dose.toReadableString()} ${ingestion.units}"
                if (ingestion.isDoseAnEstimate) {
                    "~$description"
                } else {
                    description
                }
            }
        } ?: "Unknown dose"
    }
}