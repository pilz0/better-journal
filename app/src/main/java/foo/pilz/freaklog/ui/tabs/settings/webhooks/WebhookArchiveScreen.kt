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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import foo.pilz.freaklog.data.room.experiences.entities.WebhookPreset
import foo.pilz.freaklog.ui.theme.horizontalPadding

@Composable
fun WebhookArchiveScreen(
    viewModel: WebhookArchiveViewModel = hiltViewModel()
) {
    val archivedPresets by viewModel.archivedPresetsFlow.collectAsState()

    WebhookArchiveScreenContent(
        archivedPresets = archivedPresets,
        onUnarchive = viewModel::unarchivePreset,
        onDelete = viewModel::deletePreset
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebhookArchiveScreenContent(
    archivedPresets: List<WebhookPreset>,
    onUnarchive: (WebhookPreset) -> Unit,
    onDelete: (WebhookPreset) -> Unit
) {
    var presetToDelete by remember { mutableStateOf<WebhookPreset?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Webhook Archive") })
        }
    ) { padding ->
        if (archivedPresets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No archived webhooks",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = horizontalPadding)
            ) {
                items(archivedPresets) { preset ->
                    ArchivedWebhookItem(
                        preset = preset,
                        onUnarchive = { onUnarchive(preset) },
                        onDelete = { presetToDelete = preset }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    presetToDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { presetToDelete = null },
            title = { Text("Delete Webhook?") },
            text = { Text("This will permanently delete \"${preset.name}\".") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(preset)
                        presetToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { presetToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ArchivedWebhookItem(
    preset: WebhookPreset,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preset.name.ifEmpty { "Unnamed" },
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = preset.url.take(40) + if (preset.url.length > 40) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onUnarchive) {
            Icon(
                Icons.Default.Unarchive,
                contentDescription = "Unarchive",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Preview
@Composable
fun WebhookArchiveScreenPreview() {
    val samplePresets = listOf(
        WebhookPreset(id = 1, name = "Old Server", url = "https://discord.com/...", isArchived = true),
        WebhookPreset(id = 2, name = "Test Webhook", url = "https://discord.com/...", isArchived = true)
    )
    WebhookArchiveScreenContent(
        archivedPresets = samplePresets,
        onUnarchive = {},
        onDelete = {}
    )
}
