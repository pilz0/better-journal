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
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class WebhooksViewModel @Inject constructor(
    private val webhookPresetRepository: WebhookPresetRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    val activePresetsFlow: StateFlow<List<WebhookPreset>> = webhookPresetRepository.getActivePresetsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _webhookUsername = MutableStateFlow("")
    val webhookUsername: StateFlow<String> = _webhookUsername.asStateFlow()

    private val _webhookTemplate = MutableStateFlow("")
    val webhookTemplate: StateFlow<String> = _webhookTemplate.asStateFlow()

    private val _isHyperlinked = MutableStateFlow(false)
    val isHyperlinked: StateFlow<Boolean> = _isHyperlinked.asStateFlow()

    private val _substanceInfoUrl = MutableStateFlow("")
    val substanceInfoUrl: StateFlow<String> = _substanceInfoUrl.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _webhookUsername.value = userPreferences.readWebhookName().first()
            _webhookTemplate.value = userPreferences.readWebhookTemplate().first()
            _isHyperlinked.value = userPreferences.readWebhookIsHyperlinked().first()
            _substanceInfoUrl.value = userPreferences.readWebhookSubstanceInfoUrl().first()
        }
    }

    fun setWebhookUsername(value: String) {
        _webhookUsername.value = value
        viewModelScope.launch {
            userPreferences.writeWebhookName(value)
        }
    }

    fun setWebhookTemplate(value: String) {
        _webhookTemplate.value = value
        viewModelScope.launch {
            userPreferences.writeWebhookTemplate(value)
        }
    }

    fun setIsHyperlinked(value: Boolean) {
        _isHyperlinked.value = value
        viewModelScope.launch {
            userPreferences.writeWebhookIsHyperlinked(value)
        }
    }

    fun setSubstanceInfoUrl(value: String) {
        _substanceInfoUrl.value = value
        viewModelScope.launch {
            userPreferences.writeWebhookSubstanceInfoUrl(value)
        }
    }

    fun resetTemplate() {
        val defaultTemplate = "{user}: [{dose} {units} ]{substance} via {route}[ at {site}][\\n> {note}]"
        setWebhookTemplate(defaultTemplate)
    }

    fun archivePreset(preset: WebhookPreset) {
        viewModelScope.launch {
            webhookPresetRepository.archivePreset(preset.id)
        }
    }

    fun addPreset(name: String, url: String) {
        viewModelScope.launch {
            val preset = WebhookPreset(
                name = name,
                url = url,
                isEnabled = true,
                isArchived = false,
                creationDate = Instant.now()
            )
            webhookPresetRepository.insert(preset)
        }
    }
}
