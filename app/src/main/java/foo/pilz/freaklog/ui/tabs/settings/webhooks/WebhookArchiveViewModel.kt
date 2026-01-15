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

package foo.pilz.freaklog.ui.tabs.settings.webhooks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import foo.pilz.freaklog.data.room.WebhookPresetRepository
import foo.pilz.freaklog.data.room.experiences.entities.WebhookPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WebhookArchiveViewModel @Inject constructor(
    private val webhookPresetRepository: WebhookPresetRepository
) : ViewModel() {

    val archivedPresetsFlow: StateFlow<List<WebhookPreset>> = webhookPresetRepository.getArchivedPresetsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun unarchivePreset(preset: WebhookPreset) {
        viewModelScope.launch {
            webhookPresetRepository.unarchivePreset(preset.id)
        }
    }

    fun deletePreset(preset: WebhookPreset) {
        viewModelScope.launch {
            webhookPresetRepository.delete(preset)
        }
    }
}
