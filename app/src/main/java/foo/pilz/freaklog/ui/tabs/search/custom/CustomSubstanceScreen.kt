package foo.pilz.freaklog.ui.tabs.search.custom

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteraction
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionSeverity
import foo.pilz.freaklog.data.room.experiences.relations.CustomRoaInfo
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithEverything
import foo.pilz.freaklog.data.substances.classes.Category
import foo.pilz.freaklog.data.substances.classes.InteractionType
import foo.pilz.freaklog.data.substances.classes.Tolerance
import foo.pilz.freaklog.ui.components.SectionHeader
import foo.pilz.freaklog.ui.tabs.journal.addingestion.time.TimePickerButton
import foo.pilz.freaklog.ui.tabs.journal.experience.TimelineDisplayOption
import foo.pilz.freaklog.ui.tabs.journal.experience.components.TimeDisplayOption
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.AllTimelines
import foo.pilz.freaklog.ui.tabs.search.substance.InteractionsContent
import foo.pilz.freaklog.ui.tabs.search.substance.RouteColorBar
import foo.pilz.freaklog.ui.tabs.search.substance.roa.ToleranceSection
import foo.pilz.freaklog.ui.tabs.search.substance.roa.dose.RoaDoseView
import foo.pilz.freaklog.ui.tabs.search.substance.roa.duration.RoaDurationView
import foo.pilz.freaklog.ui.tabs.search.substance.roa.toReadableString
import foo.pilz.freaklog.ui.theme.LocalSpacing
import foo.pilz.freaklog.ui.utils.getShortTimeText
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

