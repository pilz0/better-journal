package foo.pilz.freaklog.ui.tabs.stats.substancecompanion

import foo.pilz.freaklog.data.substances.classes.roa.DoseUnits
import foo.pilz.freaklog.data.substances.classes.roa.RoaDose
import foo.pilz.freaklog.ui.tabs.search.substance.roa.toReadableString
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal data class ChartYScale(
    val yMax: Double,
    val gridLines: Int,
    val ticks: List<Double>,
)

internal data class PlacedThresholdLabel(
    val name: String,
    val value: Double,
    val lineY: Float,
    val labelTopY: Float?,
    val levelIndex: Int,
)

internal enum class TrendDirection {
  RISING,
  FALLING,
  STEADY,
}

internal data class DosageChartSummaryStats(
    val average: Double,
    val trendSlope: Double,
    val trendDirection: TrendDirection,
    val trendIntercept: Double = 0.0,
    val trendStartIndex: Int = 0,
    val trendEndIndex: Int = 0,
    val hasTrend: Boolean = false,
)

internal fun formatSiValue(v: Double): String {
  if (!v.isFinite() || v <= 0.0) return "0"
  return when {
    v >= 1_000_000 -> "${formatCompactScaled(v / 1_000_000.0)}M"
    v >= 1_000 -> "${formatCompactScaled(v / 1_000.0)}k"
    v >= 0.001 -> v.toReadableString()
    else -> String.format(Locale.US, "%.4f", v).trimEnd('0').trimEnd('.')
  }
}

private fun formatCompactScaled(scaled: Double): String {
  if (scaled >= 100.0) return scaled.roundToLong().toString()
  val tenths = (scaled * 10.0).roundToLong() / 10.0
  val whole = tenths.roundToLong()
  return if (abs(tenths - whole) < 1e-6) {
    whole.toString()
  } else {
    String.format(Locale.US, "%.1f", tenths)
  }
}

internal fun niceMax(maxVal: Double): Double {
  if (!maxVal.isFinite() || maxVal <= 0.0) return 1.0
  val mag = 10.0.pow(floor(log10(maxVal)))
  val norm = maxVal / mag
  val nice =
      when {
        norm <= 1.0 -> 1.0
        norm <= 2.0 -> 2.0
        norm <= 5.0 -> 5.0
        else -> 10.0
      }
  return nice * mag
}

internal fun formatSummaryDose(amount: Double, unit: String): String {
  if (!amount.isFinite()) return "—"
  val trimmedUnit = unit.trim()
  if (trimmedUnit.isEmpty()) return amount.toReadableString()
  if (amount >= 1000.0) {
    val base = DoseUnits.toBase(trimmedUnit)
    if (base != null && (base.first == "mg" || base.first == "ml")) {
      val amountInBase = amount * base.second
      val (scaledUnit, divisor) = DoseUnits.displayUnit(base.first, amountInBase)
      if (divisor > base.second) {
        return "${(amountInBase / divisor).toReadableString()} $scaledUnit"
      }
    }
  }
  return "${amount.toReadableString()} $trimmedUnit"
}

internal fun convertDoseThresholds(
    thresholds: DoseThresholds?,
    targetUnit: String?,
): DoseThresholds? {
  if (thresholds == null || targetUnit.isNullOrBlank()) return null
  if (RoaDose.isMatchingUnit(thresholds.unit, targetUnit)) {
    return thresholds.copy(unit = targetUnit)
  }
  val (fromBase, fromFactor) = DoseUnits.toBase(thresholds.unit) ?: return null
  val (toBase, toFactor) = DoseUnits.toBase(targetUnit) ?: return null
  if (fromBase != toBase || toFactor <= 0.0) return null
  val scale = fromFactor / toFactor
  return thresholds.copy(
      unit = targetUnit,
      lightMin = thresholds.lightMin?.times(scale),
      commonMin = thresholds.commonMin?.times(scale),
      strongMin = thresholds.strongMin?.times(scale),
      heavyMin = thresholds.heavyMin?.times(scale),
  )
}

