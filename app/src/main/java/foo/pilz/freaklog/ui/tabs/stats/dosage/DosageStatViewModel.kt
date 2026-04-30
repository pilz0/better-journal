/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog (a fork of PsychonautWiki Journal).
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.ui.tabs.stats.dosage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.ui.main.navigation.graphs.DosageStatRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DosageStatViewModel @Inject constructor(
    experienceRepo: ExperienceRepository,
    substanceRepo: SubstanceRepository,
    state: SavedStateHandle,
) : ViewModel() {

    private val route = state.toRoute<DosageStatRoute>()
    val substanceName: String = route.substanceName
    val consumerName: String? = route.consumerName

    private val substance = substanceRepo.getSubstance(substanceName)

    /** Default estimate for unknown doses, taken from the substance's first known commonMin. */
    val defaultEstimateDose: Double? =
        substance?.roas?.firstNotNullOfOrNull { it.roaDose?.commonMin }
    val defaultEstimateUnits: String? =
        substance?.roas?.firstNotNullOfOrNull { it.roaDose?.units }

    private val _selectedRange = MutableStateFlow(DosageStatRange.WEEKS_26)
    val selectedRange: StateFlow<DosageStatRange> = _selectedRange.asStateFlow()
    fun setRange(range: DosageStatRange) {
        _selectedRange.value = range
    }

    private val _showAverage = MutableStateFlow(false)
    val showAverage: StateFlow<Boolean> = _showAverage.asStateFlow()
    fun setShowAverage(show: Boolean) {
        _showAverage.value = show
    }

    private val _estimatedUnknownDose = MutableStateFlow<Double?>(null)
    val estimatedUnknownDose: StateFlow<Double?> = _estimatedUnknownDose.asStateFlow()
    fun setEstimatedUnknownDose(dose: Double?) {
        _estimatedUnknownDose.value = dose
    }

    private val ingestionsFlow = experienceRepo
        .getSortedIngestionsWithExperienceAndCustomUnitFlow(substanceName)
        .map { list ->
            list.asSequence()
                .filter { it.ingestion.consumerName == consumerName }
                .map { it.ingestion }
                .toList()
        }

    val result: StateFlow<DosageStatHelper.DosageStatResult> = combine(
        ingestionsFlow,
        _selectedRange,
        _estimatedUnknownDose,
    ) { ingestions, range, estimate ->
        DosageStatHelper.computeBuckets(
            ingestions = ingestions,
            range = range,
            estimatedUnknownDose = estimate,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DosageStatHelper.DosageStatResult(emptyList(), 0, emptySet()),
    )
}
