package foo.pilz.freaklog.ui.tabs.settings.intakelimits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import foo.pilz.freaklog.data.room.experiences.entities.IntakeLimitType
import foo.pilz.freaklog.ui.tabs.journal.addingestion.components.WarningBanner
import foo.pilz.freaklog.ui.tabs.journal.experience.rating.FloatingDoneButton
import foo.pilz.freaklog.ui.tabs.search.substance.roa.dose.RoaDoseView
import foo.pilz.freaklog.ui.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditIntakeLimitScreen(
    navigateBack: () -> Unit,
    viewModel: EditIntakeLimitViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    var isShowingDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditing) "Edit intake limit" else "New intake limit") },
                actions = {
                    if (viewModel.isEditing) {
                        IconButton(onClick = { isShowingDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete intake limit")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (viewModel.isValid) {
                FloatingDoneButton(
                    onDone = { viewModel.saveAndDismiss(navigateBack) },
                    modifier = Modifier.imePadding()
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal)
            ) {
                Column(modifier = Modifier.padding(spacing.lg)) {
                    Text(
                        text = "Substance",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = viewModel.substanceName,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal)
            ) {
                Column(
                    modifier = Modifier.padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.md)
                ) {
                    Text("Limit type", style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            onClick = { viewModel.limitType = IntakeLimitType.DOSE },
                            selected = viewModel.limitType == IntakeLimitType.DOSE,
                            label = { Text("Total dose") }
                        )
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            onClick = { viewModel.limitType = IntakeLimitType.COUNT },
                            selected = viewModel.limitType == IntakeLimitType.COUNT,
                            label = { Text("Ingestions") }
                        )
                    }

                    when (viewModel.limitType) {
                        IntakeLimitType.DOSE -> {
                            OutlinedTextField(
                                value = viewModel.maxDoseText,
                                onValueChange = { viewModel.maxDoseText = it.replace(',', '.') },
                                label = { Text("Maximum dose") },
                                trailingIcon = {
                                    Text(
                                        text = viewModel.unit,
                                        modifier = Modifier.padding(horizontal = spacing.sm)
                                    )
                                },
                                isError = viewModel.maxDoseText.toDoubleOrNull() == null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = viewModel.unit,
                                onValueChange = { viewModel.onUnitChange(it) },
                                label = { Text("Unit") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                viewModel.discoveredUnits.take(5).forEach { quickUnit ->
                                    OutlinedButton(
                                        onClick = { viewModel.onUnitChange(quickUnit) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(text = quickUnit)
                                    }
                                }
                            }
                        }

                        IntakeLimitType.COUNT -> {
                            OutlinedTextField(
                                value = viewModel.maxCountText,
                                onValueChange = {
                                    viewModel.maxCountText = it.filter { c -> c.isDigit() }
                                },
                                label = { Text("Maximum ingestions") },
                                isError = (viewModel.maxCountText.toIntOrNull() ?: 0) <= 0,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            if (viewModel.limitType == IntakeLimitType.DOSE && viewModel.doseInfos.isNotEmpty()) {
                WarningBanner(
                    title = "Reference doses",
                    leadingIcon = Icons.Outlined.Info,
                    accentColor = MaterialTheme.colorScheme.primary,
                    countLabel = null,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        viewModel.doseInfos.forEach { info ->
                            Text(
                                text = info.route.displayText,
                                style = MaterialTheme.typography.titleSmall
                            )
                            RoaDoseView(roaDose = info.roaDose)
                        }
                    }
                }
            }

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal)
            ) {
                Column(
                    modifier = Modifier.padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.md)
                ) {
                    Text("Time window", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Counts intake over a rolling window ending right now.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val modes = WindowMode.entries
                        modes.forEachIndexed { index, mode ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = modes.size
                                ),
                                onClick = { viewModel.windowMode = mode },
                                selected = viewModel.windowMode == mode,
                                label = {
                                    Text(
                                        when (mode) {
                                            WindowMode.DAY -> "Day"
                                            WindowMode.WEEK -> "Week"
                                            WindowMode.MONTH -> "Month"
                                            WindowMode.CUSTOM -> "Custom"
                                        }
                                    )
                                }
                            )
                        }
                    }

                    AnimatedVisibility(visible = viewModel.windowMode == WindowMode.CUSTOM) {
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                            OutlinedTextField(
                                value = viewModel.customWindowValueText,
                                onValueChange = {
                                    viewModel.customWindowValueText = it.filter { c -> c.isDigit() }
                                },
                                label = { Text("Length") },
                                isError = (viewModel.customWindowValueText.toLongOrNull()
                                    ?: 0L) <= 0L,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                val units = CustomWindowUnit.entries
                                units.forEachIndexed { index, customUnit ->
                                    SegmentedButton(
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = index,
                                            count = units.size
                                        ),
                                        onClick = { viewModel.customWindowUnit = customUnit },
                                        selected = viewModel.customWindowUnit == customUnit,
                                        label = { Text(customUnit.label) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal)
            ) {
                Column(
                    modifier = Modifier.padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    Text("Warning threshold", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "You get a popup when an ingestion would reach this percentage of the limit. Below it, the limit shows as a collapsed card.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = viewModel.warningPercentText,
                        onValueChange = {
                            viewModel.warningPercentText = it.filter { c -> c.isDigit() }
                        },
                        label = { Text("Warn at") },
                        trailingIcon = {
                            Text(text = "%", modifier = Modifier.padding(horizontal = spacing.sm))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal)
            ) {
                Column(
                    modifier = Modifier.padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.md)
                    ) {
                        Switch(
                            checked = viewModel.isEnabled,
                            onCheckedChange = { viewModel.isEnabled = it }
                        )
                        Text("Enabled", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "Disabled limits are kept but never trigger warnings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(72.dp))
        }
    }

    if (isShowingDeleteDialog) {
        AlertDialog(
            onDismissRequest = { isShowingDeleteDialog = false },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete intake limit?") },
            text = { Text("You will no longer be warned about this limit.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        isShowingDeleteDialog = false
                        viewModel.deleteAndDismiss(navigateBack)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { isShowingDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
