/*
 * Copyright (c) 2026. Freaklog.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Freaklog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Freaklog.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.ui.tabs.search.custom.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteraction
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionSeverity
import foo.pilz.freaklog.data.room.experiences.relations.CustomRoaInfo
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.DurationUnits
import foo.pilz.freaklog.ui.theme.horizontalPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSubstanceProfileScreen(
    navigateBack: () -> Unit,
    viewModel: CustomSubstanceProfileViewModel = hiltViewModel(),
) {
    val loaded = viewModel.substanceFlow.collectAsState().value
    val context = LocalContext.current
    var routeBeingEdited by remember { mutableStateOf<CustomRouteDraft?>(null) }
    var isPickingNewRoute by remember { mutableStateOf(false) }
    var isAddingInteraction by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(loaded?.substance?.name ?: "Substance profile") },
                actions = {
                    IconButton(onClick = { viewModel.share(context) }) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share substance file")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                modifier = Modifier.imePadding(),
                onClick = {
                    viewModel.saveTexts()
                    navigateBack()
                },
                icon = { Icon(Icons.Filled.Done, contentDescription = null) },
                text = { Text("Done") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProfileCard("Routes of administration") {
                val infos = loaded?.roaInfos.orEmpty()
                if (infos.isEmpty()) {
                    Text("No routes yet. Add one to set doses and durations.", style = MaterialTheme.typography.bodySmall)
                }
                infos.forEach { info ->
                    RouteRow(info) {
                        routeBeingEdited = CustomRouteDraft.from(info, loaded?.substance?.units.orEmpty())
                    }
                }
                OutlinedButton(onClick = { isPickingNewRoute = true }, Modifier.fillMaxWidth()) { Text("Add route") }
                DropdownMenu(expanded = isPickingNewRoute, onDismissRequest = { isPickingNewRoute = false }) {
                    val used = infos.map { it.route }.toSet()
                    AdministrationRoute.entries.filter { it !in used }.forEach { route ->
                        DropdownMenuItem(
                            text = { Text(route.displayText) },
                            onClick = {
                                isPickingNewRoute = false
                                routeBeingEdited = CustomRouteDraft(
                                    route = route,
                                    doseUnits = loaded?.substance?.units.orEmpty(),
                                )
                            },
                        )
                    }
                }
            }
            ProfileCard("Interactions") {
                loaded?.interactions.orEmpty()
                    .sortedBy { it.severity.ordinal }
                    .forEach { InteractionRow(it) { viewModel.deleteInteraction(it) } }
                OutlinedButton(onClick = { isAddingInteraction = true }, Modifier.fillMaxWidth()) {
                    Text("Add interaction")
                }
            }
            ProfileCard("Cross-tolerances") {
                ProfileTextField(
                    "Substances or classes, comma separated",
                    viewModel.crossTolerancesText
                ) { viewModel.crossTolerancesText = it }
            }
            ProfileCard("Tolerance") {
                ProfileTextField("Full tolerance after", viewModel.toleranceFull) { viewModel.toleranceFull = it }
                ProfileTextField("Half tolerance after", viewModel.toleranceHalf) { viewModel.toleranceHalf = it }
                ProfileTextField("Zero tolerance after", viewModel.toleranceZero) { viewModel.toleranceZero = it }
            }
            ProfileCard("Description") {
                ProfileTextField("Summary", viewModel.summary, singleLine = false) { viewModel.summary = it }
                ProfileTextField("Effects", viewModel.effectsText, singleLine = false) { viewModel.effectsText = it }
            }
            ProfileCard("Risks") {
                ProfileTextField("General risks", viewModel.generalRisks, singleLine = false) {
                    viewModel.generalRisks = it
                }
                ProfileTextField("Long-term risks", viewModel.longTermRisks, singleLine = false) {
                    viewModel.longTermRisks = it
                }
            }
        }
    }

    routeBeingEdited?.let { draft ->
        RouteEditorDialog(
            draft = draft,
            onChange = { routeBeingEdited = it },
            onSave = {
                viewModel.saveRoute(draft)
                routeBeingEdited = null
            },
            onDelete = {
                viewModel.deleteRoute(draft.route)
                routeBeingEdited = null
            },
            onDismiss = { routeBeingEdited = null },
        )
    }
    if (isAddingInteraction) {
        AddInteractionDialog(
            onAdd = { severity, name ->
                viewModel.addInteraction(severity, name)
                isAddingInteraction = false
            },
            onDismiss = { isAddingInteraction = false },
        )
    }
}

@Composable
private fun ProfileCard(title: String, content: @Composable () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ProfileTextField(
    label: String,
    value: String,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
    )
}

/** "5 / 10 / 20 / 40 mg · total 5-7h" style one-line summary of a stored route. */
internal fun routeSummary(info: CustomRoaInfo): String {
    val dose = info.dose?.let { dose ->
        listOf(dose.lightMin, dose.commonMin, dose.strongMin, dose.heavyMin)
            .joinToString(" / ") { it?.toString()?.removeSuffix(".0") ?: "–" } + " " + dose.units.orEmpty()
    }
    val total = info.duration?.total?.let { "total ${it.text}" }
    val bioavailability = info.roa?.bioavailability?.let { bio ->
        "${bio.min?.toString()?.removeSuffix(".0")}-${bio.max?.toString()?.removeSuffix(".0")}% bioavailable"
    }
    return listOfNotNull(dose?.trim(), total, bioavailability).joinToString(" · ").ifEmpty { "No details" }
}

