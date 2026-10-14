package foo.pilz.freaklog.data.substances.classes.roa.curve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

class BatemanCurveExpandedTest {

    private fun createCurve(
        tmaxSec: Double = 900.0,
        totalSec: Double = 7200.0,
        peakHeight: Float = 1.0f,
        startSec: Float = 0f,
        endSec: Float = startSec,
    ): BatemanCurve {
        val ke = -ln(0.05) / totalSec
        val ka = BatemanCurve.solveKaForTmax(ke, tmaxSec) ?: (ke * 4.0)
        return BatemanCurve(
            ka = ka,
            ke = ke,
            ingestionStartSec = startSec,
            ingestionEndSec = endSec,
            peakHeight = peakHeight,
            isCertain = true,
        )
    }

    @Test
    fun pointCurveIsStrictlyNonNegativeAcrossWideTimeRange() {
        val curve = createCurve(tmaxSec = 1200.0, totalSec = 14400.0, peakHeight = 2.5f)
        var t = -100f
        while (t <= 30000f) {
            val value = curve.valueAt(t)
            assertTrue("Value at t=$t must be >= 0, was $value", value >= 0f)
            t += 25f
        }
    }

    @Test
    fun peakHeightIsAccurateAtTmax() {
        val targetPeak = 3.5f
        val tmax = 1800.0
        val curve = createCurve(tmaxSec = tmax, totalSec = 18000.0, peakHeight = targetPeak)
        val observedPeak = curve.valueAt(tmax.toFloat())
        assertEquals("Observed peak should match targetPeak within 5%", targetPeak, observedPeak, 0.05f * targetPeak)
    }

    @Test
    fun curveDecaysTowardsZeroFarBeyondTotalSec() {
        val curve = createCurve(tmaxSec = 600.0, totalSec = 3600.0, peakHeight = 1f)
        val tailValue = curve.valueAt(36000f)
        assertTrue("Tail value at 10x duration should be near zero, was $tailValue", tailValue < 0.01f)
    }

    @Test
    fun scalingDoseLinearlyScalesConcentration() {
        val baseCurve = createCurve(tmaxSec = 900.0, totalSec = 7200.0, peakHeight = 1.0f)
        val doubleCurve = createCurve(tmaxSec = 900.0, totalSec = 7200.0, peakHeight = 2.0f)

        for (sec in listOf(300f, 900f, 1800f, 3600f, 7200f)) {
            val baseVal = baseCurve.valueAt(sec)
            val doubleVal = doubleCurve.valueAt(sec)
            assertEquals("Doubled curve should be exactly 2x base at t=$sec", baseVal * 2f, doubleVal, 0.001f)
        }
    }

    @Test
    fun curveEndSecIsAlwaysGreaterThanTotalSec() {
        val total = 7200.0
        val curve = createCurve(tmaxSec = 900.0, totalSec = total, peakHeight = 1f)
        assertTrue("curveEndSec (${curve.curveEndSec}) must be >= totalSec ($total)", curve.curveEndSec >= total)
    }

    @Test
    fun extendedCurveRemainsSmoothDuringInfusionWindow() {
        val curve = createCurve(
            tmaxSec = 1800.0,
            totalSec = 10800.0,
            peakHeight = 1.5f,
            startSec = 0f,
            endSec = 1800f
        )
        assertNotNull(curve)
        assertTrue(curve.valueAt(-10f) == 0f)
        assertTrue(curve.valueAt(900f) > 0f)
    }
}
