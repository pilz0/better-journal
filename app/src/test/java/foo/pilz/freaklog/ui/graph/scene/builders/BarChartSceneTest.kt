package foo.pilz.freaklog.ui.graph.scene.builders

import foo.pilz.freaklog.ui.graph.scene.GraphPrimitive
import junit.framework.TestCase.assertEquals
import org.junit.Test

class BarChartSceneTest {

    @Test
    fun emptyOrZeroDataProducesNoBars() {
        assertEquals(0, buildBarChartScene(emptyList(), maxCount = 0.0, barWidthRatio = 0.7f).primitives.size)
        val zero = listOf(listOf(BarSegment(0xFFFF0000.toInt(), 0.0)))
        assertEquals(0, buildBarChartScene(zero, maxCount = 0.0, barWidthRatio = 0.7f).primitives.size)
    }

    @Test
    fun segmentsStackToFractionalHeights() {
        val buckets = listOf(listOf(BarSegment(0xFFFF0000.toInt(), 3.0), BarSegment(0xFF00FF00.toInt(), 1.0)))
        val scene = buildBarChartScene(buckets, maxCount = 4.0, barWidthRatio = 0.7f)
        val rects = scene.primitives.filterIsInstance<GraphPrimitive.RoundRect>()
        assertEquals(2, rects.size)
        assertEquals(0.0, rects[0].bottom.toDouble(), 1e-6)
        assertEquals(0.75, rects[0].top.toDouble(), 1e-6)
        assertEquals(0.75, rects[1].bottom.toDouble(), 1e-6)
        assertEquals(1.0, rects[1].top.toDouble(), 1e-6)
    }

    @Test
    fun fractionalCommonDoseRendersFractionalBar() {
        val buckets = listOf(listOf(BarSegment(0xFFFF0000.toInt(), 0.3)))
        val scene = buildBarChartScene(buckets, maxCount = 1.0, barWidthRatio = 0.7f)
        val rects = scene.primitives.filterIsInstance<GraphPrimitive.RoundRect>()
        assertEquals(1, rects.size)
        assertEquals(0.0, rects[0].bottom.toDouble(), 1e-6)
        assertEquals(0.3, rects[0].top.toDouble(), 1e-6)
    }
}
