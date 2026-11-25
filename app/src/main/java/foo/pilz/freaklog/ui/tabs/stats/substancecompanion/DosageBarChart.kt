package foo.pilz.freaklog.ui.tabs.stats.substancecompanion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
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
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.DoseClass
import kotlin.math.ceil

data class DosageBucket(
    val label: String, // short label, e.g. "Jan", "22.04"
    val fullDateText: String, // long label for tap info, e.g. "01 Jan 2026"
    val totalDose: Double,
    val sessionCount: Int,
    val unit: String,
    val endDateText: String? = null,
    val ingestionCount: Int = sessionCount,
) {
  /** Average dose per session; 0 when sessionCount == 0 */
  val avgDosePerSession: Double
    get() = if (sessionCount > 0) totalDose / sessionCount else 0.0
}

data class DoseThresholds(
    val unit: String,
    val lightMin: Double?,
    val commonMin: Double?,
    val strongMin: Double?,
    val heavyMin: Double?,
    val route: AdministrationRoute? = null,
)

enum class DosageMetric(val displayText: String) {
  TOTAL_DOSE("Dose"),
  SESSION_COUNT("Sessions"),
  AVG_DOSE_PER_SESSION("Avg"),
}

data class DosageBarChartConfig(
    val barColor: Color,
    val metric: DosageMetric = DosageMetric.TOTAL_DOSE,
    val showAverage: Boolean = false,
    val showTrendLine: Boolean = false,
    val doseThresholds: DoseThresholds? = null,
    val selectedIndex: Int? = null,
    val height: Dp = 200.dp,
)

private data class ChartColors(
    val barColor: Color,
    val labelColor: Color,
    val labelBackgroundColor: Color,
    val gridColor: Color,
    val axisColor: Color,
    val selectionColumnColor: Color,
    val averageLineColor: Color,
    val trendLineColor: Color,
    val emptyStubColor: Color,
    val thresholdColors: List<Color>,
)

