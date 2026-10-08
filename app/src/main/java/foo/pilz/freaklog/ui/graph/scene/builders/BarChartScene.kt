package foo.pilz.freaklog.ui.graph.scene.builders

import foo.pilz.freaklog.ui.graph.scene.CornerRole
import foo.pilz.freaklog.ui.graph.scene.GraphPrimitive
import foo.pilz.freaklog.ui.graph.scene.GraphScene

data class BarSegment(val colorArgb: Int, val count: Double)

fun buildBarChartScene(
    buckets: List<List<BarSegment>>,
    maxCount: Double,
    barWidthRatio: Float,
): GraphScene {
    if (maxCount <= 0.0 || buckets.isEmpty()) return GraphScene(emptyList(), 0f)
    val numBuckets = buckets.size
    val slot = 1f / numBuckets
    val barWidth = slot * barWidthRatio
    val primitives = mutableListOf<GraphPrimitive>()
    buckets.forEachIndexed { index, segments ->
        val xCenter = (index + 0.5f) * slot
        var yBottom = 0f
        segments.forEach { segment ->
            val heightFraction = (segment.count / maxCount).toFloat()
            val yTop = yBottom + heightFraction
            primitives += GraphPrimitive.RoundRect(
                left = xCenter - barWidth / 2f,
                top = yTop,
                right = xCenter + barWidth / 2f,
                bottom = yBottom,
                color = segment.colorArgb,
                corner = CornerRole.Bar,
                filled = true,
            )
            yBottom = yTop
        }
    }
    return GraphScene(primitives = primitives, widthInSeconds = 0f)
}
