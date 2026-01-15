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
    onSelectSpray: (Int) -> Unit,
    onSetWeightUnit: (WeightUnit) -> Unit,
    onSetWeightPerSpray: (String) -> Unit,
    onSetLiquidAmountInMl: (String) -> Unit,
    onSetTotalWeight: (String) -> Unit,
    onSetPurityInPercent: (String) -> Unit,
    onDeleteSpray: (Spray) -> Unit,
    onAddSpray: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Spray Calculator") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = horizontalPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Weight per spray section
            CardWithTitle(title = "Solute weight per spray") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = weightPerSpray,
                        onValueChange = onSetWeightPerSpray,
                        label = { Text("Weight per Spray") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    WeightUnitDropdown(
                        selectedUnit = weightUnit,
                        onUnitSelected = onSetWeightUnit
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Spray size section
            CardWithTitle(title = "Spray Size") {
                if (sprays.isEmpty()) {
                    Text(
                        text = "No sprays added yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    sprays.forEach { spray ->
                        SprayItem(
                            spray = spray,
                            isSelected = spray.id == selectedSprayId,
                            onSelect = { onSelectSpray(spray.id) },
                            onDelete = { onDeleteSpray(spray) }
                        )
                        HorizontalDivider()
                    }
                }
                TextButton(onClick = onAddSpray) {
                    Icon(Icons.Default.Add, contentDescription = "Add Spray")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Spray")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Result section
            CardWithTitle(title = "Result") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            contentDescription = "Swap",
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
                            label = { Text("Solute Weight") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(weightUnit.displayName, style = MaterialTheme.typography.titleMedium)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("↓", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = purityInPercent,
                            onValueChange = onSetPurityInPercent,
                            label = { Text("Purity") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("%", style = MaterialTheme.typography.titleMedium)
                    }

                    if (doseAdjustedToPurity != null) {
                        Text(
                            text = "${doseAdjustedToPurity.toReadableString()} ${weightUnit.displayName}",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Info section
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = """Oral or nasal sprays can be used for dosing substances volumetrically.
Note that substances are the most stable in their salt form and degrade more quickly if dissolved in liquid, which might be relevant to you if you plan on storing it for months or years.
Don't use tap water because it can become stale and the chlorine inside it breaks down some substances (e.g. LSD). Use distilled water instead.
Look up the solubility of the substance you want to dissolve in water/ethanol to make sure it will dissolve fully. Most if not all common substances in their salt form are more than soluble enough.
To prevent degradation by temperature use ethanol or a water/ethanol mix as the solvent such that it can be put in the freezer without freezing. However don't use ethanol for nasal sprays as this can damage the nasal mucosa.
Powders for nasal delivery have higher bioavailability than liquids because of increased stability and residence time on nasal mucosa.""",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SprayItem(
    spray: Spray,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Column {
                Text(
                    text = spray.name,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "${spray.contentInMl.toReadableString()} ml = ${spray.numSprays.toReadableString()} sprays",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Selected",
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
        weightPerSpray = "",
        liquidAmountInMl = "",
        totalWeight = "",
        purityInPercent = "90",
        doseAdjustedToPurity = 211.0,
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
