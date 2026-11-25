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

package foo.pilz.freaklog.ui.tabs.stats.substancecompanion

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.substances.classes.Tolerance
import foo.pilz.freaklog.ui.components.EmptyScreenDisclaimer
import foo.pilz.freaklog.ui.components.NavigateBackButton
import foo.pilz.freaklog.ui.tabs.search.substance.roa.ToleranceSection
import foo.pilz.freaklog.ui.theme.JournalTheme
import foo.pilz.freaklog.ui.theme.LocalSpacing
import foo.pilz.freaklog.ui.utils.HapticType
import foo.pilz.freaklog.ui.utils.getDateWithWeekdayText
import foo.pilz.freaklog.ui.utils.getShortTimeText
import foo.pilz.freaklog.ui.utils.rememberHaptic
import java.util.Locale

internal fun defaultDosageStatsModel() =
    SubstanceDosageStatsModel(
        buckets = emptyList(),
        summary =
            SubstanceDosageSummary(
                totalSessions = 0,
                totalKnownDose = null,
                averageKnownDosePerSession = null,
                peakKnownDose = null,
                unknownDoseCount = 0,
                unitsUsed = emptyList(),
                longestGapDays = null,
                currentStreakWeeks = 0,
            ),
        hasMixedUnits = false,
    )

data class SubstanceCompanionUiState(
    val substanceCompanion: SubstanceCompanion,
    val ingestionBursts: List<IngestionsBurst>?,
    val tolerance: Tolerance?,
    val crossTolerances: List<String>,
    val consumerName: String? = null,
    val dosageStats: SubstanceDosageStatsModel = defaultDosageStatsModel(),
    val selectedTimeRange: DosageTimeRange = DosageTimeRange.WEEKS_26,
    val showAverage: Boolean = false,
    val showTrendLine: Boolean = false,
    val selectedMetric: DosageMetric = DosageMetric.TOTAL_DOSE,
    val doseThresholds: DoseThresholds? = null,
    val frequency: SubstanceFrequency =
        SubstanceFrequency(last30Days = 0, last12Months = 0, allTime = 0),
    val substanceInfoTarget: SubstanceInfoTarget? = null,
)