private data class ChartPlotBounds(
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

@Composable
fun DosageBarChart(
    buckets: List<DosageBucket>,
    config: DosageBarChartConfig,
    modifier: Modifier = Modifier,
    onBarTapped: ((Int) -> Unit)? = null,
) {
  val yValues =
      remember(buckets, config.metric) {
        when (config.metric) {
          DosageMetric.TOTAL_DOSE -> buckets.map { it.totalDose }
          DosageMetric.SESSION_COUNT -> buckets.map { it.sessionCount.toDouble() }
          DosageMetric.AVG_DOSE_PER_SESSION -> buckets.map { it.avgDosePerSession }
        }
      }

  if (buckets.isEmpty() || yValues.all { it <= 0.0 }) {
    val emptyMessage =
        if (config.metric == DosageMetric.SESSION_COUNT || buckets.none { it.sessionCount > 0 }) {
          "No ingestions in this period"
        } else {
          "No known doses in this period"
        }
    EmptyDosageChartPlaceholder(
        message = emptyMessage,
        height = config.height,
        modifier = modifier,
    )
    return
  }

  DosageBarChartCanvas(
      buckets = buckets,
      yValues = yValues,
      config = config,
      modifier = modifier,
      onBarTapped = onBarTapped,
  )
}

@Composable
private fun EmptyDosageChartPlaceholder(
    message: String,
    height: Dp,
    modifier: Modifier = Modifier,
) {
  Box(
      modifier = modifier.fillMaxWidth().height(height),
      contentAlignment = Alignment.Center,
  ) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
          imageVector = Icons.Outlined.BarChart,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(28.dp),
      )
      Text(
          text = message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private data class DosageChartGeometry(
    val yScale: ChartYScale,
    val convertedThresholds: DoseThresholds?,
    val tickLabels: List<String>,
    val yAxisLabelWidth: Float,
    val labelHeight: Float,
    val chartDescription: String,
)

@Composable
private fun rememberDosageChartGeometry(
    buckets: List<DosageBucket>,
    yValues: List<Double>,
    config: DosageBarChartConfig,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
): DosageChartGeometry {
  val isIntegerMetric = config.metric == DosageMetric.SESSION_COUNT
  val yScale =
      remember(yValues, isIntegerMetric) {
        val maxValue = yValues.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
        computeChartYScale(maxValue, integerOnly = isIntegerMetric)
      }
  val unit = buckets.first().unit
  val showUnit = config.metric != DosageMetric.SESSION_COUNT && unit.isNotEmpty()
  val convertedThresholds =
      remember(config.doseThresholds, unit, config.metric) {
        if (config.metric == DosageMetric.SESSION_COUNT) null
        else convertDoseThresholds(config.doseThresholds, unit)
      }
  val density = LocalDensity.current
  val tickLabels =
      remember(yScale, showUnit, unit) {
        yScale.ticks.mapIndexed { index, tick ->
          formatSiValue(tick) + if (showUnit && index == yScale.ticks.lastIndex) " $unit" else ""
        }
      }
  val yAxisLabelWidth =
      remember(tickLabels, labelStyle, density) {
        val widest = tickLabels.maxOfOrNull { textMeasurer.measure(it, labelStyle).size.width } ?: 0
        with(density) { widest.toFloat() + 10.dp.toPx() }
      }
  val labelHeight =
      remember(labelStyle, textMeasurer) {
        textMeasurer.measure("0", labelStyle).size.height.toFloat()
      }
  val chartDescription =
      remember(buckets, config.metric) {
        val nonEmpty = buckets.count { it.totalDose > 0 || it.sessionCount > 0 }
        "${config.metric.displayText} chart with ${buckets.size} buckets and $nonEmpty non-empty buckets"
      }
  return DosageChartGeometry(
      yScale = yScale,
      convertedThresholds = convertedThresholds,
      tickLabels = tickLabels,
      yAxisLabelWidth = yAxisLabelWidth,
      labelHeight = labelHeight,
      chartDescription = chartDescription,
  )
}

@Composable
private fun DosageBarChartCanvas(
    buckets: List<DosageBucket>,
    yValues: List<Double>,
    config: DosageBarChartConfig,
    modifier: Modifier,
    onBarTapped: ((Int) -> Unit)?,
) {
  val isDarkTheme = isSystemInDarkTheme()
  val textMeasurer = rememberTextMeasurer()
  val labelStyle = MaterialTheme.typography.labelSmall
  val colors = rememberChartColors(config.barColor, isDarkTheme)
  val geometry = rememberDosageChartGeometry(buckets, yValues, config, textMeasurer, labelStyle)
  var canvasSize by remember { mutableStateOf(IntSize.Zero) }

  Box(
      modifier =
          modifier.fillMaxWidth().height(config.height).padding(horizontal = 4.dp).semantics {
            contentDescription = geometry.chartDescription
          }
  ) {
    Canvas(
        modifier =
            Modifier.fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .tapToSelectBar(buckets.size, geometry.yAxisLabelWidth, canvasSize, onBarTapped)
    ) {
      val topPad = (geometry.labelHeight / 2f + 4.dp.toPx()).coerceAtLeast(10.dp.toPx())
      val bottomPad = geometry.labelHeight + 10.dp.toPx()
      val bounds =
          ChartPlotBounds(
              yAxisLabelWidth = geometry.yAxisLabelWidth,
              totalWidth = size.width,
              plotTop = topPad,
              plotBottom = (size.height - bottomPad).coerceAtLeast(topPad + 1f),
          )
      drawYAxisGrid(
          bounds,
          geometry.yScale,
          geometry.tickLabels,
          textMeasurer,
          labelStyle,
          colors,
      )
      drawDosageBars(
          buckets = buckets,
          yValues = yValues,
          yMax = geometry.yScale.yMax,
          selectedIndex = config.selectedIndex,
          bounds = bounds,
          textMeasurer = textMeasurer,
          labelStyle = labelStyle,
          colors = colors,
      )
      drawThresholds(
          thresholds = geometry.convertedThresholds,
          yMax = geometry.yScale.yMax,
          bounds = bounds,
          labelHeight = geometry.labelHeight,
          textMeasurer = textMeasurer,
          labelStyle = labelStyle,
          colors = colors,
      )
      drawAverageAndTrendLines(
          yValues = yValues,
          yMax = geometry.yScale.yMax,
          metric = config.metric,
          showAverage = config.showAverage,
          showTrendLine = config.showTrendLine,
          bounds = bounds,
          colors = colors,
      )
    }
  }
}

@Composable
private fun rememberChartColors(barColor: Color, isDarkTheme: Boolean): ChartColors {
  val colorScheme = MaterialTheme.colorScheme
  return ChartColors(
      barColor = barColor,
      labelColor = colorScheme.onSurfaceVariant,
      labelBackgroundColor = colorScheme.surfaceContainerLow,
      gridColor = colorScheme.outlineVariant.copy(alpha = 0.55f),
      axisColor = colorScheme.outlineVariant,
      selectionColumnColor = colorScheme.onSurface.copy(alpha = 0.06f),
      averageLineColor = colorScheme.primary,
      trendLineColor = colorScheme.tertiary,
      emptyStubColor = colorScheme.outlineVariant.copy(alpha = 0.35f),
      thresholdColors =
          listOf(
              DoseClass.LIGHT.getComposeColor(isDarkTheme),
              DoseClass.COMMON.getComposeColor(isDarkTheme),
              DoseClass.STRONG.getComposeColor(isDarkTheme),
              DoseClass.HEAVY.getComposeColor(isDarkTheme),
          ),
  )
}

private fun Modifier.tapToSelectBar(
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
private fun DrawScope.drawYAxisGrid(
    bounds: ChartPlotBounds,
    yScale: ChartYScale,
    tickLabels: List<String>,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    colors: ChartColors,
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
private fun DrawScope.drawDosageBars(
    buckets: List<DosageBucket>,
    yValues: List<Double>,
    yMax: Double,
    selectedIndex: Int?,
    bounds: ChartPlotBounds,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    colors: ChartColors,
) {
  val barCount = buckets.size
  val barSpacing = bounds.plotWidth / barCount
  val barWidth = (barSpacing * 0.62f).coerceAtLeast(2.dp.toPx())
  val barGap = (barSpacing - barWidth) / 2f
  val widestLabel =
      buckets.maxOfOrNull { textMeasurer.measure(it.label, labelStyle).size.width } ?: 0
  val skip = ceil((widestLabel + 8.dp.toPx()) / barSpacing).toInt().coerceAtLeast(1)
  val minBarHeight = 3.dp.toPx()
  val stubHeight = 2.dp.toPx()
  val radius = (barWidth * 0.25f).coerceAtMost(4.dp.toPx())
  val minLabelGap = 6.dp.toPx()
  var lastLabelRight = Float.NEGATIVE_INFINITY

  buckets.forEachIndexed { idx, bucket ->
    val columnLeft = bounds.yAxisLabelWidth + idx * barSpacing
    if (selectedIndex == idx) {
      drawRect(
          color = colors.selectionColumnColor,
          topLeft = Offset(columnLeft, bounds.plotTop),
          size = Size(barSpacing, bounds.plotHeight),
      )
    }
    val x = columnLeft + barGap
    val value = yValues[idx]
    val dimFactor = if (selectedIndex != null && selectedIndex != idx) 0.42f else 1f
    if (value > 0.0) {
      val barHeight =
          ((value / yMax * bounds.plotHeight).toFloat()).coerceIn(minBarHeight, bounds.plotHeight)
      val top = bounds.plotBottom - barHeight
      drawTopRoundedBar(
          color = colors.barColor.copy(alpha = colors.barColor.alpha * dimFactor),
          topLeft = Offset(x, top),
          size = Size(barWidth, barHeight),
          radius = radius.coerceAtMost(barHeight / 2f),
      )
    } else {
      drawRoundRect(
          color = colors.emptyStubColor.copy(alpha = colors.emptyStubColor.alpha * dimFactor),
          topLeft = Offset(x, bounds.plotBottom - stubHeight),
          size = Size(barWidth, stubHeight),
          cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
      )
    }
    if (idx % skip == 0) {
      val tr = textMeasurer.measure(bucket.label, labelStyle.copy(color = colors.labelColor))
      val cx = columnLeft + barSpacing / 2f
      val left =
          (cx - tr.size.width / 2f)
              .coerceAtMost(bounds.totalWidth - tr.size.width)
              .coerceAtLeast(bounds.yAxisLabelWidth)
      if (left >= lastLabelRight + minLabelGap) {
        drawText(tr, topLeft = Offset(left, bounds.plotBottom + 6.dp.toPx()))
        lastLabelRight = left + tr.size.width
      }
    }
  }
}

private fun DrawScope.drawTopRoundedBar(
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

@Suppress("LongParameterList")
private fun DrawScope.drawThresholds(
    thresholds: DoseThresholds?,
    yMax: Double,
    bounds: ChartPlotBounds,
    labelHeight: Float,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    colors: ChartColors,
) {
  if (thresholds == null) return
  val levels =
      listOf(
          "light" to thresholds.lightMin,
          "common" to thresholds.commonMin,
          "strong" to thresholds.strongMin,
          "heavy" to thresholds.heavyMin,
      )
  val placed =
      placeThresholdLabels(
          levels = levels,
          yMax = yMax,
          plotTop = bounds.plotTop,
          plotBottom = bounds.plotBottom,
          labelHeight = labelHeight,
          minGapPx = 3.dp.toPx(),
      )
  val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
  placed.forEach { item ->
    val baseColor = colors.thresholdColors[item.levelIndex]
    val lineColor = baseColor.copy(alpha = 0.65f)
    drawLine(
        color = lineColor,
        start = Offset(bounds.yAxisLabelWidth, item.lineY),
        end = Offset(bounds.totalWidth - 2.dp.toPx(), item.lineY),
        strokeWidth = 1.dp.toPx(),
        pathEffect = dash,
    )
    val labelTopY = item.labelTopY ?: return@forEach
    val tr = textMeasurer.measure(item.name, labelStyle.copy(color = baseColor))
    val padX = 3.dp.toPx()
    val labelX =
        (bounds.totalWidth - tr.size.width - padX - 2.dp.toPx()).coerceAtLeast(
            bounds.yAxisLabelWidth + padX
        )
    drawRoundRect(
        color = colors.labelBackgroundColor.copy(alpha = 0.92f),
        topLeft = Offset(labelX - padX, labelTopY),
        size = Size(tr.size.width + padX * 2f, tr.size.height.toFloat()),
        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
    )
    drawText(textLayoutResult = tr, topLeft = Offset(labelX, labelTopY))
  }
}

@Suppress("LongParameterList")
private fun DrawScope.drawAverageAndTrendLines(
    yValues: List<Double>,
    yMax: Double,
    metric: DosageMetric,
    showAverage: Boolean,
    showTrendLine: Boolean,
    bounds: ChartPlotBounds,
    colors: ChartColors,
) {
  if ((!showAverage && !showTrendLine) || yValues.isEmpty()) return
  val stats =
      computeDosageChartSummaryStats(
          yValues = yValues,
          activeOnly = metric == DosageMetric.AVG_DOSE_PER_SESSION,
      )
  if (showAverage && stats.average > 0.0 && stats.average <= yMax) {
    val avgY =
        (bounds.plotBottom - (stats.average / yMax * bounds.plotHeight).toFloat()).coerceIn(
            bounds.plotTop,
            bounds.plotBottom,
        )
    drawLine(
        color = colors.averageLineColor,
        start = Offset(bounds.yAxisLabelWidth, avgY),
        end = Offset(bounds.totalWidth, avgY),
        strokeWidth = 1.75.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()), 0f),
    )
  }
  if (showTrendLine && stats.hasTrend) {
    val y0 =
        (bounds.plotBottom -
                ((stats.trendIntercept + stats.trendSlope * stats.trendStartIndex) / yMax *
                    bounds.plotHeight))
            .toFloat()
    val y1 =
        (bounds.plotBottom -
                ((stats.trendIntercept + stats.trendSlope * stats.trendEndIndex) / yMax *
                    bounds.plotHeight))
            .toFloat()
    if (y0.isFinite() && y1.isFinite()) {
      val barSpacing = bounds.plotWidth / yValues.size
      val x0 = bounds.yAxisLabelWidth + (stats.trendStartIndex + 0.5f) * barSpacing
      val x1 = bounds.yAxisLabelWidth + (stats.trendEndIndex + 0.5f) * barSpacing
      drawLine(
          color = colors.trendLineColor,
          start = Offset(x0, y0.coerceIn(bounds.plotTop, bounds.plotBottom)),
          end = Offset(x1, y1.coerceIn(bounds.plotTop, bounds.plotBottom)),
          strokeWidth = 2.dp.toPx(),
          cap = StrokeCap.Round,
      )
    }
  }
}
