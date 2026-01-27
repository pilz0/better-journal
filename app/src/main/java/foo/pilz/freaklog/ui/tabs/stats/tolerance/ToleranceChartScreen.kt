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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
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
import java.time.temporal.ChronoUnit

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
    var showLimitationsSheet by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
           // If we want a large title, we might put it in the scrollable content or use LargeTopAppBar.
           // For now keeping TopAppBar but maybe we should center or customize.
           // User screenshot shows "Tolerance" as a large header.
           TopAppBar(
               title = {
                   Text(
                       text = "Tolerance",
                       style = MaterialTheme.typography.displaySmall // Larger title
                   )
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
            // Settings Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showDatePicker = true }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Start Date",
                    style = MaterialTheme.typography.bodyLarge
                )
                val dateFormatter = DateTimeFormatter.ofPattern("dd. MM. yyyy")
                val localDate = sinceDate.atZone(ZoneId.systemDefault()).toLocalDate()
                Text(
                    text = localDate.format(dateFormatter),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Relative time",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = isTimeRelative,
                    onCheckedChange = onChangeIsTimeRelative
                )
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
                    isTimeRelative = isTimeRelative,
                    sinceDate = sinceDate
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Excluding 2C-B, Bupropion, Ibuprofen, Progesterone, Methylphenidate, 4-MMC, 5-Hydroxytryptophan, Paracetamol, Ketamine, N-Acetylcysteine, and Lisdexamfetamine because of missing tolerance info",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            InfoRow(
                title = "Chart Limitations",
                onClick = { showLimitationsSheet = true }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            InfoRow(
                title = "Tolerance Info",
                onClick = { showInfoSheet = true }
            )

            Spacer(modifier = Modifier.height(32.dp))
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
    
    if (showLimitationsSheet) {
        ChartLimitationsSheet(
            onDismissRequest = { showLimitationsSheet = false }
        )
    }

    if (showInfoSheet) {
        ToleranceInfoSheet(
            onDismissRequest = { showInfoSheet = false }
        )
    }
}

@Composable
fun InfoRow(
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Info, // Or a specific icon if needed
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ToleranceChart(
    toleranceWindows: List<ToleranceWindow>,
    isTimeRelative: Boolean,
    sinceDate: Instant
) {
    val isDarkTheme = isSystemInDarkTheme()

    if (toleranceWindows.isEmpty()) return

    val now = Instant.now()
    val minTime = sinceDate
    val maxTimeInWindows = toleranceWindows.maxOfOrNull { it.end } ?: now
    val maxTime = if (maxTimeInWindows > now) maxTimeInWindows else now
    
    val totalDuration = Duration.between(minTime, maxTime).toMillis().coerceAtLeast(1L)
    val nowMillis = now.toEpochMilli()
    val minTimeMillis = minTime.toEpochMilli()

    // Distinct substances, sorted if needed. The input list toleranceWindows contains all windows.
    // We need to know which substance goes to which row.
    // The view model seems to provide them sorted by name or so.
    val substanceNames = toleranceWindows.map { it.substanceName }.distinct().sorted()
    val numberOfRows = substanceNames.size
    
    val rowHeight = 32.dp
    val chartHeight = rowHeight * numberOfRows

    Column(modifier = Modifier.fillMaxWidth()) {
        
        Row(modifier = Modifier.fillMaxWidth()) {
            // Substance Labels Column
            Column(
                modifier = Modifier
                    .width(100.dp) // Fixed width for labels
                    .padding(end = 8.dp)
            ) {
                substanceNames.forEach { name ->
                    Box(
                        modifier = Modifier.height(rowHeight),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            // Chart Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(chartHeight)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val singleRowHeight = canvasHeight / numberOfRows

                    // Draw vertical grid lines (every 7 days? Or monthly?)
                    // Screenshot shows monthly/weekly lines. Let's stick to weekly for now or calculate roughly.
                    val oneWeekMillis = Duration.ofDays(7).toMillis()
                    var currentGridTime = minTimeMillis
                    
                    // Alleviate start offset to align with week boundaries if possible, but simple fixed step is fine for now
                    while (currentGridTime <= maxTime.toEpochMilli()) {
                         val gridX = ((currentGridTime - minTimeMillis).toFloat() / totalDuration) * canvasWidth
                         if (gridX in 0f..canvasWidth) {
                             drawLine(
                                color = if (isDarkTheme) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f),
                                start = Offset(gridX, 0f),
                                end = Offset(gridX, canvasHeight),
                                strokeWidth = 1f
                             )
                         }
                         currentGridTime += oneWeekMillis
                    }

                    toleranceWindows.forEach { window ->
                        val substanceIndex = substanceNames.indexOf(window.substanceName)
                        if (substanceIndex >= 0) {
                            val startMillis = window.start.toEpochMilli().coerceAtLeast(minTimeMillis)
                            val endMillis = window.end.toEpochMilli()
                            
                            if (endMillis > minTimeMillis) {
                                val startX = ((startMillis - minTimeMillis).toFloat() / totalDuration) * canvasWidth
                                val endX = ((endMillis - minTimeMillis).toFloat() / totalDuration) * canvasWidth
                                val topY = substanceIndex * singleRowHeight
                                val barWidth = (endX - startX).coerceAtLeast(2f) // Minimum visible width

                                // Bar height slightly smaller than row
                                val barHeight = singleRowHeight.toDp().toPx() * 0.6f
                                val barTopOffset = (singleRowHeight - barHeight) / 2

                                drawRoundRect(
                                    color = window.barColor,
                                    topLeft = Offset(startX, topY + barTopOffset),
                                    size = Size(barWidth, barHeight),
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                )
                            }
                        }
                    }

                    // Draw current time line
                    val nowX = ((nowMillis - minTimeMillis).toFloat() / totalDuration) * canvasWidth
                    if (nowX in 0f..canvasWidth) {
                        drawLine(
                            color = Color.White, // Strong white as in screenshot
                            start = Offset(nowX, 0f),
                            end = Offset(nowX, canvasHeight),
                            strokeWidth = 2f
                        )
                    }
                }
            }
        }

        // Time labels below chart
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 100.dp) // Align with chart start
        ) {
              Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 4.dp)
                ) {
                    Text(
                        text = if (isTimeRelative) getRelativeTimeLabel(minTime) else minTime.getStringOfPattern("MMM d"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )

                    // We could add more intermediate labels if needed
                    
                    Text(
                        text = if (isTimeRelative) getRelativeTimeLabel(maxTime) else maxTime.getStringOfPattern("MMM d"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
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
    val now = Instant.now()
    val lsdWindow = ToleranceWindow(
        substanceName = "Cocaine",
        start = now.minus(10, ChronoUnit.DAYS),
        end = now.plus(4, ChronoUnit.DAYS),
        toleranceType = ToleranceType.FULL,
        substanceColor = Color(0xFFFF453A) // Red-ish color
    )
    val mushroomsWindow = ToleranceWindow(
        substanceName = "Mephedrone",
        start = now.minus(2, ChronoUnit.DAYS),
        end = now.plus(5, ChronoUnit.DAYS),
        toleranceType = ToleranceType.HALF,
        substanceColor = Color(0xFF32D74B) // Green-ish color
    )

    ToleranceChartScreenContent(
        toleranceData = ToleranceData(
            toleranceWindows = listOf(lsdWindow,mushroomsWindow),
            numberOfSubstancesInChart = 2,
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
