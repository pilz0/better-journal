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
  fun `computeStatsYScale matches exact maxCount so peak bar uses full chart height`() {
    assertThat(computeStatsYScale(1).ticks).containsExactly(0, 1)
    assertThat(computeStatsYScale(3).ticks).containsExactly(0, 1, 2, 3)
    assertThat(computeStatsYScale(5).ticks).containsExactly(0, 3, 5)
    assertThat(computeStatsYScale(8).ticks).containsExactly(0, 2, 4, 6, 8)
    assertThat(computeStatsYScale(9).ticks).containsExactly(0, 3, 6, 9)
    assertThat(computeStatsYScale(10).ticks).containsExactly(0, 5, 10)
    assertThat(computeStatsYScale(11).ticks).containsExactly(0, 6, 11)

    for (count in 1..100) {
      val scale = computeStatsYScale(count)
      assertThat(scale.yMax).isEqualTo(count)
      assertThat(scale.ticks.first()).isEqualTo(0)
      assertThat(scale.ticks.last()).isEqualTo(count)
    }
  }

  @Test
  fun `computeStatsBarGeometry uses 85 percent of column width without overlapping`() {
    val dense =
        computeStatsBarGeometry(
            plotWidth = 300f,
            bucketCount = 30,
            minBarWidthPx = 2.5f,
        )
    assertThat(dense.barSpacing).isCloseTo(10f, 1e-4f)
    assertThat(dense.barWidth).isCloseTo(8.5f, 1e-4f)
    assertThat(dense.barGap).isCloseTo(0.75f, 1e-4f)

    val sevenDay =
        computeStatsBarGeometry(
            plotWidth = 350f,
            bucketCount = 7,
            minBarWidthPx = 2.5f,
        )
    assertThat(sevenDay.barSpacing).isCloseTo(50f, 1e-4f)
    assertThat(sevenDay.barWidth).isCloseTo(42.5f, 1e-4f)

    val cramped =
        computeStatsBarGeometry(
            plotWidth = 60f,
            bucketCount = 30,
            minBarWidthPx = 5f,
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
