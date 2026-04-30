package foo.pilz.freaklog.ui.tabs.stats.substancecompanion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

// ── Data models ──────────────────────────────────────────────────────────────

data class DosageBucket(
    val label: String,       // short label, e.g. "Jan", "W01", "04"
    val fullDateText: String, // long label for tap info, e.g. "01 Jan 2026"
    val totalDose: Double,
    val sessionCount: Int,
    val unit: String
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
    val heavyMin: Double?
)

enum class DosageMetric(val displayText: String) {
    TOTAL_DOSE("Dose"),
    SESSION_COUNT("Sessions"),
    AVG_DOSE_PER_SESSION("Avg/Session")
}

data class ChartSummary(
    val totalSessions: Int,
    val longestGapDays: Int?,
    val currentStreakWeeks: Int
)

/** Used for drawing horizontal dose-threshold lines inside [DosageBarChart]. */
private data class ThresholdEntry(val name: String, val value: Double?, val color: Color)

// ── Private helpers ───────────────────────────────────────────────────────────

internal fun formatSiValue(v: Double): String = when {
    v >= 1_000_000 -> "%.1fM".format(v / 1_000_000)
    v >= 1_000     -> "%.1fk".format(v / 1_000)
    v >= 10        -> v.toLong().toString()
    v > 0          -> "%.1f".format(v)
    else           -> "0"
}

internal fun niceMax(maxVal: Double): Double {
    if (maxVal <= 0) return 1.0
    val mag = 10.0.pow(floor(log10(maxVal)))
    val norm = maxVal / mag
    val nice = when {
        norm <= 1.0 -> 1.0
        norm <= 2.0 -> 2.0
        norm <= 5.0 -> 5.0
        else        -> 10.0
    }
    return nice * mag
}

// ── Chart composable ──────────────────────────────────────────────────────────

