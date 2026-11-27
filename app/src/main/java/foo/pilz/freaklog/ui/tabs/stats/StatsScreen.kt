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

package foo.pilz.freaklog.ui.tabs.stats

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import foo.pilz.freaklog.ui.YOU
import foo.pilz.freaklog.ui.components.EmptyScreenDisclaimer
import foo.pilz.freaklog.ui.tabs.search.substance.roa.toReadableString
import foo.pilz.freaklog.ui.theme.JournalTheme
import foo.pilz.freaklog.ui.theme.LocalSpacing
import foo.pilz.freaklog.ui.utils.HapticType
import foo.pilz.freaklog.ui.utils.getStringOfPattern
import foo.pilz.freaklog.ui.utils.rememberHaptic
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel = hiltViewModel(),
    navigateToSubstanceCompanion: (substanceName: String, consumerName: String?) -> Unit,
    navigateToToleranceChart: () -> Unit = {},
) {
  val statsModel by viewModel.statsModelFlow.collectAsStateWithLifecycle()
  val consumerNamesSorted by viewModel.sortedConsumerNamesFlow.collectAsStateWithLifecycle()
  LaunchedEffect(Unit) { viewModel.runExcelSkin() }
  val loadedStatsModel = statsModel
  if (loadedStatsModel == null) {
    Scaffold(topBar = { TopAppBar(title = { Text("Statistics") }) }) { padding ->
      Box(modifier = Modifier.padding(padding))
    }
  } else {
    StatsScreen(
        statsModel = loadedStatsModel,
        consumerNamesSorted = consumerNamesSorted,
        callbacks =
            StatsScreenCallbacks(
                navigateToSubstanceCompanion = navigateToSubstanceCompanion,
                navigateToToleranceChart = navigateToToleranceChart,
                onTapOption = viewModel::onTapOption,
                onChangeConsumerName = viewModel::onChangeConsumer,
                onChangeSortOption = viewModel::onChangeSortOption,
                onOpenSpreadsheet = viewModel::showExcelSkin,
            ),
    )
  }
}

@Preview
@Composable
fun StatsPreview(@PreviewParameter(StatsPreviewProvider::class) statsModel: StatsModel) {
  JournalTheme {
    StatsScreen(
        statsModel = statsModel,
        consumerNamesSorted = listOf("You", "Someone else"),
        callbacks = StatsScreenCallbacks(),
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    statsModel: StatsModel,
    consumerNamesSorted: List<String>,
    callbacks: StatsScreenCallbacks,
) {
  val performHaptic = rememberHaptic()

  Scaffold(
      topBar = {
        StatsTopBar(
            consumerName = statsModel.consumerName,
            consumerNamesSorted = consumerNamesSorted,
            onOpenSpreadsheet = callbacks.onOpenSpreadsheet,
            onChangeConsumerName = callbacks.onChangeConsumerName,
            onOpenToleranceChart = callbacks.navigateToToleranceChart,
        )
      },
  ) { padding ->
    if (!statsModel.areThereAnyIngestions) {
      Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        EmptyScreenDisclaimer(
            title = "Nothing to show yet",
            description =
                "Start logging your ingestions to see an overview of your consumption pattern.",
        )
      }
      return@Scaffold
    }

    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      PrimaryTabRow(
          selectedTabIndex = statsModel.selectedOption.tabIndex,
          contentColor = MaterialTheme.colorScheme.onSurface,
      ) {
        TimePickerOption.entries.forEachIndexed { index, option ->
          Tab(
              text = { Text(option.displayText) },
              selected = statsModel.selectedOption.tabIndex == index,
              onClick = {
                performHaptic(HapticType.SELECTION)
                callbacks.onTapOption(option)
              },
          )
        }
      }

      if (statsModel.statItems.isEmpty()) {
        EmptyScreenDisclaimer(
            title = "No ingestions in ${statsModel.selectedOption.emptyRangeText}",
            description = "Choose a longer duration range to see statistics.",
        )
      } else {
        StatsLoadedContent(statsModel = statsModel, callbacks = callbacks)
      }
    }
  }
}

