package foo.pilz.freaklog.ui.tabs.stats

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isCloseTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import org.junit.Test

class BarChartLayoutTest {

  @Test
  fun `computeStatsBarGeometry scales width cleanly and never overlaps adjacent bars`() {
    val dense =
        computeStatsBarGeometry(
            plotWidth = 300f,
            bucketCount = 30,
            minBarWidthPx = 2.5f,
            maxBarWidthPx = 32f,
        )
    assertThat(dense.barSpacing).isCloseTo(10f, 1e-4f)
    assertThat(dense.barWidth).isCloseTo(7.2f, 1e-4f)
    assertThat(dense.barGap).isCloseTo(1.4f, 1e-4f)

    val wide =
        computeStatsBarGeometry(
            plotWidth = 700f,
            bucketCount = 7,
            minBarWidthPx = 2.5f,
            maxBarWidthPx = 32f,
        )
    assertThat(wide.barSpacing).isCloseTo(100f, 1e-4f)
    assertThat(wide.barWidth).isEqualTo(32f)

    val cramped =
        computeStatsBarGeometry(
            plotWidth = 60f,
            bucketCount = 30,
            minBarWidthPx = 5f,
            maxBarWidthPx = 32f,
        )
    assertThat(cramped.barWidth).isLessThan(cramped.barSpacing)
  }

  @Test
  fun `placeStatsXAxisLabels keeps all 7 weekday labels when spacing fits`() {
    val placements =
        placeStatsXAxisLabels(
            labelWidths = List(7) { 24 },
            plotLeft = 20f,
            totalWidth = 300f,
            barSpacing = 40f,
            targetGapPx = 8f,
            minGapPx = 4f,
        )
    assertThat(placements.map { it.bucketIndex }).containsExactly(0, 1, 2, 3, 4, 5, 6)
  }

  @Test
  fun `placeStatsXAxisLabels offsets start index on dense 26W buckets to avoid left clamp gap`() {
    val placements =
        placeStatsXAxisLabels(
            labelWidths = List(26) { 30 },
            plotLeft = 22f,
            totalWidth = 308f,
            barSpacing = 11f,
            targetGapPx = 8f,
            minGapPx = 4f,
        )
    assertThat(placements.map { it.bucketIndex }).containsExactly(1, 5, 9, 13, 17, 21, 25)
  }

  @Test
  fun `placeStatsXAxisLabels handles empty or invalid bounds safely`() {
    assertThat(
            placeStatsXAxisLabels(
                labelWidths = emptyList(),
                plotLeft = 20f,
                totalWidth = 300f,
                barSpacing = 10f,
                targetGapPx = 8f,
                minGapPx = 4f,
            )
        )
        .isEmpty()
    assertThat(
            placeStatsXAxisLabels(
                labelWidths = listOf(20, 20),
                plotLeft = 300f,
                totalWidth = 200f,
                barSpacing = 10f,
                targetGapPx = 8f,
                minGapPx = 4f,
            )
        )
        .isEmpty()
  }
}