internal fun computeChartYScale(maxValue: Double, integerOnly: Boolean): ChartYScale {
  val safeMax = if (maxValue.isFinite() && maxValue > 0.0) maxValue else 1.0
  if (!integerOnly) {
    val yMax = niceMax(safeMax)
    val gridLines = 4
    return ChartYScale(
        yMax = yMax,
        gridLines = gridLines,
        ticks = (0..gridLines).map { yMax * it / gridLines },
    )
  }
  val maxLong = ceil(safeMax).toLong().coerceAtLeast(1L)
  if (maxLong <= 5L) {
    val count = maxLong.toInt()
    return ChartYScale(
        yMax = maxLong.toDouble(),
        gridLines = count,
        ticks = (0..count).map { it.toDouble() },
    )
  }
  val baseMag = 10.0.pow(floor(log10(maxLong / 5.0)).coerceAtLeast(0.0))
  val stepMultipliers = listOf(1.0, 2.0, 2.5, 5.0, 10.0, 20.0, 25.0, 50.0)
  val gridCounts = listOf(4, 3, 5)
  var bestStep = maxLong
  var bestGridLines = 4
  var bestYMax = Long.MAX_VALUE
  for (mult in stepMultipliers) {
    val raw = baseMag * mult
    val step = raw.roundToLong()
    if (step >= 1L && abs(raw - step.toDouble()) < 1e-6) {
      for (lines in gridCounts) {
        val candidateMax = step * lines
        if (candidateMax in maxLong until bestYMax) {
          bestYMax = candidateMax
          bestStep = step
          bestGridLines = lines
        }
      }
    }
  }
  return ChartYScale(
      yMax = bestYMax.toDouble(),
      gridLines = bestGridLines,
      ticks = (0..bestGridLines).map { (it * bestStep).toDouble() },
  )
}

@Suppress("LongParameterList")
internal fun placeThresholdLabels(
    levels: List<Pair<String, Double?>>,
    yMax: Double,
    plotTop: Float,
    plotBottom: Float,
    labelHeight: Float,
    minGapPx: Float,
): List<PlacedThresholdLabel> {
  if (!yMax.isFinite() || yMax <= 0.0) return emptyList()
  val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)
  val valid = levels.mapIndexedNotNull { index, (name, value) ->
    if (value == null || !value.isFinite() || value <= 0.0 || value > yMax) {
      null
    } else {
      Triple(index, name, value)
    }
  }
  // Place higher-severity labels first so critical levels win if thresholds bunch near the floor.
  val sortedDescending = valid.sortedByDescending { it.third }
  val placedTops = mutableListOf<Float>()
  val maxTop = (plotBottom - labelHeight).coerceAtLeast(plotTop)
  val placed = sortedDescending.map { (index, name, value) ->
    val lineY = plotBottom - (value / yMax * plotHeight).toFloat()
    val desiredTop = (lineY - labelHeight - minGapPx * 0.5f).coerceIn(plotTop, maxTop)
    val collides = placedTops.any { abs(desiredTop - it) < labelHeight + minGapPx }
    val labelTopY =
        if (collides) {
          null
        } else {
          placedTops.add(desiredTop)
          desiredTop
        }
    PlacedThresholdLabel(
        name = name,
        value = value,
        lineY = lineY,
        labelTopY = labelTopY,
        levelIndex = index,
    )
  }
  return placed.sortedBy { it.levelIndex }
}

internal fun computeDosageChartSummaryStats(
    yValues: List<Double>,
    activeOnly: Boolean = false,
): DosageChartSummaryStats {
  if (yValues.isEmpty()) {
    return DosageChartSummaryStats(0.0, 0.0, TrendDirection.STEADY)
  }
  val points =
      if (activeOnly) {
        yValues.mapIndexedNotNull { i, v -> if (v > 0.0) i.toDouble() to v else null }
      } else {
        yValues.mapIndexed { i, v -> i.toDouble() to v }
      }
  if (points.isEmpty()) {
    return DosageChartSummaryStats(0.0, 0.0, TrendDirection.STEADY)
  }
  val average = points.map { it.second }.average()
  if (points.size < 2 || points.all { it.second <= 0.0 }) {
    return DosageChartSummaryStats(
        average = average,
        trendSlope = 0.0,
        trendDirection = TrendDirection.STEADY,
        trendIntercept = average,
    )
  }
  val xMean = points.map { it.first }.average()
  val ssxx = points.sumOf { (x, _) -> (x - xMean).let { it * it } }
  val ssxy = points.sumOf { (x, y) -> (x - xMean) * (y - average) }
  val slope = if (ssxx > 0.0) ssxy / ssxx else 0.0
  val intercept = average - slope * xMean
  val startIndex = points.first().first.roundToInt()
  val endIndex = points.last().first.roundToInt()
  val totalDelta = slope * (endIndex - startIndex)
  val direction =
      when {
        abs(totalDelta) <= average.coerceAtLeast(1e-9) * 0.05 -> TrendDirection.STEADY
        slope > 0.0 -> TrendDirection.RISING
        else -> TrendDirection.FALLING
      }
  return DosageChartSummaryStats(
      average = average,
      trendSlope = slope,
      trendDirection = direction,
      trendIntercept = intercept,
      trendStartIndex = startIndex,
      trendEndIndex = endIndex,
      hasTrend = ssxx > 0.0,
  )
}
