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

package foo.pilz.freaklog.ui.tabs.journal.experience.editingestion

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import foo.pilz.freaklog.data.room.experiences.entities.CustomUnit
import foo.pilz.freaklog.ui.YOU
import foo.pilz.freaklog.ui.components.NavigateBackButton
import foo.pilz.freaklog.ui.components.clearOfImeFab
import foo.pilz.freaklog.ui.tabs.journal.addingestion.dose.StandardDeviationExplanation
import foo.pilz.freaklog.ui.tabs.journal.addingestion.time.IngestionCategoryPicker
import foo.pilz.freaklog.ui.tabs.journal.addingestion.time.IngestionTimePickerOption
import foo.pilz.freaklog.ui.tabs.journal.addingestion.time.TimePointOrRangePicker
import foo.pilz.freaklog.ui.tabs.journal.experience.components.AdministrationSitePicker
import foo.pilz.freaklog.ui.tabs.journal.experience.components.CardWithTitle
import foo.pilz.freaklog.ui.tabs.journal.experience.components.SaltFormPicker
import foo.pilz.freaklog.ui.theme.JournalTheme
import foo.pilz.freaklog.ui.theme.horizontalPadding
import java.time.LocalDateTime
import kotlinx.coroutines.launch

@Composable
fun EditIngestionScreen(
    viewModel: EditIngestionViewModel = hiltViewModel(),
    navigateToAddIngestion: () -> Unit,
    navigateBack: () -> Unit,
) {
  EditIngestionScreen(
      doseUi =
          EditIngestionDoseUi(
              dose = viewModel.dose,
              onDoseChange = viewModel::onDoseChange,
              isKnown = viewModel.isKnown,
              toggleIsKnown = viewModel::toggleIsKnown,
              isEstimate = viewModel.isEstimate,
              onChangeIsEstimate = viewModel::onChangeIsEstimate,
              estimatedDoseStandardDeviation = viewModel.estimatedDoseStandardDeviation,
              onEstimatedDoseStandardDeviationChange =
                  viewModel::onChangeEstimatedDoseStandardDeviation,
          ),
      unitUi =
          EditIngestionUnitUi(
              units = viewModel.units,
              onUnitsChange = { viewModel.units = it },
              customUnit = viewModel.customUnit,
              onCustomUnitChange = viewModel::onChangeCustomUnit,
              otherCustomUnits = viewModel.otherCustomUnits.collectAsStateWithLifecycle().value,
          ),
      timingUi =
          EditIngestionTimingUi(
              ingestionTimePickerOption =
                  viewModel.ingestionTimePickerOptionFlow.collectAsStateWithLifecycle().value,
              onChangeTimePickerOption = viewModel::onChangeTimePickerOption,
              localDateTimeStart =
                  viewModel.localDateTimeStartFlow.collectAsStateWithLifecycle().value,
              onChangeStartDateOrTime = viewModel::onChangeStartTime,
              localDateTimeEnd = viewModel.localDateTimeEndFlow.collectAsStateWithLifecycle().value,
              onChangeEndDateOrTime = viewModel::onChangeEndTime,
              experiences = viewModel.relevantExperiences.collectAsStateWithLifecycle().value,
              selectedExperienceId = viewModel.experienceId,
              onChangeId = { viewModel.experienceId = it },
          ),
      detailsUi =
          EditIngestionDetailsUi(
              note = viewModel.note,
              onNoteChange = { viewModel.note = it },
              consumerName = viewModel.consumerName,
              onChangeConsumerName = viewModel::onChangeConsumerName,
              consumerNamesSorted =
                  viewModel.sortedConsumerNamesFlow.collectAsStateWithLifecycle().value,
              administrationSite = viewModel.administrationSite,
              onChangeOfAdministrationSite = viewModel::onChangeAdministrationSite,
          ),
      metadataUi =
          EditIngestionMetadataUi(
              showSiteSelection = viewModel.showSiteSelection,
              siteOptions = viewModel.siteOptions,
              saltForm = viewModel.saltForm,
              onSaltFormChange = viewModel::onChangeSaltForm,
              ingestionCategory = viewModel.ingestionCategory,
              onIngestionCategoryChange = { viewModel.ingestionCategory = it },
              addIngestionWithClonedTime = {
                viewModel.saveClonedIngestionTime {
                  navigateBack()
                  navigateToAddIngestion()
                }
              },
              onResendWebhook = viewModel::resendWebhook,
              hasEnabledWebhooks =
                  viewModel.hasEnabledWebhooksFlow.collectAsStateWithLifecycle().value,
          ),
      deleteIngestion = { viewModel.deleteIngestion(onDeleted = navigateBack) },
      onDone = { viewModel.onDoneClicked(onSaved = navigateBack) },
  )
  if (viewModel.isShowingLimitWarning) {
    foo.pilz.freaklog.ui.tabs.settings.intakelimits.IntakeLimitWarningDialog(
        statuses = viewModel.limitWarningStatuses,
        onConfirm = viewModel::confirmLimitWarningAndSave,
        onDismiss = viewModel::dismissLimitWarning,
    )
  }
}

