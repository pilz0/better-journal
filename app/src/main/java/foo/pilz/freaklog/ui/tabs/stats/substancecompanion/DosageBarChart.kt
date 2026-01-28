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
    val averageColor = Color.White.copy(alpha = 0.8f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(16.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalWidth = size.width
            val totalHeight = size.height
            
            // Calculate Y-Axis range
            val maxDose = buckets.maxOfOrNull { it.totalDose }?.takeIf { it > 0 } ?: 1.0
            val yMax = maxDose * 1.2
            val gridLines = 4 // Increased grid lines for better readability

            // Measure widest text for Y-Axis labels to reserve space
            // We measure the largest value we might display
            val yAxisLabelSample = textMeasurer.measure(
                text = yMax.toInt().toString(), // Approximate widest label
                style = labelStyle
            )
            val yAxisLabelWidth = yAxisLabelSample.size.width + 8.dp.toPx() // Label width + padding
            val bottomPadding = 24.dp.toPx()
            
            val chartWidth = totalWidth - yAxisLabelWidth
            val chartHeight = totalHeight - bottomPadding

            // Draw Y-Axis Labels & Grid Lines
            for (i in 0..gridLines) {
                 val fraction = i.toFloat() / gridLines
                 val y = chartHeight - (chartHeight * fraction)
                 val value = (yMax * fraction).toInt()
                 
                 // Grid line
                 // Only draw if not the bottom-most line (0), or draw all? Usually cleaner to draw all or skip 0 if it overlaps X-axis.
                 // Let's draw horizontal lines across the chart area
                 drawLine(
                     color = gridColor,
                     start = Offset(yAxisLabelWidth, y),
                     end = Offset(totalWidth, y),
                     strokeWidth = 1.dp.toPx()
                 )

                 // Y-Axis Label
                 // We only draw labels for non-zero values to keep it clean, or all.
                 // Let's skip 0 if it interferes with X-axis labels to some extent, but 0 is usually fine.
                 if (i >= 0) {
                     val textLayoutResult = textMeasurer.measure(
                         text = value.toString(),
                         style = labelStyle.copy(color = labelColor)
                     )
                     // Right-align text within the yAxisLabelWidth area
                     drawText(
                         textLayoutResult = textLayoutResult,
                         topLeft = Offset(
                             x = yAxisLabelWidth - textLayoutResult.size.width - 6.dp.toPx(), // padding from line
                             y = y - textLayoutResult.size.height / 2 // Center vertically on the line
                         )
                     )
                 }
            }

            // Draw Bars
            val barCount = buckets.size
            // Use available chart width
            val barSpacing = chartWidth / barCount
            val barWidth = barSpacing * 0.6f
            val gap = barSpacing * 0.2f 

            buckets.forEachIndexed { index, bucket ->
                // x relative to chart start
                val xOffset = index * barSpacing + gap + ((barSpacing - 2*gap - barWidth)/2) // Center bar in slot
                // absolute x
                val x = yAxisLabelWidth + xOffset
                
                val barHeight = (bucket.totalDose / yMax) * chartHeight
                val top = chartHeight - barHeight.toFloat()

                // Draw bar
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, top.toFloat()),
                    size = Size(barWidth, barHeight.toFloat()),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )

                // X-Axis Labels
                val skip = if(barCount > 10) barCount / 5 else 1
                if (index % skip == 0) {
                    val textLayoutResult = textMeasurer.measure(
                        text = bucket.label,
                        style = labelStyle.copy(color = labelColor)
                    )
                    // Center text below the bar slot
                    val slotCenter = yAxisLabelWidth + (index * barSpacing) + (barSpacing / 2)
                    val textX = slotCenter - (textLayoutResult.size.width / 2)
                    
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(textX, chartHeight + 6.dp.toPx())
                    )
                }
            }

            // Draw Average Line
            if (showAverage) {
                val averageDose = buckets.map { it.totalDose }.average()
                if (averageDose > 0 && averageDose <= yMax) {
                    val avgY = chartHeight - ((averageDose / yMax) * chartHeight).toFloat()
                    drawLine(
                        color = averageColor,
                        start = Offset(yAxisLabelWidth, avgY),
                        end = Offset(totalWidth, avgY),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }
            }
        }
    }
}
