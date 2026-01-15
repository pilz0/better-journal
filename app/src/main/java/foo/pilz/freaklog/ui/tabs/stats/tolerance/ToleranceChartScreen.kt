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

package foo.pilz.freaklog.ui.tabs.stats.tolerance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import foo.pilz.freaklog.ui.tabs.journal.experience.components.CardWithTitle
import foo.pilz.freaklog.ui.theme.horizontalPadding
import foo.pilz.freaklog.ui.utils.getStringOfPattern
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ToleranceChartScreen(
    viewModel: ToleranceChartViewModel = hiltViewModel(),
    navigateToAddIngestion: () -> Unit = {}
) {
    val toleranceData by viewModel.toleranceDataFlow.collectAsState()
    val sinceDate by viewModel.sinceDate.collectAsState()
    val isTimeRelative by viewModel.isTimeRelative.collectAsState()

    ToleranceChartScreenContent(
        toleranceData = toleranceData,
        sinceDate = sinceDate,
        isTimeRelative = isTimeRelative,
        onChangeSinceDate = viewModel::setSinceDate,
        onChangeIsTimeRelative = viewModel::setIsTimeRelative,
        onAddTap = navigateToAddIngestion
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToleranceChartScreenContent(
    toleranceData: ToleranceData,
    sinceDate: Instant,
    isTimeRelative: Boolean,
    onChangeSinceDate: (Instant) -> Unit,
    onChangeIsTimeRelative: (Boolean) -> Unit,
    onAddTap: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tolerance") },
                actions = {
                    IconButton(onClick = onAddTap) {
                        Icon(Icons.Default.Add, contentDescription = "Add Temporary Ingestion")
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
            CardWithTitle(title = "Chart Settings") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Start Date")
                    TextButton(onClick = { showDatePicker = true }) {
                        val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        val localDate = sinceDate.atZone(ZoneId.systemDefault()).toLocalDate()
                        Text(localDate.format(dateFormatter))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Relative time")
                    Switch(
                        checked = isTimeRelative,
                        onCheckedChange = onChangeIsTimeRelative
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (toleranceData.toleranceWindows.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No ingestions with tolerance info",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                ToleranceChart(
                    toleranceWindows = toleranceData.toleranceWindows,
                    numberOfRows = toleranceData.numberOfSubstancesInChart,
                    isTimeRelative = isTimeRelative
                )
            }

            if (toleranceData.substancesInIngestionsButNotChart.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Substances without tolerance data: ${toleranceData.substancesInIngestionsButNotChart.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            CardWithTitle(title = "Chart Limitations") {
                Text(
                    text = "This chart shows estimated tolerance windows based on general guidelines. " +
                            "Individual tolerance varies significantly based on genetics, frequency of use, " +
                            "and other factors. The data is approximate and should not be used as medical advice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = sinceDate.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onChangeSinceDate(Instant.ofEpochMilli(millis))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun ToleranceChart(
    toleranceWindows: List<ToleranceWindow>,
    numberOfRows: Int,
    isTimeRelative: Boolean
) {
    val isDarkTheme = isSystemInDarkTheme()

    if (toleranceWindows.isEmpty()) return

    val now = Instant.now()
    val minTime = toleranceWindows.minOfOrNull { it.start } ?: now
    val maxTime = toleranceWindows.maxOfOrNull { it.end } ?: now
    val totalDuration = Duration.between(minTime, maxTime).toMillis().coerceAtLeast(1L)

    val substanceNames = toleranceWindows.map { it.substanceName }.distinct()
    val rowHeight = when {
        numberOfRows < 4 -> 55.dp
        numberOfRows < 7 -> 50.dp
        else -> 45.dp
    }
    val chartHeight = rowHeight * numberOfRows

    Column(modifier = Modifier.fillMaxWidth()) {
        // Chart
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val singleRowHeight = canvasHeight / numberOfRows

                toleranceWindows.forEach { window ->
                    val substanceIndex = substanceNames.indexOf(window.substanceName)
                    if (substanceIndex >= 0) {
                        val startX = ((window.start.toEpochMilli() - minTime.toEpochMilli()).toFloat() / totalDuration) * canvasWidth
                        val endX = ((window.end.toEpochMilli() - minTime.toEpochMilli()).toFloat() / totalDuration) * canvasWidth
                        val topY = substanceIndex * singleRowHeight
                        val barWidth = (endX - startX).coerceAtLeast(1f)

                        drawRect(
                            color = window.barColor,
                            topLeft = Offset(startX, topY + 4),
                            size = Size(barWidth, singleRowHeight - 8)
                        )
                    }
                }

                // Draw current time line
                val nowX = ((now.toEpochMilli() - minTime.toEpochMilli()).toFloat() / totalDuration) * canvasWidth
                if (nowX in 0f..canvasWidth) {
                    drawLine(
                        color = if (isDarkTheme) Color.White else Color.Black,
                        start = Offset(nowX, 0f),
                        end = Offset(nowX, canvasHeight),
                        strokeWidth = 2f
                    )
                }
            }

            // Substance labels
            Column(modifier = Modifier.fillMaxSize()) {
                substanceNames.forEach { name ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .padding(start = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Time labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isTimeRelative) {
                    getRelativeTimeLabel(minTime)
                } else {
                    minTime.getStringOfPattern("MMM d")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (isTimeRelative) "Now" else now.getStringOfPattern("MMM d"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (isTimeRelative) {
                    getRelativeTimeLabel(maxTime)
                } else {
                    maxTime.getStringOfPattern("MMM d")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getRelativeTimeLabel(time: Instant): String {
    val now = Instant.now()
    val duration = Duration.between(time, now)
    
    return when {
        duration.isNegative -> {
            val absDuration = duration.abs()
            when {
                absDuration.toDays() > 0 -> "in ${absDuration.toDays()}d"
                absDuration.toHours() > 0 -> "in ${absDuration.toHours()}h"
                else -> "in ${absDuration.toMinutes()}m"
            }
        }
        duration.toDays() > 0 -> "${duration.toDays()}d ago"
        duration.toHours() > 0 -> "${duration.toHours()}h ago"
        else -> "${duration.toMinutes()}m ago"
    }
}

@Preview
@Composable
fun ToleranceChartScreenPreview() {
    ToleranceChartScreenContent(
        toleranceData = ToleranceData(
            toleranceWindows = emptyList(),
            numberOfSubstancesInChart = 0,
            substancesInIngestionsButNotChart = listOf("DMT", "2C-B"),
            substanceCompanions = emptyList()
        ),
        sinceDate = Instant.now().minus(Duration.ofDays(90)),
        isTimeRelative = false,
        onChangeSinceDate = {},
        onChangeIsTimeRelative = {},
        onAddTap = {}
    )
}
