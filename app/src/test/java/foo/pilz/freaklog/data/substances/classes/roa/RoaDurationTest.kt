/*
 * Copyright (c) 2022-2024. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.data.substances.classes.roa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RoaDurationTest {

    @Test
    fun testRoaDuration_allPhasesPresent() {
        val duration = RoaDuration(
            onset = DurationRange(15f, 30f, DurationUnits.MINUTES),
            comeup = DurationRange(30f, 60f, DurationUnits.MINUTES),
            peak = DurationRange(2f, 4f, DurationUnits.HOURS),
            offset = DurationRange(2f, 4f, DurationUnits.HOURS),
            total = DurationRange(6f, 10f, DurationUnits.HOURS),
            afterglow = DurationRange(6f, 12f, DurationUnits.HOURS)
        )
        assertNotNull(duration.onset)
        assertNotNull(duration.comeup)
        assertNotNull(duration.peak)
        assertNotNull(duration.offset)
        assertNotNull(duration.total)
        assertNotNull(duration.afterglow)
    }

    @Test
    fun testRoaDuration_onlyOnset() {
        val duration = RoaDuration(
            onset = DurationRange(5f, 15f, DurationUnits.MINUTES),
            comeup = null,
            peak = null,
            offset = null,
            total = null,
            afterglow = null
        )
        assertNotNull(duration.onset)
        assertNull(duration.comeup)
        assertNull(duration.peak)
        assertNull(duration.offset)
        assertNull(duration.total)
        assertNull(duration.afterglow)
    }

    @Test
    fun testRoaDuration_onlyPeak() {
        val duration = RoaDuration(
            onset = null,
            comeup = null,
            peak = DurationRange(3f, 5f, DurationUnits.HOURS),
            offset = null,
            total = null,
            afterglow = null
        )
        assertNull(duration.onset)
        assertNotNull(duration.peak)
    }

    @Test
    fun testRoaDuration_onlyTotal() {
        val duration = RoaDuration(
            onset = null,
            comeup = null,
            peak = null,
            offset = null,
            total = DurationRange(8f, 12f, DurationUnits.HOURS),
            afterglow = null
        )
        assertNotNull(duration.total)
    }

    @Test
    fun testRoaDuration_allNull() {
        val duration = RoaDuration(
            onset = null,
            comeup = null,
            peak = null,
            offset = null,
            total = null,
            afterglow = null
        )
        assertNull(duration.onset)
        assertNull(duration.comeup)
        assertNull(duration.peak)
        assertNull(duration.offset)
        assertNull(duration.total)
        assertNull(duration.afterglow)
    }

    @Test
    fun testRoaDuration_typicalMDMA() {
        val duration = RoaDuration(
            onset = DurationRange(30f, 60f, DurationUnits.MINUTES),
            comeup = DurationRange(15f, 30f, DurationUnits.MINUTES),
            peak = DurationRange(1.5f, 2.5f, DurationUnits.HOURS),
            offset = DurationRange(1f, 1.5f, DurationUnits.HOURS),
            total = DurationRange(3f, 5f, DurationUnits.HOURS),
            afterglow = DurationRange(12f, 48f, DurationUnits.HOURS)
        )
        assertEquals(30f, duration.onset!!.min!!, 0.01f)
        assertEquals(3f, duration.total!!.min!!, 0.01f)
    }

    @Test
    fun testRoaDuration_typicalLSD() {
        val duration = RoaDuration(
            onset = DurationRange(15f, 30f, DurationUnits.MINUTES),
            comeup = DurationRange(15f, 30f, DurationUnits.MINUTES),
            peak = DurationRange(3f, 5f, DurationUnits.HOURS),
            offset = DurationRange(3f, 5f, DurationUnits.HOURS),
            total = DurationRange(8f, 12f, DurationUnits.HOURS),
            afterglow = DurationRange(12f, 48f, DurationUnits.HOURS)
        )
        assertEquals(8f, duration.total!!.min!!, 0.01f)
        assertEquals(12f, duration.total!!.max!!, 0.01f)
    }

    @Test
    fun testRoaDuration_shortActing() {
        // Short acting substance like DMT smoked
        val duration = RoaDuration(
            onset = DurationRange(0f, 1f, DurationUnits.MINUTES),
            comeup = DurationRange(1f, 3f, DurationUnits.MINUTES),
            peak = DurationRange(3f, 10f, DurationUnits.MINUTES),
            offset = DurationRange(5f, 15f, DurationUnits.MINUTES),
            total = DurationRange(10f, 30f, DurationUnits.MINUTES),
            afterglow = DurationRange(15f, 60f, DurationUnits.MINUTES)
        )
        assertEquals(10f, duration.total!!.min!!, 0.01f)
        assertEquals(30f, duration.total!!.max!!, 0.01f)
    }

    @Test
    fun testRoaDuration_longActing() {
        // Long acting substance like ibogaine
        val duration = RoaDuration(
            onset = DurationRange(1f, 3f, DurationUnits.HOURS),
            comeup = DurationRange(1f, 2f, DurationUnits.HOURS),
            peak = DurationRange(4f, 8f, DurationUnits.HOURS),
            offset = DurationRange(8f, 12f, DurationUnits.HOURS),
            total = DurationRange(24f, 36f, DurationUnits.HOURS),
            afterglow = DurationRange(1f, 3f, DurationUnits.DAYS)
        )
        assertEquals(24f, duration.total!!.min!!, 0.01f)
    }

    @Test
    fun testRoaDuration_mixedUnits() {
        // Different phases can have different time units
        val duration = RoaDuration(
            onset = DurationRange(30f, 60f, DurationUnits.SECONDS),
            comeup = DurationRange(5f, 10f, DurationUnits.MINUTES),
            peak = DurationRange(1f, 2f, DurationUnits.HOURS),
            offset = DurationRange(30f, 60f, DurationUnits.MINUTES),
            total = DurationRange(2f, 4f, DurationUnits.HOURS),
            afterglow = DurationRange(1f, 2f, DurationUnits.DAYS)
        )
        assertEquals(DurationUnits.SECONDS, duration.onset!!.units)
        assertEquals(DurationUnits.MINUTES, duration.comeup!!.units)
        assertEquals(DurationUnits.HOURS, duration.peak!!.units)
        assertEquals(DurationUnits.DAYS, duration.afterglow!!.units)
    }
}
