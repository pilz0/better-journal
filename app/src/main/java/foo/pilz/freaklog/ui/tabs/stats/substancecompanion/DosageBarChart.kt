package foo.pilz.freaklog.ui.tabs.stats.substancecompanion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor

data class DosageBucket(
    val label: String, // e.g., "Jan", "Feb", "W1"
    val fullDateText: String, // e.g. "January 2026" for accessibility/tap
    val totalDose: Double,
    val unit: String
)

@Composable
fun DosageBarChart(
    buckets: List<DosageBucket>,
    barColor: Color,
    showAverage: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp
) {
    if (buckets.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val averageColor = Color.White.copy(alpha = 0.8f) // Or specific color from design

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(16.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val bottomPadding = 24.dp.toPx() // Space for X-axis labels
            val chartHeight = height - bottomPadding

            val maxDose = buckets.maxOfOrNull { it.totalDose }?.takeIf { it > 0 } ?: 1.0
            // Add some headroom
            val yMax = maxDose * 1.2

            // Draw Grid Lines (Horizontal)
            val gridLines = 3
            for (i in 0..gridLines) {
                val y = chartHeight - (chartHeight * (i.toFloat() / gridLines))
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
                
                // Y-Axis Labels
                if (i > 0) {
                     val value = (yMax * (i.toFloat() / gridLines)).toInt() // Simplified integer labels for now
                     val textLayoutResult = textMeasurer.measure(
                         text = value.toString(),
                         style = labelStyle.copy(color = labelColor)
                     )
                     drawText(
                         textLayoutResult = textLayoutResult,
                         topLeft = Offset(width - textLayoutResult.size.width, y - textLayoutResult.size.height - 4.dp.toPx())
                     )
                }
            }

            // Draw Bars
            val barCount = buckets.size
            val barSpacing = width / barCount
            // We want some gap between bars. let's say 20% gap.
            val barWidth = barSpacing * 0.7f
            val gap = barSpacing * 0.15f // half gap on each side

            buckets.forEachIndexed { index, bucket ->
                val x = (index * barSpacing) + gap
                val barHeight = (bucket.totalDose / yMax) * chartHeight
                val top = chartHeight - barHeight.toFloat()

                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, top.toFloat()),
                    size = Size(barWidth, barHeight.toFloat()),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )

                // Draw X-Axis Labels (every nth label to avoid overcrowding if many buckets)
                // For simplified version, let's try to draw meaningful ones, e.g. first, middle, last or if few enough, all.
                // Logic can be refined. For now, draw if it fits or modulo.
                val skip = if(barCount > 15) barCount / 6 else 1
                if (index % skip == 0) {
                    val textLayoutResult = textMeasurer.measure(
                        text = bucket.label,
                        style = labelStyle.copy(color = labelColor)
                    )
                    // Center text below bar
                    val textX = x + (barWidth - textLayoutResult.size.width) / 2
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(textX, height - textLayoutResult.size.height)
                    )
                }
            }

            // Draw Average Line
            if (showAverage) {
                val averageDose = buckets.map { it.totalDose }.average()
                if (averageDose > 0) {
                    val avgY = chartHeight - ((averageDose / yMax) * chartHeight).toFloat()
                    drawLine(
                        color = averageColor,
                        start = Offset(0f, avgY),
                        end = Offset(width, avgY),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }
            }
        }
    }
}
