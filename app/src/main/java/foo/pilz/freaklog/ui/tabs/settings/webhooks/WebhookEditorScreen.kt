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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import foo.pilz.freaklog.data.webhook.WebhookService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebhookEditorScreen(
    navigateBack: () -> Unit,
    viewModel: WebhookEditorViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isExisting) "Edit webhook" else "New webhook") },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        WebhookEditorBody(
            padding = padding,
            viewModel = viewModel,
            navigateBack = navigateBack
        )
    }
}

@Composable
private fun WebhookEditorBody(
    padding: PaddingValues,
    viewModel: WebhookEditorViewModel,
    navigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = viewModel.name,
            onValueChange = { viewModel.name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = viewModel.url,
            onValueChange = { viewModel.url = it },
            label = { Text("Discord webhook URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = viewModel.displayName,
            onValueChange = { viewModel.displayName = it },
            label = { Text("Display name (used as {user})") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = viewModel.template,
            onValueChange = { viewModel.template = it },
            label = { Text("Template (leave blank for default)") },
            placeholder = { Text(WebhookService.DEFAULT_TEMPLATE) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        Text(
            "Placeholders: {user}, {dose}, {units}, {substance}, {route}, {site}, {note}. " +
                "Square-bracketed blocks are removed when their placeholder is empty.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SwitchRow("Enabled", viewModel.isEnabled) { viewModel.isEnabled = it }
        SwitchRow("Link substance to wiki", viewModel.isHyperlinked) { viewModel.isHyperlinked = it }
        Button(
            onClick = { viewModel.save(navigateBack) },
            enabled = viewModel.canSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Switch(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}