@Composable
fun DosageBarChart(
    buckets: List<DosageBucket>,
    barColor: Color,
    showAverage: Boolean,
    showTrendLine: Boolean = false,
    metric: DosageMetric = DosageMetric.TOTAL_DOSE,
    doseThresholds: DoseThresholds? = null,
    onBarTapped: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp
) {
    if (buckets.isEmpty()) return

    val textMeasurer     = rememberTextMeasurer()
    val labelStyle       = MaterialTheme.typography.labelSmall
    val labelColor       = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor        = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val averageLineColor = MaterialTheme.colorScheme.primary          // #9 fix: was Color.White
    val trendLineColor   = MaterialTheme.colorScheme.tertiary
    val emptyStubColor   = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    // Threshold line colours keyed by threshold name
    val lightCol  = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val commonCol = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    val strongCol = MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
    val heavyCol  = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)

    // Y-values according to the selected metric
    val yValues: List<Double> = when (metric) {
        DosageMetric.TOTAL_DOSE        -> buckets.map { it.totalDose }
        DosageMetric.SESSION_COUNT     -> buckets.map { it.sessionCount.toDouble() }
        DosageMetric.AVG_DOSE_PER_SESSION -> buckets.map { it.avgDosePerSession }
    }
    val maxValue = yValues.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val yMax = niceMax(maxValue)
    val gridLines = 4

    val unit = buckets.first().unit
    val showUnit = metric != DosageMetric.SESSION_COUNT && unit.isNotEmpty()

    // Compute y-axis label width once (textMeasurer.measure is safe outside DrawScope).
    // Use density-aware padding so spacing looks correct on high-DPI screens.
    val density = LocalDensity.current
    val yAxisSample = formatSiValue(yMax) + if (showUnit) " $unit" else ""
    val yAxisLabelWidth: Float = remember(yAxisSample, labelStyle, density) {
        with(density) { textMeasurer.measure(yAxisSample, labelStyle).size.width.toFloat() + 12.dp.toPx() }
    }

    // Canvas size tracked for tap geometry
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 4.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .then(
                    if (onBarTapped != null) {
                        Modifier.pointerInput(buckets.size, yAxisLabelWidth) {
                            detectTapGestures { tap ->
                                val chartW = canvasSize.width.toFloat() - yAxisLabelWidth
                                val spacing = if (buckets.isNotEmpty()) chartW / buckets.size else 0f
                                if (spacing > 0 && tap.x >= yAxisLabelWidth) {
                                    val idx = ((tap.x - yAxisLabelWidth) / spacing).toInt()
                                        .coerceIn(0, buckets.lastIndex)
                                    onBarTapped(idx)
                                }
                            }
                        }
                    } else Modifier
                )
        ) {
            val w = size.width
            val h = size.height
            val bottomPad = 24.dp.toPx()
            val chartW = w - yAxisLabelWidth
            val chartH = h - bottomPad

            // ── Y-axis grid lines + labels ────────────────────────────────
            for (i in 0..gridLines) {
                val frac = i.toFloat() / gridLines
                val y    = chartH - chartH * frac
                val v    = yMax * frac

                drawLine(
                    color = gridColor,
                    start = Offset(yAxisLabelWidth, y),
                    end   = Offset(w, y),
                    strokeWidth = 1.dp.toPx()
                )

                val labelText = formatSiValue(v) + if (showUnit && i == gridLines) " $unit" else ""
                val tr = textMeasurer.measure(labelText, labelStyle.copy(color = labelColor))
                drawText(
                    textLayoutResult = tr,
                    topLeft = Offset(
                        x = yAxisLabelWidth - tr.size.width - 4.dp.toPx(),
                        y = y - tr.size.height / 2f
                    )
                )
            }

            // ── Bars ──────────────────────────────────────────────────────
            val barCount   = buckets.size
            val barSpacing = chartW / barCount
            val barWidth   = barSpacing * 0.6f
            val barGap     = (barSpacing - barWidth) / 2f

            buckets.forEachIndexed { idx, bucket ->
                val x     = yAxisLabelWidth + idx * barSpacing + barGap
                val value = yValues[idx]

                if (value > 0) {
                    val bh  = (value / yMax * chartH).toFloat()
                    val top = chartH - bh
                    drawRoundRect(
                        color       = barColor,
                        topLeft     = Offset(x, top),
                        size        = Size(barWidth, bh),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                } else {
                    // ── Empty-bucket stub (#6) ──────────────────────────
                    val stubH = 3.dp.toPx()
                    drawRoundRect(
                        color       = emptyStubColor,
                        topLeft     = Offset(x, chartH - stubH),
                        size        = Size(barWidth, stubH),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }

                // X-axis labels (skip for dense charts)
                val skip = when {
                    barCount <= 12 -> 1
                    barCount <= 26 -> 2
                    barCount <= 60 -> 5
                    else           -> barCount / 6
                }
                if (idx % skip == 0) {
                    val tr = textMeasurer.measure(bucket.label, labelStyle.copy(color = labelColor))
                    val cx = yAxisLabelWidth + idx * barSpacing + barSpacing / 2f
                    drawText(tr, topLeft = Offset(cx - tr.size.width / 2f, chartH + 6.dp.toPx()))
                }
            }

            // ── Dose threshold lines (#2) ─────────────────────────────────
            if (metric != DosageMetric.SESSION_COUNT && doseThresholds != null
                && doseThresholds.unit == unit
            ) {
                listOf(
                    ThresholdEntry("light",  doseThresholds.lightMin,  lightCol),
                    ThresholdEntry("common", doseThresholds.commonMin, commonCol),
                    ThresholdEntry("strong", doseThresholds.strongMin, strongCol),
                    ThresholdEntry("heavy",  doseThresholds.heavyMin,  heavyCol),
                ).forEach { entry ->
                    val v = entry.value ?: return@forEach
                    if (v > yMax) return@forEach
                    val ty = chartH - (v / yMax * chartH).toFloat()
                    drawLine(
                        color       = entry.color,
                        start       = Offset(yAxisLabelWidth, ty),
                        end         = Offset(w - 2.dp.toPx(), ty),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect  = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                    val tr = textMeasurer.measure(entry.name, labelStyle.copy(color = entry.color))
                    drawText(
                        textLayoutResult = tr,
                        topLeft = Offset(w - tr.size.width, ty - tr.size.height - 1.dp.toPx())
                    )
                }
            }

            // ── Average line (#9 colour fix) ──────────────────────────────
            if (showAverage) {
                val avg = yValues.average()
                if (avg > 0 && avg <= yMax) {
                    val avgY = chartH - (avg / yMax * chartH).toFloat()
                    drawLine(
                        color       = averageLineColor,
                        start       = Offset(yAxisLabelWidth, avgY),
                        end         = Offset(w, avgY),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect  = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }
            }

            // ── Trend line (#7) ───────────────────────────────────────────
            if (showTrendLine && yValues.size >= 2) {
                val n     = yValues.size
                val xMean = (n - 1) / 2.0
                val yMean = yValues.average()
                val ssxx  = yValues.indices.sumOf { i -> (i - xMean).let { it * it } }
                val ssxy  = yValues.indices.sumOf { i -> (i - xMean) * (yValues[i] - yMean) }
                val slope = if (ssxx > 0) ssxy / ssxx else 0.0
                val intercept = yMean - slope * xMean

                val y0 = (chartH - (intercept / yMax * chartH)).toFloat()
                val y1 = (chartH - ((intercept + slope * (n - 1)) / yMax * chartH)).toFloat()

                drawLine(
                    color       = trendLineColor,
                    start       = Offset(yAxisLabelWidth + barSpacing / 2f, y0.coerceIn(0f, chartH)),
                    end         = Offset(w - barSpacing / 2f,               y1.coerceIn(0f, chartH)),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}
