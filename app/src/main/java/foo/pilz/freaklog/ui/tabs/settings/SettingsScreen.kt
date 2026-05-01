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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Webhook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
        navigateToFreakQueryShell = {},
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
        isInventoryEnabled = false,
        saveIsInventoryEnabled = {},
        isRedoseShown = true,
        saveRedoseShown = {},
        redoseOnsetFraction = 1.0f,
        redoseComeupFraction = 1.0f,
        redosePeakFraction = 0.5f,
        saveRedoseFractions = { _, _, _ -> },
        isHapticFeedbackEnabled = true,
        isStatsHidden = false,
        saveIsStatsHidden = {},
        isDrugsHidden = false,
        saveIsDrugsHidden = {},
        saveHapticFeedbackEnabled = {},
        aiApiKey = "",
        saveAiApiKey = {},
        aiModelName = "gemini-2.5-flash",
        saveAiModelName = {},
        isLockEnabled = false,
        saveLockEnabled = {},
        lockTimeOption = foo.pilz.freaklog.ui.tabs.settings.lock.LockTimeOption.IMMEDIATELY,
        saveLockTimeOption = {},
        biometricAvailability = foo.pilz.freaklog.ui.tabs.settings.lock.BiometricAvailability.AVAILABLE,
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
    navigateToAchievements: () -> Unit = {},
    navigateToFreakQueryShell: () -> Unit = {},
) {
    SettingsScreen(
        navigateToFAQ = navigateToFAQ,
        navigateToWebhook = navigateToWebhook,
        navigateToComboSettings = navigateToComboSettings,
        navigateToSubstanceColors = navigateToSubstanceColors,
        navigateToCustomUnits = navigateToCustomUnits,
        navigateToReminders = navigateToReminders,
        navigateToAchievements = navigateToAchievements,
        navigateToFreakQueryShell = navigateToFreakQueryShell,
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
        isStatsHidden = viewModel.isStatsHiddenFlow.collectAsState().value,
        saveIsStatsHidden = viewModel::saveIsStatsHidden,
        isDrugsHidden = viewModel.isDrugsHiddenFlow.collectAsState().value,
        saveIsDrugsHidden = viewModel::saveIsDrugsHidden,
        activateSafer = viewModel.activateSaferFlow.collectAsState().value,
        saveActivateSafer = viewModel::saveActivateSafer,
        isInventoryEnabled = viewModel.isInventoryEnabledFlow.collectAsState().value,
        saveIsInventoryEnabled = viewModel::saveIsInventoryEnabled,
        isRedoseShown = viewModel.isRedoseShownFlow.collectAsState().value,
        saveRedoseShown = viewModel::saveRedoseShown,
        redoseOnsetFraction = viewModel.redoseOnsetFractionFlow.collectAsState().value,
        redoseComeupFraction = viewModel.redoseComeupFractionFlow.collectAsState().value,
        redosePeakFraction = viewModel.redosePeakFractionFlow.collectAsState().value,
        saveRedoseFractions = viewModel::saveRedoseFractions,
        isHapticFeedbackEnabled = viewModel.isHapticFeedbackEnabledFlow.collectAsState().value,
        saveHapticFeedbackEnabled = viewModel::saveHapticFeedbackEnabled,
        aiApiKey = viewModel.aiApiKeyFlow.collectAsState().value,
        saveAiApiKey = viewModel::saveAiApiKey,
        aiModelName = viewModel.aiModelNameFlow.collectAsState().value,
        saveAiModelName = viewModel::saveAiModelName,
        isLockEnabled = viewModel.isLockEnabledFlow.collectAsState().value,
        saveLockEnabled = viewModel::saveLockEnabled,
        lockTimeOption = viewModel.lockTimeOptionFlow.collectAsState().value,
        saveLockTimeOption = viewModel::saveLockTimeOption,
        biometricAvailability = viewModel.biometricAvailability(),
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
    navigateToAchievements: () -> Unit = {},
    navigateToFreakQueryShell: () -> Unit = {},
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
    isStatsHidden: Boolean,
    saveIsStatsHidden: (Boolean) -> Unit,
    isDrugsHidden: Boolean,
    saveIsDrugsHidden: (Boolean) -> Unit,
    activateSafer: Boolean,
    saveActivateSafer: (Boolean) -> Unit,
    isInventoryEnabled: Boolean,
    saveIsInventoryEnabled: (Boolean) -> Unit,
    isRedoseShown: Boolean,
    saveRedoseShown: (Boolean) -> Unit,
    redoseOnsetFraction: Float,
    redoseComeupFraction: Float,
    redosePeakFraction: Float,
    saveRedoseFractions: (Float, Float, Float) -> Unit,
    isHapticFeedbackEnabled: Boolean,
    saveHapticFeedbackEnabled: (Boolean) -> Unit,
    aiApiKey: String,
    saveAiApiKey: (String) -> Unit,
    aiModelName: String,
    saveAiModelName: (String) -> Unit,
    isLockEnabled: Boolean,
    saveLockEnabled: (Boolean) -> Unit,
    lockTimeOption: foo.pilz.freaklog.ui.tabs.settings.lock.LockTimeOption,
    saveLockTimeOption: (foo.pilz.freaklog.ui.tabs.settings.lock.LockTimeOption) -> Unit,
    biometricAvailability: foo.pilz.freaklog.ui.tabs.settings.lock.BiometricAvailability,
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
            var isShowingVisibleTabsDialog by remember { mutableStateOf(false) }
            var isShowingExportDialog by remember { mutableStateOf(false) }
            var isShowingImportDialog by remember { mutableStateOf(false) }
            var isShowingDeleteDialog by remember { mutableStateOf(false) }
            val jsonMIMEType = "application/json"
            val scope = rememberCoroutineScope()
            val uriHandler = LocalUriHandler.current
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
            val launcherImport =
                rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
                    if (uri != null) {
                        importFile(uri)
                    }
                }

            CardWithTitle(title = "Manage", innerPaddingHorizontal = 0.dp) {
                SettingsButton(
                    imageVector = Icons.Outlined.Medication,
                    text = "Custom units"
                ) {
                    performHaptic(HapticType.CLICK)
                    navigateToCustomUnits()
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
                SettingsButton(imageVector = Icons.Outlined.Webhook, text = "Webhooks") {
                    performHaptic(HapticType.CLICK)
                    navigateToWebhook()
                }
            }

            CardWithTitle(title = "Automation", innerPaddingHorizontal = 0.dp) {
                SettingsButton(
                    imageVector = Icons.Outlined.Notifications,
                    text = "Reminders"
                ) {
                    performHaptic(HapticType.CLICK)
                    navigateToReminders()
                }
                HorizontalDivider()
                SettingsButton(
                    imageVector = Icons.Outlined.Code,
                    text = "FreakQuery shell"
                ) {
                    performHaptic(HapticType.CLICK)
                    navigateToFreakQueryShell()
                }
            }

            CardWithTitle(title = "Appearance", innerPaddingHorizontal = 0.dp) {
                SettingsButton(
                    imageVector = Icons.Outlined.Palette,
                    text = "Substance colors"
                ) {
                    performHaptic(HapticType.CLICK)
                    navigateToSubstanceColors()
                }
                HorizontalDivider()
                SettingsButton(
                    imageVector = Icons.Outlined.Visibility,
                    text = "Visible tabs"
                ) {
                    performHaptic(HapticType.CLICK)
                    isShowingVisibleTabsDialog = true
                }
                HorizontalDivider()
                SettingsSwitchRow(
                    text = "Hide dosage dots",
                    checked = areDosageDotsHidden,
                    onCheckedChange = {
                        performHaptic(HapticType.TOGGLE)
                        saveDosageDotsAreHidden(it)
                    }
                )
                HorizontalDivider()
                SettingsSwitchRow(
                    text = "Hide timeline",
                    checked = isTimelineHidden,
                    onCheckedChange = {
                        performHaptic(HapticType.TOGGLE)
                        saveIsTimelineHidden(it)
                    }
                )
                HorizontalDivider()
                SettingsSwitchRow(
                    text = "Independent substance heights",
                    checked = areSubstanceHeightsIndependent,
                    description = "Scale each substance and route independently in timelines.",
                    trailingInfo = {
                        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                        var showBottomSheet by remember { mutableStateOf(false) }
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = "Show more info",
                            modifier = Modifier
                                .clickable {
                                    performHaptic(HapticType.CLICK)
                                    showBottomSheet = true
                                }
                                .padding(8.dp)
                        )
                        if (showBottomSheet) {
                            ModalBottomSheet(
                                onDismissRequest = { showBottomSheet = false },
                                sheetState = sheetState
                            ) {
                                Text(
                                    text = """
                                        Enable this if you want timelines for different substances and routes of administration to use their own height scale.

                                        When disabled, timeline heights are relative across substances and routes using average common doses.
                                    """.trimIndent(),
                                    modifier = Modifier
                                        .padding(horizontal = horizontalPadding)
                                        .padding(bottom = 15.dp)
                                        .verticalScroll(state = rememberScrollState())
                                )
                            }
                        }
                    },
                    onCheckedChange = {
                        performHaptic(HapticType.TOGGLE)
                        saveAreSubstanceHeightsIndependent(it)
                    }
                )
                HorizontalDivider()
                SettingsSwitchRow(
                    text = "Haptic feedback",
                    checked = isHapticFeedbackEnabled,
                    onCheckedChange = {
                        performHaptic(HapticType.TOGGLE)
                        saveHapticFeedbackEnabled(it)
                    }
                )
            }

            CardWithTitle(title = "Recommendations", innerPaddingHorizontal = 0.dp) {
                SettingsSwitchRow(
                    text = "Show redose recommendation",
                    checked = isRedoseShown,
                    onCheckedChange = {
                        performHaptic(HapticType.TOGGLE)
                        saveRedoseShown(it)
                    }
                )
                if (isRedoseShown) {
                    HorizontalDivider()
                    RedoseFractionsSection(
                        onsetFraction = redoseOnsetFraction,
                        comeupFraction = redoseComeupFraction,
                        peakFraction = redosePeakFraction,
                        onSave = saveRedoseFractions
                    )
                }
            }

            CardWithTitle(title = "Tools", innerPaddingHorizontal = 0.dp) {
                SettingsButton(
                    imageVector = Icons.Outlined.EmojiEvents,
                    text = "Achievements"
                ) {
                    performHaptic(HapticType.CLICK)
                    navigateToAchievements()
                }
            }

            CardWithTitle(title = "AI chatbot", innerPaddingHorizontal = horizontalPadding) {
                androidx.compose.material3.OutlinedTextField(
                    value = aiApiKey,
                    onValueChange = saveAiApiKey,
                    label = { Text("Gemini API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                androidx.compose.material3.OutlinedTextField(
                    value = aiModelName,
                    onValueChange = saveAiModelName,
                    label = { Text("Gemini Model Name") },
                    supportingText = { Text("Recommended: gemini-2.5-flash (fast) or gemini-2.5-pro (smartest)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
            }

            CardWithTitle(title = "Privacy", innerPaddingHorizontal = horizontalPadding) {
                LockSettingsSection(
                    isLockEnabled = isLockEnabled,
                    lockTimeOption = lockTimeOption,
                    biometricAvailability = biometricAvailability,
                    onLockEnabledChange = saveLockEnabled,
                    onLockTimeOptionChange = saveLockTimeOption,
                )
            }

            CardWithTitle(title = "App data", innerPaddingHorizontal = 0.dp) {
                SettingsButton(imageVector = Icons.Outlined.FileUpload, text = "Export File") {
                    performHaptic(HapticType.CLICK)
                    isShowingExportDialog = true
                }
                HorizontalDivider()
                SettingsButton(imageVector = Icons.Outlined.FileDownload, text = "Import file") {
                    performHaptic(HapticType.CLICK)
                    isShowingImportDialog = true
                }
                HorizontalDivider()
                SettingsButton(
                    imageVector = Icons.Outlined.DeleteForever,
                    text = "Delete everything"
                ) {
                    performHaptic(HapticType.CLICK)
                    isShowingDeleteDialog = true
                }
            }

            CardWithTitle(title = "Help", innerPaddingHorizontal = 0.dp) {
                SettingsButton(imageVector = Icons.Outlined.QuestionAnswer, text = "FAQ") {
                    performHaptic(HapticType.CLICK)
                    navigateToFAQ()
                }
            }

            CardWithTitle(title = "App", innerPaddingHorizontal = 0.dp) {
                SettingsButton(imageVector = Icons.Outlined.Code, text = "Source Code") {
                    performHaptic(HapticType.CLICK)
                    uriHandler.openUri("https://woof.rip/FreakLog/FreakLog-Android")
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

            VisibleTabsDialog(
                visible = isShowingVisibleTabsDialog,
                onDismiss = { isShowingVisibleTabsDialog = false },
                isStatsHidden = isStatsHidden,
                saveIsStatsHidden = saveIsStatsHidden,
                isDrugsHidden = isDrugsHidden,
                saveIsDrugsHidden = saveIsDrugsHidden,
                activateSafer = activateSafer,
                saveActivateSafer = saveActivateSafer,
                isInventoryEnabled = isInventoryEnabled,
                saveIsInventoryEnabled = saveIsInventoryEnabled,
                performHaptic = performHaptic
            )
            ConfirmActionDialog(
                visible = isShowingExportDialog,
                title = "Export file?",
                text = "Export all app data into a JSON file.",
                confirmText = "Export",
                onDismiss = { isShowingExportDialog = false },
                onConfirm = {
                    isShowingExportDialog = false
                    launcherExport.launch(
                        "Journal ${Instant.now().getStringOfPattern("dd MMM yyyy")}.json"
                    )
                }
            )
            ConfirmActionDialog(
                visible = isShowingImportDialog,
                title = "Import file?",
                text = "Importing replaces the data currently stored in the app.",
                confirmText = "Import",
                onDismiss = { isShowingImportDialog = false },
                onConfirm = {
                    isShowingImportDialog = false
                    launcherImport.launch(jsonMIMEType)
                }
            )
            ConfirmActionDialog(
                visible = isShowingDeleteDialog,
                title = "Delete everything?",
                text = "This will delete all experiences, ingestions and custom substances.",
                confirmText = "Delete",
                onDismiss = { isShowingDeleteDialog = false },
                onConfirm = {
                    isShowingDeleteDialog = false
                    deleteEverything()
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Deleted everything",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            )
        }
    }
}

const val SHARE_APP_URL = "https://psychonautwiki.org/wiki/PsychonautWiki_Journal"

@Composable
private fun SettingsSwitchRow(
    text: String,
    checked: Boolean,
    description: String? = null,
    trailingInfo: @Composable (() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailingInfo?.invoke()
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun VisibleTabsDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    isStatsHidden: Boolean,
    saveIsStatsHidden: (Boolean) -> Unit,
    isDrugsHidden: Boolean,
    saveIsDrugsHidden: (Boolean) -> Unit,
    activateSafer: Boolean,
    saveActivateSafer: (Boolean) -> Unit,
    isInventoryEnabled: Boolean,
    saveIsInventoryEnabled: (Boolean) -> Unit,
    performHaptic: (HapticType) -> Unit,
) {
    AnimatedVisibility(visible = visible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Visible tabs") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TabVisibilityRow(
                        text = "Stats",
                        checked = !isStatsHidden,
                        onCheckedChange = {
                            performHaptic(HapticType.TOGGLE)
                            saveIsStatsHidden(!it)
                        }
                    )
                    TabVisibilityRow(
                        text = "Drugs",
                        checked = !isDrugsHidden,
                        onCheckedChange = {
                            performHaptic(HapticType.TOGGLE)
                            saveIsDrugsHidden(!it)
                        }
                    )
                    TabVisibilityRow(
                        text = "Safer use",
                        checked = activateSafer,
                        onCheckedChange = {
                            performHaptic(HapticType.TOGGLE)
                            saveActivateSafer(it)
                        }
                    )
                    TabVisibilityRow(
                        text = "Inventory",
                        checked = isInventoryEnabled,
                        onCheckedChange = {
                            performHaptic(HapticType.TOGGLE)
                            saveIsInventoryEnabled(it)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
private fun TabVisibilityRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Text(text)
    }
}

@Composable
private fun ConfirmActionDialog(
    visible: Boolean,
    title: String,
    text: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AnimatedVisibility(visible = visible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(confirmText)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsButton(imageVector: ImageVector, text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
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

@Composable
private fun RedoseFractionsSection(
    onsetFraction: Float,
    comeupFraction: Float,
    peakFraction: Float,
    onSave: (Float, Float, Float) -> Unit
) {
    // These are expressed as percentages in the UI (0–300%) for readability.
    // Internally they are multipliers on the average duration of each phase.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 4.dp)
    ) {
        Text(
            text = "Redose recommendation formula",
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = "redoseAt = ingestionTime + onset×${"%.2f".format(onsetFraction)} + comeup×${"%.2f".format(comeupFraction)} + peak×${"%.2f".format(peakFraction)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        RedoseFractionSlider("Onset", onsetFraction) { onSave(it, comeupFraction, peakFraction) }
        RedoseFractionSlider("Comeup", comeupFraction) { onSave(onsetFraction, it, peakFraction) }
        RedoseFractionSlider("Peak", peakFraction) { onSave(onsetFraction, comeupFraction, it) }
        TextButton(onClick = { onSave(1.0f, 1.0f, 0.5f) }) {
            Text("Reset to defaults")
        }
    }
}

@Composable
private fun RedoseFractionSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    // Sanitise the incoming value once so the label, slider, and onSave
    // callback all operate on the same in-range float (guards against
    // out-of-range or NaN values that may linger in preferences).
    val safe = if (value.isFinite()) value.coerceIn(0f, 3f) else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, modifier = Modifier.width(70.dp))
        Slider(
            value = safe,
            onValueChange = { onChange(it.coerceIn(0f, 3f)) },
            valueRange = 0f..3f,
            steps = 29,
            modifier = Modifier.weight(1f)
        )
        Text(text = "%.2f".format(safe), modifier = Modifier.width(48.dp))
    }
}
