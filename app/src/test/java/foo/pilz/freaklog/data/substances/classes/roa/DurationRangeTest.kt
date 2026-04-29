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
import org.junit.Assert.assertNull
import org.junit.Test

class DurationRangeTest {

    // ===== text property Tests =====

    @Test
    fun testText_hoursRange() {
        val range = DurationRange(1.0f, 3.0f, DurationUnits.HOURS)
        assertEquals("1-3h", range.text)
    }

    @Test
    fun testText_minutesRange() {
        val range = DurationRange(15.0f, 45.0f, DurationUnits.MINUTES)
        assertEquals("15-45m", range.text)
    }

    @Test
    fun testText_secondsRange() {
        val range = DurationRange(30.0f, 90.0f, DurationUnits.SECONDS)
        assertEquals("30-90s", range.text)
    }

    @Test
    fun testText_daysRange() {
        val range = DurationRange(1.0f, 2.0f, DurationUnits.DAYS)
        assertEquals("1-2d", range.text)
    }

    @Test
    fun testText_floatValues() {
        val range = DurationRange(1.5f, 3.5f, DurationUnits.HOURS)
        assertEquals("1.5-3.5h", range.text)
    }

    @Test
    fun testText_nullUnits() {
        val range = DurationRange(1.0f, 3.0f, null)
        assertEquals("1-3", range.text)
    }

    @Test
    fun testText_removeSuffixZero() {
        val range = DurationRange(2.0f, 4.0f, DurationUnits.HOURS)
        assertEquals("2-4h", range.text)
    }

    // ===== minInSec Tests =====

    @Test
    fun testMinInSec_seconds() {
        val range = DurationRange(30.0f, 60.0f, DurationUnits.SECONDS)
        assertEquals(30.0f, range.minInSec!!, 0.01f)
    }

    @Test
    fun testMinInSec_minutes() {
        val range = DurationRange(30.0f, 60.0f, DurationUnits.MINUTES)
        assertEquals(1800.0f, range.minInSec!!, 0.01f) // 30 * 60
    }

    @Test
    fun testMinInSec_hours() {
        val range = DurationRange(2.0f, 4.0f, DurationUnits.HOURS)
        assertEquals(7200.0f, range.minInSec!!, 0.01f) // 2 * 3600
    }

    @Test
    fun testMinInSec_days() {
        val range = DurationRange(1.0f, 2.0f, DurationUnits.DAYS)
        assertEquals(86400.0f, range.minInSec!!, 0.01f) // 1 * 86400
    }

    @Test
    fun testMinInSec_nullMin() {
        val range = DurationRange(null, 60.0f, DurationUnits.MINUTES)
        assertNull(range.minInSec)
    }

    @Test
    fun testMinInSec_nullUnits() {
        val range = DurationRange(30.0f, 60.0f, null)
        assertNull(range.minInSec)
    }

    // ===== maxInSec Tests =====

    @Test
    fun testMaxInSec_seconds() {
        val range = DurationRange(30.0f, 90.0f, DurationUnits.SECONDS)
        assertEquals(90.0f, range.maxInSec!!, 0.01f)
    }

    @Test
    fun testMaxInSec_minutes() {
        val range = DurationRange(30.0f, 60.0f, DurationUnits.MINUTES)
        assertEquals(3600.0f, range.maxInSec!!, 0.01f) // 60 * 60
    }

    @Test
    fun testMaxInSec_hours() {
        val range = DurationRange(2.0f, 6.0f, DurationUnits.HOURS)
        assertEquals(21600.0f, range.maxInSec!!, 0.01f) // 6 * 3600
    }

    @Test
    fun testMaxInSec_days() {
        val range = DurationRange(1.0f, 3.0f, DurationUnits.DAYS)
        assertEquals(259200.0f, range.maxInSec!!, 0.01f) // 3 * 86400
    }

    @Test
    fun testMaxInSec_nullMax() {
        val range = DurationRange(30.0f, null, DurationUnits.MINUTES)
        assertNull(range.maxInSec)
    }

    @Test
    fun testMaxInSec_nullUnits() {
        val range = DurationRange(30.0f, 60.0f, null)
        assertNull(range.maxInSec)
    }

    // ===== interpolateAtValueInSeconds Tests =====

    @Test
    fun testInterpolate_atZero() {
        val range = DurationRange(1.0f, 3.0f, DurationUnits.HOURS)
        val result = range.interpolateAtValueInSeconds(0.0f)
        assertEquals(3600.0f, result!!, 0.01f) // 1 hour in seconds
    }

    @Test
    fun testInterpolate_atOne() {
        val range = DurationRange(1.0f, 3.0f, DurationUnits.HOURS)
        val result = range.interpolateAtValueInSeconds(1.0f)
        assertEquals(10800.0f, result!!, 0.01f) // 3 hours in seconds
    }

    @Test
    fun testInterpolate_atHalf() {
        val range = DurationRange(1.0f, 3.0f, DurationUnits.HOURS)
        val result = range.interpolateAtValueInSeconds(0.5f)
        assertEquals(7200.0f, result!!, 0.01f) // 2 hours in seconds
    }

    @Test
    fun testInterpolate_minutesRange() {
        val range = DurationRange(30.0f, 60.0f, DurationUnits.MINUTES)
        val result = range.interpolateAtValueInSeconds(0.5f)
        assertEquals(2700.0f, result!!, 0.01f) // 45 minutes in seconds
    }

    @Test
    fun testInterpolate_nullMin() {
        val range = DurationRange(null, 3.0f, DurationUnits.HOURS)
        assertNull(range.interpolateAtValueInSeconds(0.5f))
    }

    @Test
    fun testInterpolate_nullMax() {
        val range = DurationRange(1.0f, null, DurationUnits.HOURS)
        assertNull(range.interpolateAtValueInSeconds(0.5f))
    }

    @Test
    fun testInterpolate_nullUnits() {
        val range = DurationRange(1.0f, 3.0f, null)
        assertNull(range.interpolateAtValueInSeconds(0.5f))
    }

    @Test
    fun testInterpolate_beyondOne() {
        val range = DurationRange(1.0f, 3.0f, DurationUnits.HOURS)
        val result = range.interpolateAtValueInSeconds(1.5f)
        assertEquals(14400.0f, result!!, 0.01f) // 4 hours in seconds
    }

    @Test
    fun testInterpolate_negativeValue() {
        val range = DurationRange(1.0f, 3.0f, DurationUnits.HOURS)
        val result = range.interpolateAtValueInSeconds(-0.5f)
        assertEquals(0.0f, result!!, 0.01f) // 0 hours in seconds
    }
}