private data class EditIngestionDoseUi(
    val dose: String,
    val onDoseChange: (String) -> Unit,
    val isKnown: Boolean,
    val toggleIsKnown: () -> Unit,
    val isEstimate: Boolean,
    val onChangeIsEstimate: (Boolean) -> Unit,
    val estimatedDoseStandardDeviation: String,
    val onEstimatedDoseStandardDeviationChange: (String) -> Unit,
)

private data class EditIngestionUnitUi(
    val units: String,
    val onUnitsChange: (String) -> Unit,
    val customUnit: CustomUnit?,
    val onCustomUnitChange: (CustomUnit?) -> Unit,
    val otherCustomUnits: List<CustomUnit>,
)

private data class EditIngestionTimingUi(
    val ingestionTimePickerOption: IngestionTimePickerOption,
    val onChangeTimePickerOption: (option: IngestionTimePickerOption) -> Unit,
    val localDateTimeStart: LocalDateTime,
    val onChangeStartDateOrTime: (LocalDateTime) -> Unit,
    val localDateTimeEnd: LocalDateTime,
    val onChangeEndDateOrTime: (LocalDateTime) -> Unit,
    val experiences: List<ExperienceOption>,
    val selectedExperienceId: Int,
    val onChangeId: (Int) -> Unit,
)

private data class EditIngestionDetailsUi(
    val note: String,
    val onNoteChange: (String) -> Unit,
    val consumerName: String,
    val onChangeConsumerName: (String) -> Unit,
    val consumerNamesSorted: List<String>,
    val administrationSite: String,
    val onChangeOfAdministrationSite: (String) -> Unit,
)

private data class EditIngestionMetadataUi(
    val showSiteSelection: Boolean,
    val siteOptions: List<String>,
    val saltForm: String,
    val onSaltFormChange: (String) -> Unit,
    val ingestionCategory: foo.pilz.freaklog.data.substances.classes.IngestionCategory?,
    val onIngestionCategoryChange:
        (foo.pilz.freaklog.data.substances.classes.IngestionCategory?) -> Unit,
    val addIngestionWithClonedTime: () -> Unit,
    val onResendWebhook: () -> Unit,
    val hasEnabledWebhooks: Boolean,
)

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun EditIngestionScreenPreview() {
  JournalTheme {
    EditIngestionScreen(
        doseUi =
            EditIngestionDoseUi(
                dose = "5",
                onDoseChange = {},
                isKnown = true,
                toggleIsKnown = {},
                isEstimate = false,
                onChangeIsEstimate = {},
                estimatedDoseStandardDeviation = "",
                onEstimatedDoseStandardDeviationChange = {},
            ),
        unitUi =
            EditIngestionUnitUi(
                units = "mg",
                onUnitsChange = {},
                customUnit = null,
                onCustomUnitChange = {},
                otherCustomUnits = emptyList(),
            ),
        timingUi =
            EditIngestionTimingUi(
                ingestionTimePickerOption = IngestionTimePickerOption.POINT_IN_TIME,
                onChangeTimePickerOption = {},
                localDateTimeStart = LocalDateTime.now(),
                onChangeStartDateOrTime = {},
                localDateTimeEnd = LocalDateTime.now(),
                onChangeEndDateOrTime = {},
                experiences = emptyList(),
                selectedExperienceId = 2,
                onChangeId = {},
            ),
        detailsUi =
            EditIngestionDetailsUi(
                note = "This is my note",
                onNoteChange = {},
                consumerName = "",
                onChangeConsumerName = {},
                consumerNamesSorted = listOf("Dave", "Ali"),
                administrationSite = "Left nostril",
                onChangeOfAdministrationSite = {},
            ),
        metadataUi =
            EditIngestionMetadataUi(
                showSiteSelection = true,
                siteOptions = listOf("Left nostril", "Right nostril", "Both nostrils"),
                saltForm = "",
                onSaltFormChange = {},
                ingestionCategory = null,
                onIngestionCategoryChange = {},
                addIngestionWithClonedTime = {},
                onResendWebhook = {},
                hasEnabledWebhooks = true,
            ),
        deleteIngestion = {},
        onDone = {},
    )
  }
}

