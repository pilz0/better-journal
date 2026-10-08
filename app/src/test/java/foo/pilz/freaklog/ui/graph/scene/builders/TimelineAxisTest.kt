package foo.pilz.freaklog.ui.graph.scene.builders

import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.time.Instant

class TimelineAxisTest {

    @Test
    fun axisLabelsXFractionsAreWithinRangeAndAscending() {
        val start = Instant.parse("2021-01-01T10:05:00Z")
        val labels = buildAxisLabels(start, widthInSeconds = 4 * 3600f, canvasWidthPx = 100000f)
        assertTrue(labels.isNotEmpty())
        assertTrue(labels.all { it.xFraction in 0f..1f })
        assertTrue(labels.zipWithNext().all { (a, b) -> a.xFraction < b.xFraction })
    }

    @Test
    fun narrowCanvasYieldsFewerOrEqualLabelsThanWide() {
        val start = Instant.parse("2021-01-01T10:05:00Z")
        val wide = buildAxisLabels(start, widthInSeconds = 24 * 3600f, canvasWidthPx = 100000f)
        val narrow = buildAxisLabels(start, widthInSeconds = 24 * 3600f, canvasWidthPx = 200f)
        assertTrue(narrow.size <= wide.size)
    }
}
