/*
 * Copyright (c) 2022-2023. Isaak Hanimann.
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

package foo.pilz.freaklog.ui.tabs.stats.substancecompanion

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.substances.classes.Tolerance
import foo.pilz.freaklog.ui.tabs.journal.experience.components.CardWithTitle
import foo.pilz.freaklog.ui.tabs.search.substance.roa.ToleranceSection
import foo.pilz.freaklog.ui.theme.JournalTheme
import foo.pilz.freaklog.ui.theme.horizontalPadding
import foo.pilz.freaklog.ui.utils.getDateWithWeekdayText
import foo.pilz.freaklog.ui.utils.getShortTimeText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.scale

@Composable
fun SubstanceCompanionScreen(
    viewModel: SubstanceCompanionViewModel = hiltViewModel()
) {
    val companion = viewModel.thisCompanionFlow.collectAsState().value
    if (companion == null) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {}
    } else {
        SubstanceCompanionScreen(
            substanceCompanion = companion,
            ingestionBursts = viewModel.ingestionBurstsFlow.collectAsState().value,
            tolerance = viewModel.tolerance,
            crossTolerances = viewModel.crossTolerances,
            consumerName = viewModel.consumerName,
            dosageBuckets = viewModel.dosageChartDataFlow.collectAsState().value,
            selectedTimeRange = viewModel.selectedTimeRange.collectAsState().value,
            onTimeRangeSelected = viewModel::setTimeRange,
            showAverage = viewModel.showAverage.collectAsState().value,
            onToggleShowAverage = viewModel::toggleShowAverage,
            showTrendLine = viewModel.showTrendLine.collectAsState().value,
            onToggleShowTrendLine = viewModel::toggleShowTrendLine,
            selectedMetric = viewModel.selectedMetric.collectAsState().value,
            onMetricSelected = viewModel::setMetric,
            doseThresholds = viewModel.doseThresholds,
            chartSummary = viewModel.chartSummaryFlow.collectAsState().value
        )
    }
}

@Preview
@Composable
fun SubstanceCompanionPreview(@PreviewParameter(SubstanceCompanionScreenPreviewProvider::class) pair: Pair<SubstanceCompanion, List<IngestionsBurst>>) {
    JournalTheme {
        SubstanceCompanionScreen(
            substanceCompanion = pair.first,
            ingestionBursts = pair.second,
            tolerance = Tolerance(
                full = "with prolonged use",
                half = "two weeks",
                zero = "1 month"
            ),
            crossTolerances = listOf(
                "dopamine",
                "stimulant"
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubstanceCompanionScreen(
    substanceCompanion: SubstanceCompanion,
    ingestionBursts: List<IngestionsBurst>,
    tolerance: Tolerance?,
    crossTolerances: List<String>,
    consumerName: String? = null,
    dosageBuckets: List<DosageBucket> = emptyList(),
    selectedTimeRange: DosageTimeRange = DosageTimeRange.WEEKS_26,
    onTimeRangeSelected: (DosageTimeRange) -> Unit = {},
    showAverage: Boolean = false,
    onToggleShowAverage: (Boolean) -> Unit = {},
    showTrendLine: Boolean = false,
    onToggleShowTrendLine: (Boolean) -> Unit = {},
    selectedMetric: DosageMetric = DosageMetric.TOTAL_DOSE,
    onMetricSelected: (DosageMetric) -> Unit = {},
    doseThresholds: DoseThresholds? = null,
    chartSummary: ChartSummary? = null
) {
    // Tracks which bar the user last tapped; null = none selected
    var tappedIndex by remember { mutableStateOf<Int?>(null) }
    // Clear the selection whenever the time range changes so the info card
    // doesn't display stale data from a different bucket at the same index.
    LaunchedEffect(selectedTimeRange) { tappedIndex = null }

    Scaffold(
        topBar = {
            val title = if (consumerName == null) {
                substanceCompanion.substanceName
            } else {
                "${substanceCompanion.substanceName} ($consumerName)"
            }
            TopAppBar(title = { Text(title) })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Dosage stats card ────────────────────────────────────────────
            item {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {

                        // Time-range selector pill
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            DosageTimeRange.values().forEach { range ->
                                val isSelected = range == selectedTimeRange
                                val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                val fg = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(bg, RoundedCornerShape(6.dp))
                                        .clickable { onTimeRangeSelected(range) }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = range.displayText,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = fg
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // ── Summary row (#8) ──────────────────────────────
                        if (chartSummary != null && chartSummary.totalSessions > 0) {
                            val parts = buildList {
                                add("${chartSummary.totalSessions} sessions")
                                chartSummary.longestGapDays?.let { add("${it}d max gap") }
                                if (chartSummary.currentStreakWeeks > 0) {
                                    add("${chartSummary.currentStreakWeeks}w streak")
                                }
                            }
                            Text(
                                text = parts.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // Chart subtitle + title
                        val chartSubtitle = when (selectedTimeRange) {
                            DosageTimeRange.DAYS_30   -> "Dosage by day"
                            DosageTimeRange.WEEKS_26  -> "Dosage by week"
                            DosageTimeRange.MONTHS_12 -> "Dosage by month"
                            DosageTimeRange.ALL       -> "Dosage over all time"
                        }
                        Text(
                            text = chartSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = selectedTimeRange.title,
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Chart (#4 tap handling)
                        DosageBarChart(
                            buckets = dosageBuckets,
                            barColor = substanceCompanion.color.getComposeColor(isSystemInDarkTheme()),
                            showAverage = showAverage,
                            showTrendLine = showTrendLine,
                            metric = selectedMetric,
                            doseThresholds = doseThresholds,
                            onBarTapped = { idx -> tappedIndex = if (tappedIndex == idx) null else idx },
                            height = 200.dp
                        )

                        // ── Tapped-bar info row (#4) ──────────────────────
                        val tappedBucket = tappedIndex?.let { dosageBuckets.getOrNull(it) }
                        if (tappedBucket != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(
                                        text = tappedBucket.fullDateText,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (tappedBucket.totalDose > 0)
                                                "${formatSiValue(tappedBucket.totalDose)} ${tappedBucket.unit} total"
                                            else
                                                "No dose logged",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "${tappedBucket.sessionCount} session${if (tappedBucket.sessionCount != 1) "s" else ""}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (tappedBucket.sessionCount > 1 && tappedBucket.avgDosePerSession > 0) {
                                        Text(
                                            text = "avg ${formatSiValue(tappedBucket.avgDosePerSession)} ${tappedBucket.unit}/session",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Metric selector (#3 / #10) ────────────────────────
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DosageMetric.values().forEach { m ->
                            val isSelected = m == selectedMetric
                            val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            val fg = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(bg, RoundedCornerShape(6.dp))
                                    .clickable { onMetricSelected(m) }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = m.displayText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = fg
                                )
                            }
                        }
                    }

                    // ── Toggle row: Average + Trend Line (#7, #9) ─────────
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Average toggle
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onToggleShowAverage(!showAverage) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Average", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = showAverage,
                                onCheckedChange = onToggleShowAverage,
                                modifier = Modifier.scale(0.72f)
                            )
                        }

                        // Trend line toggle
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onToggleShowTrendLine(!showTrendLine) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Trend line", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = showTrendLine,
                                onCheckedChange = onToggleShowTrendLine,
                                modifier = Modifier.scale(0.72f)
                            )
                        }
                    }
                }
            }

            item {
                if (tolerance != null || crossTolerances.isNotEmpty()) {
                    CardWithTitle(title = "Tolerance", modifier = Modifier.fillMaxWidth()) {
                        ToleranceSection(
                            tolerance = tolerance,
                            crossTolerances = crossTolerances
                        )
                    }
                }
                Text(text = "Now")
            }
            items(ingestionBursts) { burst ->
                TimeArrowUp(timeText = burst.timeUntil)
                ElevatedCard(modifier = Modifier.padding(vertical = 5.dp)) {
                    Column(modifier = Modifier.padding(horizontal = horizontalPadding)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                        ) {
                            Text(
                                text = burst.experience.title,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = burst.experience.sortDate.getDateWithWeekdayText(),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        HorizontalDivider()
                        burst.ingestions.forEachIndexed { index, ingestion ->
                            IngestionRowOnSubstanceCompanionScreen(ingestionAndCustomUnit = ingestion)
                            if (index < burst.ingestions.size - 1) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IngestionRowOnSubstanceCompanionScreen(ingestionAndCustomUnit: IngestionsBurst.IngestionAndCustomUnit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val text = buildAnnotatedString {
            append(ingestionAndCustomUnit.doseDescription)
            if (ingestionAndCustomUnit.customUnit != null) {
                append(" " + ingestionAndCustomUnit.customUnit.name)
            }
            withStyle(style = SpanStyle(color = if (isSystemInDarkTheme()) Color.Gray else Color.LightGray )) {
                val routeText =
                    ingestionAndCustomUnit.ingestion.administrationRoute.displayText.lowercase()
                if (ingestionAndCustomUnit.customUnit == null) {
                    append(" $routeText")
                }
                ingestionAndCustomUnit.customUnitDose?.calculatedDoseDescription?.let {
                    append(" = $it $routeText")
                }
            }
        }
        Text(text = text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        val dateString = ingestionAndCustomUnit.ingestion.time.getShortTimeText()
        Text(text = dateString)
    }
}