package foo.pilz.freaklog.ui.tabs.search.custom.customdurations

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoa
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDose
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDuration
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.Bioavailability
import foo.pilz.freaklog.data.substances.classes.roa.DoseClass
import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.DurationUnits
import foo.pilz.freaklog.ui.tabs.journal.experience.TimelineDisplayOption
import foo.pilz.freaklog.ui.tabs.journal.experience.components.TimeDisplayOption
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.AllTimelines
import foo.pilz.freaklog.ui.tabs.search.substance.roa.dose.RoaDoseView
import foo.pilz.freaklog.ui.theme.LocalSpacing
import foo.pilz.freaklog.ui.utils.DisappearingFAB
import java.util.Locale

@Preview(
    name = "420dp x 1200dp",
    widthDp = 420,
    heightDp = 1200
)
@Composable
fun CustomDurationEditorScreenPreview() {
    SharedTransitionLayout {
        AnimatedVisibility(true) {
            CustomDurationEditorScreen(
                substanceName = "Ibuprofen",
                substanceUnit = "mg",
                route = AdministrationRoute.SMOKED,
                roaDuration = CustomRoaDuration(
                    route = AdministrationRoute.SMOKED,
                    customSubstanceId = 67,
                    onset = null,
                    comeup = DurationRange(null, null, DurationUnits.HOURS),
                    peak = DurationRange(3f, null, DurationUnits.HOURS),
                    offset = null,
                    total = DurationRange(4f, 5f, DurationUnits.HOURS),
                ),
                roaDose = CustomRoaDose(
                    customSubstanceId = 67,
                    route = AdministrationRoute.SMOKED,
                    units = "mg",
                    lightMin = 50.0,
                    commonMin = 200.0,
                ),
                roa = CustomRoa(
                    customSubstanceId = 67,
                    route = AdministrationRoute.SMOKED,
                    bioavailability = Bioavailability(min = 30.0, max = 50.0),
                ),
                onRoaChange = { },
                previewTimelineDisplayOption = TimelineDisplayOption.Hidden,
                onDurationChange = { },
                onDoseChange = { },
                onDone = { },
                onDelete = { },
                navigateBack = { },
                animatedVisibilityScope = this@AnimatedVisibility,
                sharedTransitionScope = this@SharedTransitionLayout,
            )
        }
    }
}