@Composable
private fun StatsLoadedContent(
    statsModel: StatsModel,
    callbacks: StatsScreenCallbacks,
) {
  val spacing = LocalSpacing.current
  val performHaptic = rememberHaptic()
  val isDarkTheme = isSystemInDarkTheme()
  var selectedBucketIndex by
      remember(
          statsModel.selectedOption,
          statsModel.consumerName,
          statsModel.chartBuckets,
      ) {
        mutableStateOf<Int?>(null)
      }
  LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding =
          PaddingValues(
              start = spacing.lg,
              end = spacing.lg,
              top = spacing.md,
              bottom = spacing.xxl,
          ),
      verticalArrangement = Arrangement.spacedBy(spacing.md),
  ) {
    item(key = "overview_row") { StatsOverviewRow(statsModel = statsModel) }
    item(key = "activity_chart") {
      StatsActivityChartCard(
          statsModel = statsModel,
          selectedBucketIndex = selectedBucketIndex,
          isDarkTheme = isDarkTheme,
          onSelectBucket = { idx ->
            performHaptic(HapticType.TICK)
            selectedBucketIndex = if (selectedBucketIndex == idx) null else idx
          },
          onClearSelection = { selectedBucketIndex = null },
      )
    }
    item(key = "substances_header") {
      SubstancesSectionHeader(
          sortOption = statsModel.sortOption,
          onChangeSortOption = callbacks.onChangeSortOption,
      )
    }
    items(statsModel.statItems, key = { it.substanceName }) { subStat ->
      SubstanceStatCard(
          stat = subStat,
          isDarkTheme = isDarkTheme,
          onClick = {
            performHaptic(HapticType.SELECTION)
            callbacks.navigateToSubstanceCompanion(
                subStat.substanceName,
                statsModel.consumerName,
            )
          },
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsTopBar(
    consumerName: String?,
    consumerNamesSorted: List<String>,
    onOpenSpreadsheet: () -> Unit,
    onChangeConsumerName: (String?) -> Unit,
    onOpenToleranceChart: () -> Unit,
) {
  val performHaptic = rememberHaptic()
  TopAppBar(
      title = {
        Text(
            text = if (consumerName == null) "Statistics" else "Statistics for $consumerName",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
      },
      actions = {
        IconButton(onClick = onOpenSpreadsheet) {
          Icon(Icons.Outlined.GridOn, contentDescription = "Show as spreadsheet")
        }
        if (consumerNamesSorted.isNotEmpty()) {
          var isConsumerSelectionExpanded by remember { mutableStateOf(false) }
          IconButton(
              onClick = {
                performHaptic(HapticType.CLICK)
                isConsumerSelectionExpanded = true
              }
          ) {
            Icon(Icons.Outlined.Person, contentDescription = "Consumer")
          }
          DropdownMenu(
              expanded = isConsumerSelectionExpanded,
              onDismissRequest = { isConsumerSelectionExpanded = false },
          ) {
            ConsumerMenuItem(
                label = YOU,
                selected = consumerName == null,
                onClick = {
                  performHaptic(HapticType.SELECTION)
                  onChangeConsumerName(null)
                  isConsumerSelectionExpanded = false
                },
            )
            consumerNamesSorted.forEach { name ->
              ConsumerMenuItem(
                  label = name,
                  selected = consumerName == name,
                  onClick = {
                    performHaptic(HapticType.SELECTION)
                    onChangeConsumerName(name)
                    isConsumerSelectionExpanded = false
                  },
              )
            }
          }
        }
        IconButton(
            onClick = {
              performHaptic(HapticType.CLICK)
              onOpenToleranceChart()
            }
        ) {
          Icon(Icons.Outlined.Schedule, contentDescription = "Tolerance")
        }
      },
  )
}

@Composable
private fun ConsumerMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
  DropdownMenuItem(
      text = { Text(label) },
      onClick = onClick,
      leadingIcon = {
        if (selected) {
          Icon(
              Icons.Filled.Check,
              contentDescription = null,
              modifier = Modifier.size(ButtonDefaults.IconSize),
          )
        }
      },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsOverviewRow(statsModel: StatsModel) {
  val spacing = LocalSpacing.current
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
      OverviewMetricItem(label = "Ingestions", value = statsModel.totalIngestions.toString())
      OverviewMetricItem(label = "Experiences", value = statsModel.totalExperiences.toString())
      OverviewMetricItem(label = "Substances", value = statsModel.statItems.size.toString())
    }
  }
}

@Composable
private fun OverviewMetricItem(
    label: String,
    value: String,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
        text = value,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier.alignByBaseline(),
    )
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.alignByBaseline(),
    )
  }
}