@Composable
private fun EditIngestionScreen(
    doseUi: EditIngestionDoseUi,
    unitUi: EditIngestionUnitUi,
    timingUi: EditIngestionTimingUi,
    detailsUi: EditIngestionDetailsUi,
    metadataUi: EditIngestionMetadataUi,
    deleteIngestion: () -> Unit,
    onDone: () -> Unit,
) {
  var isPresentingBottomSheet by rememberSaveable { mutableStateOf(false) }
  Scaffold(
      topBar = { EditIngestionTopBar(deleteIngestion = deleteIngestion) },
      floatingActionButton = {
        ExtendedFloatingActionButton(
            modifier = Modifier.imePadding(),
            onClick = onDone,
            icon = {
              Icon(
                  Icons.Filled.Done,
                  contentDescription = null,
              )
            },
            text = { Text("Done") },
        )
      },
  ) { padding ->
    Column(
        modifier =
            Modifier.clearOfImeFab()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = horizontalPadding)
    ) {
      Spacer(modifier = Modifier.height(3.dp))
      EditIngestionDoseCard(doseUi = doseUi, unitUi = unitUi)
      EditIngestionNotesCard(note = detailsUi.note, onNoteChange = detailsUi.onNoteChange)
      EditIngestionTimeCard(timingUi = timingUi)
      EditIngestionConsumerCard(
          consumerName = detailsUi.consumerName,
          onChangeConsumerName = detailsUi.onChangeConsumerName,
          consumerNamesSorted = detailsUi.consumerNamesSorted,
          onOpenConsumerSheet = { isPresentingBottomSheet = !isPresentingBottomSheet },
      )
      EditIngestionExtraCards(
          detailsUi = detailsUi,
          metadataUi = metadataUi,
      )
    }
  }
  if (isPresentingBottomSheet) {
    EditIngestionConsumerSheet(
        consumerNamesSorted = detailsUi.consumerNamesSorted,
        onSelectConsumer = detailsUi.onChangeConsumerName,
        onDismiss = { isPresentingBottomSheet = false },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditIngestionTopBar(deleteIngestion: () -> Unit) {
  var isShowingDeleteDialog by remember { mutableStateOf(false) }
  TopAppBar(
      navigationIcon = { NavigateBackButton() },
      title = { Text("Edit ingestion") },
      actions = {
        AnimatedVisibility(visible = isShowingDeleteDialog) {
          AlertDialog(
              onDismissRequest = { isShowingDeleteDialog = false },
              title = { Text(text = "Delete ingestion?") },
              confirmButton = {
                TextButton(
                    onClick = {
                      isShowingDeleteDialog = false
                      deleteIngestion()
                    }
                ) {
                  Text("Delete")
                }
              },
              dismissButton = {
                TextButton(onClick = { isShowingDeleteDialog = false }) { Text("Cancel") }
              },
          )
        }
        IconButton(onClick = { isShowingDeleteDialog = true }) {
          Icon(
              Icons.Default.Delete,
              contentDescription = "Delete ingestion",
          )
        }
      },
  )
}

@Composable
private fun EditIngestionDoseCard(
    doseUi: EditIngestionDoseUi,
    unitUi: EditIngestionUnitUi,
) {
  val focusManager = LocalFocusManager.current
  val title = unitUi.customUnit?.let { "Dose ${it.name}" } ?: "Dose"
  CardWithTitle(title = title) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = doseUi.toggleIsKnown).fillMaxWidth(),
    ) {
      Checkbox(checked = doseUi.isKnown, onCheckedChange = { doseUi.toggleIsKnown() })
      Text("Dose is known")
    }
    AnimatedVisibility(visible = doseUi.isKnown) {
      Column {
        if (unitUi.customUnit == null) {
          OutlinedTextField(
              value = unitUi.units,
              onValueChange = unitUi.onUnitsChange,
              label = { Text(text = "Units") },
              modifier = Modifier.fillMaxWidth(),
              keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
              singleLine = true,
          )
        }
        OutlinedTextField(
            value = doseUi.dose,
            onValueChange = { doseUi.onDoseChange(it.replace(oldChar = ',', newChar = '.')) },
            label = { Text(text = "Dose") },
            trailingIcon = { Text(text = unitUi.units) },
            modifier = Modifier.fillMaxWidth(),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            isError = !foo.pilz.freaklog.ui.utils.isValidPositiveDoseText(doseUi.dose),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            singleLine = true,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Switch(checked = doseUi.isEstimate, onCheckedChange = doseUi.onChangeIsEstimate)
          Text("Estimate", style = MaterialTheme.typography.titleMedium)
        }
        AnimatedVisibility(visible = doseUi.isEstimate) {
          EditIngestionEstimateFields(doseUi = doseUi, units = unitUi.units)
        }
        if (unitUi.otherCustomUnits.isNotEmpty()) {
          EditIngestionCustomUnitPicker(unitUi = unitUi)
        }
      }
    }
    unitUi.customUnit?.let {
      if (it.note.isNotBlank()) {
        Text("${it.name}: ${it.note}")
      }
    }
  }
}

