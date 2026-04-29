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
import org.junit.Test

class DurationUnitsTest {

    @Test
    fun testSecondsText() {
        assertEquals("seconds", DurationUnits.SECONDS.text)
    }

    @Test
    fun testMinutesText() {
        assertEquals("minutes", DurationUnits.MINUTES.text)
    }

    @Test
    fun testHoursText() {
        assertEquals("hours", DurationUnits.HOURS.text)
    }

    @Test
    fun testDaysText() {
        assertEquals("days", DurationUnits.DAYS.text)
    }

    @Test
    fun testSecondsShortText() {
        assertEquals("s", DurationUnits.SECONDS.shortText)
    }

    @Test
    fun testMinutesShortText() {
        assertEquals("m", DurationUnits.MINUTES.shortText)
    }

    @Test
    fun testHoursShortText() {
        assertEquals("h", DurationUnits.HOURS.shortText)
    }

    @Test
    fun testDaysShortText() {
        assertEquals("d", DurationUnits.DAYS.shortText)
    }

    @Test
    fun testSecondsMultiplier() {
        assertEquals(1, DurationUnits.SECONDS.inSecondsMultiplier)
    }

    @Test
    fun testMinutesMultiplier() {
        assertEquals(60, DurationUnits.MINUTES.inSecondsMultiplier)
    }

    @Test
    fun testHoursMultiplier() {
        assertEquals(3600, DurationUnits.HOURS.inSecondsMultiplier)
    }

    @Test
    fun testDaysMultiplier() {
        assertEquals(86400, DurationUnits.DAYS.inSecondsMultiplier)
    }

    @Test
    fun testAllEnumValues() {
        val allUnits = DurationUnits.entries
        assertEquals(4, allUnits.size)
    }
}
