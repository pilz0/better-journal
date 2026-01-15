/*
 * Copyright (c) 2024. Isaak Hanimann.
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

package foo.pilz.freaklog.ui.tabs.stats.tolerance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class ToleranceChartViewModel @Inject constructor(
    private val experienceRepository: ExperienceRepository,
    private val substanceRepository: SubstanceRepository
) : ViewModel() {

    companion object {
        private const val MAX_INGESTIONS_TO_LOAD = 1000
        private const val DEFAULT_LOOKBACK_DAYS = 90L // 3 months
    }

    private val _sinceDate = MutableStateFlow(Instant.now().minus(Duration.ofDays(DEFAULT_LOOKBACK_DAYS)))
    val sinceDate: StateFlow<Instant> = _sinceDate.asStateFlow()

    private val _isTimeRelative = MutableStateFlow(false)
    val isTimeRelative: StateFlow<Boolean> = _isTimeRelative.asStateFlow()

    private val _additionalSubstanceDays = MutableStateFlow<List<SubstanceAndDay>>(emptyList())

    val toleranceDataFlow: StateFlow<ToleranceData> = combine(
        experienceRepository.getSortedIngestionsWithSubstanceCompanionsFlow(MAX_INGESTIONS_TO_LOAD),
        experienceRepository.getAllSubstanceCompanionsFlow(),
        _sinceDate,
        _additionalSubstanceDays
    ) { ingestions, companions, since, additionalDays ->
        val relevantIngestions = ingestions.filter { it.ingestion.time > since }

        val persistedSubstanceDays = relevantIngestions.map { ingestion ->
            SubstanceAndDay(
                substanceName = ingestion.ingestion.substanceName,
                day = ingestion.ingestion.time
            )
        }

        val allSubstanceDays = persistedSubstanceDays + additionalDays

        val toleranceWindows = ToleranceChartCalculator.getToleranceWindows(
            substanceAndDays = allSubstanceDays,
            substanceCompanions = companions,
            substanceRepository = substanceRepository
        )

        val substanceNamesInIngestions = relevantIngestions.map { it.ingestion.substanceName }.toSet()
        val substanceNamesInToleranceWindows = toleranceWindows.map { it.substanceName }.toSet()
        val substancesWithoutToleranceWindows = (substanceNamesInIngestions - substanceNamesInToleranceWindows).toList()

        ToleranceData(
            toleranceWindows = toleranceWindows,
            numberOfSubstancesInChart = substanceNamesInToleranceWindows.size,
            substancesInIngestionsButNotChart = substancesWithoutToleranceWindows,
            substanceCompanions = companions
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ToleranceData(
            toleranceWindows = emptyList(),
            numberOfSubstancesInChart = 0,
            substancesInIngestionsButNotChart = emptyList(),
            substanceCompanions = emptyList()
        )
    )

    fun setSinceDate(date: Instant) {
        _sinceDate.value = date
    }

    fun setIsTimeRelative(value: Boolean) {
        _isTimeRelative.value = value
    }

    fun addTemporaryIngestion(substanceAndDay: SubstanceAndDay) {
        _additionalSubstanceDays.value = _additionalSubstanceDays.value + substanceAndDay
    }
}

data class ToleranceData(
    val toleranceWindows: List<ToleranceWindow>,
    val numberOfSubstancesInChart: Int,
    val substancesInIngestionsButNotChart: List<String>,
    val substanceCompanions: List<SubstanceCompanion>
)
