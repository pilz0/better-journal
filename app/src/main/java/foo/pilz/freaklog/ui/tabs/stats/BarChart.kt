/*
 * Copyright (c) 2022. Isaak Hanimann.
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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import foo.pilz.freaklog.ui.tabs.stats.substancecompanion.ChartYScale
import foo.pilz.freaklog.ui.tabs.stats.substancecompanion.computeChartYScale
import foo.pilz.freaklog.ui.tabs.stats.substancecompanion.formatSiValue
import kotlin.math.ceil

private data class StatsBarChartColors(
    val labelColor: Color,
    val gridColor: Color,
    val axisColor: Color,
    val selectionColumnColor: Color,
    val emptyStubColor: Color,
)

private data class StatsPlotBounds(
    val yAxisLabelWidth: Float,
    val totalWidth: Float,
    val plotTop: Float,
    val plotBottom: Float,
) {
  val plotWidth: Float
    get() = (totalWidth - yAxisLabelWidth).coerceAtLeast(1f)

  val plotHeight: Float
    get() = (plotBottom - plotTop).coerceAtLeast(1f)
}

internal data class StatsBarGeometry(
    val barSpacing: Float,
    val barWidth: Float,
    val barGap: Float,
)

internal data class PlacedXAxisLabel(
    val bucketIndex: Int,
    val leftX: Float,
)

internal fun computeStatsBarGeometry(
    plotWidth: Float,
    bucketCount: Int,
    minBarWidthPx: Float,
    maxBarWidthPx: Float,
): StatsBarGeometry {
  val safeCount = bucketCount.coerceAtLeast(1)
  val spacing = plotWidth.coerceAtLeast(1f) / safeCount
  val maxAllowedWidth = maxBarWidthPx.coerceAtMost((spacing - 1f).coerceAtLeast(1f))
  val minAllowedWidth = minBarWidthPx.coerceAtMost(maxAllowedWidth)
  val width = (spacing * 0.72f).coerceIn(minAllowedWidth, maxAllowedWidth)
  val gap = ((spacing - width) / 2f).coerceAtLeast(0f)
  return StatsBarGeometry(barSpacing = spacing, barWidth = width, barGap = gap)
}

@Suppress("LongParameterList")
internal fun placeStatsXAxisLabels(
    labelWidths: List<Int>,
    plotLeft: Float,
    totalWidth: Float,
    barSpacing: Float,
    targetGapPx: Float,
    minGapPx: Float,
): List<PlacedXAxisLabel> {
  if (labelWidths.isEmpty() || barSpacing <= 0f || totalWidth <= plotLeft) return emptyList()
  val widest = labelWidths.maxOrNull() ?: 0
  val skip = ceil((widest + targetGapPx) / barSpacing).toInt().coerceAtLeast(1)
  val startIdx = (ceil((widest / 2f) / barSpacing).toInt() - 1).coerceIn(0, labelWidths.lastIndex)
  val result = mutableListOf<PlacedXAxisLabel>()
  var lastRight = Float.NEGATIVE_INFINITY
  for (idx in startIdx..labelWidths.lastIndex step skip) {
    val w = labelWidths[idx].toFloat()
    val cx = plotLeft + idx * barSpacing + barSpacing / 2f
    val left =
        (cx - w / 2f).coerceAtMost((totalWidth - w).coerceAtLeast(plotLeft)).coerceAtLeast(plotLeft)
    if (left >= lastRight + minGapPx) {
      result += PlacedXAxisLabel(bucketIndex = idx, leftX = left)
      lastRight = left + w
    }
  }
  return result
}

@Composable
fun BarChart(
    buckets: List<StatsChartBucket>,
    modifier: Modifier = Modifier,
    height: Dp = 240.dp,
    selectedIndex: Int? = null,
    onBarTapped: ((Int) -> Unit)? = null,
) {
  if (buckets.isEmpty()) return

  val isDarkTheme = isSystemInDarkTheme()
  val textMeasurer = rememberTextMeasurer()
  val labelStyle = MaterialTheme.typography.labelSmall
  val colorScheme = MaterialTheme.colorScheme
  val colors =
      StatsBarChartColors(
          labelColor = colorScheme.onSurfaceVariant,
          gridColor = colorScheme.outlineVariant.copy(alpha = 0.55f),
          axisColor = colorScheme.outlineVariant,
          selectionColumnColor = colorScheme.onSurface.copy(alpha = 0.06f),
          emptyStubColor = colorScheme.outlineVariant.copy(alpha = 0.35f),
      )

  val yScale =
      remember(buckets) {
        val maxCount = buckets.maxOfOrNull { it.totalCount }?.coerceAtLeast(1) ?: 1
        computeChartYScale(maxCount.toDouble(), integerOnly = true)
      }
  val tickLabels = remember(yScale) { yScale.ticks.map(::formatSiValue) }
  val density = LocalDensity.current
  val yAxisLabelWidth =
      remember(tickLabels, labelStyle, density) {
        val widest = tickLabels.maxOfOrNull { textMeasurer.measure(it, labelStyle).size.width } ?: 0
        with(density) { widest.toFloat() + 6.dp.toPx() }
      }
  val labelHeight =
      remember(labelStyle, textMeasurer) {
        textMeasurer.measure("0", labelStyle).size.height.toFloat()
      }

  var canvasSize by remember { mutableStateOf(IntSize.Zero) }
  val totalIngestions = remember(buckets) { buckets.sumOf { it.totalCount } }

  StatsBarChartCanvas(
      buckets = buckets,
      yScale = yScale,
      tickLabels = tickLabels,
      yAxisLabelWidth = yAxisLabelWidth,
      labelHeight = labelHeight,
      totalIngestions = totalIngestions,
      height = height,
      selectedIndex = selectedIndex,
      isDarkTheme = isDarkTheme,
      textMeasurer = textMeasurer,
      labelStyle = labelStyle,
      colors = colors,
      canvasSize = canvasSize,
      onCanvasSizeChanged = { canvasSize = it },
      onBarTapped = onBarTapped,
      modifier = modifier,
  )
}

@Suppress("LongParameterList")
@Composable
private fun StatsBarChartCanvas(
    buckets: List<StatsChartBucket>,
    yScale: ChartYScale,
    tickLabels: List<String>,
    yAxisLabelWidth: Float,
    labelHeight: Float,
    totalIngestions: Int,
    height: Dp,
    selectedIndex: Int?,
    isDarkTheme: Boolean,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    colors: StatsBarChartColors,
    canvasSize: IntSize,
    onCanvasSizeChanged: (IntSize) -> Unit,
    onBarTapped: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
  Box(
      modifier =
          modifier.fillMaxWidth().height(height).semantics {
            contentDescription = "Ingestions chart showing $totalIngestions ingestions"
          }
  ) {
    Canvas(
        modifier =
            Modifier.fillMaxSize()
                .onSizeChanged(onCanvasSizeChanged)
                .tapToSelectStatsBucket(buckets.size, yAxisLabelWidth, canvasSize, onBarTapped)
    ) {
      val topPad = (labelHeight / 2f + 4.dp.toPx()).coerceAtLeast(10.dp.toPx())
      val bottomPad = labelHeight + 8.dp.toPx()
      val bounds =
          StatsPlotBounds(
              yAxisLabelWidth = yAxisLabelWidth,
              totalWidth = size.width,
              plotTop = topPad,
              plotBottom = (size.height - bottomPad).coerceAtLeast(topPad + 1f),
          )
      drawStatsGrid(bounds, yScale, tickLabels, textMeasurer, labelStyle, colors)
      drawStatsStackedBars(
          buckets = buckets,
          yMax = yScale.yMax,
          selectedIndex = selectedIndex,
          isDarkTheme = isDarkTheme,
          bounds = bounds,
          textMeasurer = textMeasurer,
          labelStyle = labelStyle,
          colors = colors,
      )
    }
  }
}

private fun Modifier.tapToSelectStatsBucket(
    bucketCount: Int,
    yAxisLabelWidth: Float,
    canvasSize: IntSize,
    onBarTapped: ((Int) -> Unit)?,
): Modifier {
  if (onBarTapped == null) return this
  return pointerInput(bucketCount, yAxisLabelWidth, canvasSize.width) {
    detectTapGestures { tap ->
      val chartW = canvasSize.width.toFloat() - yAxisLabelWidth
      val spacing = if (bucketCount > 0) chartW / bucketCount else 0f
      if (spacing > 0f && tap.x >= yAxisLabelWidth) {
        val idx = ((tap.x - yAxisLabelWidth) / spacing).toInt().coerceIn(0, bucketCount - 1)
        onBarTapped(idx)
      }
    }
  }
}

@Suppress("LongParameterList")
private fun DrawScope.drawStatsGrid(
    bounds: StatsPlotBounds,
    yScale: ChartYScale,
    tickLabels: List<String>,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    colors: StatsBarChartColors,
) {
  val count = yScale.gridLines.coerceAtLeast(1)
  for (i in 0..count) {
    val frac = i.toFloat() / count
    val y = bounds.plotBottom - bounds.plotHeight * frac
    drawLine(
        color = if (i == 0) colors.axisColor else colors.gridColor,
        start = Offset(bounds.yAxisLabelWidth, y),
        end = Offset(bounds.totalWidth, y),
        strokeWidth = 1.dp.toPx(),
    )
    val labelText = tickLabels.getOrNull(i) ?: continue
    val tr = textMeasurer.measure(labelText, labelStyle.copy(color = colors.labelColor))
    drawText(
        textLayoutResult = tr,
        topLeft =
            Offset(
                x = (bounds.yAxisLabelWidth - tr.size.width - 6.dp.toPx()).coerceAtLeast(0f),
                y = y - tr.size.height / 2f,
            ),
    )
  }
}

@Suppress("LongParameterList")
private fun DrawScope.drawStatsStackedBars(
    buckets: List<StatsChartBucket>,
    yMax: Double,
    selectedIndex: Int?,
    isDarkTheme: Boolean,
    bounds: StatsPlotBounds,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    colors: StatsBarChartColors,
) {
  val barGeometry =
      computeStatsBarGeometry(
          plotWidth = bounds.plotWidth,
          bucketCount = buckets.size,
          minBarWidthPx = 2.5.dp.toPx(),
          maxBarWidthPx = 32.dp.toPx(),
      )
  val minTotalBarHeight = 3.dp.toPx()
  val stubHeight = 2.dp.toPx()
  val radius = (barGeometry.barWidth * 0.25f).coerceAtMost(4.dp.toPx())

  buckets.forEachIndexed { idx, bucket ->
    drawSingleStatsBucket(
        idx = idx,
        bucket = bucket,
        yMax = yMax,
        barSpacing = barGeometry.barSpacing,
        barWidth = barGeometry.barWidth,
        barGap = barGeometry.barGap,
        minTotalBarHeight = minTotalBarHeight,
        stubHeight = stubHeight,
        radius = radius,
        selectedIndex = selectedIndex,
        isDarkTheme = isDarkTheme,
        bounds = bounds,
        colors = colors,
    )
  }
  drawStatsXAxisLabels(
      buckets = buckets,
      barSpacing = barGeometry.barSpacing,
      bounds = bounds,
      textMeasurer = textMeasurer,
      labelStyle = labelStyle,
      colors = colors,
  )
}

@Suppress("LongParameterList")
private fun DrawScope.drawStatsXAxisLabels(
    buckets: List<StatsChartBucket>,
    barSpacing: Float,
    bounds: StatsPlotBounds,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    colors: StatsBarChartColors,
) {
  val coloredStyle = labelStyle.copy(color = colors.labelColor)
  val measured = buckets.map { textMeasurer.measure(it.label, coloredStyle) }
  val placements =
      placeStatsXAxisLabels(
          labelWidths = measured.map { it.size.width },
          plotLeft = bounds.yAxisLabelWidth,
          totalWidth = bounds.totalWidth,
          barSpacing = barSpacing,
          targetGapPx = 8.dp.toPx(),
          minGapPx = 4.dp.toPx(),
      )
  val labelTopY = bounds.plotBottom + 6.dp.toPx()
  for (placed in placements) {
    drawText(measured[placed.bucketIndex], topLeft = Offset(placed.leftX, labelTopY))
  }
}

@Suppress("LongParameterList")
private fun DrawScope.drawSingleStatsBucket(
    idx: Int,
    bucket: StatsChartBucket,
    yMax: Double,
    barSpacing: Float,
    barWidth: Float,
    barGap: Float,
    minTotalBarHeight: Float,
    stubHeight: Float,
    radius: Float,
    selectedIndex: Int?,
    isDarkTheme: Boolean,
    bounds: StatsPlotBounds,
    colors: StatsBarChartColors,
) {
  val columnLeft = bounds.yAxisLabelWidth + idx * barSpacing
  if (selectedIndex == idx) {
    drawRect(
        color = colors.selectionColumnColor,
        topLeft = Offset(columnLeft, bounds.plotTop),
        size = Size(barSpacing, bounds.plotHeight),
    )
  }
  val x = columnLeft + barGap
  val dimFactor = if (selectedIndex != null && selectedIndex != idx) 0.42f else 1f
  val totalCount = bucket.totalCount
  if (totalCount > 0 && bucket.items.isNotEmpty()) {
    val totalHeight =
        ((totalCount / yMax * bounds.plotHeight).toFloat()).coerceIn(
            minTotalBarHeight,
            bounds.plotHeight,
        )
    var yBottom = bounds.plotBottom
    bucket.items.forEachIndexed { segIndex, item ->
      val segHeight = totalHeight * (item.count.toFloat() / totalCount.toFloat())
      val yTop = (yBottom - segHeight).coerceAtLeast(bounds.plotTop)
      val segColor =
          item.color.getComposeColor(isDarkTheme).let { it.copy(alpha = it.alpha * dimFactor) }
      val actualHeight = (yBottom - yTop).coerceAtLeast(0.5f)
      if (segIndex == bucket.items.lastIndex) {
        drawTopRoundedStackCap(
            color = segColor,
            topLeft = Offset(x, yTop),
            size = Size(barWidth, actualHeight),
            radius = radius.coerceAtMost(actualHeight / 2f),
        )
      } else {
        drawRect(color = segColor, topLeft = Offset(x, yTop), size = Size(barWidth, actualHeight))
      }
      yBottom = yTop
    }
  } else {
    drawRoundRect(
        color = colors.emptyStubColor.copy(alpha = colors.emptyStubColor.alpha * dimFactor),
        topLeft = Offset(x, bounds.plotBottom - stubHeight),
        size = Size(barWidth, stubHeight),
        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
    )
  }
}

private fun DrawScope.drawTopRoundedStackCap(
    color: Color,
    topLeft: Offset,
    size: Size,
    radius: Float,
) {
  val path =
      Path().apply {
        addRoundRect(
            RoundRect(
                left = topLeft.x,
                top = topLeft.y,
                right = topLeft.x + size.width,
                bottom = topLeft.y + size.height,
                topLeftCornerRadius = CornerRadius(radius, radius),
                topRightCornerRadius = CornerRadius(radius, radius),
                bottomLeftCornerRadius = CornerRadius.Zero,
                bottomRightCornerRadius = CornerRadius.Zero,
            )
        )
      }
  drawPath(path = path, color = color)
}
