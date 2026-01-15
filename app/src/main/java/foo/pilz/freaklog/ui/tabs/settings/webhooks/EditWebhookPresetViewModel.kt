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
import foo.pilz.freaklog.data.webhook.WebhookService
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditWebhookPresetViewModel @Inject constructor(
    private val webhookPresetRepository: WebhookPresetRepository,
    private val webhookService: WebhookService,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private var currentPreset: WebhookPreset? = null

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    fun loadPreset(presetId: Int) {
        viewModelScope.launch {
            val preset = webhookPresetRepository.getPresetById(presetId)
            if (preset != null) {
                currentPreset = preset
                _name.value = preset.name
                _url.value = preset.url
                _isEnabled.value = preset.isEnabled
            }
            _isLoaded.value = true
        }
    }

    fun setName(value: String) {
        _name.value = value
    }

    fun setEnabled(value: Boolean) {
        _isEnabled.value = value
    }

    fun save() {
        val preset = currentPreset ?: return
        viewModelScope.launch {
            val updatedPreset = preset.copy(
                name = _name.value,
                isEnabled = _isEnabled.value
            )
            webhookPresetRepository.update(updatedPreset)
        }
    }

    fun delete() {
        val preset = currentPreset ?: return
        viewModelScope.launch {
            webhookPresetRepository.delete(preset)
        }
    }

    fun testWebhook() {
        viewModelScope.launch {
            val template = userPreferences.readWebhookTemplate().first().ifEmpty {
                WebhookService.DEFAULT_TEMPLATE
            }
            val isHyperlinked = userPreferences.readWebhookIsHyperlinked().first()
            val substanceInfoUrl = userPreferences.readWebhookSubstanceInfoUrl().first()

            webhookService.sendWebhook(
                url = _url.value,
                user = "Test User",
                substance = "Caffeine",
                dose = 100.0,
                units = "mg",
                isEstimate = false,
                route = "oral",
                site = "",
                note = "This is a test note.",
                template = template,
                isHyperlinked = isHyperlinked,
                substanceInfoUrl = substanceInfoUrl
            )
        }
    }
}