@Composable
fun CustomDurationEditorScreen(
    viewModel: CustomDurationEditorViewModel = hiltViewModel(),
    navigateBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    CustomDurationEditorScreen(
        substanceName = viewModel.substanceName,
        substanceUnit = viewModel.substanceUnitFlow.collectAsStateWithLifecycle().value,
        route = viewModel.route,
        roaDuration = viewModel.roaDurationFlow.collectAsStateWithLifecycle().value,
        onDurationChange = { viewModel.roaDurationFlow.value = it },
        roaDose = viewModel.roaDoseFlow.collectAsStateWithLifecycle().value,
        onDoseChange = { viewModel.roaDoseFlow.value = it },
        roa = viewModel.roaFlow.collectAsStateWithLifecycle().value,
        onRoaChange = { viewModel.roaFlow.value = it },
        previewTimelineDisplayOption = viewModel.timelineDisplayOptionFlow.collectAsStateWithLifecycle().value,
        onDone = viewModel::onDone,
        onDelete = viewModel::onDelete,
        navigateBack = navigateBack,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDurationEditorScreen(
    substanceName: String,
    substanceUnit: String,
    route: AdministrationRoute,
    roaDuration: CustomRoaDuration?,
    onDurationChange: (CustomRoaDuration?) -> Unit,
    roaDose: CustomRoaDose?,
    onDoseChange: (CustomRoaDose?) -> Unit,
    roa: CustomRoa?,
    onRoaChange: (CustomRoa?) -> Unit,
    previewTimelineDisplayOption: TimelineDisplayOption,
    onDone: () -> Unit,
    onDelete: () -> Unit,
    navigateBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val spacing = LocalSpacing.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Edit Route")
                        Text(
                            "$substanceName · ${route.displayText}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showDeleteDialog = true }
                    ) {
                        Icon(Icons.Outlined.Delete, "Delete")
                    }
                }
            )
        },
        floatingActionButton = {
            DisappearingFAB(
                // hidden if scrolled all the way down, unless there's not enough content to allow
                // scrolling
                scrollState.canScrollForward || (
                        !scrollState.canScrollForward && !scrollState.canScrollBackward
                        )
            ) {
                FloatingActionButton(onClick = { onDone(); navigateBack() }) {
                    Row(
                        modifier = Modifier.padding(horizontal = spacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Check, null)
                        Spacer(modifier = Modifier.padding(horizontal = spacing.xs))
                        Text("Save")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(scrollState)
                .imePadding()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.sm)
            ) {

                if (roaDuration != null) {
                    Column(
                        modifier = Modifier
                            .padding(spacing.lg)
                    ) {
                        Text(
                            "Preview",
                            style = MaterialTheme.typography.titleLarge,
                        )

                        with(sharedTransitionScope) {
                            Box(
                                modifier = Modifier
                                    .sharedBounds(
                                        rememberSharedContentState(
                                            key = "timeline-${route.name}"
                                        ),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                    )
                            ) {
                                when (previewTimelineDisplayOption) {
                                    TimelineDisplayOption.Hidden -> {
                                        Text(
                                            "Timeline preview is hidden",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.secondary,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .padding(vertical = spacing.sm)
                                                .fillMaxWidth(),
                                        )
                                    }

                                    TimelineDisplayOption.Loading -> {
                                        LinearProgressIndicator(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(spacing.sm)
                                        )
                                    }

                                    TimelineDisplayOption.NotWorthDrawing -> {}
                                    is TimelineDisplayOption.Shown -> {
                                        val timelineModel =
                                            previewTimelineDisplayOption.allTimelinesModel
                                        AllTimelines(
                                            model = timelineModel,
                                            isShowingCurrentTime = false,
                                            timeDisplayOption = TimeDisplayOption.RELATIVE_TO_NOW,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                // need to use requiredHeight() instead of height()
                                                // here to avoid a crash with the sketchy page
                                                // transition
                                                .requiredHeight(200.dp)
                                                .padding(
                                                    horizontal = spacing.xs,
                                                    vertical = spacing.lg
                                                )
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(spacing.sm))

                        val previewRoaDose = roaDose?.toRoaDose()
                        Text(
                            text = route.displayText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (previewRoaDose != null) {
                            RoaDoseView(previewRoaDose)
                        } else {
                            Text(
                                text = "Dosage info unavailable",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.sm)
            ) {
                if (roaDuration != null) {
                    Column(
                        modifier = Modifier
                            .padding(spacing.sm)
                    ) {
                        Text(
                            "Ingestion durations",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(spacing.sm)
                        )
                        Text(
                            "These values are used to generate the ingestion timeline. " +
                                    "If a field has both a min and max value, the actual value " +
                                    "will be interpolated between those based on dosage classification " +
                                    "data, if available.",
                            modifier = Modifier.padding(
                                start = spacing.sm,
                                bottom = spacing.sm,
                                end = spacing.sm
                            )
                        )

                        DurationInputRow(
                            duration = roaDuration.onset,
                            onDurationChange = { onDurationChange(roaDuration.copy(onset = it)) },
                            "Onset",
                        )
                        DurationInputRow(
                            duration = roaDuration.comeup,
                            onDurationChange = { onDurationChange(roaDuration.copy(comeup = it)) },
                            "Comeup",
                        )
                        DurationInputRow(
                            duration = roaDuration.peak,
                            onDurationChange = { onDurationChange(roaDuration.copy(peak = it)) },
                            "Peak",
                        )
                        DurationInputRow(
                            duration = roaDuration.offset,
                            onDurationChange = { onDurationChange(roaDuration.copy(offset = it)) },
                            "Offset",
                        )
                        DurationInputRow(
                            duration = roaDuration.total,
                            onDurationChange = { onDurationChange(roaDuration.copy(total = it)) },
                            "Total",
                        )
                    }
                }
            }

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.sm)
            ) {

                if (roaDose != null) {
                    Column(
                        modifier = Modifier
                            .padding(spacing.sm)
                    ) {
                        Text(
                            "Dosage classifications",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(spacing.sm)
                        )
                        Text(
                            "Dosage classification thresholds are used to determine the " +
                                    "ingestion dosage dots and timeline graph's relative height.",
                            modifier = Modifier.padding(
                                start = spacing.sm,
                                bottom = spacing.lg,
                                end = spacing.sm
                            )
                        )

                        Row() {
                            DosageClassificationInput(
                                doseClass = DoseClass.LIGHT,
                                value = roaDose.lightMin,
                                onValueChange = { onDoseChange(roaDose.copy(lightMin = it)) },
                                unit = substanceUnit,
                                modifier = Modifier.weight(1.0f),
                            )
                            DosageClassificationInput(
                                doseClass = DoseClass.COMMON,
                                value = roaDose.commonMin,
                                onValueChange = { onDoseChange(roaDose.copy(commonMin = it)) },
                                unit = substanceUnit,
                                modifier = Modifier.weight(1.0f),
                            )
                            DosageClassificationInput(
                                doseClass = DoseClass.STRONG,
                                value = roaDose.strongMin,
                                onValueChange = { onDoseChange(roaDose.copy(strongMin = it)) },
                                unit = substanceUnit,
                                modifier = Modifier.weight(1.0f),
                            )
                            DosageClassificationInput(
                                doseClass = DoseClass.HEAVY,
                                value = roaDose.heavyMin,
                                onValueChange = { onDoseChange(roaDose.copy(heavyMin = it)) },
                                unit = substanceUnit,
                                modifier = Modifier.weight(1.0f),
                            )
                        }
                    }
                }
            }

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.sm)
            ) {
                if (roa != null) {
                    Column(
                        modifier = Modifier
                            .padding(spacing.sm)
                    ) {
                        Text(
                            "Bioavailability",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(spacing.sm),
                        )
                        Text(
                            "Estimated percentage of the dose that reaches systemic circulation " +
                                    "for this route. Leave blank if unknown.",
                            modifier = Modifier.padding(
                                start = spacing.sm,
                                bottom = spacing.lg,
                                end = spacing.sm,
                            ),
                        )
                        BioavailabilityInputRow(
                            bioavailability = roa.bioavailability,
                            onChange = { onRoaChange(roa.copy(bioavailability = it)) },
                        )
                    }
                }
            }
        }

        if (showDeleteDialog) {
            DeleteCustomDurationAlert(
                onDelete = {
                    onDelete()
                    navigateBack()
                },
                onClose = { showDeleteDialog = false }
            )
        }
    }
}

@Composable
fun DurationInputRow(
    duration: DurationRange?,
    onDurationChange: (duration: DurationRange?) -> Unit,
    name: String,
) {
    val spacing = LocalSpacing.current
    var minInput by remember { mutableStateOf(duration?.min?.toString() ?: "") }
    var maxInput by remember { mutableStateOf(duration?.max?.toString() ?: "") }
    var inputUnit by remember { mutableStateOf(duration?.units ?: DurationUnits.MINUTES) }
    var unitDropdownOpen by remember { mutableStateOf(false) }
    var unitDropdownSize by remember { mutableStateOf(Size.Zero) }

    SharedTransitionLayout {
        AnimatedContent(
            duration == null,
            label = "DurationInputRow_transition"
        ) { targetState ->
            if (targetState) {
                OutlinedButton(
                    onClick = {
                        onDurationChange(
                            DurationRange(
                                null,
                                null,
                                DurationUnits.MINUTES
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.xs),
                ) {
                    Icon(Icons.Default.Add, "Add $name input")
                    Spacer(modifier = Modifier.padding(spacing.xs))
                    Text(
                        name,
                        modifier = Modifier
                            .sharedBounds(
                                rememberSharedContentState("text"),
                                animatedVisibilityScope = this@AnimatedContent,
                            )
                    )
                }
            } else {
                Card(
                    modifier = Modifier
                        .padding(spacing.xs)
                        .fillMaxWidth(),
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .padding(start = spacing.lg, top = spacing.sm, end = spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                name,
                                modifier = Modifier
                                    .sharedElement(
                                        rememberSharedContentState("text"),
                                        animatedVisibilityScope = this@AnimatedContent,
                                    ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(modifier = Modifier.weight(1.0f))
                            FilledTonalIconButton(
                                onClick = { onDurationChange(null) },
                            ) {
                                Icon(Icons.Outlined.Close, "Delete")
                            }
                        }
                        Row(
                            modifier = Modifier.padding(
                                start = spacing.sm,
                                end = spacing.sm,
                                bottom = spacing.sm,
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = minInput,
                                onValueChange = {
                                    minInput = it
                                    try {
                                        if (it.isEmpty()) {
                                            onDurationChange(
                                                duration!!.copy(
                                                    min = null,
                                                    max = null
                                                )
                                            )
                                        } else if (validateNumberField(it)) {
                                            onDurationChange(duration!!.copy(min = it.toFloat()))
                                        }
                                    } catch (_: java.lang.NumberFormatException) {
                                    }
                                },
                                isError = !validateNumberField(minInput),
                                modifier = Modifier.width(72.dp),
                                label = { Text("min") },
                                maxLines = 1,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                            Text(" - ", modifier = Modifier.padding(horizontal = spacing.xs))
                            OutlinedTextField(
                                value = maxInput,
                                onValueChange = {
                                    maxInput = it
                                    try {
                                        if (it.isEmpty()) {
                                            onDurationChange(duration!!.copy(max = null))
                                        } else if (validateNumberField(it)) {
                                            onDurationChange(duration!!.copy(max = it.toFloat()))
                                        }
                                    } catch (_: java.lang.NumberFormatException) {
                                    }
                                },
                                isError = !validateNumberField(maxInput),
                                modifier = Modifier.width(72.dp),
                                label = { Text("max") },
                                maxLines = 1,
                                enabled = duration?.min != null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                            Spacer(modifier = Modifier.padding(spacing.xs))

                            Box(
                                modifier = Modifier
                                    .height(IntrinsicSize.Min)
                                    .weight(1.0f)
                            ) {
                                OutlinedTextField(
                                    value = inputUnit.text,
                                    onValueChange = { },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .onGloballyPositioned { coordinates ->
                                            unitDropdownSize = coordinates.size.toSize()
                                        },
                                    label = { Text("unit") },
                                    readOnly = true,
                                    maxLines = 1,
                                    trailingIcon = {
                                        if (unitDropdownOpen) {
                                            Icon(Icons.Default.ArrowDropUp, null)
                                        } else {
                                            Icon(Icons.Default.ArrowDropDown, null)
                                        }
                                    },
                                )

                                // invisible surface layered on top of the text input to capture
                                // click events
                                Surface(
                                    modifier = Modifier
                                        // this perfectly matches the dimensions of the input field
                                        .fillMaxSize()
                                        .padding(top = spacing.sm)
                                        .clip(MaterialTheme.shapes.extraSmall)
                                        // clickable modifier must come after padding and clip,
                                        // otherwise the visual click indication won't honour those
                                        .clickable { unitDropdownOpen = !unitDropdownOpen },
                                    color = Color.Transparent,
                                ) {}

                                DropdownMenu(
                                    expanded = unitDropdownOpen,
                                    onDismissRequest = { unitDropdownOpen = false },
                                    modifier = Modifier.width(
                                        with(LocalDensity.current) { unitDropdownSize.width.toDp() }
                                    ),
                                ) {
                                    enumValues<DurationUnits>().forEach { unit ->
                                        DropdownMenuItem(
                                            text = { Text(unit.text) },
                                            onClick = {
                                                inputUnit = unit
                                                unitDropdownOpen = false
                                                onDurationChange(duration!!.copy(units = unit))
                                            },
                                        )
                                    }
                                }
                            }

                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DosageClassificationInput(
    value: Double?,
    onValueChange: (value: Double?) -> Unit,
    doseClass: DoseClass,
    unit: String,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    var inputValue by remember { mutableStateOf(value?.toString() ?: "") }
    val doseColor by animateColorAsState(
        if (value != null) {
            doseClass.getComposeColor(isSystemInDarkTheme())
        } else {
            TextFieldDefaults.colors().focusedIndicatorColor
        }
    )

    Column(
        modifier = modifier
            .width(IntrinsicSize.Min)
    ) {
        TextField(
            value = inputValue,
            onValueChange = {
                inputValue = it
                if (inputValue.isEmpty()) onValueChange(null)
                else if (validateNumberField(it)) {
                    try {
                        onValueChange(it.toDouble())
                    } catch (_: java.lang.NumberFormatException) {
                    }
                }
            },
            isError = !validateNumberField(inputValue),
            modifier = Modifier.padding(horizontal = spacing.xs),
            colors = TextFieldDefaults.colors(
                unfocusedIndicatorColor = doseColor,
                focusedIndicatorColor = doseColor,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            maxLines = 1,
        )
        Spacer(modifier = Modifier.padding(top = spacing.sm))
        Text(
            doseClass.name
                .lowercase()
                .replaceFirstChar { it.titlecase(Locale.UK) },
            color = doseColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview
@Composable
fun DeleteCustomDurationAlertPreview() {
    DeleteCustomDurationAlert(onDelete = {}, onClose = {})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteCustomDurationAlert(
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
        title = { Text("Delete route") },
        text = { Text("Do you really want to delete this route?") },
    )
}

@Composable
fun BioavailabilityInputRow(
    bioavailability: Bioavailability?,
    onChange: (Bioavailability?) -> Unit,
) {
    val spacing = LocalSpacing.current
    var minInput by remember {
        mutableStateOf(bioavailability?.min?.toString().orEmpty().removeSuffix(".0"))
    }
    var maxInput by remember {
        mutableStateOf(bioavailability?.max?.toString().orEmpty().removeSuffix(".0"))
    }

    fun emit() {
        val min = minInput.toDoubleOrNull()
        val max = maxInput.toDoubleOrNull()
        if (min == null && max == null) {
            onChange(null)
        } else {
            onChange(Bioavailability(min = min, max = max))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.sm, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = minInput,
            onValueChange = {
                minInput = it
                if (validateNumberField(it)) emit()
            },
            isError = !validateNumberField(minInput),
            label = { Text("min %") },
            maxLines = 1,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        Text(" - ", modifier = Modifier.padding(horizontal = spacing.xs))
        OutlinedTextField(
            value = maxInput,
            onValueChange = {
                maxInput = it
                if (validateNumberField(it)) emit()
            },
            isError = !validateNumberField(maxInput),
            label = { Text("max %") },
            maxLines = 1,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
    }
}

private fun validateNumberField(value: String): Boolean {
    if (value.isEmpty()) return true
    try {
        val num = value.toFloat()
        return num >= 0
    } catch (_: NumberFormatException) {
        return false
    }
}
