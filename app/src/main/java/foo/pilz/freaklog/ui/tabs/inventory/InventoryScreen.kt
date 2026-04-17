/*
 * Copyright (c) 2026. FreakLog contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package foo.pilz.freaklog.ui.tabs.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import foo.pilz.freaklog.ui.theme.horizontalPadding
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    navigateToAddIngestionForSubstance: (name: String, isCustom: Boolean) -> Unit,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val items by viewModel.itemsFlow.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Inventory") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (items.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            val picked = viewModel.lucky()
                            if (picked == null) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Inventory is empty")
                                }
                            } else {
                                navigateToAddIngestionForSubstance(picked.substanceName, picked.isCustom)
                            }
                        },
                        icon = {
                            Icon(Icons.Filled.Casino, contentDescription = "Feeling Lucky")
                        },
                        text = { Text("I'm feeling lucky") }
                    )
                }
                FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add item")
                }
            }
        }
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Your inventory is empty",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Tap + to add a substance you have on hand.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.substanceName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (item.isCustom) {
                                    Text(
                                        text = "Custom substance",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (item.notes.isNotBlank()) {
                                    Text(
                                        text = item.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.delete(item) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddInventoryDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, isCustom, notes ->
                viewModel.add(name, isCustom, notes)
                showAdd = false
            },
            viewModel = viewModel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddInventoryDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Boolean, String) -> Unit,
    viewModel: InventoryViewModel
) {
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isCustom by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf(listOf<InventoryViewModel.Suggestion>()) }

    LaunchedEffect(name) {
        suggestions = viewModel.suggestions(name)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to inventory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        isCustom = false
                    },
                    label = { Text("Substance name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                suggestions.take(5).forEach { s ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                name = s.name
                                isCustom = s.isCustom
                            }
                    ) {
                        Text(
                            text = s.name + if (s.isCustom) "  (custom)" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val match = suggestions.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
                    // If the user typed a name that matches no known PW or custom substance,
                    // treat it as a brand-new custom entry rather than silently mis-categorising it.
                    val resolvedIsCustom = match?.isCustom ?: true
                    onAdd(name.trim(), resolvedIsCustom, notes.trim())
                }
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