data class SubstanceCompanionCallbacks(
    val onTimeRangeSelected: (DosageTimeRange) -> Unit = {},
    val onToggleShowAverage: (Boolean) -> Unit = {},
    val onToggleShowTrendLine: (Boolean) -> Unit = {},
    val onMetricSelected: (DosageMetric) -> Unit = {},
    val onExperienceClick: (Int) -> Unit = {},
    val onOpenSubstanceInfo: (SubstanceInfoTarget) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubstanceCompanionScreen(
    viewModel: SubstanceCompanionViewModel = hiltViewModel(),
    navigateToExperience: (Int) -> Unit = {},
    navigateToSubstanceInfo: (SubstanceInfoTarget) -> Unit = {},
) {
  val companion = viewModel.thisCompanionFlow.collectAsStateWithLifecycle().value
  val dosageStats = viewModel.dosageStatsFlow.collectAsStateWithLifecycle().value
  val frequency = viewModel.frequencyFlow.collectAsStateWithLifecycle().value
  val ingestionBursts = viewModel.ingestionBurstsFlow.collectAsStateWithLifecycle().value
  if (companion == null || dosageStats == null || frequency == null || ingestionBursts == null) {
    Scaffold(
        topBar = {
          TopAppBar(
              navigationIcon = { NavigateBackButton() },
              title = {
                Text(
                    text = viewModel.substanceName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
              },
          )
        }
    ) { padding ->
      Box(modifier = Modifier.fillMaxSize().padding(padding))
    }
    return
  }

  val state =
      SubstanceCompanionUiState(
          substanceCompanion = companion,
          ingestionBursts = ingestionBursts,
          tolerance = viewModel.toleranceFlow.collectAsStateWithLifecycle().value,
          crossTolerances = viewModel.crossTolerancesFlow.collectAsStateWithLifecycle().value,
          consumerName = viewModel.consumerName,
          dosageStats = dosageStats,
          selectedTimeRange = viewModel.selectedTimeRange.collectAsStateWithLifecycle().value,
          showAverage = viewModel.showAverage.collectAsStateWithLifecycle().value,
          showTrendLine = viewModel.showTrendLine.collectAsStateWithLifecycle().value,
          selectedMetric = viewModel.selectedMetric.collectAsStateWithLifecycle().value,
          doseThresholds = viewModel.doseThresholdsFlow.collectAsStateWithLifecycle().value,
          frequency = frequency,
          substanceInfoTarget =
              viewModel.substanceInfoTargetFlow.collectAsStateWithLifecycle().value,
      )
  val callbacks =
      SubstanceCompanionCallbacks(
          onTimeRangeSelected = viewModel::setTimeRange,
          onToggleShowAverage = viewModel::toggleShowAverage,
          onToggleShowTrendLine = viewModel::toggleShowTrendLine,
          onMetricSelected = viewModel::setMetric,
          onExperienceClick = navigateToExperience,
          onOpenSubstanceInfo = navigateToSubstanceInfo,
      )
  SubstanceCompanionScreen(state = state, callbacks = callbacks)
}

@Preview
@Composable
fun SubstanceCompanionPreview(
    @PreviewParameter(SubstanceCompanionScreenPreviewProvider::class)
    pair: Pair<SubstanceCompanion, List<IngestionsBurst>>
) {
  JournalTheme {
    SubstanceCompanionScreen(
        state =
            SubstanceCompanionUiState(
                substanceCompanion = pair.first,
                ingestionBursts = pair.second,
                tolerance =
                    Tolerance(
                        full = "with prolonged use",
                        half = "two weeks",
                        zero = "1 month",
                    ),
                crossTolerances = listOf("dopamine", "stimulant"),
                dosageStats = SubstanceCompanionScreenPreviewProvider.previewStats,
                frequency = SubstanceFrequency(last30Days = 2, last12Months = 5, allTime = 8),
                substanceInfoTarget = SubstanceInfoTarget.BuiltIn("Cocaine"),
            ),
        callbacks = SubstanceCompanionCallbacks(),
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubstanceCompanionScreen(
    state: SubstanceCompanionUiState,
    callbacks: SubstanceCompanionCallbacks,
) {
  val isDarkTheme = isSystemInDarkTheme()
  val substanceColor = state.substanceCompanion.color.getComposeColor(isDarkTheme)

  Scaffold(
      topBar = {
        SubstanceCompanionTopBar(
            substanceName = state.substanceCompanion.substanceName,
            consumerName = state.consumerName,
            substanceColor = substanceColor,
            infoTarget = state.substanceInfoTarget,
            onOpenSubstanceInfo = callbacks.onOpenSubstanceInfo,
        )
      }
  ) { padding ->
    SubstanceCompanionContent(
        state = state,
        substanceColor = substanceColor,
        callbacks = callbacks,
        modifier = Modifier.fillMaxSize().padding(padding),
    )
  }
}

@Composable
private fun SubstanceCompanionContent(
    state: SubstanceCompanionUiState,
    substanceColor: Color,
    callbacks: SubstanceCompanionCallbacks,
    modifier: Modifier = Modifier,
) {
  val spacing = LocalSpacing.current
  val bursts = state.ingestionBursts
  LazyColumn(
      modifier = modifier,
      contentPadding =
          PaddingValues(
              start = spacing.lg,
              end = spacing.lg,
              top = spacing.md,
              bottom = spacing.xxl,
          ),
      verticalArrangement = Arrangement.spacedBy(spacing.md),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item(key = "summary_section") { SubstanceSummarySection(state = state) }

    item(key = "dosage_chart") {
      DosageChartCard(
          state = state,
          substanceColor = substanceColor,
          callbacks = callbacks,
      )
    }

    item(key = "frequency_section") {
      SubstanceFrequencySection(
          frequency = state.frequency,
          barColor = substanceColor,
      )
    }

    if (state.tolerance != null || state.crossTolerances.isNotEmpty()) {
      item(key = "tolerance_section") {
        ToleranceCard(tolerance = state.tolerance, crossTolerances = state.crossTolerances)
      }
    }

    if (bursts != null) {
      item(key = "history_header") {
        HistoryHeader(burstCount = bursts.size, substanceColor = substanceColor)
      }
      if (bursts.isEmpty()) {
        item(key = "history_empty") {
          EmptyScreenDisclaimer(
              title = "No ingestions logged",
              description =
                  "Ingestions for ${state.substanceCompanion.substanceName} will appear here.",
          )
        }
      } else {
        items(bursts, key = { it.experience.id }) { burst ->
          Column(
              modifier = Modifier.fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(spacing.xs),
          ) {
            TimeArrowUp(timeText = burst.timeUntil)
            ExperienceBurstCard(
                burst = burst,
                onClick = { callbacks.onExperienceClick(burst.experience.id) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SubstanceSummarySection(state: SubstanceCompanionUiState) {
  val spacing = LocalSpacing.current
  val unit = state.dosageStats.summary.unitsUsed.firstOrNull().orEmpty()
  Column(
      verticalArrangement = Arrangement.spacedBy(spacing.sm),
      modifier = Modifier.fillMaxWidth(),
  ) {
    SubstanceSummaryCards(
        stats = state.dosageStats,
        unit = unit,
        rangeLabel = state.selectedTimeRange.displayText,
    )
    SubstanceInsightsStrip(
        summary = state.dosageStats.summary,
        unit = unit,
        hasMixedUnits = state.dosageStats.hasMixedUnits,
    )
  }
}

@Composable
private fun ToleranceCard(
    tolerance: Tolerance?,
    crossTolerances: List<String>,
) {
  val spacing = LocalSpacing.current
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(spacing.lg)) {
      Text("Tolerance", style = MaterialTheme.typography.titleMedium)
      Spacer(modifier = Modifier.height(spacing.sm))
      ToleranceSection(tolerance = tolerance, crossTolerances = crossTolerances)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubstanceCompanionTopBar(
    substanceName: String,
    consumerName: String?,
    substanceColor: Color,
    infoTarget: SubstanceInfoTarget?,
    onOpenSubstanceInfo: (SubstanceInfoTarget) -> Unit,
) {
  val spacing = LocalSpacing.current
  TopAppBar(
      navigationIcon = { NavigateBackButton() },
      title = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
          Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(substanceColor))
          Column {
            Text(
                text = substanceName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (consumerName != null) {
              Text(
                  text = "For $consumerName",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      },
      actions = {
        if (infoTarget != null) {
          IconButton(onClick = { onOpenSubstanceInfo(infoTarget) }) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Substance info",
            )
          }
        }
      },
  )
}

@Composable
private fun SubstanceSummaryCards(
    stats: SubstanceDosageStatsModel,
    unit: String,
    rangeLabel: String,
) {
  val spacing = LocalSpacing.current
  val summary = stats.summary
  val totalDoseText =
      when {
        stats.hasMixedUnits -> "Mixed"
        summary.totalKnownDose != null && summary.totalKnownDose > 0 ->
            formatSummaryDose(summary.totalKnownDose, unit)
        summary.unknownDoseCount > 0 -> "Unknown"
        else -> "—"
      }
  val totalDoseSublabel =
      when {
        stats.hasMixedUnits -> summary.unitsUsed.joinToString(", ")
        summary.unknownDoseCount > 0 -> "${summary.unknownDoseCount} unknown"
        else -> rangeLabel
      }
  val avgDoseText =
      when {
        stats.hasMixedUnits -> "Mixed"
        summary.averageKnownDosePerSession != null && summary.averageKnownDosePerSession > 0 ->
            formatSummaryDose(summary.averageKnownDosePerSession, unit)
        summary.unknownDoseCount > 0 -> "Unknown"
        else -> "—"
      }
  val avgDoseSublabel =
      when {
        stats.hasMixedUnits -> "mixed units"
        summary.unknownDoseCount > 0 && summary.averageKnownDosePerSession != null -> "known doses"
        else -> "per session"
      }

  Row(
      modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
      horizontalArrangement = Arrangement.spacedBy(spacing.sm),
  ) {
    SummaryCard(
        label = "Sessions",
        value = summary.totalSessions.toString(),
        sublabel =
            "${summary.totalIngestions} ${if (summary.totalIngestions == 1) "dose" else "doses"}",
        modifier = Modifier.weight(1f).fillMaxHeight(),
    )
    SummaryCard(
        label = "Total dose",
        value = totalDoseText,
        sublabel = totalDoseSublabel,
        modifier = Modifier.weight(1f).fillMaxHeight(),
    )
    SummaryCard(
        label = "Average",
        value = avgDoseText,
        sublabel = avgDoseSublabel,
        modifier = Modifier.weight(1f).fillMaxHeight(),
    )
  }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    sublabel: String,
    modifier: Modifier = Modifier,
) {
  val spacing = LocalSpacing.current
  ElevatedCard(modifier = modifier) {
    Column(
        modifier = Modifier.fillMaxSize().padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      Text(
          text = value,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      Text(
          text = sublabel,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubstanceInsightsStrip(
    summary: SubstanceDosageSummary,
    unit: String,
    hasMixedUnits: Boolean,
) {
  val spacing = LocalSpacing.current
  val chips =
      remember(summary, unit, hasMixedUnits) {
        buildList {
          if (!hasMixedUnits && summary.peakKnownDose != null && summary.peakKnownDose > 0) {
            add("Peak ${formatSummaryDose(summary.peakKnownDose, unit)}")
          }
          summary.longestGapDays?.let { add("Longest break ${it}d") }
          if (summary.currentStreakWeeks >= 2) {
            add("${summary.currentStreakWeeks}w streak")
          }
          summary.routeCounts.take(3).forEach { rc ->
            add("${rc.administrationRoute.displayText} ${rc.count}x")
          }
        }
      }
  if (chips.isEmpty()) return

  FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(spacing.xs),
      verticalArrangement = Arrangement.spacedBy(spacing.xs),
  ) {
    chips.forEach { label ->
      Surface(
          shape = RoundedCornerShape(50),
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
      ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
      }
    }
  }
}

@Composable
private fun DosageChartCard(
    state: SubstanceCompanionUiState,
    substanceColor: Color,
    callbacks: SubstanceCompanionCallbacks,
) {
  val spacing = LocalSpacing.current
  val performHaptic = rememberHaptic()
  val effectiveMetric =
      if (state.dosageStats.hasMixedUnits && state.selectedMetric != DosageMetric.SESSION_COUNT) {
        DosageMetric.SESSION_COUNT
      } else {
        state.selectedMetric
      }
  var selectedBucketIndex by
      remember(state.selectedTimeRange, effectiveMetric, state.dosageStats.buckets) {
        mutableStateOf<Int?>(null)
      }

  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
      DosageChartCardHeader(rangeTitle = state.selectedTimeRange.title)

      DosageRangeAndMetricSelectors(
          selectedTimeRange = state.selectedTimeRange,
          effectiveMetric = effectiveMetric,
          hasMixedUnits = state.dosageStats.hasMixedUnits,
          onTimeRangeSelected = { range ->
            performHaptic(HapticType.SELECTION)
            callbacks.onTimeRangeSelected(range)
          },
          onMetricSelected = { metric ->
            performHaptic(HapticType.SELECTION)
            callbacks.onMetricSelected(metric)
          },
      )

      DosageDiagnosticsBanners(stats = state.dosageStats)

      DosageChartInteractivePlot(
          buckets = state.dosageStats.buckets,
          substanceColor = substanceColor,
          effectiveMetric = effectiveMetric,
          showAverage = state.showAverage,
          showTrendLine = state.showTrendLine,
          doseThresholds = if (state.dosageStats.hasMixedUnits) null else state.doseThresholds,
          selectedBucketIndex = selectedBucketIndex,
          onSelectBucket = { idx ->
            performHaptic(HapticType.TICK)
            selectedBucketIndex = if (selectedBucketIndex == idx) null else idx
          },
          onClearSelection = { selectedBucketIndex = null },
      )

      DosageChartOverlayControls(
          showAverage = state.showAverage,
          showTrendLine = state.showTrendLine,
          onToggleAverage = {
            performHaptic(HapticType.TOGGLE)
            callbacks.onToggleShowAverage(!state.showAverage)
          },
          onToggleTrendLine = {
            performHaptic(HapticType.TOGGLE)
            callbacks.onToggleShowTrendLine(!state.showTrendLine)
          },
      )

      DosageChartLegendCaption(
          buckets = state.dosageStats.buckets,
          effectiveMetric = effectiveMetric,
          showAverage = state.showAverage,
          showTrendLine = state.showTrendLine,
          doseThresholds = if (state.dosageStats.hasMixedUnits) null else state.doseThresholds,
      )
    }
  }
}

@Composable
private fun DosageChartCardHeader(rangeTitle: String) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("Dosage over time", style = MaterialTheme.typography.titleMedium)
    Text(
        text = rangeTitle,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Suppress("LongParameterList")
@Composable
private fun DosageChartInteractivePlot(
    buckets: List<DosageBucket>,
    substanceColor: Color,
    effectiveMetric: DosageMetric,
    showAverage: Boolean,
    showTrendLine: Boolean,
    doseThresholds: DoseThresholds?,
    selectedBucketIndex: Int?,
    onSelectBucket: (Int) -> Unit,
    onClearSelection: () -> Unit,
) {
  val selectedBucket = selectedBucketIndex?.let { buckets.getOrNull(it) }
  if (selectedBucket != null) {
    SelectedDosageBucketStrip(
        bucket = selectedBucket,
        onDismiss = onClearSelection,
    )
  }
  DosageBarChart(
      buckets = buckets,
      config =
          DosageBarChartConfig(
              barColor = substanceColor,
              metric = effectiveMetric,
              showAverage = showAverage,
              showTrendLine = showTrendLine,
              doseThresholds = doseThresholds,
              selectedIndex = selectedBucketIndex,
              height = 200.dp,
          ),
      onBarTapped = onSelectBucket,
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DosageChartOverlayControls(
    showAverage: Boolean,
    showTrendLine: Boolean,
    onToggleAverage: () -> Unit,
    onToggleTrendLine: () -> Unit,
) {
  val spacing = LocalSpacing.current
  FlowRow(
      horizontalArrangement = Arrangement.spacedBy(spacing.sm),
      verticalArrangement = Arrangement.spacedBy(spacing.xs),
  ) {
    FilterChip(
        selected = showAverage,
        onClick = onToggleAverage,
        label = { Text("Average") },
    )
    FilterChip(
        selected = showTrendLine,
        onClick = onToggleTrendLine,
        label = { Text("Trend line") },
    )
  }
}

@Composable
private fun DosageRangeAndMetricSelectors(
    selectedTimeRange: DosageTimeRange,
    effectiveMetric: DosageMetric,
    hasMixedUnits: Boolean,
    onTimeRangeSelected: (DosageTimeRange) -> Unit,
    onMetricSelected: (DosageMetric) -> Unit,
) {
  val spacing = LocalSpacing.current
  Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      DosageTimeRange.entries.forEachIndexed { index, range ->
        SegmentedButton(
            selected = selectedTimeRange == range,
            onClick = { onTimeRangeSelected(range) },
            shape =
                SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = DosageTimeRange.entries.size,
                ),
            icon = {},
        ) {
          Text(range.displayText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      DosageMetric.entries.forEachIndexed { index, metric ->
        val isLocked = hasMixedUnits && metric != DosageMetric.SESSION_COUNT
        SegmentedButton(
            selected = effectiveMetric == metric,
            onClick = { onMetricSelected(metric) },
            enabled = !isLocked,
            shape =
                SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = DosageMetric.entries.size,
                ),
            icon = {},
        ) {
          Text(metric.displayText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}

@Composable
private fun DosageDiagnosticsBanners(stats: SubstanceDosageStatsModel) {
  if (stats.hasMixedUnits) {
    val unitsList = stats.summary.unitsUsed.joinToString(", ")
    DiagnosticBanner(
        text = "Mixed units ($unitsList). Showing session counts because units cannot be summed.",
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    )
  }
  if (stats.summary.unknownDoseCount > 0) {
    val count = stats.summary.unknownDoseCount
    val noun = if (count == 1) "ingestion has" else "ingestions have"
    DiagnosticBanner(
        text =
            "$count $noun no known dose and ${if (count == 1) "is" else "are"} excluded from dose totals.",
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun DiagnosticBanner(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
  val spacing = LocalSpacing.current
  Surface(
      shape = RoundedCornerShape(spacing.cornerLg),
      color = containerColor,
      modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
        modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          imageVector = Icons.Outlined.Info,
          contentDescription = null,
          tint = contentColor,
          modifier = Modifier.size(16.dp),
      )
      Text(
          text = text,
          style = MaterialTheme.typography.bodySmall,
          color = contentColor,
      )
    }
  }
}

@Composable
private fun SelectedDosageBucketStrip(
    bucket: DosageBucket,
    onDismiss: () -> Unit,
) {
  val spacing = LocalSpacing.current
  val dateLabel = bucket.endDateText?.let { "${bucket.fullDateText} – $it" } ?: bucket.fullDateText
  val doseSummary =
      when {
        bucket.totalDose > 0.0 && bucket.unit.isNotEmpty() ->
            formatSummaryDose(bucket.totalDose, bucket.unit)
        bucket.ingestionCount > 0 -> "Dose unknown"
        else -> "No ingestions"
      }
  val sessionsLabel =
      "${bucket.sessionCount} ${if (bucket.sessionCount == 1) "session" else "sessions"} · " +
          "${bucket.ingestionCount} ${if (bucket.ingestionCount == 1) "dose" else "doses"}"

  Surface(
      shape = RoundedCornerShape(spacing.cornerLg),
      color = MaterialTheme.colorScheme.secondaryContainer,
      modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
        modifier =
            Modifier.padding(
                start = spacing.md,
                end = spacing.xs,
                top = spacing.sm,
                bottom = spacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            text = "$doseSummary · $sessionsLabel",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        if (bucket.sessionCount > 1 && bucket.totalDose > 0.0 && bucket.unit.isNotEmpty()) {
          Text(
              text = "Avg/session: ${formatSummaryDose(bucket.avgDosePerSession, bucket.unit)}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
          )
        }
      }
      IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Clear selection",
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(16.dp),
        )
      }
    }
  }
}

@Composable
private fun DosageChartLegendCaption(
    buckets: List<DosageBucket>,
    effectiveMetric: DosageMetric,
    showAverage: Boolean,
    showTrendLine: Boolean,
    doseThresholds: DoseThresholds?,
) {
  val yValues =
      remember(buckets, effectiveMetric) {
        when (effectiveMetric) {
          DosageMetric.TOTAL_DOSE -> buckets.map { it.totalDose }
          DosageMetric.SESSION_COUNT -> buckets.map { it.sessionCount.toDouble() }
          DosageMetric.AVG_DOSE_PER_SESSION -> buckets.map { it.avgDosePerSession }
        }
      }
  if (buckets.isEmpty() || yValues.all { it <= 0.0 }) return

  val unit = buckets.firstOrNull()?.unit.orEmpty()
  val stats =
      remember(yValues, effectiveMetric) {
        computeDosageChartSummaryStats(
            yValues = yValues,
            activeOnly = effectiveMetric == DosageMetric.AVG_DOSE_PER_SESSION,
        )
      }
  val parts = buildList {
    if (showAverage && stats.average > 0.0) {
      val avgFormatted =
          if (effectiveMetric == DosageMetric.SESSION_COUNT || unit.isEmpty()) {
            formatSiValue(stats.average)
          } else {
            formatSummaryDose(stats.average, unit)
          }
      add("Avg $avgFormatted")
    }
    if (showTrendLine && stats.hasTrend) {
      val trendWord =
          when (stats.trendDirection) {
            TrendDirection.RISING -> "rising"
            TrendDirection.FALLING -> "falling"
            TrendDirection.STEADY -> "steady"
          }
      add("Trend $trendWord")
    }
    val converted = convertDoseThresholds(doseThresholds, unit)
    if (effectiveMetric != DosageMetric.SESSION_COUNT && converted != null) {
      val routePrefix = converted.route?.displayText?.let { "$it " }.orEmpty()
      add("${routePrefix}dose levels")
    }
  }
  if (parts.isEmpty()) return

  Text(
      text = parts.joinToString(" · "),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
private fun HistoryHeader(burstCount: Int, substanceColor: Color) {
  val spacing = LocalSpacing.current
  Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(spacing.sm),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("History", style = MaterialTheme.typography.titleMedium)
      Text(
          text = "$burstCount ${if (burstCount == 1) "session" else "sessions"}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (burstCount > 0) {
      Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
          Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(substanceColor))
            Text(
                text = "Now",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ExperienceBurstCard(
    burst: IngestionsBurst,
    onClick: () -> Unit,
) {
  val spacing = LocalSpacing.current
  ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md)) {
      Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(spacing.sm),
          modifier = Modifier.fillMaxWidth().padding(bottom = spacing.sm),
      ) {
        Text(
            text = burst.experience.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = burst.experience.sortDate.getDateWithWeekdayText(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
      burst.ingestions.forEachIndexed { index, ingestion ->
        IngestionRowOnSubstanceCompanionScreen(ingestionAndCustomUnit = ingestion)
        if (index < burst.ingestions.size - 1) {
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        }
      }
    }
  }
}

@Composable
fun IngestionRowOnSubstanceCompanionScreen(
    ingestionAndCustomUnit: IngestionsBurst.IngestionAndCustomUnit
) {
  val locales = LocalConfiguration.current.locales
  val locale = if (locales.isEmpty) Locale.ROOT else locales[0]
  Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    val text = buildAnnotatedString {
      append(ingestionAndCustomUnit.doseDescription)
      ingestionAndCustomUnit.customUnit?.name?.takeIf(String::isNotBlank)?.let { append(" $it") }
      withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
        val routeText =
            ingestionAndCustomUnit.ingestion.administrationRoute.displayText.lowercase(locale)
        ingestionAndCustomUnit.customUnitDose?.calculatedDoseDescription?.let { append(" = $it") }
        append(" · $routeText")
      }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.weight(1f),
    )
    Text(
        text = ingestionAndCustomUnit.ingestion.time.getShortTimeText(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp),
        maxLines = 1,
    )
  }
}
