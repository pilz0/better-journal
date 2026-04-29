/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog (a fork of PsychonautWiki Journal).
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.ui.tabs.stats.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import foo.pilz.freaklog.ui.tabs.stats.BarChart
import foo.pilz.freaklog.ui.theme.horizontalPadding

/**
 * "More charts" screen reachable from the Statistics tab.
 *
 * Bundles the previously iOS-only Daily / Monthly / Yearly experience charts and the
 * fractional substance breakdown ("Experience details") into a single scrollable view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreChartsScreen(
    onBack: () -> Unit,
    viewModel: MoreChartsViewModel = hiltViewModel(),
) {
    val daily by viewModel.dailyBuckets.collectAsState()
    val monthly by viewModel.monthlyBuckets.collectAsState()
    val yearly by viewModel.yearlyBuckets.collectAsState()
    val fractional by viewModel.fractionalCounts.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("More charts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item {
                ChartCard(title = "Daily — last 30 days", subtitle = "Ingestions per day, color-coded by substance.") {
                    if (daily.any { it.isNotEmpty() }) {
                        BarChart(buckets = daily, startDateText = "30 days ago")
                    } else {
                        EmptyChartHint()
                    }
                }
            }
            item {
                ChartCard(title = "Monthly — last 12 months", subtitle = "Ingestions per calendar month.") {
                    if (monthly.any { it.isNotEmpty() }) {
                        BarChart(buckets = monthly, startDateText = "12 months ago")
                    } else {
                        EmptyChartHint()
                    }
                }
            }
            item {
                ChartCard(title = "Yearly — all years", subtitle = "Ingestions per calendar year.") {
                    if (yearly.any { it.isNotEmpty() }) {
                        BarChart(buckets = yearly, startDateText = "First year of data")
                    } else {
                        EmptyChartHint()
                    }
                }
            }
            item {
                FractionalSection(fractional = fractional)
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun EmptyChartHint() {
    Text(
        text = "No data in this range yet.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FractionalSection(fractional: List<SubstanceFraction>) {
    val isDark = isSystemInDarkTheme()
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Substance share", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Each multi-substance experience is split fractionally between its substances " +
                    "(a 2-substance experience counts as 0.5 for each substance).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (fractional.isEmpty()) {
                EmptyChartHint()
            } else {
                val maxValue = fractional.maxOf { it.fractionalCount }
                fractional.forEach { fraction ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(fraction.color.getComposeColor(isDark)),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = fraction.substanceName,
                            modifier = Modifier.fillMaxWidth(0.4f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(10.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
                        ) {
                            val frac = (fraction.fractionalCount / maxValue).toFloat().coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(frac)
                                    .height(10.dp)
                                    .background(fraction.color.getComposeColor(isDark), RoundedCornerShape(4.dp)),
                            )
                        }
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = "%.2f".format(fraction.fractionalCount),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = "Total experiences are fractionally split — see footer.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
