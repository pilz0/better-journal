/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog (a fork of PsychonautWiki Journal).
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.ui.tabs.stats.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel backing [MoreChartsScreen].
 *
 * Exposes precomputed Daily/Monthly/Yearly chart buckets and the fractional substance
 * breakdown derived from the user's full ingestion history.
 */
@HiltViewModel
class MoreChartsViewModel @Inject constructor(
    experienceRepo: ExperienceRepository,
) : ViewModel() {

    private val allIngestionsFlow = experienceRepo.getSortedExperiencesWithIngestionsAndCustomUnitsFlow()
        .map { experiences ->
            experiences.flatMap { it.ingestionsWithCompanionAndCustomUnit }
        }

    val dailyBuckets: StateFlow<List<List<foo.pilz.freaklog.ui.tabs.stats.ColorCount>>> =
        allIngestionsFlow.map { ExperienceStatsHelper.dailyBuckets(it, days = 30) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlyBuckets: StateFlow<List<List<foo.pilz.freaklog.ui.tabs.stats.ColorCount>>> =
        allIngestionsFlow.map { ExperienceStatsHelper.monthlyBuckets(it, months = 12) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val yearlyBuckets: StateFlow<List<List<foo.pilz.freaklog.ui.tabs.stats.ColorCount>>> =
        allIngestionsFlow.map { ExperienceStatsHelper.yearlyBuckets(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fractionalCounts: StateFlow<List<SubstanceFraction>> =
        allIngestionsFlow.map { ExperienceStatsHelper.fractionalSubstanceCounts(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawIngestions: StateFlow<List<IngestionWithCompanionAndCustomUnit>> =
        allIngestionsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
