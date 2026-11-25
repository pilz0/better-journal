package foo.pilz.freaklog.ui.tabs.stats.substancecompanion

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import foo.pilz.freaklog.data.substances.AdministrationRoute
import org.junit.Test

class DosageBarChartMathTest {

  @Test
  fun `formatSiValue handles zero tiny decimals integers and scaled values cleanly`() {
    assertThat(formatSiValue(0.0)).isEqualTo("0")
    assertThat(formatSiValue(-5.0)).isEqualTo("0")
    assertThat(formatSiValue(0.05)).isEqualTo("0.05")
    assertThat(formatSiValue(0.25)).isEqualTo("0.25")
    assertThat(formatSiValue(2.5)).isEqualTo("2.5")
    assertThat(formatSiValue(10.0)).isEqualTo("10")
    assertThat(formatSiValue(1500.0)).isEqualTo("1.5k")
    assertThat(formatSiValue(2000.0)).isEqualTo("2k")
    assertThat(formatSiValue(2_000_000.0)).isEqualTo("2M")
  }

  @Test
  fun `formatSummaryDose scales metric units up instead of showing k prefix`() {
    assertThat(formatSummaryDose(1500.0, "mg")).isEqualTo("1.5 g")
    assertThat(formatSummaryDose(2500.0, "µg")).isEqualTo("2.5 mg")
    assertThat(formatSummaryDose(1500.0, "ml")).isEqualTo("1.5 l")
    assertThat(formatSummaryDose(0.25, "mg")).isEqualTo("0.25 mg")
    assertThat(formatSummaryDose(1500.0, "kg")).isEqualTo("1500 kg")
    assertThat(formatSummaryDose(3.0, "tab")).isEqualTo("3 tab")
  }

  @Test
  fun `computeChartYScale uses exact integer ticks for integer metrics`() {
    val max1 = computeChartYScale(1.0, integerOnly = true)
    assertThat(max1.gridLines).isEqualTo(1)
    assertThat(max1.ticks).containsExactly(0.0, 1.0)

    val max2 = computeChartYScale(2.0, integerOnly = true)
    assertThat(max2.gridLines).isEqualTo(2)
    assertThat(max2.ticks).containsExactly(0.0, 1.0, 2.0)

    val max3 = computeChartYScale(3.0, integerOnly = true)
    assertThat(max3.gridLines).isEqualTo(3)
    assertThat(max3.ticks).containsExactly(0.0, 1.0, 2.0, 3.0)

    val max5 = computeChartYScale(5.0, integerOnly = true)
    assertThat(max5.gridLines).isEqualTo(4)
    assertThat(max5.ticks).containsExactly(0.0, 2.0, 4.0, 6.0, 8.0)

    val max13 = computeChartYScale(13.0, integerOnly = true)
    assertThat(max13.gridLines).isEqualTo(4)
    assertThat(max13.ticks).containsExactly(0.0, 5.0, 10.0, 15.0, 20.0)
  }

  @Test
  fun `computeChartYScale uses nice float ticks for continuous doses`() {
    val small = computeChartYScale(0.5, integerOnly = false)
    assertThat(small.yMax).isCloseTo(0.5, 1e-9)
    assertThat(small.ticks).containsExactly(0.0, 0.125, 0.25, 0.375, 0.5)

    val medium = computeChartYScale(120.0, integerOnly = false)
    assertThat(medium.yMax).isCloseTo(200.0, 1e-9)
    assertThat(medium.ticks).containsExactly(0.0, 50.0, 100.0, 150.0, 200.0)
  }

  @Test
  fun `convertDoseThresholds converts matching and scaled units and rejects incompatible ones`() {
    val thresholds =
        DoseThresholds(
            unit = "mg",
            lightMin = 50.0,
            commonMin = 100.0,
            strongMin = 200.0,
            heavyMin = 400.0,
            route = AdministrationRoute.ORAL,
        )

    val toGrams = convertDoseThresholds(thresholds, "g")
    assertThat(toGrams).isNotNull()
    assertThat(toGrams!!.unit).isEqualTo("g")
    assertThat(toGrams.lightMin!!).isCloseTo(0.05, 1e-9)
    assertThat(toGrams.heavyMin!!).isCloseTo(0.4, 1e-9)
    assertThat(toGrams.route).isEqualTo(AdministrationRoute.ORAL)

    val aliasMatch =
        convertDoseThresholds(
            thresholds.copy(unit = "ug"),
            "µg",
        )
    assertThat(aliasMatch).isNotNull()
    assertThat(aliasMatch!!.lightMin!!).isCloseTo(50.0, 1e-9)

    assertThat(convertDoseThresholds(thresholds, "ml")).isNull()
    assertThat(convertDoseThresholds(thresholds, "pill")).isNull()
    assertThat(convertDoseThresholds(thresholds, "")).isNull()
  }

  @Test
  fun `placeThresholdLabels omits colliding labels while retaining dashed line positions`() {
    val placed =
        placeThresholdLabels(
            levels =
                listOf(
                    "light" to 10.0,
                    "common" to 15.0,
                    "strong" to 20.0,
                    "heavy" to 180.0,
                ),
            yMax = 200.0,
            plotTop = 0f,
            plotBottom = 200f,
            labelHeight = 20f,
            minGapPx = 4f,
        )

    assertThat(placed.size).isEqualTo(4)
    val heavy = placed.first { it.name == "heavy" }
    val strong = placed.first { it.name == "strong" }
    val common = placed.first { it.name == "common" }
    val light = placed.first { it.name == "light" }

    assertThat(heavy.labelTopY).isNotNull()
    assertThat(strong.labelTopY).isNotNull()
    assertThat(common.labelTopY).isNull()
    assertThat(light.labelTopY).isNull()
  }

  @Test
  fun `computeDosageChartSummaryStats classifies rising falling and steady trends`() {
    val rising = computeDosageChartSummaryStats(listOf(10.0, 20.0, 30.0, 40.0))
    assertThat(rising.average).isCloseTo(25.0, 1e-9)
    assertThat(rising.trendDirection).isEqualTo(TrendDirection.RISING)

    val falling = computeDosageChartSummaryStats(listOf(40.0, 30.0, 20.0, 10.0))
    assertThat(falling.average).isCloseTo(25.0, 1e-9)
    assertThat(falling.trendDirection).isEqualTo(TrendDirection.FALLING)

    val steady = computeDosageChartSummaryStats(listOf(20.0, 20.0, 20.0, 20.0))
    assertThat(steady.average).isCloseTo(20.0, 1e-9)
    assertThat(steady.trendDirection).isEqualTo(TrendDirection.STEADY)
  }

  @Test
  fun `computeDosageChartSummaryStats with activeOnly ignores zero buckets`() {
    val sparseSteady =
        computeDosageChartSummaryStats(
            listOf(0.0, 0.0, 100.0, 0.0, 0.0, 100.0),
            activeOnly = true,
        )
    assertThat(sparseSteady.average).isCloseTo(100.0, 1e-9)
    assertThat(sparseSteady.trendDirection).isEqualTo(TrendDirection.STEADY)
    assertThat(sparseSteady.hasTrend).isEqualTo(true)
    assertThat(sparseSteady.trendStartIndex).isEqualTo(2)
    assertThat(sparseSteady.trendEndIndex).isEqualTo(5)

    val singleActive = computeDosageChartSummaryStats(listOf(0.0, 100.0, 0.0), activeOnly = true)
    assertThat(singleActive.average).isCloseTo(100.0, 1e-9)
    assertThat(singleActive.hasTrend).isEqualTo(false)
  }
}
