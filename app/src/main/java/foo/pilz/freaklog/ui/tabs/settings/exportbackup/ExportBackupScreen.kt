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

package foo.pilz.freaklog.ui.tabs.settings.exportbackup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import foo.pilz.freaklog.data.export.ExportFilter
import foo.pilz.freaklog.data.report.ReportPeriod
import foo.pilz.freaklog.data.report.ReportSettings
import foo.pilz.freaklog.data.report.ReportStyle
import foo.pilz.freaklog.data.substances.classes.IngestionCategory
import foo.pilz.freaklog.ui.components.NavigateBackButton
import foo.pilz.freaklog.ui.theme.horizontalPadding
import foo.pilz.freaklog.ui.utils.getStringOfPattern
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
fun ExportBackupScreen(viewModel: ExportBackupViewModel = hiltViewModel()) {
  val filter = viewModel.exportFilterFlow.collectAsStateWithLifecycle().value
  val usedSubstanceNames = viewModel.usedSubstanceNamesFlow.collectAsStateWithLifecycle().value
  val exportPassphrase = viewModel.exportPassphrase.collectAsStateWithLifecycle().value
  val pendingEncryptedImport = viewModel.pendingEncryptedImport.collectAsStateWithLifecycle().value
  val backupDirUri = viewModel.backupDirUriFlow.collectAsStateWithLifecycle().value
  val lastBackupTime = viewModel.lastBackupTimeFlow.collectAsStateWithLifecycle().value
  val reportSettings = viewModel.reportSettingsFlow.collectAsStateWithLifecycle().value

  var showFilterControls by remember { mutableStateOf(filter.isActive) }
  var showSubstanceDialog by remember { mutableStateOf(false) }
  var importToConfirm by remember { mutableStateOf<Uri?>(null) }
  var backupFolderToSetUp by remember { mutableStateOf<Uri?>(null) }

  val exportName = "Journal ${Instant.now().getStringOfPattern("dd MMM yyyy")}"
  val launcherEncrypted =
      rememberLauncherForActivityResult(
          ActivityResultContracts.CreateDocument("application/octet-stream")
      ) { uri ->
        uri?.let(viewModel::exportEncryptedFile)
      }
  val launcherPlaintext =
      rememberLauncherForActivityResult(
          ActivityResultContracts.CreateDocument("application/json")
      ) { uri ->
        uri?.let(viewModel::exportPlaintextFile)
      }
  val launcherImport =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        importToConfirm = uri
      }
  val substanceCollision = viewModel.substanceCollision.collectAsStateWithLifecycle().value
  val launcherSubstance =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importSubstanceFile)
      }
  val launcherBackupFolder =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        backupFolderToSetUp = uri
      }

  Scaffold(
      topBar = {
        TopAppBar(
            navigationIcon = { NavigateBackButton() },
            title = { Text("Export, import & backup") },
        )
      },
      snackbarHost = { SnackbarHost(viewModel.snackbarHostState) },
  ) { padding ->
    Column(
        modifier =
            Modifier.padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      SectionCard("Export") {
        Button(onClick = { launcherEncrypted.launch("$exportName.enc") }, Modifier.fillMaxWidth()) {
          Text("Export encrypted")
        }
        OutlinedButton(
            onClick = { launcherPlaintext.launch("$exportName.json") },
            Modifier.fillMaxWidth(),
        ) {
          Text("Export plaintext (JSON)")
        }
        HorizontalDivider()
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable { showFilterControls = !showFilterControls }
                    .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Filter what to export", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (filter.isActive) "Filtered subset active" else "Exporting all data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Icon(
              imageVector =
                  if (showFilterControls) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
              contentDescription = if (showFilterControls) "Hide filters" else "Show filters",
          )
        }
        AnimatedVisibility(visible = showFilterControls) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportFilterControls(
                filter = filter,
                setFilter = viewModel::setExportFilter,
                usedSubstanceCount = usedSubstanceNames.size,
                onPickSubstances = { showSubstanceDialog = true },
            )
          }
        }
      }
      SectionCard("Import") {
        Text(
            "Importing a journal file replaces everything currently in the app. Plaintext (.json) " +
                "and encrypted (.enc) exports are both accepted.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = { launcherImport.launch(arrayOf("*/*")) },
            Modifier.fillMaxWidth(),
        ) {
          Text("Import journal file")
        }
        HorizontalDivider()
        Text(
            "Adds a custom substance from a shared substance file, including its doses, durations " +
                "and interactions.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(
            onClick = { launcherSubstance.launch(arrayOf("*/*")) },
            Modifier.fillMaxWidth(),
        ) {
          Text("Import custom substance")
        }
      }
      SectionCard("Automatic backups") {
        if (backupDirUri == null) {
          Text(
              "Writes an encrypted full export to a folder you choose once a day and keeps the " +
                  "newest 7.",
              style = MaterialTheme.typography.bodySmall,
          )
          Button(onClick = { launcherBackupFolder.launch(null) }, Modifier.fillMaxWidth()) {
            Text("Choose backup folder")
          }
        } else {
          Text(
              "Last backup: " +
                  (lastBackupTime?.getStringOfPattern("dd MMM yyyy HH:mm") ?: "none yet"),
              style = MaterialTheme.typography.bodyMedium,
          )
          Button(onClick = viewModel::backupNow, Modifier.fillMaxWidth()) { Text("Back up now") }
          OutlinedButton(onClick = viewModel::disableAutomaticBackups, Modifier.fillMaxWidth()) {
            Text("Turn off automatic backups")
          }
        }
      }
      SectionCard("Report for a doctor or therapist") {
        ClinicianReportControls(
            settings = reportSettings,
            setSettings = viewModel::setReportSettings,
            onSharePdf = viewModel::shareReportPdf,
            onShareText = viewModel::shareReportText,
        )
      }
    }
  }

  if (substanceCollision != null) {
    AlertDialog(
        onDismissRequest = viewModel::dismissSubstanceCollision,
        title = { Text("${substanceCollision.name} already exists") },
        text = {
          Text("Replace the existing custom substance, or keep both under different names?")
        },
        confirmButton = {
          TextButton(onClick = { viewModel.resolveSubstanceCollision(replace = false) }) {
            Text("Keep both")
          }
        },
        dismissButton = {
          Row {
            TextButton(onClick = viewModel::dismissSubstanceCollision) { Text("Cancel") }
            TextButton(onClick = { viewModel.resolveSubstanceCollision(replace = true) }) {
              Text("Replace")
            }
          }
        },
    )
  }
  if (showSubstanceDialog) {
    SubstanceFilterDialog(
        allNames = usedSubstanceNames,
        selected = filter.substanceNames,
        onConfirm = {
          viewModel.setExportFilter(filter.copy(substanceNames = it))
          showSubstanceDialog = false
        },
        onDismiss = { showSubstanceDialog = false },
    )
  }
  importToConfirm?.let { uri ->
    AlertDialog(
        onDismissRequest = { importToConfirm = null },
        title = { Text("Replace all journal data?") },
        text = {
          Text(
              "Everything currently in the journal is deleted and replaced by the file's contents."
          )
        },
        confirmButton = {
          TextButton(
              onClick = {
                importToConfirm = null
                viewModel.handleImportFile(uri)
              }
          ) {
            Text("Import")
          }
        },
        dismissButton = { TextButton(onClick = { importToConfirm = null }) { Text("Cancel") } },
    )
  }
  if (pendingEncryptedImport != null) {
    PasswordDialog(
        title = "Encrypted file",
        description = "Enter the passphrase this file was encrypted with.",
        confirmLabel = "Import",
        onConfirm = viewModel::importWithPassphrase,
        onDismiss = viewModel::clearPendingImport,
    )
  }
  backupFolderToSetUp?.let { uri ->
    PasswordDialog(
        title = "Backup password",
        description =
            "Backups are encrypted with this password. It cannot be recovered, so store it " +
                "somewhere safe.",
        confirmLabel = "Turn on",
        onConfirm = {
          backupFolderToSetUp = null
          viewModel.enableAutomaticBackups(uri, it)
        },
        onDismiss = { backupFolderToSetUp = null },
    )
  }
  if (exportPassphrase != null) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Save this passphrase") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("The export cannot be opened without it, and it is not shown again.")
            SelectionContainer {
              Text(exportPassphrase, fontFamily = FontFamily.Monospace)
            }
          }
        },
        confirmButton = {
          TextButton(onClick = viewModel::clearExportPassphrase) { Text("I saved it") }
        },
    )
  }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
  ElevatedCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      content()
    }
  }
}

