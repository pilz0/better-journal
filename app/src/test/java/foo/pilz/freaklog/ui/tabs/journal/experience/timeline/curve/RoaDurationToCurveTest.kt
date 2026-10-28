/*
 * Copyright (c) 2026.
 * This file is part of FreakLog.
 *
 * FreakLog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.ui.tabs.journal.experience.timeline.curve

import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.DurationUnits
import foo.pilz.freaklog.data.substances.classes.roa.RoaDuration
import foo.pilz.freaklog.data.substances.classes.roa.curve.IngestionCurve
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.WeightedLine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RoaDurationToCurveTest {

    private val origin: Instant = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `full RoaDuration produces a certain curve with peak near onset+comeup`() {
        val rd = RoaDuration(
            onset = range(20f, 40f, DurationUnits.MINUTES),
            comeup = range(15f, 30f, DurationUnits.MINUTES),
            peak = range(1.5f, 2.5f, DurationUnits.HOURS),
            offset = range(2f, 4f, DurationUnits.HOURS),
            total = range(6f, 10f, DurationUnits.HOURS),
            afterglow = null,
        )
        val curve = rd.toIngestionCurve(line(weight = 0.5f), origin)
        assertNotNull(curve)
        assertTrue(curve!!.isCertain)
        // Peak should be roughly onset.center (30 min) + comeup.center (22.5 min) ≈ 52.5 min.
        val sampled = (1..curve.curveEndSec.toInt())
            .maxByOrNull { curve.valueAt(it.toFloat()) }!!
            .toFloat()
        assertTrue("tmax sample $sampled should be within ~25 min of 52 min", sampled in 1500f..4500f)
        assertTrue("curve should end after total min", curve.curveEndSec >= 6 * 3600f * 0.95f)
    }

    @Test
    fun `total-only RoaDuration falls back to an uncertain curve`() {
        val rd = RoaDuration(
            onset = null,
            comeup = null,
            peak = null,
            offset = null,
            total = range(6f, 10f, DurationUnits.HOURS),
            afterglow = null,
        )
        val curve = rd.toIngestionCurve(line(weight = 0.5f), origin)
        assertNotNull(curve)
        assertFalse(curve!!.isCertain)
    }

    @Test
    fun `empty RoaDuration produces no curve`() {
        val rd = RoaDuration(null, null, null, null, null, null)
        assertNull(rd.toIngestionCurve(line(), origin))
    }

    @Test
    fun `time-ranged ingestion produces a curve whose peak comes after the ingestion mid-point`() {
        val rd = RoaDuration(
            onset = range(20f, 40f, DurationUnits.MINUTES),
            comeup = range(15f, 30f, DurationUnits.MINUTES),
            peak = range(1.5f, 2.5f, DurationUnits.HOURS),
            offset = range(2f, 4f, DurationUnits.HOURS),
            total = range(6f, 10f, DurationUnits.HOURS),
            afterglow = null,
        )
        val start = origin.plusSeconds(0)
        val end = origin.plusSeconds(3600)
        val wl = WeightedLine(
            startTime = start,
            endTime = end,
            horizontalWeight = 0.5f,
            height = 1f,
        )
        val curve = rd.toIngestionCurve(wl, origin)
        assertNotNull(curve)
        // Numerically scan for the peak and ensure it sits after the ingestion mid-point.
        val tPeak = (1..curve!!.curveEndSec.toInt())
            .maxByOrNull { curve.valueAt(it.toFloat()) }!!
        val ingestionMidPointSec = ((start.epochSecond + end.epochSecond) / 2) - origin.epochSecond
        assertTrue(
            "infusion peak ($tPeak s) should be after ingestion mid-point ($ingestionMidPointSec s)",
            tPeak.toLong() > ingestionMidPointSec
        )
    }

    @Test
    fun `zero total duration safely produces null curve without throwing`() {
        val rd = RoaDuration(
            onset = null,
            comeup = null,
            peak = null,
            offset = null,
            total = range(0f, 0f, DurationUnits.MINUTES),
            afterglow = null,
        )
        assertNull(rd.toIngestionCurve(line(), origin))
    }

    @Test
    fun `negative total duration safely produces null curve without throwing`() {
        val rd = RoaDuration(
            onset = null,
            comeup = null,
            peak = null,
            offset = null,
            total = range(-10f, -5f, DurationUnits.MINUTES),
            afterglow = null,
        )
        assertNull(rd.toIngestionCurve(line(), origin))
    }

    @Test
    fun `short total-only duration peaks at a quarter of the total`() {
        val rd = RoaDuration(
            onset = null,
            comeup = null,
            peak = null,
            offset = null,
            total = range(60f, 120f, DurationUnits.SECONDS),
            afterglow = null,
        )
        val curve = rd.toIngestionCurve(line(weight = 0.5f), origin)
        assertNotNull(curve)
        // targetTotal = 90 s, so tmax = 22.5 s. A 60 s floor used to push tmax past the
        // solvable range and fall back to ~15 s.
        val peak = peakSec(curve!!)
        assertTrue("peak $peak s should be near 22.5 s", peak in 20f..25f)
        assertTrue("curve should outlast its peak", curve.curveEndSec > peak)
        assertTrue("curve should be capped at 1.2 * total", curve.curveEndSec <= 90f * 1.2f + 1f)
    }

    @Test
    fun `short phased duration peaks near onset+comeup`() {
        val rd = RoaDuration(
            onset = range(5f, 15f, DurationUnits.SECONDS),
            comeup = range(10f, 20f, DurationUnits.SECONDS),
            peak = range(30f, 60f, DurationUnits.SECONDS),
            offset = range(20f, 40f, DurationUnits.SECONDS),
            total = range(60f, 120f, DurationUnits.SECONDS),
            afterglow = null,
        )
        val curve = rd.toIngestionCurve(line(weight = 0.5f), origin)
        assertNotNull(curve)
        assertTrue(curve!!.isCertain)
        val peak = peakSec(curve)
        assertTrue("peak $peak s should be near onset+comeup = 25 s", peak in 22f..28f)
    }

    @Test
    fun `total of a few seconds does not throw on contradictory tmax bounds`() {
        val rd = RoaDuration(
            onset = null,
            comeup = null,
            peak = null,
            offset = null,
            total = range(4f, 8f, DurationUnits.SECONDS),
            afterglow = null,
        )
        val curve = rd.toIngestionCurve(line(weight = 0.5f), origin)
        assertNotNull(curve)
        val peak = peakSec(curve!!)
        assertTrue("peak $peak s should lie within the 6 s total", peak > 0f && peak < 6f)
    }

    @Test
    fun `late peak beyond the Bateman limit is clamped instead of collapsing`() {
        // total = 8 h, so the latest reachable Bateman peak is 1/ke = 8 h / ln(20) ≈ 2.67 h.
        // onset+comeup = 3 h used to make the solver fail and fall back to 0.5/ke ≈ 1.33 h.
        val late = phasedDuration(onsetCenterHours = 2f, comeupCenterHours = 1f)
        val earlier = phasedDuration(onsetCenterHours = 1.5f, comeupCenterHours = 1f)
        val lateCurve = late.toIngestionCurve(line(weight = 0.5f), origin)
        val earlierCurve = earlier.toIngestionCurve(line(weight = 0.5f), origin)
        assertNotNull(lateCurve)
        assertNotNull(earlierCurve)
        val latePeak = coarsePeakSec(lateCurve!!)
        val earlierPeak = coarsePeakSec(earlierCurve!!)
        assertTrue("late peak $latePeak s should stay near 1/ke ≈ 9600 s", latePeak > 2.4f * 3600f)
        assertTrue(
            "later onset ($latePeak s) must not peak before earlier onset ($earlierPeak s)",
            latePeak >= earlierPeak
        )
    }

    private fun phasedDuration(onsetCenterHours: Float, comeupCenterHours: Float) = RoaDuration(
        onset = range(onsetCenterHours - 0.5f, onsetCenterHours + 0.5f, DurationUnits.HOURS),
        comeup = range(comeupCenterHours - 0.5f, comeupCenterHours + 0.5f, DurationUnits.HOURS),
        peak = range(1f, 2f, DurationUnits.HOURS),
        offset = range(1f, 2f, DurationUnits.HOURS),
        total = range(6f, 10f, DurationUnits.HOURS),
        afterglow = null,
    )

    /** Samples the curve every 10 s and returns the time of its maximum. */
    private fun coarsePeakSec(curve: IngestionCurve): Float =
        (1..(curve.curveEndSec / 10).toInt())
            .map { it * 10f }
            .maxByOrNull { curve.valueAt(it) }!!

    /** Samples the curve every 0.1 s and returns the time of its maximum. */
    private fun peakSec(curve: IngestionCurve): Float =
        (1..(curve.curveEndSec * 10).toInt())
            .map { it / 10f }
            .maxByOrNull { curve.valueAt(it) }!!

    private fun range(min: Float, max: Float, units: DurationUnits) =
        DurationRange(min = min, max = max, units = units)

    private fun line(weight: Float = 0.5f) = WeightedLine(
        startTime = origin,
        endTime = null,
        horizontalWeight = weight,
        height = 1f,
    )
}
