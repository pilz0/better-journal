package foo.pilz.freaklog.ui.tabs.journal.addingestion.group

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroupItem
import foo.pilz.freaklog.ui.YOU
import foo.pilz.freaklog.ui.components.SectionHeader
import foo.pilz.freaklog.ui.tabs.journal.addingestion.components.ReadOnlyOutlinedField
import foo.pilz.freaklog.ui.tabs.journal.addingestion.time.IngestionCategoryPicker
import foo.pilz.freaklog.ui.tabs.journal.addingestion.time.NoteSection
import foo.pilz.freaklog.ui.tabs.journal.addingestion.time.TimePointOrRangePicker
import foo.pilz.freaklog.ui.tabs.journal.experience.rating.FloatingDoneButton
import foo.pilz.freaklog.ui.tabs.search.substance.roa.toReadableString
import foo.pilz.freaklog.ui.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubstanceGroupFinishScreen(
    dismissAddIngestionScreens: () -> Unit,
    viewModel: SubstanceGroupFinishViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val focusManager = LocalFocusManager.current
    val isLoaded by viewModel.isLoaded.collectAsStateWithLifecycle()
    val group by viewModel.group.collectAsStateWithLifecycle()
    val ingestionTimePickerOption by viewModel.ingestionTimePickerOptionFlow.collectAsStateWithLifecycle()
    val localDateTimeStart by viewModel.localDateTimeStartFlow.collectAsStateWithLifecycle()
    val localDateTimeEnd by viewModel.localDateTimeEndFlow.collectAsStateWithLifecycle()
    val experiencesInRange by viewModel.experiencesInRangeFlow.collectAsStateWithLifecycle()
    val selectedExperience by viewModel.selectedExperienceFlow.collectAsStateWithLifecycle()
    val consumerNamesSorted by viewModel.sortedConsumerNamesFlow.collectAsStateWithLifecycle()
    val previousNotes by viewModel.previousNotesFlow.collectAsStateWithLifecycle()
    val standaloneIngestionsEnabled by viewModel.areStandaloneIngestionsEnabledFlow.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Save group") })
        },
        floatingActionButton = {
            FloatingDoneButton(
                onDone = { viewModel.confirmAndDismiss(dismissAddIngestionScreens) },
                modifier = Modifier.imePadding(),
            )
        },
    ) { padding ->
        val loadedGroup = group
        if (!isLoaded || loadedGroup == null) {
            Box(modifier = Modifier
                .padding(padding)
                .fillMaxSize())
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = spacing.md),
            ) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = spacing.screenHorizontal,
                            vertical = spacing.md,
                        ),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = loadedGroup.group.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${loadedGroup.items.size} substances",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SectionHeader(text = "When")
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.screenHorizontal),
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = spacing.md,
                            vertical = spacing.md
                        )
                    ) {
                        TimePointOrRangePicker(
                            onChangeTimePickerOption = viewModel::onChangeTimePickerOption,
                            ingestionTimePickerOption = ingestionTimePickerOption,
                            localDateTimeStart = localDateTimeStart,
                            onChangeStartDateOrTime = viewModel::onChangeStartDateOrTime,
                            localDateTimeEnd = localDateTimeEnd,
                            onChangeEndDateOrTime = viewModel::onChangeEndDateOrTime,
                        )
                    }
                }

                SectionHeader(text = "Experience")
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.screenHorizontal),
                ) {
                    val verticalSpacing by animateDpAsState(
                        if (viewModel.addingWithoutExperience) 0.dp else spacing.md
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                        modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.md),
                    ) {
                        var isShowingDropDownMenu by remember { mutableStateOf(false) }

                        AnimatedVisibility(!viewModel.addingWithoutExperience) {
                            Box {
                                ReadOnlyOutlinedField(
                                    label = "Experience",
                                    value = selectedExperience?.experience?.title ?: "",
                                    placeholder = "New experience",
                                    onClick = { isShowingDropDownMenu = true },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.ArrowDropDown,
                                            contentDescription = "Choose experience",
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                DropdownMenu(
                                    expanded = isShowingDropDownMenu,
                                    onDismissRequest = { isShowingDropDownMenu = false },
                                ) {
                                    experiencesInRange.forEach { experienceWithIngestions ->
                                        val experience = experienceWithIngestions.experience
                                        DropdownMenuItem(
                                            text = { Text(experience.title) },
                                            onClick = {
                                                viewModel.onChangeOfSelectedExperience(
                                                    experienceWithIngestions
                                                )
                                                isShowingDropDownMenu = false
                                            },
                                        )
                                    }

                                    DropdownMenuItem(
                                        text = { Text("New experience") },
                                        leadingIcon = { Icon(Icons.Default.Add, null) },
                                        onClick = {
                                            viewModel.onChangeOfSelectedExperience(null)
                                            isShowingDropDownMenu = false
                                        },
                                    )
                                }
                            }
                        }
                        AnimatedVisibility(selectedExperience == null && !viewModel.addingWithoutExperience) {
                            OutlinedTextField(
                                value = viewModel.enteredTitle,
                                onValueChange = viewModel::changeTitle,
                                singleLine = true,
                                label = { Text("New experience title") },
                                isError = !viewModel.isEnteredTitleOk,
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                keyboardOptions = KeyboardOptions.Default.copy(
                                    imeAction = ImeAction.Done,
                                    capitalization = KeyboardCapitalization.Words,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        if (standaloneIngestionsEnabled) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.addingWithoutExperience = !viewModel.addingWithoutExperience
                                    },
                            ) {
                                Text(
                                    "Log without experience",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = spacing.sm),
                                )

                                Checkbox(
                                    checked = viewModel.addingWithoutExperience,
                                    onCheckedChange = {
                                        viewModel.addingWithoutExperience = !viewModel.addingWithoutExperience
                                    }
                                )
                            }
                        }
                    }
                }

                SectionHeader(text = "Logged by")
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.screenHorizontal),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.md),
                        verticalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedTextField(
                                value = viewModel.consumerName,
                                onValueChange = viewModel::changeConsumerName,
                                label = { Text("Consumer") },
                                placeholder = { Text(YOU) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions.Default.copy(
                                    imeAction = ImeAction.Done,
                                    capitalization = KeyboardCapitalization.Words,
                                ),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                trailingIcon = {
                                    IconButton(onClick = { menuExpanded = true }) {
                                        Icon(
                                            imageVector = Icons.Filled.ArrowDropDown,
                                            contentDescription = "Choose consumer",
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(YOU) },
                                    onClick = {
                                        viewModel.changeConsumerName("")
                                        menuExpanded = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Person,
                                            contentDescription = null
                                        )
                                    },
                                )
                                consumerNamesSorted.forEach { name ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            viewModel.changeConsumerName(name)
                                            menuExpanded = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.Person,
                                                contentDescription = null
                                            )
                                        },
                                    )
                                }
                            }
                        }
                        IngestionCategoryPicker(
                            ingestionCategory = viewModel.ingestionCategory,
                            onIngestionCategoryChange = viewModel::onIngestionCategoryChange,
                            inheritedCategory = viewModel.inheritedIngestionCategory,
                        )
                    }
                }

                SectionHeader(text = "Substances")
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.screenHorizontal),
                ) {
                    Column {
                        loadedGroup.sortedItems.forEachIndexed { index, item ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                )
                            }
                            GroupFinishItemRow(
                                item = item,
                                checked = viewModel.selectedItemIds[item.id] ?: true,
                                onToggle = { viewModel.toggleItem(item) },
                            )
                        }
                    }
                }

                SectionHeader(text = "Notes")
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.screenHorizontal),
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = spacing.md,
                            vertical = spacing.md
                        )
                    ) {
                        NoteSection(
                            previousNotes = previousNotes,
                            note = viewModel.note,
                            onNoteChange = { viewModel.note = it },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(spacing.fabBottomPad))
            }
        }
    }
}

@Composable
private fun GroupFinishItemRow(
    item: SubstanceGroupItem,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${item.substanceName} · ${item.administrationRoute.displayText.lowercase()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatDoseRecap(item),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatDoseRecap(item: SubstanceGroupItem): String {
    val d = item.dose ?: return "Unknown dose"
    val u = item.units.orEmpty()
    val sd = item.estimatedDoseStandardDeviation
    return when {
        item.isEstimate && sd != null -> "${d.toReadableString()} ± ${sd.toReadableString()} $u"
        item.isEstimate -> "~${d.toReadableString()} $u"
        else -> "${d.toReadableString()} $u"
    }.trim()
}