@Composable
private fun ExportFilterControls(
    filter: ExportFilter,
    setFilter: (ExportFilter) -> Unit,
    usedSubstanceCount: Int,
    onPickSubstances: () -> Unit,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FilterChip(
        selected = filter.category == null,
        onClick = { setFilter(filter.copy(category = null)) },
        label = { Text("All") },
    )
    IngestionCategory.entries.forEach { category ->
      FilterChip(
          selected = filter.category == category,
          onClick = { setFilter(filter.copy(category = category)) },
          label = { Text(category.displayText) },
      )
    }
  }
  OutlinedButton(onClick = onPickSubstances, Modifier.fillMaxWidth()) {
    Text(
        when (val selected = filter.substanceNames) {
          null -> "All substances"
          else -> "${selected.size} of $usedSubstanceCount substances"
        }
    )
  }
  SwitchRow("Include other consumers", filter.includeOtherConsumers) {
    setFilter(filter.copy(includeOtherConsumers = it))
  }
  SwitchRow("Include notes", filter.includeNotes) { setFilter(filter.copy(includeNotes = it)) }
  SwitchRow("Include timed notes", filter.includeTimedNotes) {
    setFilter(filter.copy(includeTimedNotes = it))
  }
  SwitchRow("Include locations", filter.includeLocations) {
    setFilter(filter.copy(includeLocations = it))
  }
  SwitchRow("Include ratings", filter.includeRatings) {
    setFilter(filter.copy(includeRatings = it))
  }
  if (filter.isActive) {
    Text(
        "A filtered export leaves out reminders and webhooks.",
        style = MaterialTheme.typography.bodySmall,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ClinicianReportControls(
    settings: ReportSettings,
    setSettings: (ReportSettings) -> Unit,
    onSharePdf: () -> Unit,
    onShareText: () -> Unit,
) {
  var showRangePicker by remember { mutableStateOf(false) }
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    ReportStyle.entries.forEach { style ->
      FilterChip(
          selected = settings.style == style,
          onClick = { setSettings(settings.copy(style = style)) },
          label = { Text(style.label) },
      )
    }
  }
  Text(settings.style.description, style = MaterialTheme.typography.bodySmall)
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    ReportPeriod.entries.forEach { period ->
      FilterChip(
          selected = settings.period == period,
          onClick = {
            if (period == ReportPeriod.CUSTOM) {
              showRangePicker = true
            } else {
              setSettings(settings.copy(period = period))
            }
          },
          label = {
            val from = settings.customFrom
            val to = settings.customTo
            Text(
                if (period == ReportPeriod.CUSTOM && from != null && to != null) {
                  val fmt = customRangeFormat()
                  "${from.format(fmt)} – ${to.format(fmt)}"
                } else {
                  period.label
                }
            )
          },
      )
    }
  }
  OutlinedTextField(
      value = settings.patientName,
      onValueChange = { setSettings(settings.copy(patientName = it.take(100))) },
      label = { Text("Name on report (optional)") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
  )
  Button(onClick = onSharePdf, Modifier.fillMaxWidth()) { Text("Share PDF") }
  OutlinedButton(onClick = onShareText, Modifier.fillMaxWidth()) { Text("Share as text") }
  Text(
      "Uses the export filters above. Only your own ingestions are included.",
      style = MaterialTheme.typography.bodySmall,
  )

  if (showRangePicker) {
    ReportRangeDialog(
        settings = settings,
        onConfirm = { from, to ->
          setSettings(settings.copy(period = ReportPeriod.CUSTOM, customFrom = from, customTo = to))
          showRangePicker = false
        },
        onDismiss = { showRangePicker = false },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportRangeDialog(
    settings: ReportSettings,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
  val state =
      rememberDateRangePickerState(
          initialSelectedStartDateMillis = settings.customFrom?.toUtcMillis(),
          initialSelectedEndDateMillis = settings.customTo?.toUtcMillis(),
      )
  DatePickerDialog(
      onDismissRequest = onDismiss,
      confirmButton = {
        TextButton(
            onClick = {
              val start = state.selectedStartDateMillis?.toUtcDate() ?: return@TextButton
              onConfirm(start, state.selectedEndDateMillis?.toUtcDate() ?: start)
            },
            enabled = state.selectedStartDateMillis != null,
        ) {
          Text("Done")
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  ) {
    DateRangePicker(state = state, modifier = Modifier.heightIn(max = 520.dp))
  }
}

private fun customRangeFormat(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yy", Locale.getDefault())

// The Material date pickers work in UTC midnight millis.
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun SwitchRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(text)
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}

@Composable
private fun SubstanceFilterDialog(
    allNames: List<String>,
    selected: Set<String>?,
    onConfirm: (Set<String>?) -> Unit,
    onDismiss: () -> Unit,
) {
  var current by remember { mutableStateOf(selected ?: allNames.toSet()) }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("Substances to export") },
      text = {
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
          items(allNames, key = { it }) { name ->
            Row(
                modifier =
                    Modifier.fillMaxWidth().clickable {
                      current = if (name in current) current - name else current + name
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Checkbox(checked = name in current, onCheckedChange = null)
              Text(name, Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp))
            }
          }
        }
      },
      confirmButton = {
        // Selecting everything is the same as not filtering by substance at all.
        TextButton(onClick = { onConfirm(current.takeIf { it.size < allNames.size }) }) {
          Text("Done")
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun PasswordDialog(
    title: String,
    description: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
  val passwordState = rememberTextFieldState()
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(title) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(description)
          OutlinedSecureTextField(
              state = passwordState,
              textObfuscationMode = TextObfuscationMode.Hidden,
              label = { Text("Passphrase") },
          )
        }
      },
      confirmButton = {
        TextButton(
            onClick = { onConfirm(passwordState.text.toString()) },
            enabled = passwordState.text.isNotBlank(),
        ) {
          Text(confirmLabel)
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
