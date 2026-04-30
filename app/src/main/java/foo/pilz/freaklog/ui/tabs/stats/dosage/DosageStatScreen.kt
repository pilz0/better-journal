/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog (a fork of PsychonautWiki Journal).
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.ui.tabs.stats.dosage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import foo.pilz.freaklog.ui.tabs.stats.substancecompanion.DosageBarChart
import foo.pilz.freaklog.ui.theme.horizontalPadding

/**
 * Standalone "Dosage statistics" screen reachable from `SubstanceCompanionScreen`.
 *
 * Mirrors the iOS `DosageStatScreen`: time-bucketed total-dose bar chart with average-line
 * toggle, an estimation field for unknown doses, and a unit-mismatch warning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DosageStatScreen(
    onBack: () -> Unit,
    viewModel: DosageStatViewModel = hiltViewModel(),
) {
    val range by viewModel.selectedRange.collectAsState()
    val showAverage by viewModel.showAverage.collectAsState()
    val estimate by viewModel.estimatedUnknownDose.collectAsState()
    val result by viewModel.result.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dosage stats — ${viewModel.substanceName}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TimeRangeChips(selected = range, onSelected = viewModel::setRange)

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = range.title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (result.buckets.all { it.totalDose <= 0.0 }) {
                        Text(
                            text = "No dose data in this range yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        DosageBarChart(
                            buckets = result.buckets,
                            barColor = MaterialTheme.colorScheme.primary,
                            showAverage = showAverage,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Show average",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(checked = showAverage, onCheckedChange = viewModel::setShowAverage)
                    }
                }
            }

            if (result.unitsUsed.size > 1) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        modifier = Modifier.padding(12.dp),
                        text = "Heads-up: ingestions for this substance use multiple unit strings " +
                            "(${result.unitsUsed.joinToString(", ")}). The chart sums them — interpret with care.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (result.unknownDoseCount > 0) {
                EstimateUnknownCard(
                    unknownCount = result.unknownDoseCount,
                    defaultDose = viewModel.defaultEstimateDose,
                    defaultUnits = viewModel.defaultEstimateUnits,
                    currentEstimate = estimate,
                    onEstimateChange = viewModel::setEstimatedUnknownDose,
                )
            }
        }
    }
}

@Composable
private fun TimeRangeChips(
    selected: DosageStatRange,
    onSelected: (DosageStatRange) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DosageStatRange.entries.forEach { range ->
            FilterChip(
                selected = selected == range,
                onClick = { onSelected(range) },
                label = { Text(range.displayText) },
            )
        }
    }
}

@Composable
private fun EstimateUnknownCard(
    unknownCount: Int,
    defaultDose: Double?,
    defaultUnits: String?,
    currentEstimate: Double?,
    onEstimateChange: (Double?) -> Unit,
) {
    var text by remember(currentEstimate, defaultDose) {
        mutableStateOf(currentEstimate?.toString() ?: defaultDose?.toString().orEmpty())
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Estimate unknown doses",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "$unknownCount ingestion${if (unknownCount != 1) "s" else ""} have no recorded dose. " +
                    "Provide a placeholder so the chart can include them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = {
                    val sanitized = it.replace(',', '.')
                    text = sanitized
                    onEstimateChange(sanitized.toDoubleOrNull())
                },
                label = { Text("Estimate per unknown dose${defaultUnits?.let { " ($it)" }.orEmpty()}") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (defaultDose != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Default suggestion: ${defaultDose} ${defaultUnits.orEmpty()} (substance common-min).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
