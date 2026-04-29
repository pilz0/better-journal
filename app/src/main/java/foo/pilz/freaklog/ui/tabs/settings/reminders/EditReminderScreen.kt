/*
 * Copyright (c) 2026. FreakLog contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package foo.pilz.freaklog.ui.tabs.settings.reminders

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.hilt.navigation.compose.hiltViewModel
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.scheduled.ReminderSchedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditReminderScreen(
    navigateBack: () -> Unit,
    viewModel: EditReminderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "Add reminder" else "Edit reminder") },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save(navigateBack) }) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Enabled",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = state.isEnabled, onCheckedChange = viewModel::setEnabled)
            }

            ScheduleTypePicker(
                current = state.scheduleType,
                onChange = viewModel::setScheduleType,
            )

            when (state.scheduleType) {
                ReminderSchedule.SCHEDULE_TYPE_INTERVAL -> IntervalEditor(
                    intervalValue = state.intervalValue,
                    intervalUnit = state.intervalUnit,
                    onValueChange = viewModel::setIntervalValue,
                    onUnitChange = viewModel::setIntervalUnit,
                )

                else -> TimesOfDayEditor(
                    times = state.timesOfDay,
                    onAdd = viewModel::addTime,
                    onRemove = viewModel::removeTime,
                )
            }

            DaysOfWeekEditor(
                mask = state.daysOfWeekMask,
                onToggle = viewModel::toggleDay,
            )

            EndDatePicker(
                endEpochMillis = state.endEpochMillis,
                onChange = viewModel::setEndDate,
            )

            SectionLabel("Substance (optional)")
            Text(
                "If you set a substance, dose and route, the notification gets a one-tap “Take” action that adds an ingestion to your journal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.substanceName,
                onValueChange = viewModel::setSubstanceName,
                label = { Text("Substance name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = state.dose,
                    onValueChange = viewModel::setDose,
                    label = { Text("Dose") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.units,
                    onValueChange = viewModel::setUnits,
                    label = { Text("Units") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            AdministrationRoutePicker(
                current = state.administrationRoute,
                onChange = viewModel::setAdministrationRoute,
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = { viewModel.test() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Test now")
                }
                Button(
                    onClick = { viewModel.save(navigateBack) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleTypePicker(current: String, onChange: (String) -> Unit) {
    val options = listOf(
        ReminderSchedule.SCHEDULE_TYPE_DAILY_AT_TIMES to "Times of day",
        ReminderSchedule.SCHEDULE_TYPE_INTERVAL to "Every X",
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = current == value,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(label)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TimesOfDayEditor(
    times: List<LocalTime>,
    onAdd: (LocalTime) -> Unit,
    onRemove: (LocalTime) -> Unit,
) {
    val context = LocalContext.current
    val is24h = DateFormat.is24HourFormat(context)
    SectionLabel("Times of day")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        times.forEach { time ->
            InputChip(
                selected = true,
                onClick = {
                    showTimePicker(context, is24h, time) { picked ->
                        if (picked != time) {
                            onAdd(picked)
                            onRemove(time)
                        }
                    }
                },
                label = { Text("%02d:%02d".format(time.hour, time.minute)) },
                trailingIcon = {
                    IconButton(
                        onClick = { onRemove(time) },
                        modifier = Modifier.size(20.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove time",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                },
            )
        }
        AssistChip(
            onClick = {
                val now = LocalTime.now()
                showTimePicker(context, is24h, now, onAdd)
            },
            label = { Text("Add time") },
            leadingIcon = {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
        )
    }
    if (times.isEmpty()) {
        Text(
            "Add at least one time of day or this reminder will never fire.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun IntervalEditor(
    intervalValue: Int,
    intervalUnit: String,
    onValueChange: (Int) -> Unit,
    onUnitChange: (String) -> Unit,
) {
    SectionLabel("Repeat every")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = intervalValue.toString(),
            onValueChange = { input ->
                input.toIntOrNull()?.let(onValueChange)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        UnitDropdown(intervalUnit, onUnitChange)
    }
}

@Composable
private fun UnitDropdown(current: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }) {
            Text(current)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("Minutes", "Hours", "Days").forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit) },
                    onClick = {
                        onChange(unit)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DaysOfWeekEditor(mask: Int, onToggle: (DayOfWeek) -> Unit) {
    SectionLabel("Days")
    val days = listOf(
        DayOfWeek.MONDAY to "Mon",
        DayOfWeek.TUESDAY to "Tue",
        DayOfWeek.WEDNESDAY to "Wed",
        DayOfWeek.THURSDAY to "Thu",
        DayOfWeek.FRIDAY to "Fri",
        DayOfWeek.SATURDAY to "Sat",
        DayOfWeek.SUNDAY to "Sun",
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        days.forEach { (day, label) ->
            FilterChip(
                selected = ReminderSchedule.isDayAllowed(day, mask),
                onClick = { onToggle(day) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun EndDatePicker(endEpochMillis: Long?, onChange: (Long?) -> Unit) {
    val context = LocalContext.current
    SectionLabel("End date (optional)")
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = {
                val cal = Calendar.getInstance().apply {
                    if (endEpochMillis != null) {
                        // endEpochMillis is the start of the day AFTER the chosen end date,
                        // so subtract a millisecond to land on the chosen day.
                        timeInMillis = endEpochMillis - 1
                    }
                }
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        // Store the cutoff as the start of the day AFTER the selected one
                        // so the entire chosen day is included by the (inclusive) scheduler.
                        val picked = LocalDate.of(year, month + 1, day)
                            .plusDays(1)
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                        onChange(picked)
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH),
                ).show()
            },
        ) {
            val label = endEpochMillis?.let {
                // The cutoff is the start of the day after the chosen end date, so subtract
                // one day's worth of millis to get the human-meaningful "end day".
                DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(it - 86_400_000L))
            } ?: "No end"
            Text(label)
        }
        if (endEpochMillis != null) {
            TextButton(onClick = { onChange(null) }) { Text("Clear") }
        }
    }
}

@Composable
private fun AdministrationRoutePicker(current: String?, onChange: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    SectionLabel("Route (optional)")
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { expanded = true }) {
            val label = current?.let { name ->
                runCatching { AdministrationRoute.valueOf(name).displayText }.getOrNull() ?: name
            } ?: "Choose route"
            Text(label)
        }
        if (current != null) {
            TextButton(onClick = { onChange(null) }) { Text("Clear") }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AdministrationRoute.entries.forEach { route ->
                DropdownMenuItem(
                    text = { Text(route.displayText) },
                    onClick = {
                        onChange(route.name)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 4.dp),
    )
}

private fun showTimePicker(
    context: android.content.Context,
    is24h: Boolean,
    initial: LocalTime,
    onPicked: (LocalTime) -> Unit,
) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) },
        initial.hour,
        initial.minute,
        is24h,
    ).show()
}
