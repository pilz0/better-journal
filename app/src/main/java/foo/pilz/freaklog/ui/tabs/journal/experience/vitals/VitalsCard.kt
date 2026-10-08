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

package foo.pilz.freaklog.ui.tabs.journal.experience.vitals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import foo.pilz.freaklog.data.room.experiences.entities.BloodPressureReading
import foo.pilz.freaklog.ui.tabs.journal.experience.components.CardWithTitle
import foo.pilz.freaklog.ui.utils.getShortTimeWithWeekdayText

/** Blood pressure readings and (when enabled) Health Connect heart rate for one experience. */
@Composable
fun VitalsCard(experienceId: Int, viewModel: VitalsViewModel = hiltViewModel()) {
    LaunchedEffect(experienceId) { viewModel.setExperience(experienceId) }
    val readings = viewModel.readingsFlow.collectAsState().value
    val heartRate = viewModel.heartRateSamples.collectAsState().value
    var isAdding by remember { mutableStateOf(false) }
    var readingBeingEdited by remember { mutableStateOf<BloodPressureReading?>(null) }

    CardWithTitle(title = "Vitals") {
        summarizeHeartRate(heartRate)?.let { summary ->
            Text(
                "Heart rate ${summary.min}–${summary.max} bpm, average ${summary.average}",
                style = MaterialTheme.typography.bodyMedium,
            )
            HeartRateChart(heartRate, Modifier.fillMaxWidth().height(70.dp).padding(vertical = 6.dp))
        }
        readings.forEach { reading ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { readingBeingEdited = reading }
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    "${reading.systolic}/${reading.diastolic} mmHg" +
                        (reading.pulse?.let { " · $it bpm" } ?: ""),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    reading.time.getShortTimeWithWeekdayText() + " · " +
                        bloodPressureCategory(reading.systolic, reading.diastolic),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedButton(onClick = { isAdding = true }, Modifier.fillMaxWidth()) { Text("Add blood pressure") }
    }

    if (isAdding || readingBeingEdited != null) {
        val existing = readingBeingEdited
        BloodPressureDialog(
            existing = existing,
            onSave = { systolic, diastolic, pulse ->
                viewModel.saveReading(existing, systolic, diastolic, pulse)
                isAdding = false
                readingBeingEdited = null
            },
            onDelete = {
                existing?.let(viewModel::deleteReading)
                readingBeingEdited = null
            },
            onDismiss = {
                isAdding = false
                readingBeingEdited = null
            },
        )
    }
}

@Composable
private fun HeartRateChart(samples: List<HeartRateSample>, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.error
    Canvas(modifier) {
        if (samples.size < 2) return@Canvas
        val startMillis = samples.first().time.toEpochMilli()
        val spanMillis = (samples.last().time.toEpochMilli() - startMillis).coerceAtLeast(1)
        val minBpm = samples.minOf { it.bpm }
        val spanBpm = (samples.maxOf { it.bpm } - minBpm).coerceAtLeast(1)
        val path = Path()
        samples.forEachIndexed { index, sample ->
            val point = Offset(
                x = (sample.time.toEpochMilli() - startMillis).toFloat() / spanMillis * size.width,
                y = size.height - (sample.bpm - minBpm).toFloat() / spanBpm * size.height,
            )
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun BloodPressureDialog(
    existing: BloodPressureReading?,
    onSave: (systolic: String, diastolic: String, pulse: String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var systolic by remember { mutableStateOf(existing?.systolic?.toString().orEmpty()) }
    var diastolic by remember { mutableStateOf(existing?.diastolic?.toString().orEmpty()) }
    var pulse by remember { mutableStateOf(existing?.pulse?.toString().orEmpty()) }
    val errors = bloodPressureFieldErrors(systolic, diastolic, pulse)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add blood pressure" else "Edit blood pressure") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberField("Systolic (mmHg)", systolic, errors.systolic) { systolic = it }
                NumberField("Diastolic (mmHg)", diastolic, errors.diastolic) { diastolic = it }
                NumberField("Pulse (bpm, optional)", pulse, errors.pulse) { pulse = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(systolic, diastolic, pulse) },
                enabled = isBloodPressureInputValid(systolic, diastolic, pulse),
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun NumberField(label: String, value: String, error: String?, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
