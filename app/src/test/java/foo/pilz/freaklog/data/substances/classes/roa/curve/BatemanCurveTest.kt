/*
 * Copyright (c) 2026.
 * This file is part of FreakLog.
 *
 * FreakLog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.data.substances.classes.roa.curve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

class BatemanCurveTest {

    @Test
    fun `solveKaForTmax returns a ka greater than ke that reproduces the requested tmax`() {
        val ke = 0.001
        val tmax = 600.0
        val ka = BatemanCurve.solveKaForTmax(ke, tmax)!!
        assertTrue("ka must exceed ke", ka > ke)
        val computedTmax = ln(ka / ke) / (ka - ke)
        assertEquals(tmax, computedTmax, 1.0)
    }

    @Test
    fun `solveKaForTmax returns null when tmax is at or above the achievable limit`() {
        val ke = 0.01
        // limit is 1/ke = 100 s
        assertNull(BatemanCurve.solveKaForTmax(ke, 1000.0))
    }

    @Test
    fun `point curve is zero before ingestion start`() {
        val curve = curve(tmaxSec = 600.0, totalSec = 6_000.0, peakHeight = 1f)
        assertEquals(0f, curve.valueAt(-10f), 0f)
        assertEquals(0f, curve.valueAt(0f), 0f)
    }

    @Test
    fun `point curve rises monotonically before tmax and falls after`() {
        val tmax = 600f
        val curve = curve(tmaxSec = tmax.toDouble(), totalSec = 6_000.0, peakHeight = 1f)
        var prev = 0f
        var t = 1f
        while (t <= tmax) {
            val v = curve.valueAt(t)
            assertTrue("curve must rise: t=$t prev=$prev v=$v", v >= prev - 1e-5f)
            prev = v
            t += 5f
        }
        var prev2 = curve.valueAt(tmax + 5f)
        var t2 = tmax + 10f
        while (t2 < curve.curveEndSec) {
            val v = curve.valueAt(t2)
            assertTrue("curve must fall: t=$t2 prev=$prev2 v=$v", v <= prev2 + 1e-5f)
            prev2 = v
            t2 += 30f
        }
    }

    @Test
    fun `point curve peak is near the requested tmax with the requested height`() {
        val tmax = 1_200f
        val curve = curve(tmaxSec = tmax.toDouble(), totalSec = 12_000.0, peakHeight = 1f)
        // Sample around the requested tmax — the actual numerical maximum should be within ~3 %.
        val sampled = (1..(curve.curveEndSec.toInt())).asSequence()
            .map { it.toFloat() to curve.valueAt(it.toFloat()) }
            .maxByOrNull { it.second }!!
        assertEquals(1f, sampled.second, 0.02f)
        assertEquals(tmax, sampled.first, tmax * 0.05f) // within 5 % of requested tmax
    }

    @Test
    fun `point curve decays below the tail threshold by the requested total time`() {
        val total = 6_000f
        val curve = curve(tmaxSec = 600.0, totalSec = total.toDouble(), peakHeight = 1f)
        val valueAtTotal = curve.valueAt(total)
        // We aimed for ~5 % of peak at the requested total time. The unscaled-peak normalisation
        // makes the post-peak tail decay at ke, so 5 % is a reasonable check with slack.
        assertTrue(
            "value at requested total ($valueAtTotal) should be below 8 % of peak",
            valueAtTotal < 0.08f
        )
        assertTrue(
            "value at requested total ($valueAtTotal) should still be visible above 1 %",
            valueAtTotal > 0.005f
        )
    }

    @Test
    fun `infusion curve peaks later than the equivalent point ingestion`() {
        val point = BatemanCurve(
            ka = solve(ke = 0.001, tmax = 600.0),
            ke = 0.001,
            ingestionStartSec = 0f,
            ingestionEndSec = 0f,
            peakHeight = 1f,
            isCertain = true,
        )
        val infusion = BatemanCurve(
            ka = point.ka,
            ke = point.ke,
            ingestionStartSec = 0f,
            ingestionEndSec = 1_800f,
            peakHeight = 1f,
            isCertain = true,
        )
        fun peakTime(c: BatemanCurve): Float {
            var bestT = 0f
            var bestV = 0f
            var t = 0f
            while (t < c.curveEndSec) {
                val v = c.valueAt(t)
                if (v > bestV) { bestV = v; bestT = t }
                t += 1f
            }
            return bestT
        }
        val pPeak = peakTime(point)
        val iPeak = peakTime(infusion)
        assertTrue(
            "infusion peak time ($iPeak) should occur after point peak time ($pPeak)",
            iPeak > pPeak
        )
    }

    @Test
    fun `curveEndSec is finite and after ingestion end`() {
        val curve = curve(tmaxSec = 600.0, totalSec = 6_000.0, peakHeight = 1f)
        assertTrue(curve.curveEndSec > curve.ingestionStartSec)
        assertTrue(curve.curveEndSec.isFinite())
    }

    @Test
    fun `valueAt is non-negative everywhere`() {
        val curve = curve(tmaxSec = 600.0, totalSec = 6_000.0, peakHeight = 2.5f)
        var t = -100f
        while (t < curve.curveEndSec + 1000f) {
            assertTrue("v at $t must be >= 0", curve.valueAt(t) >= 0f)
            t += 17f
        }
    }

    @Test
    fun `stacking two overlapping curves never misses the joint maximum`() {
        // Two identical point ingestions 10 minutes apart.
        val a = curve(tmaxSec = 600.0, totalSec = 6_000.0, peakHeight = 1f, startSec = 0f)
        val b = curve(tmaxSec = 600.0, totalSec = 6_000.0, peakHeight = 1f, startSec = 600f)
        // Sample at fine resolution to find the real combined maximum.
        var maxFine = 0f
        var t = 0f
        while (t < 8000f) {
            val v = a.valueAt(t) + b.valueAt(t)
            if (v > maxFine) maxFine = v
            t += 1f
        }
        // The combined curve should always exceed each individual peak once they overlap.
        assertTrue("joint peak ($maxFine) must exceed single peak", maxFine > 1.2f)
        assertTrue("joint peak ($maxFine) must be at most the sum of peaks", maxFine <= 2.0f + 1e-3)
    }

    private fun solve(ke: Double, tmax: Double): Double = BatemanCurve.solveKaForTmax(ke, tmax)!!

    private fun curve(
        tmaxSec: Double,
        totalSec: Double,
        peakHeight: Float,
        startSec: Float = 0f,
        endSec: Float = startSec,
    ): BatemanCurve {
        val ke = -ln(0.05) / totalSec
        val ka = BatemanCurve.solveKaForTmax(ke, tmaxSec)!!
        return BatemanCurve(
            ka = ka,
            ke = ke,
            ingestionStartSec = startSec,
            ingestionEndSec = endSec,
            peakHeight = peakHeight,
            isCertain = true,
        )
    }
}
