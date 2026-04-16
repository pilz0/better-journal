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

package foo.pilz.freaklog.ui.tabs.safer.spray

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import foo.pilz.freaklog.data.room.experiences.entities.Spray
import foo.pilz.freaklog.ui.tabs.journal.experience.components.CardWithTitle
import foo.pilz.freaklog.ui.tabs.search.substance.roa.toReadableString
import foo.pilz.freaklog.ui.theme.horizontalPadding

@Composable
fun SprayCalculatorScreen(
    viewModel: SprayCalculatorViewModel = hiltViewModel(),
    navigateToAddSpray: () -> Unit
) {
    val sprays by viewModel.spraysFlow.collectAsState()
    val selectedSprayId by viewModel.selectedSprayId.collectAsState()
    val weightUnit by viewModel.weightUnit.collectAsState()
    val weightPerSpray by viewModel.weightPerSpray.collectAsState()
    val liquidAmountInMl by viewModel.liquidAmountInMl.collectAsState()
    val totalWeight by viewModel.totalWeight.collectAsState()
    val purityInPercent by viewModel.purityInPercent.collectAsState()
    val doseAdjustedToPurity = viewModel.getDoseAdjustedToPurity()
    val concentrationPerMl = viewModel.getConcentrationPerMl()
    val numberOfSprays = viewModel.getNumberOfSprays()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveSelection()
        }
    }

    SprayCalculatorScreenContent(
        sprays = sprays,
        selectedSprayId = selectedSprayId,
        weightUnit = weightUnit,
        weightPerSpray = weightPerSpray,
        liquidAmountInMl = liquidAmountInMl,
        totalWeight = totalWeight,
        purityInPercent = purityInPercent,
        doseAdjustedToPurity = doseAdjustedToPurity,
        concentrationPerMl = concentrationPerMl,
        numberOfSprays = numberOfSprays,
        onSelectSpray = viewModel::selectSpray,
        onSetWeightUnit = viewModel::setWeightUnit,
        onSetWeightPerSpray = viewModel::setWeightPerSpray,
        onSetLiquidAmountInMl = viewModel::setLiquidAmountInMl,
        onSetTotalWeight = viewModel::setTotalWeight,
        onSetPurityInPercent = viewModel::setPurityInPercent,
        onDeleteSpray = viewModel::deleteSpray,
        onAddSpray = navigateToAddSpray
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SprayCalculatorScreenContent(
    sprays: List<Spray>,
    selectedSprayId: Int?,
    weightUnit: WeightUnit,
    weightPerSpray: String,
    liquidAmountInMl: String,
    totalWeight: String,
    purityInPercent: String,
    doseAdjustedToPurity: Double?,
    concentrationPerMl: Double?,
    numberOfSprays: Double?,
    onSelectSpray: (Int) -> Unit,
    onSetWeightUnit: (WeightUnit) -> Unit,
    onSetWeightPerSpray: (String) -> Unit,
    onSetLiquidAmountInMl: (String) -> Unit,
    onSetTotalWeight: (String) -> Unit,
    onSetPurityInPercent: (String) -> Unit,
    onDeleteSpray: (Spray) -> Unit,
    onAddSpray: () -> Unit
) {
    var showInfoDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spray Calculator") },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Info")
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
            // Calculator Section
            CardWithTitle(title = "Calculator") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    
                    // Spray Selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SpraySelectionDropdown(
                            sprays = sprays,
                            selectedSprayId = selectedSprayId,
                            onSelectSpray = onSelectSpray,
                            onDeleteSpray = onDeleteSpray,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = onAddSpray) {
                            Icon(Icons.Default.Add, contentDescription = "Add Spray")
                        }
                    }

                    HorizontalDivider()

                    // Target Strength
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = weightPerSpray,
                            onValueChange = onSetWeightPerSpray,
                            label = { Text("Target Strength / Spray") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        WeightUnitDropdown(
                            selectedUnit = weightUnit,
                            onUnitSelected = onSetWeightUnit
                        )
                    }

                    HorizontalDivider()

                    // Bidirectional Calculation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = liquidAmountInMl,
                            onValueChange = onSetLiquidAmountInMl,
                            label = { Text("Liquid Volume") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ml", style = MaterialTheme.typography.titleMedium)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Outlined.SwapVert,
                            contentDescription = "Linked",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = totalWeight,
                            onValueChange = onSetTotalWeight,
                            label = { Text("Total Weight") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(weightUnit.displayName, style = MaterialTheme.typography.titleMedium)
                    }

                    // Purity Adjustment
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = purityInPercent,
                            onValueChange = onSetPurityInPercent,
                            label = { Text("Purity (%)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (doseAdjustedToPurity != null) {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                           Column(modifier = Modifier.padding(16.dp)) {
                               Text(
                                   text = "Pure substance needed:",
                                   style = MaterialTheme.typography.labelMedium,
                                   color = MaterialTheme.colorScheme.onSurfaceVariant
                               )
                               Text(
                                   text = "${doseAdjustedToPurity.toReadableString()} ${weightUnit.displayName}",
                                   style = MaterialTheme.typography.headlineSmall,
                                   color = MaterialTheme.colorScheme.primary
                               )
                           }
                        }
                    }

                    // Results Summary
                    if (concentrationPerMl != null || numberOfSprays != null) {
                        HorizontalDivider()
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Results",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (concentrationPerMl != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Concentration",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${concentrationPerMl.toReadableString()} ${weightUnit.displayName}/ml",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                                if (numberOfSprays != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Total sprays",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = numberOfSprays.toReadableString(),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showInfoDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Close")
                }
            },
            title = { Text("Information") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = """Oral or nasal sprays can be used for dosing substances volumetrically.
Note that substances are the most stable in their salt form and degrade more quickly if dissolved in liquid, which might be relevant to you if you plan on storing it for months or years.
Don't use tap water because it can become stale and the chlorine inside it breaks down some substances (e.g. LSD). Use distilled water instead.
Look up the solubility of the substance you want to dissolve in water/ethanol to make sure it will dissolve fully. Most if not all common substances in their salt form are more than soluble enough.
To prevent degradation by temperature use ethanol or a water/ethanol mix as the solvent such that it can be put in the freezer without freezing. However don't use ethanol for nasal sprays as this can damage the nasal mucosa.
Powders for nasal delivery have higher bioavailability than liquids because of increased stability and residence time on nasal mucosa.""",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpraySelectionDropdown(
    sprays: List<Spray>,
    selectedSprayId: Int?,
    onSelectSpray: (Int) -> Unit,
    onDeleteSpray: (Spray) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedSpray = sprays.find { it.id == selectedSprayId }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedSpray?.name ?: if (sprays.isEmpty()) "No sprays" else "Select spray",
            onValueChange = {},
            readOnly = true,
            label = { Text("Spray Bottle") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (sprays.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No sprays added") },
                    onClick = { expanded = false }
                )
            } else {
                sprays.forEach { spray ->
                    DropdownMenuItem(
                        text = { 
                             Column {
                                 Text(spray.name)
                                 Text(
                                     "${spray.contentInMl.toReadableString()}ml / ${spray.numSprays.toReadableString()} sprays",
                                     style = MaterialTheme.typography.bodySmall,
                                     color = MaterialTheme.colorScheme.onSurfaceVariant
                                 )
                             }
                        },
                        trailingIcon = {
                             IconButton(onClick = { 
                                 onDeleteSpray(spray) 
                                 // Don't close menu immediately upon delete to allow multiple deletes? 
                                 // Or close it. Let's close for simplicity/safety.
                                 expanded = false
                             }) {
                                 Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                             }
                        },
                        onClick = {
                            onSelectSpray(spray.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightUnitDropdown(
    selectedUnit: WeightUnit,
    onUnitSelected: (WeightUnit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedUnit.displayName,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .width(100.dp)
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            WeightUnit.entries.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit.displayName) },
                    onClick = {
                        onUnitSelected(unit)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Preview
@Composable
fun SprayCalculatorScreenPreview() {
    val sampleSprays = listOf(
        Spray(id = 1, name = "Small Spray", contentInMl = 5.0, numSprays = 32.0),
        Spray(id = 2, name = "Big Spray", contentInMl = 10.0, numSprays = 50.0)
    )
    SprayCalculatorScreenContent(
        sprays = sampleSprays,
        selectedSprayId = 1,
        weightUnit = WeightUnit.MG,
        weightPerSpray = "1.5",
        liquidAmountInMl = "10",
        totalWeight = "300",
        purityInPercent = "90",
        doseAdjustedToPurity = 333.3,
        concentrationPerMl = 30.0,
        numberOfSprays = 200.0,
        onSelectSpray = {},
        onSetWeightUnit = {},
        onSetWeightPerSpray = {},
        onSetLiquidAmountInMl = {},
        onSetTotalWeight = {},
        onSetPurityInPercent = {},
        onDeleteSpray = {},
        onAddSpray = {}
    )
}
