/*
 * Copyright (c) 2026. FreakLog contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package foo.pilz.freaklog.ui.tabs.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.inventory.InventoryItem
import foo.pilz.freaklog.data.room.inventory.InventoryRepository
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val experienceRepository: ExperienceRepository,
    private val substanceRepository: SubstanceRepository,
) : ViewModel() {

    val itemsFlow: StateFlow<List<InventoryItem>> = inventoryRepository.getAllFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _searchText = MutableStateFlow("")
    val searchText = _searchText.asStateFlow()

    fun setSearchText(text: String) { _searchText.value = text }

    /**
     * Returns up to [limit] known substance names (PsychonautWiki + user-defined
     * custom substances) whose names start with the current search text.
     */
    suspend fun suggestions(searchText: String, limit: Int = 10): List<Suggestion> {
        if (searchText.isBlank()) return emptyList()
        val trimmed = searchText.trim()
        val builtin = substanceRepository.getAllSubstances()
            .asSequence()
            .filter { it.name.contains(trimmed, ignoreCase = true) }
            .take(limit)
            .map { Suggestion(name = it.name, isCustom = false) }
            .toList()
        val custom = experienceRepository.getAllCustomSubstances()
            .asSequence()
            .filter { it.name.contains(trimmed, ignoreCase = true) }
            .take(limit)
            .map { Suggestion(name = it.name, isCustom = true) }
            .toList()
        return (custom + builtin).distinctBy { it.name }.take(limit)
    }

    fun add(name: String, isCustom: Boolean, notes: String = "") = viewModelScope.launch {
        if (name.isBlank()) return@launch
        inventoryRepository.insert(
            InventoryItem(
                substanceName = name.trim(),
                isCustom = isCustom,
                notes = notes
            )
        )
    }

    fun delete(item: InventoryItem) = viewModelScope.launch {
        inventoryRepository.delete(item)
    }

    /** Returns a random item from the inventory, or null if empty. */
    fun lucky(): InventoryItem? = itemsFlow.value.randomOrNull()

    data class Suggestion(val name: String, val isCustom: Boolean)
}
