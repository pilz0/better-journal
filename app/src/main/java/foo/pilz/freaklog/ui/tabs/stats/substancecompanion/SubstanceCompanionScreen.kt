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
            onToggleShowAverage = viewModel::toggleShowAverage
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
    onToggleShowAverage: (Boolean) -> Unit = {}
) {
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
            // Dosage Stats Section
            item {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Time Range Selector
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
                                val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(backgroundColor, RoundedCornerShape(6.dp))
                                        .clickable { onTimeRangeSelected(range) }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = range.displayText,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = contentColor
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        val chartTitle = when (selectedTimeRange) {
                            DosageTimeRange.DAYS_30 -> "Dosage by day"
                            DosageTimeRange.WEEKS_26 -> "Dosage by week"
                            DosageTimeRange.MONTHS_12 -> "Dosage by month"
                        }
                        
                        Text(
                            text = chartTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Text(
                            text = selectedTimeRange.title,
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        DosageBarChart(
                            buckets = dosageBuckets,
                            barColor = substanceCompanion.color.getComposeColor(isSystemInDarkTheme()),
                            showAverage = showAverage,
                            height = 200.dp
                        )
                    }
                    
                    // Show Average Toggle Section
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleShowAverage(!showAverage) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Show Average",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = showAverage,
                            onCheckedChange = onToggleShowAverage,
                            modifier = Modifier.scale(0.8f)
                        )
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