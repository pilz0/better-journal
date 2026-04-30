/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.ui.tabs.settings.webhooks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.webhooks.WebhookRepository
import foo.pilz.freaklog.data.room.webhooks.entities.Webhook
import foo.pilz.freaklog.ui.main.navigation.graphs.WebhookEditorRoute
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WebhookEditorViewModel @Inject constructor(
    private val webhookRepository: WebhookRepository,
    private val userPreferences: UserPreferences,
    state: SavedStateHandle
) : ViewModel() {

    /** Webhook id being edited, or null when creating a new one. */
    val webhookId: Int? = state.toRoute<WebhookEditorRoute>().webhookId.takeIf { it >= 0 }

    var name by mutableStateOf("")
    var url by mutableStateOf("")
    var displayName by mutableStateOf("")
    var template by mutableStateOf("")
    var isHyperlinked by mutableStateOf(true)
    var isEnabled by mutableStateOf(true)
    var useFreakQuery by mutableStateOf(true)
    var freakQuerySeparator by mutableStateOf(", ")
    var hyperlinkSubstances by mutableStateOf(true)
    var sortOrder: Int = 0

    val isExisting: Boolean get() = webhookId != null

    init {
        viewModelScope.launch {
            useFreakQuery = userPreferences.webhookUseFreakQueryFlow.first()
            freakQuerySeparator = userPreferences.webhookFreakQuerySeparatorFlow.first()
            hyperlinkSubstances = userPreferences.webhookHyperlinkSubstancesFlow.first()

            val id = webhookId ?: return@launch
            val existing = webhookRepository.getById(id) ?: return@launch
            name = existing.name
            url = existing.url
            displayName = existing.displayName
            template = existing.template
            isHyperlinked = existing.isHyperlinked
            isEnabled = existing.isEnabled
            sortOrder = existing.sortOrder
        }
    }

    val canSave: Boolean
        get() = url.isNotBlank() && name.isNotBlank()

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val webhook = Webhook(
                id = webhookId ?: 0,
                name = name.trim(),
                url = url.trim(),
                displayName = displayName.trim(),
                template = template,
                isHyperlinked = isHyperlinked,
                isEnabled = isEnabled,
                sortOrder = sortOrder
            )
            webhookRepository.upsert(webhook)
            userPreferences.saveWebhookUseFreakQuery(useFreakQuery)
            userPreferences.saveWebhookFreakQuerySeparator(freakQuerySeparator)
            userPreferences.saveWebhookHyperlinkSubstances(hyperlinkSubstances)
            onDone()
        }
    }
}
