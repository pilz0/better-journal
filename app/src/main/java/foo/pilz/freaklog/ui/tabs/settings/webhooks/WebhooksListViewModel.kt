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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.webhooks.WebhookRepository
import foo.pilz.freaklog.data.room.webhooks.entities.Webhook
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WebhooksListViewModel @Inject constructor(
    private val webhookRepository: WebhookRepository,
) : ViewModel() {

    val webhooks = webhookRepository.getAllFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun toggleEnabled(webhook: Webhook) {
        viewModelScope.launch {
            webhookRepository.update(webhook.copy(isEnabled = !webhook.isEnabled))
        }
    }

    fun delete(webhook: Webhook) {
        viewModelScope.launch {
            webhookRepository.delete(webhook)
        }
    }
}
