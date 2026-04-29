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

package foo.pilz.freaklog.data.substances.classes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ToleranceTest {

    @Test
    fun testTolerance_allFieldsPresent() {
        val tolerance = Tolerance(
            full = "almost immediately",
            half = "5-7 days",
            zero = "14 days"
        )
        assertEquals("almost immediately", tolerance.full)
        assertEquals("5-7 days", tolerance.half)
        assertEquals("14 days", tolerance.zero)
    }

    @Test
    fun testTolerance_onlyFull() {
        val tolerance = Tolerance(
            full = "immediately",
            half = null,
            zero = null
        )
        assertEquals("immediately", tolerance.full)
        assertNull(tolerance.half)
        assertNull(tolerance.zero)
    }

    @Test
    fun testTolerance_onlyHalf() {
        val tolerance = Tolerance(
            full = null,
            half = "3 days",
            zero = null
        )
        assertNull(tolerance.full)
        assertEquals("3 days", tolerance.half)
        assertNull(tolerance.zero)
    }

    @Test
    fun testTolerance_onlyZero() {
        val tolerance = Tolerance(
            full = null,
            half = null,
            zero = "2 weeks"
        )
        assertNull(tolerance.full)
        assertNull(tolerance.half)
        assertEquals("2 weeks", tolerance.zero)
    }

    @Test
    fun testTolerance_allNull() {
        val tolerance = Tolerance(
            full = null,
            half = null,
            zero = null
        )
        assertNull(tolerance.full)
        assertNull(tolerance.half)
        assertNull(tolerance.zero)
    }

    @Test
    fun testTolerance_fullAndHalf() {
        val tolerance = Tolerance(
            full = "1 hour",
            half = "12 hours",
            zero = null
        )
        assertEquals("1 hour", tolerance.full)
        assertEquals("12 hours", tolerance.half)
        assertNull(tolerance.zero)
    }

    @Test
    fun testTolerance_halfAndZero() {
        val tolerance = Tolerance(
            full = null,
            half = "7 days",
            zero = "14 days"
        )
        assertNull(tolerance.full)
        assertEquals("7 days", tolerance.half)
        assertEquals("14 days", tolerance.zero)
    }

    @Test
    fun testTolerance_typicalLSD() {
        val tolerance = Tolerance(
            full = "almost immediately after ingestion",
            half = "5-7 days",
            zero = "14 days"
        )
        assertNotNull(tolerance.full)
        assertNotNull(tolerance.half)
        assertNotNull(tolerance.zero)
    }

    @Test
    fun testTolerance_typicalMDMA() {
        val tolerance = Tolerance(
            full = "with repeated use within 1-2 weeks",
            half = "1 month",
            zero = "3 months"
        )
        assertNotNull(tolerance.full)
        assertNotNull(tolerance.half)
        assertNotNull(tolerance.zero)
    }

    @Test
    fun testTolerance_emptyStrings() {
        val tolerance = Tolerance(
            full = "",
            half = "",
            zero = ""
        )
        assertEquals("", tolerance.full)
        assertEquals("", tolerance.half)
        assertEquals("", tolerance.zero)
    }
}
