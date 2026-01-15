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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import foo.pilz.freaklog.data.room.experiences.entities.WebhookPreset
import foo.pilz.freaklog.ui.tabs.journal.experience.components.CardWithTitle
import foo.pilz.freaklog.ui.theme.horizontalPadding

@Composable
fun WebhooksScreen(
    viewModel: WebhooksViewModel = hiltViewModel(),
    navigateToEditPreset: (Int) -> Unit,
    navigateToArchive: () -> Unit
) {
    val presets by viewModel.activePresetsFlow.collectAsState()
    val webhookUsername by viewModel.webhookUsername.collectAsState()
    val webhookTemplate by viewModel.webhookTemplate.collectAsState()
    val isHyperlinked by viewModel.isHyperlinked.collectAsState()
    val substanceInfoUrl by viewModel.substanceInfoUrl.collectAsState()

    WebhooksScreenContent(
        presets = presets,
        webhookUsername = webhookUsername,
        webhookTemplate = webhookTemplate,
        isHyperlinked = isHyperlinked,
        substanceInfoUrl = substanceInfoUrl,
        onSetWebhookUsername = viewModel::setWebhookUsername,
        onSetWebhookTemplate = viewModel::setWebhookTemplate,
        onSetIsHyperlinked = viewModel::setIsHyperlinked,
        onSetSubstanceInfoUrl = viewModel::setSubstanceInfoUrl,
        onResetTemplate = viewModel::resetTemplate,
        onArchivePreset = viewModel::archivePreset,
        onAddPreset = viewModel::addPreset,
        navigateToEditPreset = navigateToEditPreset,
        navigateToArchive = navigateToArchive
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebhooksScreenContent(
    presets: List<WebhookPreset>,
    webhookUsername: String,
    webhookTemplate: String,
    isHyperlinked: Boolean,
    substanceInfoUrl: String,
    onSetWebhookUsername: (String) -> Unit,
    onSetWebhookTemplate: (String) -> Unit,
    onSetIsHyperlinked: (Boolean) -> Unit,
    onSetSubstanceInfoUrl: (String) -> Unit,
    onResetTemplate: () -> Unit,
    onArchivePreset: (WebhookPreset) -> Unit,
    onAddPreset: (String, String) -> Unit,
    navigateToEditPreset: (Int) -> Unit,
    navigateToArchive: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Webhooks") },
                actions = {
                    IconButton(onClick = navigateToArchive) {
                        Icon(Icons.Default.Archive, contentDescription = "Archive")
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Webhook")
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
            // Webhooks list
            CardWithTitle(title = "Webhooks") {
                if (presets.isEmpty()) {
                    Text(
                        text = "No webhooks configured.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    presets.forEach { preset ->
                        WebhookPresetItem(
                            preset = preset,
                            onClick = { navigateToEditPreset(preset.id) },
                            onArchive = { onArchivePreset(preset) }
                        )
                        HorizontalDivider()
                    }
                }
                Text(
                    text = "Configure multiple Discord webhooks. You can select which ones to use when finishing an ingestion.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Username section
            CardWithTitle(title = "Username") {
                OutlinedTextField(
                    value = webhookUsername,
                    onValueChange = onSetWebhookUsername,
                    label = { Text("Display Name") },
                    placeholder = { Text("Benjamin Engel") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    text = "The name used for webhook messages. Gets overridden if a name is entered during ingestion.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Links section
            CardWithTitle(title = "Links") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Hyperlink Substance Name")
                    Switch(
                        checked = isHyperlinked,
                        onCheckedChange = onSetIsHyperlinked
                    )
                }
                if (isHyperlinked) {
                    OutlinedTextField(
                        value = substanceInfoUrl,
                        onValueChange = onSetSubstanceInfoUrl,
                        label = { Text("Base URL") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        singleLine = true
                    )
                }
                Text(
                    text = "If enabled, the substance name becomes clickable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Message Layout section
            CardWithTitle(title = "Message Layout") {
                OutlinedTextField(
                    value = webhookTemplate,
                    onValueChange = onSetWebhookTemplate,
                    label = { Text("Template") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 5
                )
                TextButton(onClick = onResetTemplate) {
                    Text("Reset to Default")
                }
                Text(
                    text = "Variables: {user}, {dose}, {units}, {substance}, {route}, {site}, {note}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showAddDialog) {
        AddWebhookDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, url ->
                onAddPreset(name, url)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun WebhookPresetItem(
    preset: WebhookPreset,
    onClick: () -> Unit,
    onArchive: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = preset.name.ifEmpty { "Unnamed" },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        if (preset.isEnabled) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Enabled",
                tint = Color.Green
            )
        } else {
            Icon(
                Icons.Outlined.Circle,
                contentDescription = "Disabled",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onArchive) {
            Icon(
                Icons.Default.Archive,
                contentDescription = "Archive",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun AddWebhookDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Webhook") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("My Server") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Webhook URL") },
                    placeholder = { Text("https://discord.com/api/webhooks/...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name, url) },
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview
@Composable
fun WebhooksScreenPreview() {
    val samplePresets = listOf(
        WebhookPreset(id = 1, name = "My Server", url = "https://discord.com/...", isEnabled = true),
        WebhookPreset(id = 2, name = "Other Server", url = "https://discord.com/...", isEnabled = false)
    )
    WebhooksScreenContent(
        presets = samplePresets,
        webhookUsername = "Test User",
        webhookTemplate = "{user}: [{dose} {units} ]{substance} via {route}",
        isHyperlinked = false,
        substanceInfoUrl = "",
        onSetWebhookUsername = {},
        onSetWebhookTemplate = {},
        onSetIsHyperlinked = {},
        onSetSubstanceInfoUrl = {},
        onResetTemplate = {},
        onArchivePreset = {},
        onAddPreset = { _, _ -> },
        navigateToEditPreset = {},
        navigateToArchive = {}
    )
}