@Composable
fun CustomSubstanceScreen(
    navigateBack: () -> Unit,
    navigateToEdit: (customSubstanceId: Int) -> Unit,
    viewModel: CustomSubstanceViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val substance = viewModel.substanceFlow.collectAsStateWithLifecycle().value
    var hasLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(substance) {
        if (substance != null) hasLoaded = true
        else if (hasLoaded) navigateBack()
    }
    CustomSubstanceScreen(
        substance = substance,
        categoriesByName = viewModel.categoriesByName,
        ingestionTime = viewModel.ingestionTimeFlow.collectAsStateWithLifecycle().value,
        onChangeIngestionTime = viewModel::changeIngestionTime,
        timelineDisplayOption = viewModel.timelineDisplayOptionFlow.collectAsStateWithLifecycle().value,
        navigateBack = navigateBack,
        navigateToEdit = { substance?.let { navigateToEdit(it.substance.id) } },
        onShare = { viewModel.share(context) },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomSubstanceScreen(
    substance: CustomSubstanceWithEverything?,
    categoriesByName: Map<String, Category>,
    ingestionTime: LocalDateTime,
    onChangeIngestionTime: (LocalDateTime) -> Unit,
    timelineDisplayOption: TimelineDisplayOption,
    navigateBack: () -> Unit,
    navigateToEdit: () -> Unit,
    onShare: () -> Unit,
) {
    val spacing = LocalSpacing.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(substance?.substance?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Outlined.IosShare, contentDescription = "Share")
                    }
                    IconButton(onClick = navigateToEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit")
                    }
                },
            )
        },
    ) { padding ->
        if (substance == null) {
            return@Scaffold
        }

        val custom = substance.substance
        val roaInfos = substance.roaInfos
        val customCategories = substance.categories
            .mapNotNull { categoriesByName[it.categoryName] }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            if (custom.summary != null || customCategories.isNotEmpty()) {
                item {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.screenHorizontal),
                    ) {
                        Column(
                            modifier = Modifier.padding(spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(spacing.md),
                        ) {
                            if (custom.summary != null) {
                                Text(
                                    text = custom.summary!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (customCategories.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                                ) {
                                    customCategories.forEach { category ->
                                        CategoryChip(category)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val roasWithDoses = roaInfos.filter { info ->
                val dose = info.dose
                dose != null && (dose.lightMin != null || dose.commonMin != null ||
                        dose.strongMin != null || dose.heavyMin != null)
            }

            if (roasWithDoses.isNotEmpty()) {
                item { SectionHeader("Dosage") }
                item {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.screenHorizontal),
                    ) {
                        Column(modifier = Modifier.padding(spacing.lg)) {
                            roasWithDoses.forEachIndexed { index, info ->
                                RouteDoseBlock(info)
                                if (index < roasWithDoses.size - 1) {
                                    Spacer(modifier = Modifier.height(spacing.md))
                                    HorizontalDivider()
                                    Spacer(modifier = Modifier.height(spacing.md))
                                }
                            }
                        }
                    }
                }
            }

            val tolerance = Tolerance(
                full = custom.toleranceFull,
                half = custom.toleranceHalf,
                zero = custom.toleranceZero,
            ).takeIf { it.full != null || it.half != null || it.zero != null }

            val crossToleranceNames = substance.crossTolerances.map { it.categoryName }

            if (tolerance != null || crossToleranceNames.isNotEmpty()) {
                item { SectionHeader("Tolerance") }
                item {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.screenHorizontal),
                    ) {
                        ToleranceSection(
                            tolerance = tolerance,
                            crossTolerances = crossToleranceNames,
                        )
                    }
                }
            }

            val roasWithDurations = roaInfos.filter { info ->
                val duration = info.duration
                duration != null && (duration.onset != null || duration.comeup != null ||
                        duration.peak != null || duration.offset != null || duration.total != null)
            }

            if (roasWithDurations.isNotEmpty()) {
                item { SectionHeader("Duration") }
                item {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.screenHorizontal),
                    ) {
                        Column(modifier = Modifier.padding(spacing.lg)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "Start:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.width(spacing.sm))
                                TimePickerButton(
                                    localDateTime = ingestionTime,
                                    onChange = onChangeIngestionTime,
                                    timeString = ingestionTime.getShortTimeText(),
                                    hasOutline = false,
                                )
                                val isTimeALotDifferentToNow = ChronoUnit.MINUTES.between(
                                    ingestionTime, LocalDateTime.now()
                                ).absoluteValue > 5
                                AnimatedVisibility(visible = isTimeALotDifferentToNow) {
                                    IconButton(onClick = { onChangeIngestionTime(LocalDateTime.now()) }) {
                                        Icon(
                                            imageVector = Icons.Default.Update,
                                            contentDescription = "Reset to now",
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(spacing.md))

                            when (timelineDisplayOption) {
                                TimelineDisplayOption.Hidden -> {}
                                TimelineDisplayOption.Loading -> {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }

                                TimelineDisplayOption.NotWorthDrawing -> {}
                                is TimelineDisplayOption.Shown -> {
                                    AllTimelines(
                                        model = timelineDisplayOption.allTimelinesModel,
                                        isShowingCurrentTime = false,
                                        timeDisplayOption = TimeDisplayOption.RELATIVE_TO_NOW,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(spacing.md))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(spacing.md))

                            roasWithDurations.forEachIndexed { index, info ->
                                RouteDurationBlock(info)
                                if (index < roasWithDurations.size - 1) {
                                    Spacer(modifier = Modifier.height(spacing.md))
                                    HorizontalDivider()
                                    Spacer(modifier = Modifier.height(spacing.md))
                                }
                            }
                        }
                    }
                }
            }

            val interactionRows = buildInteractionRows(substance.interactions)
            if (interactionRows.isNotEmpty()) {
                item { SectionHeader("Interactions") }
                item { InteractionsContent(rows = interactionRows) }
            }

            if (custom.effectsText != null) {
                item { SectionHeader("Effects") }
                item { TextCard(custom.effectsText!!) }
            }

            if (custom.generalRisks != null) {
                item { SectionHeader("Risks") }
                item { TextCard(custom.generalRisks!!) }
            }

            if (custom.longTermRisks != null) {
                item { SectionHeader("Long-term risks") }
                item { TextCard(custom.longTermRisks!!) }
            }
        }
    }
}

@Composable
private fun CategoryChip(category: Category) {
    val spacing = LocalSpacing.current
    Surface(
        shape = CircleShape,
        color = category.color.copy(alpha = 0.15f),
    ) {
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
        )
    }
}

@Composable
private fun RouteDoseBlock(info: CustomRoaInfo) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(
            text = info.route.displayText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        val roaDose = info.dose?.toRoaDose()
        if (roaDose != null) {
            RoaDoseView(roaDose = roaDose)
        }
        val bio = info.roa?.bioavailability
        if (bio != null) {
            Text(
                text = "Bioavailability: ${bio.min?.toReadableString() ?: ".."}-${bio.max?.toReadableString() ?: ".."}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RouteDurationBlock(info: CustomRoaInfo) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            RouteColorBar(info.route)
            Text(
                text = info.route.displayText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        val roaDuration = info.duration?.toRoaDuration()
        if (roaDuration != null) {
            RoaDurationView(roaDuration = roaDuration)
        }
    }
}

@Composable
private fun TextCard(text: String) {
    val spacing = LocalSpacing.current
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(spacing.lg),
        )
    }
}

private fun buildInteractionRows(
    interactions: List<CustomInteraction>,
): List<Pair<String, InteractionType>> {
    val rows = mutableListOf<Pair<String, InteractionType>>()
    interactions
        .filter { it.severity == CustomInteractionSeverity.DANGEROUS }
        .forEach { rows.add(it.targetName to InteractionType.DANGEROUS) }
    interactions
        .filter { it.severity == CustomInteractionSeverity.UNSAFE }
        .forEach { rows.add(it.targetName to InteractionType.UNSAFE) }
    interactions
        .filter { it.severity == CustomInteractionSeverity.UNCERTAIN }
        .forEach { rows.add(it.targetName to InteractionType.UNCERTAIN) }
    return rows
}
