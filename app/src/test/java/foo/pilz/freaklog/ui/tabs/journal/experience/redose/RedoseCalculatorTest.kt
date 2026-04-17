/*
 * Copyright (c) 2026. FreakLog contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package foo.pilz.freaklog.ui.tabs.journal.experience.redose

import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.DurationUnits
import foo.pilz.freaklog.data.substances.classes.roa.RoaDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class RedoseCalculatorTest {

    private fun min(min: Float, max: Float) = DurationRange(min, max, DurationUnits.MINUTES)

    @Test
    fun `returns null when duration is null`() {
        val t = Instant.parse("2025-01-01T10:00:00Z")
        val result = computeRedoseTime(t, null, RedoseParameters.Default)
        assertNull(result)
    }

    @Test
    fun `returns null when all phases are missing`() {
        val t = Instant.parse("2025-01-01T10:00:00Z")
        val duration = RoaDuration(null, null, null, null, null, null)
        val result = computeRedoseTime(t, duration, RedoseParameters.Default)
        assertNull(result)
    }

    @Test
    fun `default params produce onset + comeup + half peak offset`() {
        val t = Instant.parse("2025-01-01T10:00:00Z")
        // onset avg = 30min, comeup avg = 30min, peak avg = 120min -> default yields
        // onset*1 + comeup*1 + peak*0.5 = 30 + 30 + 60 = 120 min
        val duration = RoaDuration(
            onset = min(15f, 45f),
            comeup = min(15f, 45f),
            peak = min(60f, 180f),
            offset = null,
            total = null,
            afterglow = null
        )
        val result = computeRedoseTime(t, duration, RedoseParameters.Default)
        assertEquals(t.plusSeconds((30 + 30 + 60) * 60L), result)
    }

    @Test
    fun `custom params scale phases linearly`() {
        val t = Instant.parse("2025-01-01T10:00:00Z")
        val duration = RoaDuration(
            onset = min(10f, 10f),
            comeup = min(20f, 20f),
            peak = min(60f, 60f),
            offset = null,
            total = null,
            afterglow = null
        )
        // onset x2 (20) + comeup x0 (0) + peak x1 (60) = 80 min
        val params = RedoseParameters(2f, 0f, 1f)
        val result = computeRedoseTime(t, duration, params)
        assertEquals(t.plusSeconds(80 * 60L), result)
    }

    @Test
    fun `parameters clamp into legal range`() {
        val p = RedoseParameters.sanitize(-1f, 100f, 0.25f)
        assertEquals(0f, p.onsetFraction, 0.0001f)
        assertEquals(3f, p.comeupFraction, 0.0001f)
        assertEquals(0.25f, p.peakFraction, 0.0001f)
    }

    @Test
    fun `parameters fall back to defaults on NaN or infinity`() {
        val p = RedoseParameters.sanitize(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        assertEquals(RedoseParameters.Default.onsetFraction, p.onsetFraction, 0.0001f)
        assertEquals(RedoseParameters.Default.comeupFraction, p.comeupFraction, 0.0001f)
        assertEquals(RedoseParameters.Default.peakFraction, p.peakFraction, 0.0001f)
    }

    @Test
    fun `uses only available phases when some are missing`() {
        val t = Instant.parse("2025-01-01T10:00:00Z")
        val duration = RoaDuration(
            onset = null,
            comeup = null,
            peak = min(60f, 60f),
            offset = null,
            total = null,
            afterglow = null
        )
        // peak x 0.5 = 30 min
        val result = computeRedoseTime(t, duration, RedoseParameters.Default)
        assertEquals(t.plusSeconds(30 * 60L), result)
    }

    @Test
    fun `zero total offset returns null`() {
        val t = Instant.parse("2025-01-01T10:00:00Z")
        val duration = RoaDuration(
            onset = min(0f, 0f),
            comeup = null,
            peak = null,
            offset = null,
            total = null,
            afterglow = null
        )
        val result = computeRedoseTime(t, duration, RedoseParameters.Default)
        assertNull(result)
    }
}