@Composable
private fun RouteRow(info: CustomRoaInfo, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Text(info.route.displayText, style = MaterialTheme.typography.bodyLarge)
        Text(
            routeSummary(info),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InteractionRow(interaction: CustomInteraction, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(interaction.targetName)
            Text(
                interaction.severity.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                color = if (interaction.severity == CustomInteractionSeverity.UNCERTAIN) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, contentDescription = "Remove interaction") }
    }
}

@Composable
private fun AddInteractionDialog(onAdd: (CustomInteractionSeverity, String) -> Unit, onDismiss: () -> Unit) {
    var severity by remember { mutableStateOf(CustomInteractionSeverity.DANGEROUS) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add interaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileTextField("Other substance", name) { name = it }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CustomInteractionSeverity.entries.forEach { option ->
                        FilterChip(
                            selected = severity == option,
                            onClick = { severity = option },
                            label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(severity, name) }, enabled = name.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RouteEditorDialog(
    draft: CustomRouteDraft,
    onChange: (CustomRouteDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(draft.route.displayText) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Dose thresholds", style = MaterialTheme.typography.titleSmall)
                ProfileTextField("Units", draft.doseUnits) { onChange(draft.copy(doseUnits = it)) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberField("Light", draft.lightMin, !draft.areDosesValid, Modifier.weight(1f)) {
                        onChange(draft.copy(lightMin = it))
                    }
                    NumberField("Common", draft.commonMin, !draft.areDosesValid, Modifier.weight(1f)) {
                        onChange(draft.copy(commonMin = it))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberField("Strong", draft.strongMin, !draft.areDosesValid, Modifier.weight(1f)) {
                        onChange(draft.copy(strongMin = it))
                    }
                    NumberField("Heavy", draft.heavyMin, !draft.areDosesValid, Modifier.weight(1f)) {
                        onChange(draft.copy(heavyMin = it))
                    }
                }
                Text("Duration", style = MaterialTheme.typography.titleSmall)
                DurationFields("Onset", draft.onset) { onChange(draft.copy(onset = it)) }
                DurationFields("Come-up", draft.comeup) { onChange(draft.copy(comeup = it)) }
                DurationFields("Peak", draft.peak) { onChange(draft.copy(peak = it)) }
                DurationFields("Offset", draft.offset) { onChange(draft.copy(offset = it)) }
                DurationFields("Total", draft.total) { onChange(draft.copy(total = it)) }
                Text("Bioavailability (%)", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberField("Min", draft.bioavailabilityMin, !draft.isBioavailabilityValid, Modifier.weight(1f)) {
                        onChange(draft.copy(bioavailabilityMin = it))
                    }
                    NumberField("Max", draft.bioavailabilityMax, !draft.isBioavailabilityValid, Modifier.weight(1f)) {
                        onChange(draft.copy(bioavailabilityMax = it))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = draft.isValid) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    isError: Boolean,
    modifier: Modifier,
    onValueChange: (String) -> Unit,
) = ProfileTextField(
    label = label,
    value = value,
    keyboardType = KeyboardType.Decimal,
    isError = isError && value.isNotBlank(),
    modifier = modifier,
    onValueChange = onValueChange,
)

@Composable
private fun DurationFields(label: String, draft: DurationDraft, onChange: (DurationDraft) -> Unit) {
    var isUnitMenuOpen by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        NumberField("$label min", draft.min, !draft.isValid, Modifier.weight(1f)) { onChange(draft.copy(min = it)) }
        NumberField("max", draft.max, !draft.isValid, Modifier.weight(1f)) { onChange(draft.copy(max = it)) }
        TextButton(onClick = { isUnitMenuOpen = true }) {
            Text(draft.units.shortText)
            DropdownMenu(expanded = isUnitMenuOpen, onDismissRequest = { isUnitMenuOpen = false }) {
                DurationUnits.entries.forEach { unit ->
                    DropdownMenuItem(
                        text = { Text(unit.text) },
                        onClick = {
                            isUnitMenuOpen = false
                            onChange(draft.copy(units = unit))
                        },
                    )
                }
            }
        }
    }
}
