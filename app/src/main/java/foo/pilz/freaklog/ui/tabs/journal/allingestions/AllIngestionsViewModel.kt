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

package foo.pilz.freaklog.ui.tabs.journal.allingestions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class IngestionSection(
    val date: LocalDate,
    val ingestions: List<IngestionWithCompanionAndCustomUnit>
)

@HiltViewModel
class AllIngestionsViewModel @Inject constructor(
    private val experienceRepository: ExperienceRepository
) : ViewModel() {

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _isTimeRelative = MutableStateFlow(false)
    val isTimeRelative: StateFlow<Boolean> = _isTimeRelative.asStateFlow()

    val ingestionSectionsFlow: StateFlow<List<IngestionSection>> = combine(
        experienceRepository.getSortedIngestionsWithSubstanceCompanionsFlow(500),
        _searchText
    ) { ingestions, searchQuery ->
        val filtered = if (searchQuery.isBlank()) {
            ingestions
        } else {
            ingestions.filter { it.ingestion.substanceName.contains(searchQuery, ignoreCase = true) }
        }
        groupIngestionsByDate(filtered)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private fun groupIngestionsByDate(ingestions: List<IngestionWithCompanionAndCustomUnit>): List<IngestionSection> {
        return ingestions
            .groupBy { ingestionWithCompanion ->
                ingestionWithCompanion.ingestion.time.atZone(ZoneId.systemDefault()).toLocalDate()
            }
            .map { (date, ingestionList) ->
                IngestionSection(
                    date = date,
                    ingestions = ingestionList.sortedByDescending { it.ingestion.time }
                )
            }
            .sortedByDescending { it.date }
    }

    fun setSearchText(text: String) {
        _searchText.value = text
    }

    fun setIsTimeRelative(value: Boolean) {
        _isTimeRelative.value = value
    }

    fun deleteIngestion(ingestion: Ingestion) {
        viewModelScope.launch {
            experienceRepository.delete(ingestion)
        }
    }
}
