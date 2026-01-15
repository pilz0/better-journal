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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import foo.pilz.freaklog.ui.tabs.journal.experience.components.CardWithTitle
import foo.pilz.freaklog.ui.theme.horizontalPadding

@Composable
fun EditWebhookPresetScreen(
    presetId: Int,
    viewModel: EditWebhookPresetViewModel = hiltViewModel(),
    navigateBack: () -> Unit
) {
    LaunchedEffect(presetId) {
        viewModel.loadPreset(presetId)
    }

    val name by viewModel.name.collectAsState()
    val url by viewModel.url.collectAsState()
    val isEnabled by viewModel.isEnabled.collectAsState()
    val isLoaded by viewModel.isLoaded.collectAsState()

    if (isLoaded) {
        EditWebhookPresetScreenContent(
            name = name,
            url = url,
            isEnabled = isEnabled,
            onSetName = viewModel::setName,
            onSetEnabled = viewModel::setEnabled,
            onSave = {
                viewModel.save()
                navigateBack()
            },
            onDelete = {
                viewModel.delete()
                navigateBack()
            },
            onTestWebhook = viewModel::testWebhook
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWebhookPresetScreenContent(
    name: String,
    url: String,
    isEnabled: Boolean,
    onSetName: (String) -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onTestWebhook: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name.ifEmpty { "Edit Webhook" }) },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(
                        onClick = onSave,
                        enabled = name.isNotBlank() && url.isNotBlank()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = horizontalPadding)
                .verticalScroll(rememberScrollState())
        ) {
            CardWithTitle(title = "General") {
                OutlinedTextField(
                    value = name,
                    onValueChange = onSetName,
                    label = { Text("Preset Name") },
                    placeholder = { Text("e.g. My Server") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enabled")
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = onSetEnabled
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            CardWithTitle(title = "Configuration") {
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "The Webhook URL cannot be changed after saving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onTestWebhook,
                enabled = url.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Test Webhook")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Webhook?") },
            text = { Text("This will permanently delete this webhook preset.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Preview
@Composable
fun EditWebhookPresetScreenPreview() {
    EditWebhookPresetScreenContent(
        name = "My Server",
        url = "https://discord.com/api/webhooks/...",
        isEnabled = true,
        onSetName = {},
        onSetEnabled = {},
        onSave = {},
        onDelete = {},
        onTestWebhook = {}
    )
}
