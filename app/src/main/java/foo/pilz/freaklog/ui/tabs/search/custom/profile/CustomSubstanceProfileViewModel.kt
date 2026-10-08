/*
 * Copyright (c) 2026. Freaklog.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Freaklog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Freaklog.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.ui.tabs.search.custom.profile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteraction
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionSeverity
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionTargetType
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithEverything
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substanceshare.shareCustomSubstance
import foo.pilz.freaklog.ui.main.navigation.graphs.CustomSubstanceProfileRoute
import foo.pilz.freaklog.ui.utils.stateInVm
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomSubstanceProfileViewModel @Inject constructor(
    private val repository: CustomSubstanceRepository,
    state: SavedStateHandle,
) : ViewModel() {

    private val substanceId = state.toRoute<CustomSubstanceProfileRoute>().customSubstanceId

    /** Routes, interactions and cross-tolerances are saved as they are edited, so they come straight from the DB. */
    val substanceFlow = repository.getWithEverythingFlow(substanceId).stateInVm(viewModelScope, null)

    // Free-text fields are edited locally and written by [saveTexts].
    var summary by mutableStateOf("")
    var effectsText by mutableStateOf("")
    var toleranceFull by mutableStateOf("")
    var toleranceHalf by mutableStateOf("")
    var toleranceZero by mutableStateOf("")
    var generalRisks by mutableStateOf("")
    var longTermRisks by mutableStateOf("")
    var crossTolerancesText by mutableStateOf("")

    init {
        viewModelScope.launch {
            val loaded = repository.getWithEverythingFlow(substanceId).first() ?: return@launch
            val substance = loaded.substance
            summary = substance.summary.orEmpty()
            effectsText = substance.effectsText.orEmpty()
            toleranceFull = substance.toleranceFull.orEmpty()
            toleranceHalf = substance.toleranceHalf.orEmpty()
            toleranceZero = substance.toleranceZero.orEmpty()
            generalRisks = substance.generalRisks.orEmpty()
            longTermRisks = substance.longTermRisks.orEmpty()
            crossTolerancesText = loaded.crossTolerances.joinToString(", ") { it.categoryName }
        }
    }

    fun saveTexts() {
        viewModelScope.launch { persistTexts() }
    }

    private suspend fun persistTexts() {
        val current = substanceFlow.value?.substance ?: return
        repository.update(
            current.copy(
                summary = summary.trim().ifBlank { null },
                effectsText = effectsText.trim().ifBlank { null },
                toleranceFull = toleranceFull.trim().ifBlank { null },
                toleranceHalf = toleranceHalf.trim().ifBlank { null },
                toleranceZero = toleranceZero.trim().ifBlank { null },
                generalRisks = generalRisks.trim().ifBlank { null },
                longTermRisks = longTermRisks.trim().ifBlank { null },
            )
        )
        repository.replaceCrossTolerances(substanceId, parseCrossTolerances(crossTolerancesText))
    }

    fun saveRoute(draft: CustomRouteDraft) {
        if (!draft.isValid) return
        viewModelScope.launch {
            repository.replaceRoute(substanceId, draft.toRoa(), draft.toDose(), draft.toDuration())
        }
    }

    fun deleteRoute(route: AdministrationRoute) {
        viewModelScope.launch { repository.deleteRoute(substanceId, route) }
    }

    fun addInteraction(severity: CustomInteractionSeverity, targetName: String) {
        val name = targetName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            repository.upsertInteraction(
                substanceId,
                CustomInteraction(
                    severity = severity,
                    targetType = CustomInteractionTargetType.SUBSTANCE,
                    targetName = name,
                )
            )
        }
    }

    fun deleteInteraction(interaction: CustomInteraction) {
        viewModelScope.launch { repository.deleteInteraction(interaction.id) }
    }

    /** Saves pending text edits first so the shared file matches what is on screen. */
    fun share(context: Context) {
        viewModelScope.launch {
            persistTexts()
            val saved: CustomSubstanceWithEverything = repository.getWithEverything(substanceId) ?: return@launch
            shareCustomSubstance(context, saved)
        }
    }
}
