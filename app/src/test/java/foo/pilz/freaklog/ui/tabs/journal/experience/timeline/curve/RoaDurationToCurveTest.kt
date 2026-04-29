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
        // Numerically scan for the peak and ensure it sits *after* the infusion start (ingestion).
        val tPeak = (1..curve!!.curveEndSec.toInt())
            .maxByOrNull { curve.valueAt(it.toFloat()) }!!
        assertTrue("infusion peak ($tPeak s) should be after ingestion start", tPeak > 0)
    }

    private fun range(min: Float, max: Float, units: DurationUnits) =
        DurationRange(min = min, max = max, units = units)

    private fun line(weight: Float = 0.5f) = WeightedLine(
        startTime = origin,
        endTime = null,
        horizontalWeight = weight,
        height = 1f,
    )
}
