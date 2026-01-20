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

package foo.pilz.freaklog.ui.tabs.journal.allingestions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.ui.theme.horizontalPadding
import foo.pilz.freaklog.ui.utils.getStringOfPattern
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun AllIngestionsScreen(
    viewModel: AllIngestionsViewModel = hiltViewModel(),
    navigateToEditIngestion: (Int) -> Unit
) {
    val sections by viewModel.ingestionSectionsFlow.collectAsState()
    val searchText by viewModel.searchText.collectAsState()
    val isTimeRelative by viewModel.isTimeRelative.collectAsState()

    AllIngestionsScreenContent(
        sections = sections,
        searchText = searchText,
        isTimeRelative = isTimeRelative,
        onSearchTextChange = viewModel::setSearchText,
        onTimeRelativeChange = viewModel::setIsTimeRelative,
        onIngestionClick = { navigateToEditIngestion(it.ingestion.id) },
        onDeleteIngestion = { viewModel.deleteIngestion(it.ingestion) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllIngestionsScreenContent(
    sections: List<IngestionSection>,
    searchText: String,
    isTimeRelative: Boolean,
    onSearchTextChange: (String) -> Unit,
    onTimeRelativeChange: (Boolean) -> Unit,
    onIngestionClick: (IngestionWithCompanionAndCustomUnit) -> Unit,
    onDeleteIngestion: (IngestionWithCompanionAndCustomUnit) -> Unit
) {
    var ingestionToDelete by remember { mutableStateOf<IngestionWithCompanionAndCustomUnit?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Ingestions") },
                actions = {
                    IconToggleButton(
                        checked = isTimeRelative,
                        onCheckedChange = onTimeRelativeChange
                    ) {
                        if (isTimeRelative) {
                            Icon(Icons.Filled.Timer, contentDescription = "Absolute time")
                        } else {
                            Icon(Icons.Outlined.Timer, contentDescription = "Relative time")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            val focusManager = LocalFocusManager.current
            TextField(
                value = searchText,
                onValueChange = onSearchTextChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by substance name") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { onSearchTextChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                    capitalization = KeyboardCapitalization.Sentences
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                singleLine = true
            )

            if (sections.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (searchText.isEmpty()) "No ingestions yet" else "No results",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    sections.forEach { section ->
                        item {
                            SectionHeader(date = section.date)
                        }
                        items(section.ingestions) { ingestionWithCompanion ->
                            IngestionItem(
                                ingestionWithCompanion = ingestionWithCompanion,
                                isTimeRelative = isTimeRelative,
                                onClick = { onIngestionClick(ingestionWithCompanion) },
                                onDelete = { ingestionToDelete = ingestionWithCompanion }
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 0.dp))
                        }
                    }
                }
            }
        }
    }

    ingestionToDelete?.let { ingestionWithCompanion ->
        AlertDialog(
            onDismissRequest = { ingestionToDelete = null },
            title = { Text("Delete Ingestion?") },
            text = { Text("This will permanently delete this ${ingestionWithCompanion.ingestion.substanceName} ingestion.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteIngestion(ingestionWithCompanion)
                        ingestionToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { ingestionToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SectionHeader(date: LocalDate) {
    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    Text(
        text = date.format(dateFormatter),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 8.dp)
    )
}

@Composable
fun IngestionItem(
    ingestionWithCompanion: IngestionWithCompanionAndCustomUnit,
    isTimeRelative: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    val ingestion = ingestionWithCompanion.ingestion
    val color = (ingestionWithCompanion.substanceCompanion?.color ?: AdaptiveColor.RED).getComposeColor(isDarkTheme)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = color,
            modifier = Modifier.size(8.dp, 40.dp)
        ) {}

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ingestion.substanceName,
                style = MaterialTheme.typography.titleMedium
            )
            Row {
                Text(
                    text = "${ingestionWithCompanion.doseDescription} • ${ingestion.administrationRoute.displayText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = if (isTimeRelative) {
                getRelativeTimeString(ingestion.time)
            } else {
                ingestion.time.getStringOfPattern("HH:mm")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun getRelativeTimeString(time: Instant): String {
    val now = Instant.now()
    val duration = Duration.between(time, now)
    
    return when {
        duration.toMinutes() < 1 -> "now"
        duration.toHours() < 1 -> "${duration.toMinutes()}m ago"
        duration.toDays() < 1 -> "${duration.toHours()}h ago"
        duration.toDays() < 7 -> "${duration.toDays()}d ago"
        else -> time.getStringOfPattern("MMM d")
    }
}

@Preview
@Composable
fun AllIngestionsScreenPreview() {
    AllIngestionsScreenContent(
        sections = emptyList(),
        searchText = "",
        isTimeRelative = false,
        onSearchTextChange = {},
        onTimeRelativeChange = {},
        onIngestionClick = {},
        onDeleteIngestion = {}
    )
}