@Composable
private fun StatsActivityChartCard(
    statsModel: StatsModel,
    selectedBucketIndex: Int?,
    isDarkTheme: Boolean,
    onSelectBucket: (Int) -> Unit,
    onClearSelection: () -> Unit,
) {
  val spacing = LocalSpacing.current

  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
      Text(
          text = "Ingestions since ${statsModel.startDateText}",
          style = MaterialTheme.typography.titleMedium,
      )
      val selectedBucket = selectedBucketIndex?.let { statsModel.chartBuckets.getOrNull(it) }
      if (selectedBucket != null) {
        SelectedStatsBucketStrip(
            bucket = selectedBucket,
            isDarkTheme = isDarkTheme,
            onDismiss = onClearSelection,
        )
      }
      BarChart(
          buckets = statsModel.chartBuckets,
          selectedIndex = selectedBucketIndex,
          onBarTapped = onSelectBucket,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedStatsBucketStrip(
    bucket: StatsChartBucket,
    isDarkTheme: Boolean,
    onDismiss: () -> Unit,
) {
  val spacing = LocalSpacing.current
  val countLabel =
      "${bucket.totalCount} ${if (bucket.totalCount == 1) "ingestion" else "ingestions"}"
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
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
            text = "${bucket.fullDateText} · $countLabel",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        if (bucket.items.isNotEmpty()) {
          FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            bucket.items.forEach { item ->
              Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Box(
                    modifier =
                        Modifier.size(8.dp)
                            .clip(CircleShape)
                            .background(item.color.getComposeColor(isDarkTheme))
                )
                val name = item.substanceName.ifBlank { "Substance" }
                Text(
                    text = "$name ×${item.count}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
              }
            }
          }
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
private fun SubstancesSectionHeader(
    sortOption: StatsSortOption,
    onChangeSortOption: (StatsSortOption) -> Unit,
) {
  val performHaptic = rememberHaptic()
  var isSortMenuExpanded by remember { mutableStateOf(false) }

  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("Substances", style = MaterialTheme.typography.titleMedium)
    Box {
      FilterChip(
          selected = false,
          onClick = { isSortMenuExpanded = true },
          label = { Text("Sort: ${sortOption.displayText}") },
          trailingIcon = {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
          },
      )
      DropdownMenu(
          expanded = isSortMenuExpanded,
          onDismissRequest = { isSortMenuExpanded = false },
      ) {
        StatsSortOption.entries.forEach { option ->
          DropdownMenuItem(
              text = { Text(option.displayText) },
              onClick = {
                performHaptic(HapticType.SELECTION)
                onChangeSortOption(option)
                isSortMenuExpanded = false
              },
              leadingIcon = {
                if (sortOption == option) {
                  Icon(
                      Icons.Filled.Check,
                      contentDescription = null,
                      modifier = Modifier.size(ButtonDefaults.IconSize),
                  )
                }
              },
          )
        }
      }
    }
  }
}

@Composable
private fun SubstanceStatCard(
    stat: StatItem,
    isDarkTheme: Boolean,
    onClick: () -> Unit,
) {
  val spacing = LocalSpacing.current
  val substanceColor = stat.color.getComposeColor(isDarkTheme)
  val primaryDoseText =
      when {
        stat.totalDose != null -> formatTotalDose(stat.totalDose)
        stat.knownTotalDose != null -> "${formatTotalDose(stat.knownTotalDose)}+"
        stat.hasMixedUnits -> "Mixed units"
        else -> "Unknown dose"
      }

  ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
          modifier =
              Modifier.width(6.dp)
                  .fillMaxHeight()
                  .clip(RoundedCornerShape(3.dp))
                  .background(substanceColor)
      )
      Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        SubstanceStatCardHeader(stat = stat, primaryDoseText = primaryDoseText)
        Text(
            text =
                "${stat.ingestionCount} ${if (stat.ingestionCount == 1) "ingestion" else "ingestions"} · " +
                    "${stat.experienceCount} ${if (stat.experienceCount == 1) "experience" else "experiences"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val metaLine = rememberMetaLine(stat)
        if (metaLine.isNotEmpty()) {
          Text(
              text = metaLine,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun SubstanceStatCardHeader(stat: StatItem, primaryDoseText: String) {
  val spacing = LocalSpacing.current
  Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(spacing.sm),
  ) {
    Text(
        text = stat.substanceName,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Column(horizontalAlignment = Alignment.End) {
      Text(
          text = primaryDoseText,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      if (stat.unknownDoseCount > 0 && (stat.knownTotalDose != null || stat.hasMixedUnits)) {
        Text(
            text = "${stat.unknownDoseCount} unknown",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
    )
  }
}

@Composable
private fun rememberMetaLine(stat: StatItem): String {
  val locales = LocalConfiguration.current.locales
  val locale = if (locales.isEmpty) Locale.ROOT else locales[0]
  return remember(stat.routeCounts, stat.lastUsed, locale) {
    val routes =
        stat.routeCounts.joinToString(" · ") {
          "${it.administrationRoute.displayText.lowercase(locale)} ${it.count}x"
        }
    val formattedDate =
        stat.lastUsed?.let { instant ->
          // Corrupt timestamps can fall outside the supported date range and throw
          runCatching { instant.getStringOfPattern("dd MMM yyyy") }.getOrNull()
        }
    buildList {
          if (routes.isNotBlank()) add(routes)
          if (formattedDate != null) add("Last $formattedDate")
        }
        .joinToString(" · ")
  }
}

private fun formatTotalDose(totalDose: TotalDose): String {
  val value = totalDose.dose.toReadableString()
  val uncertainty =
      totalDose.estimatedDoseStandardDeviation?.let { "±${it.toReadableString()}" }.orEmpty()
  val estimatePrefix =
      if (totalDose.isEstimate && totalDose.estimatedDoseStandardDeviation == null) "~" else ""
  return "$estimatePrefix$value$uncertainty ${totalDose.units}"
}
