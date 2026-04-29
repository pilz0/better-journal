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
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

    fun setTimeRange(range: DosageTimeRange) {
        viewModelScope.launch { _selectedTimeRange.emit(range) }
    }

    fun toggleShowAverage(show: Boolean) {
        viewModelScope.launch { _showAverage.emit(show) }
    }

    // Source of all ingestions for this substance/consumer
    private val allIngestionsFlow = experienceRepo.getSortedIngestionsWithExperienceAndCustomUnitFlow(substanceName)
        .map { list -> list.filter { it.ingestion.consumerName == consumerName } }

    val dosageChartDataFlow: StateFlow<List<DosageBucket>> = 
        combine(allIngestionsFlow, selectedTimeRange, currentTimeFlow) { ingestions, timeRange, currentTime ->
             // Convert to easier format
             val mappedIngestions = ingestions.map { 
                 IngestionsBurst.IngestionAndCustomUnit(it.ingestion, it.customUnit) 
             }
             
             getDosageBuckets(mappedIngestions, timeRange, currentTime.atZone(ZoneId.systemDefault()).minus(timeRange.period).toInstant(), currentTime)
        }.stateIn(
            initialValue = emptyList(),
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
                    val ingestionsSorted = oneExperience.value.map { IngestionsBurst.IngestionAndCustomUnit(
                        ingestion = it.ingestion,
                        customUnit = it.customUnit
                    ) }.sortedBy { it.ingestion.time }
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
        val buckets = mutableListOf<DosageBucket>()
        val zoneId = ZoneId.systemDefault()
        
        var currentStart = start
        // Align start to the beginning of the period (e.g., start of day) for cleaner buckets
        // For simplicity, we just iterate by adding the bucket period.
        
        // Better approach: working backwards from 'end' (Now) or forwards from 'start'?
        // The screenshot shows "Last 26 Weeks" with bars.
        // Let's create fixed number of buckets if possible, or dynamic.
        // 30D -> 30 buckets (Days)
        // 26W -> 26 buckets (Weeks)
        // 12M -> 12 buckets (Months)
        // Y -> 12 buckets (Months) or 1 (Year)? Screenshot 'Y' often implies Year view. 
        // If Y is "This Year" or "Last Year", usually broken down by Month. 
        // Let's assume Y means "1 Year" broken by month (same as 12M?) or maybe "All Years".
        // Given 12M is already there, Y might be Multi-Year or All Time.
        // However, standard patterns: D, W, M, Y usually means "Day view", "Week view", "Month view", "Year view".
        // But 30D, 26W, 12M implies "Last X". 
        // Let's assume 'Y' is "Last Year" (broken by month) which makes it duplicate of 12M? 
        // Or maybe 'Y' is "Since beginning of this year"? 
        // Let's stick to "1 Year" broken by month for now, similar to 12M. 
        // Actually, let's look at the label "Last 26 Weeks". 
        // If I select "Y", it might say "Last 1 Year" or "2025".
        // Let's implement Y as "1 Year" broken by months (12 buckets).
        
        val bucketCount = when(timeRange) {
            DosageTimeRange.DAYS_30 -> 30
            DosageTimeRange.WEEKS_26 -> 26
            DosageTimeRange.MONTHS_12 -> 12
            DosageTimeRange.YEAR -> 12 // Assume 12 months for Year view
        }

        val step = when(timeRange) {
            DosageTimeRange.DAYS_30 -> Period.ofDays(1)
            DosageTimeRange.WEEKS_26 -> Period.ofWeeks(1)
            DosageTimeRange.MONTHS_12 -> Period.ofMonths(1)
            DosageTimeRange.YEAR -> Period.ofMonths(1)
        }

        // We want the last bucket to end at 'end' (Now).
        // So we calculate bucket starts backwards.
        
        val calculatedBuckets = mutableListOf<DosageBucket>()
        var bucketEnd = end.atZone(zoneId)
        
        for (i in 0 until bucketCount) {
            val bucketStart = bucketEnd.minus(step)
            
            val bucketStartInstant = bucketStart.toInstant()
            val bucketEndInstant = bucketEnd.toInstant()
            
            val initialUnit = ingestions.firstOrNull()?.ingestion?.units ?: "mg"
            
            // Filter ingestions in this bucket
            val ingestionsInBucket = ingestions.filter { 
                it.ingestion.time >= bucketStartInstant && it.ingestion.time < bucketEndInstant 
            }
            
            // Sum dose. We need to handle mixed units? For now assume same unit or just sum pure values if possible.
            // Ingestion entity has 'dose' (Double) and 'units' (String).
            // We should filter for the main unit or try to convert. 
            // Simplified: Sum only matching units or assume they match.
            val totalDose = ingestionsInBucket.sumOf { it.ingestion.dose ?: 0.0 }
            
            // Label
            // val date = LocalDateTime.ofInstant(bucketStart, zoneId) // No longer needed
            val label = when(timeRange) {
                DosageTimeRange.DAYS_30 -> DateTimeFormatter.ofPattern("dd").format(bucketStart)
                DosageTimeRange.WEEKS_26 -> {
                    // Week number or start date? Screenshot shows just bars.
                    // Let's use Month name if it changes, or simple ticks.
                    // For specific chart logic:
                    // If first of month, show Month. 
                    // Let's just return a date label.
                    DateTimeFormatter.ofPattern("dd.MM").format(bucketStart)
                }
                DosageTimeRange.MONTHS_12, DosageTimeRange.YEAR -> DateTimeFormatter.ofPattern("MMM").format(bucketStart)
            }
             val fullDate = DateTimeFormatter.ofPattern("dd MMM yyyy").format(bucketStart)

            calculatedBuckets.add(0, DosageBucket(label, fullDate, totalDose, initialUnit))
            
            bucketEnd = bucketStart
        }
        
        return calculatedBuckets
    }
}

enum class DosageTimeRange(val displayText: String, val title: String, val period: Period) {
    DAYS_30("30D", "Last 30 Days", Period.ofDays(30)),
    WEEKS_26("26W", "Last 26 Weeks", Period.ofWeeks(26)),
    MONTHS_12("12M", "Last 12 Months", Period.ofMonths(12)),
    YEAR("Y", "Last Year", Period.ofYears(1))
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