@Composable
private fun EditIngestionEstimateFields(
    doseUi: EditIngestionDoseUi,
    units: String,
) {
  val focusManager = LocalFocusManager.current
  Column {
    OutlinedTextField(
        value = doseUi.estimatedDoseStandardDeviation,
        onValueChange = {
          doseUi.onEstimatedDoseStandardDeviationChange(it.replace(oldChar = ',', newChar = '.'))
        },
        label = { Text("Estimated standard deviation") },
        trailingIcon = {
          Text(
              text = units,
              modifier = Modifier.padding(horizontal = horizontalPadding),
          )
        },
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        isError =
            !foo.pilz.freaklog.ui.utils.isValidOptionalStandardDeviationText(
                doseUi.estimatedDoseStandardDeviation
            ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    val mean = foo.pilz.freaklog.ui.utils.evaluateNumericExpression(doseUi.dose)
    val standardDeviation =
        foo.pilz.freaklog.ui.utils.evaluateNumericExpression(doseUi.estimatedDoseStandardDeviation)
    AnimatedVisibility(mean != null && standardDeviation != null) {
      if (mean != null && standardDeviation != null) {
        StandardDeviationExplanation(
            mean = mean,
            standardDeviation = standardDeviation,
            unit = units,
        )
      }
    }
  }
}

@Composable
private fun EditIngestionCustomUnitPicker(unitUi: EditIngestionUnitUi) {
  var isShowingDropDownMenu by remember { mutableStateOf(false) }
  Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
    OutlinedButton(
        onClick = { isShowingDropDownMenu = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text(text = "Unit: ${unitUi.customUnit?.name ?: "Default"}")
    }
    DropdownMenu(
        expanded = isShowingDropDownMenu,
        onDismissRequest = { isShowingDropDownMenu = false },
    ) {
      unitUi.otherCustomUnits.forEach { unit ->
        DropdownMenuItem(
            text = { Text(unit.name) },
            onClick = {
              unitUi.onCustomUnitChange(unit)
              isShowingDropDownMenu = false
            },
        )
      }
    }
  }
}

@Composable
private fun EditIngestionNotesCard(
    note: String,
    onNoteChange: (String) -> Unit,
) {
  val focusManager = LocalFocusManager.current
  CardWithTitle(title = "Ingestion notes") {
    OutlinedTextField(
        value = note,
        onValueChange = onNoteChange,
        label = { Text(text = "Notes") },
        modifier = Modifier.fillMaxWidth(),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Sentences,
            ),
        singleLine = true,
    )
  }
}

@Composable
private fun EditIngestionTimeCard(timingUi: EditIngestionTimingUi) {
  CardWithTitle(title = "Time") {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      TimePointOrRangePicker(
          onChangeTimePickerOption = timingUi.onChangeTimePickerOption,
          ingestionTimePickerOption = timingUi.ingestionTimePickerOption,
          localDateTimeStart = timingUi.localDateTimeStart,
          onChangeStartDateOrTime = timingUi.onChangeStartDateOrTime,
          localDateTimeEnd = timingUi.localDateTimeEnd,
          onChangeEndDateOrTime = timingUi.onChangeEndDateOrTime,
      )
      var isShowingDropDownMenu by remember { mutableStateOf(false) }
      Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
        val selectedOption =
            timingUi.experiences.firstOrNull { it.id == timingUi.selectedExperienceId }
        OutlinedButton(
            onClick = { isShowingDropDownMenu = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
              text =
                  if (selectedOption?.title != null) "Part of " + selectedOption.title
                  else "Part of unknown experience"
          )
        }
        DropdownMenu(
            expanded = isShowingDropDownMenu,
            onDismissRequest = { isShowingDropDownMenu = false },
        ) {
          timingUi.experiences.forEach { experience ->
            DropdownMenuItem(
                text = { Text(experience.title) },
                onClick = {
                  timingUi.onChangeId(experience.id)
                  isShowingDropDownMenu = false
                },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun EditIngestionConsumerCard(
    consumerName: String,
    onChangeConsumerName: (String) -> Unit,
    consumerNamesSorted: List<String>,
    onOpenConsumerSheet: () -> Unit,
) {
  val focusManager = LocalFocusManager.current
  ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
    Column(
        modifier =
            Modifier.padding(
                horizontal = horizontalPadding,
                vertical = 3.dp,
            )
    ) {
      Text(
          text = "Consumed by: ${consumerName.ifBlank { YOU }}",
          style = MaterialTheme.typography.titleMedium,
      )
      if (consumerNamesSorted.isNotEmpty() || consumerName.isNotBlank()) {
        TextButton(onClick = onOpenConsumerSheet) {
          Text(text = "Choose other consumer")
        }
      }
      var showNewConsumerTextField by remember { mutableStateOf(false) }
      Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Switch(
            checked = showNewConsumerTextField,
            onCheckedChange = { showNewConsumerTextField = !showNewConsumerTextField },
        )
        Text("Enter new consumer")
      }
      AnimatedVisibility(visible = showNewConsumerTextField) {
        OutlinedTextField(
            value = consumerName,
            onValueChange = onChangeConsumerName,
            leadingIcon = {
              Icon(
                  imageVector = Icons.Default.Person,
                  contentDescription = "Consumer",
              )
            },
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            keyboardOptions =
                KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done,
                    capitalization = KeyboardCapitalization.Words,
                ),
            placeholder = { Text("New consumer name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}

@Composable
private fun EditIngestionExtraCards(
    detailsUi: EditIngestionDetailsUi,
    metadataUi: EditIngestionMetadataUi,
) {
  if (metadataUi.showSiteSelection) {
    CardWithTitle(title = "Administration site") {
      AdministrationSitePicker(
          administrationSite = detailsUi.administrationSite,
          siteOptions = metadataUi.siteOptions,
          onSiteChange = detailsUi.onChangeOfAdministrationSite,
      )
    }
  }
  CardWithTitle(title = "Salt form") {
    SaltFormPicker(
        saltForm = metadataUi.saltForm,
        previousSaltForms = emptyList(),
        onSaltFormChange = metadataUi.onSaltFormChange,
    )
  }
  CardWithTitle(title = "Category") {
    IngestionCategoryPicker(
        ingestionCategory = metadataUi.ingestionCategory,
        inheritedCategory = null,
        onIngestionCategoryChange = metadataUi.onIngestionCategoryChange,
    )
  }
  ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
    Column(
        modifier =
            Modifier.padding(
                horizontal = horizontalPadding,
                vertical = 3.dp,
            )
    ) {
      TextButton(onClick = metadataUi.addIngestionWithClonedTime) {
        Icon(
            Icons.Outlined.Add,
            contentDescription = "Add",
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(text = "Add ingestion at same time")
      }
    }
  }
  if (metadataUi.hasEnabledWebhooks) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
      Column(
          modifier =
              Modifier.padding(
                  horizontal = horizontalPadding,
                  vertical = 3.dp,
              )
      ) {
        TextButton(onClick = metadataUi.onResendWebhook) {
          Text(text = "Resend webhook")
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditIngestionConsumerSheet(
    consumerNamesSorted: List<String>,
    onSelectConsumer: (String) -> Unit,
    onDismiss: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val bottomSheetState = rememberBottomSheetState(SheetValue.Hidden)
  ModalBottomSheet(
      onDismissRequest = onDismiss,
      sheetState = bottomSheetState,
  ) {
    LazyColumn {
      item {
        ListItem(
            content = { Text(YOU) },
            leadingContent = {
              Icon(
                  Icons.Default.Person,
                  contentDescription = "Consumer",
              )
            },
            modifier =
                Modifier.clickable {
                  onSelectConsumer("")
                  scope
                      .launch { bottomSheetState.hide() }
                      .invokeOnCompletion {
                        if (!bottomSheetState.isVisible) {
                          onDismiss()
                        }
                      }
                },
        )
      }
      items(consumerNamesSorted, key = { it }) { consumerName ->
        ListItem(
            content = { Text(consumerName) },
            leadingContent = {
              Icon(
                  Icons.Default.Person,
                  contentDescription = "Consumer",
              )
            },
            modifier =
                Modifier.clickable {
                  onSelectConsumer(consumerName)
                  scope
                      .launch { bottomSheetState.hide() }
                      .invokeOnCompletion {
                        if (!bottomSheetState.isVisible) {
                          onDismiss()
                        }
                      }
                },
        )
      }
    }
  }
}
