/*
 * Copyright (c) 2026. FreakLog contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package foo.pilz.freaklog.ui.tabs.settings.reminders

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import foo.pilz.freaklog.scheduled.ReminderSchedule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    navigateBack: () -> Unit,
    navigateToEdit: (Int) -> Unit,
    viewModel: RemindersViewModel = hiltViewModel(),
) {
    val reminders by viewModel.reminders.collectAsState()
    val context = LocalContext.current
    var canExact by remember { mutableStateOf(viewModel.canScheduleExactAlarms()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // Re-check the exact-alarm permission whenever the screen resumes (e.g. after the
        // user comes back from the system settings page) so the banner clears without a
        // wasteful polling loop.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canExact = viewModel.canScheduleExactAlarms()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PostNotificationsPermissionRequester()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reminders") },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Add reminder") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = { navigateToEdit(-1) },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (!canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ExactAlarmBanner(
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    }
                )
            }
            if (reminders.isEmpty()) {
                EmptyState(onAdd = { navigateToEdit(-1) })
            } else {
                ReminderList(
                    reminders = reminders,
                    onEdit = { navigateToEdit(it.id) },
                    onToggle = viewModel::toggleReminder,
                    onDelete = viewModel::deleteReminder,
                    onDuplicate = viewModel::duplicateReminder,
                )
            }
        }
    }
}

@Composable
private fun ExactAlarmBanner(onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(
                MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            Text(
                "Exact alarms not allowed",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Reminders may fire late or not at all. Grant the permission so they fire on time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onOpenSettings) {
                Text("Open settings")
            }
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Outlined.NotificationsOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "No reminders yet",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "Add a reminder to get notified when it's time to take a substance.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.size(4.dp))
                Text("Add reminder")
            }
        }
    }
}

@Composable
private fun ReminderList(
    reminders: List<Reminder>,
    onEdit: (Reminder) -> Unit,
    onToggle: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit,
    onDuplicate: (Reminder) -> Unit,
) {
    val (active, inactive) = reminders.partition { it.isEnabled }
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (active.isNotEmpty()) {
            item {
                SectionHeader("Active")
            }
            items(active, key = { "active-${it.id}" }) {
                ReminderRow(it, onEdit, onToggle, onDelete, onDuplicate)
            }
        }
        if (inactive.isNotEmpty()) {
            item { Spacer(Modifier.height(8.dp)) }
            item { SectionHeader("Disabled") }
            items(inactive, key = { "inactive-${it.id}" }) {
                ReminderRow(it, onEdit, onToggle, onDelete, onDuplicate)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun ReminderRow(
    reminder: Reminder,
    onEdit: (Reminder) -> Unit,
    onToggle: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit,
    onDuplicate: (Reminder) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onEdit(reminder) },
        colors = CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (reminder.isEnabled) Icons.Outlined.NotificationsActive
                    else Icons.Outlined.NotificationsOff,
                    contentDescription = null,
                    tint = if (reminder.isEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        reminder.title.ifBlank { reminder.substanceName ?: "Reminder" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        ReminderSchedule.summarize(reminder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val doseLine = doseSummary(reminder)
                    if (doseLine.isNotBlank()) {
                        Text(
                            doseLine,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (reminder.isEnabled) {
                        val now = System.currentTimeMillis()
                        val nextFire = ReminderSchedule.nextFireAt(reminder, now)
                        val countdown = nextFire?.let { humanizeCountdown(it - now) }
                            ?: "No upcoming fire"
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Next: $countdown",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Switch(
                    checked = reminder.isEnabled,
                    onCheckedChange = { onToggle(reminder) },
                )
                ReminderRowMenu(
                    reminder = reminder,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onDuplicate = onDuplicate,
                )
            }
        }
    }
}

@Composable
private fun ReminderRowMenu(
    reminder: Reminder,
    onEdit: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit,
    onDuplicate: (Reminder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEdit(reminder)
                },
            )
            DropdownMenuItem(
                text = { Text("Duplicate") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDuplicate(reminder)
                },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDelete(reminder)
                },
            )
        }
    }
}

private fun doseSummary(reminder: Reminder): String {
    val parts = mutableListOf<String>()
    val dose = reminder.dose
    if (dose != null) {
        val doseText = if (dose == dose.toLong().toDouble()) dose.toLong().toString() else dose.toString()
        val units = reminder.units?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        parts += "$doseText$units"
    }
    val substance = reminder.substanceName?.takeIf { it.isNotBlank() }
    if (substance != null) parts += substance
    val route = reminder.administrationRoute?.takeIf { it.isNotBlank() }
    if (route != null) parts += route.lowercase().replaceFirstChar { it.uppercase() }
    return parts.joinToString(" · ")
}

private fun humanizeCountdown(deltaMillis: Long): String {
    if (deltaMillis < 0) return "due now"
    val totalMinutes = deltaMillis / 60_000L
    if (totalMinutes < 1) return "in <1 min"
    if (totalMinutes < 60) return "in ${totalMinutes} min"
    val totalHours = totalMinutes / 60
    val mins = totalMinutes % 60
    if (totalHours < 24) {
        return if (mins == 0L) "in ${totalHours} h" else "in ${totalHours} h ${mins} m"
    }
    val days = totalHours / 24
    val hours = totalHours % 24
    return if (hours == 0L) "in ${days} d" else "in ${days} d ${hours} h"
}
