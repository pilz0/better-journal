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

package foo.pilz.freaklog.ui.tabs.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ContactSupport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Webhook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import foo.pilz.freaklog.ui.VERSION_NAME
import foo.pilz.freaklog.ui.tabs.journal.experience.components.CardWithTitle
import foo.pilz.freaklog.ui.theme.horizontalPadding
import foo.pilz.freaklog.ui.utils.HapticType
import foo.pilz.freaklog.ui.utils.getStringOfPattern
import foo.pilz.freaklog.ui.utils.rememberHaptic
import kotlinx.coroutines.launch
import java.time.Instant

@Preview
@Composable
fun SettingsPreview() {
    SettingsScreen(
        deleteEverything = {},
        navigateToFAQ = {},
        navigateToComboSettings = {},
        navigateToSubstanceColors = {},
        navigateToCustomUnits = {},
        navigateToReminders = {},
        navigateToWebhook = {},
        importFile = {},
        exportFile = {},
        snackbarHostState = remember { SnackbarHostState() },
        areDosageDotsHidden = false,
        saveDosageDotsAreHidden = {},
        isTimelineHidden = false,
        saveIsTimelineHidden = {},
        areSubstanceHeightsIndependent = false,
        saveAreSubstanceHeightsIndependent = {},
        activateSafer = true,
        saveActivateSafer = {},
        isHapticFeedbackEnabled = true,
        saveHapticFeedbackEnabled = {},
        )
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    navigateToFAQ: () -> Unit,
    navigateToWebhook: () -> Unit,
    navigateToComboSettings: () -> Unit,
    navigateToSubstanceColors: () -> Unit,
    navigateToCustomUnits: () -> Unit,
    navigateToReminders: () -> Unit,
) {
    SettingsScreen(
        navigateToFAQ = navigateToFAQ,
        navigateToWebhook = navigateToWebhook,
        navigateToComboSettings = navigateToComboSettings,
        navigateToSubstanceColors = navigateToSubstanceColors,
        navigateToCustomUnits = navigateToCustomUnits,
        navigateToReminders = navigateToReminders,
        deleteEverything = viewModel::deleteEverything,
        importFile = viewModel::importFile,
        exportFile = viewModel::exportFile,
        snackbarHostState = viewModel.snackbarHostState,
        areDosageDotsHidden = viewModel.areDosageDotsHiddenFlow.collectAsState().value,
        saveDosageDotsAreHidden = viewModel::saveDosageDotsAreHidden,
        isTimelineHidden = viewModel.isTimelineHiddenFlow.collectAsState().value,
        saveIsTimelineHidden = viewModel::saveIsTimelineHidden,
        areSubstanceHeightsIndependent = viewModel.areSubstanceHeightsIndependentFlow.collectAsState().value,
        saveAreSubstanceHeightsIndependent = viewModel::saveAreSubstanceHeightsIndependent,
        activateSafer = viewModel.activateSaferFlow.collectAsState().value,
        saveActivateSafer = viewModel::saveActivateSafer,
        isHapticFeedbackEnabled = viewModel.isHapticFeedbackEnabledFlow.collectAsState().value,
        saveHapticFeedbackEnabled = viewModel::saveHapticFeedbackEnabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navigateToFAQ: () -> Unit,
    navigateToWebhook: () -> Unit,
    navigateToComboSettings: () -> Unit,
    navigateToSubstanceColors: () -> Unit,
    navigateToCustomUnits: () -> Unit,
    navigateToReminders: () -> Unit,
    deleteEverything: () -> Unit,
    importFile: (uri: Uri) -> Unit,
    exportFile: (uri: Uri) -> Unit,
    snackbarHostState: SnackbarHostState,
    areDosageDotsHidden: Boolean,
    saveDosageDotsAreHidden: (Boolean) -> Unit,
    isTimelineHidden: Boolean,
    saveIsTimelineHidden: (Boolean) -> Unit,
    areSubstanceHeightsIndependent: Boolean,
    saveAreSubstanceHeightsIndependent: (Boolean) -> Unit,
    activateSafer: Boolean,
    saveActivateSafer: (Boolean) -> Unit,
    isHapticFeedbackEnabled: Boolean,
    saveHapticFeedbackEnabled: (Boolean) -> Unit,
) {
    val performHaptic = rememberHaptic()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = horizontalPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            CardWithTitle(title = "UI", innerPaddingHorizontal = 0.dp) {
                SettingsButton(
                    imageVector = Icons.Outlined.Medication,
                    text = "Custom units"
                ) {
                    performHaptic(HapticType.CLICK)
                    navigateToCustomUnits()
                }
                HorizontalDivider()
                SettingsButton(
                    imageVector = Icons.Outlined.Palette,
                    text = "Substance colors"
                ) {
                    performHaptic(HapticType.CLICK)
                    navigateToSubstanceColors()
                }
                HorizontalDivider()
                SettingsButton(
                    imageVector = Icons.Outlined.WarningAmber,
                    text = "Interaction settings"
                ) {
                    performHaptic(HapticType.CLICK)
                    navigateToComboSettings()
                }
                HorizontalDivider()
                SettingsButton(
                    imageVector = Icons.Outlined.Notifications,
                    text = "Reminders"
                ) {
                    performHaptic(HapticType.CLICK)
                    navigateToReminders()
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Hide dosage dots")
                    Switch(
                        checked = areDosageDotsHidden,
                        onCheckedChange = { 
                            performHaptic(HapticType.TOGGLE)
                            saveDosageDotsAreHidden(it)
                        }
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Hide timeline")
                    Switch(
                        checked = isTimelineHidden,
                        onCheckedChange = { 
                            performHaptic(HapticType.TOGGLE)
                            saveIsTimelineHidden(it)
                        }
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    var showBottomSheet by remember { mutableStateOf(false) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clickable {
                                performHaptic(HapticType.CLICK)
                                showBottomSheet = true
                            }
                            .padding(end = ButtonDefaults.IconSpacing)
                    ) {
                        Text(text = "Independent substance heights")
                        if (showBottomSheet) {
                            ModalBottomSheet(
                                onDismissRequest = {
                                    showBottomSheet = false
                                },
                                sheetState = sheetState
                            ) {
                                Text(
                                    text = """
                                    Enable this setting if you want the timeline of different substances and routes of administration (roas) to be independent. Then ingestions of different substances and roas will always take the full height of the timeline.
                                    
                                    If this setting is disabled then timelines of different substances have a height relative to each other. In that case the average of the common dose is used as the point to compare it to.
                                    E.g. if the oral average common dose of MDMA is 100mg and the average common dose of insufflated MDMA is 50mg then the timeline for 100mg of oral MDMA is the same height as for 50mg of insufflated MDMA.
                                    This is also applied across substances. E.g. if the common dose of oral 2C-B is 20mg then the timeline of 40mg oral 2C-B will be twice as high as 100mg of oral MDMA.
                                """.trimIndent(),
                                    modifier = Modifier
                                        .padding(horizontal = horizontalPadding)
                                        .padding(bottom = 15.dp)
                                        .verticalScroll(state = rememberScrollState())
                                )
                            }
                        }
                        Icon(Icons.Outlined.Info, contentDescription = "Show more info")
                    }
                    Switch(
                        checked = areSubstanceHeightsIndependent,
                        onCheckedChange = { 
                            performHaptic(HapticType.TOGGLE)
                            saveAreSubstanceHeightsIndependent(it)
                        }
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Hide Safer page")
                    Switch(
                        checked = activateSafer,
                        onCheckedChange = { 
                            performHaptic(HapticType.TOGGLE)
                            saveActivateSafer(it)
                        }
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Haptic feedback")
                    Switch(
                        checked = isHapticFeedbackEnabled,
                        onCheckedChange = { 
                            performHaptic(HapticType.TOGGLE)
                            saveHapticFeedbackEnabled(it)
                        }
                    )
                }
            }
            CardWithTitle(title = "App data", innerPaddingHorizontal = 0.dp) {
                var isShowingExportDialog by remember { mutableStateOf(false) }
                SettingsButton(imageVector = Icons.Outlined.FileUpload, text = "Export File") {
                    performHaptic(HapticType.CLICK)
                    isShowingExportDialog = true
                }
                val jsonMIMEType = "application/json"
                val launcherExport =
                    rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument(
                            mimeType = jsonMIMEType
                        )
                    ) { uri ->
                        if (uri != null) {
                            exportFile(uri)
                        }
                    }
                AnimatedVisibility(visible = isShowingExportDialog) {
                    AlertDialog(
                        onDismissRequest = { isShowingExportDialog = false },
                        title = {
                            Text(text = "Export?")
                        },
                        text = {
                            Text("This will export all your data from the app into a file so you can send it to someone or import it again on a new phone")
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    isShowingExportDialog = false
                                    launcherExport.launch(
                                        "Journal ${
                                            Instant.now().getStringOfPattern("dd MMM yyyy")
                                        }.json"
                                    )
                                }
                            ) {
                                Text("Export")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { isShowingExportDialog = false }
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }
                HorizontalDivider()
                var isShowingImportDialog by remember { mutableStateOf(false) }
                SettingsButton(imageVector = Icons.Outlined.FileDownload, text = "Import file") {
                    isShowingImportDialog = true
                }
                val launcherImport =
                    rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
                        if (uri != null) {
                            importFile(uri)
                        }
                    }
                AnimatedVisibility(visible = isShowingImportDialog) {
                    AlertDialog(
                        onDismissRequest = { isShowingImportDialog = false },
                        title = {
                            Text(text = "Import file?")
                        },
                        text = {
                            Text("Import a file that was exported before. Note that this will delete the data that you already have in the app.")
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    isShowingImportDialog = false
                                    launcherImport.launch(jsonMIMEType)
                                }
                            ) {
                                Text("Import")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { isShowingImportDialog = false }
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }
                HorizontalDivider()
                var isShowingDeleteDialog by remember { mutableStateOf(false) }
                SettingsButton(
                    imageVector = Icons.Outlined.DeleteForever,
                    text = "Delete everything"
                ) {
                    isShowingDeleteDialog = true
                }
                val scope = rememberCoroutineScope()
                AnimatedVisibility(visible = isShowingDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { isShowingDeleteDialog = false },
                        title = {
                            Text(text = "Delete everything?")
                        },
                        text = {
                            Text("This will delete all your experiences, ingestions and custom substances.")
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    isShowingDeleteDialog = false
                                    deleteEverything()
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Deleted everything",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            ) {
                                Text("Delete")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { isShowingDeleteDialog = false }
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }
                HorizontalDivider()
                SettingsButton(imageVector = Icons.Outlined.Webhook, text = "Webhook") {
                    navigateToWebhook()
                }
            }
            val uriHandler = LocalUriHandler.current
            CardWithTitle(title = "Feedback", innerPaddingHorizontal = 0.dp) {
                SettingsButton(imageVector = Icons.Outlined.QuestionAnswer, text = "FAQ") {
                    navigateToFAQ()
                }
            }
            CardWithTitle(title = "App", innerPaddingHorizontal = 0.dp) {
                SettingsButton(imageVector = Icons.Outlined.Code, text = "Source Code") {
                    uriHandler.openUri("https://woof.rip/pilz0/better-journal")
                }
                HorizontalDivider()
                LocalContext.current
                Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, SHARE_APP_URL)
                    type = "text/plain"
                }
                HorizontalDivider()
                Text(
                    text = "Version $VERSION_NAME",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .padding(horizontal = 15.dp)
                        .padding(vertical = 10.dp)
                )
            }
        }
    }
}

const val SHARE_APP_URL = "https://psychonautwiki.org/wiki/PsychonautWiki_Journal"


@Composable
fun SettingsButton(imageVector: ImageVector, text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Icon(
            imageVector,
            contentDescription = imageVector.name,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(text)
        Spacer(modifier = Modifier.weight(1f))
    }
}