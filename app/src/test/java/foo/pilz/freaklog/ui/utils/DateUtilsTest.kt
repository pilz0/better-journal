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

package foo.pilz.freaklog.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class DateUtilsTest {

    // ===== getInstant Tests =====

    @Test
    fun testGetInstant_validDate() {
        val instant = getInstant(2022, 6, 15, 14, 30)
        assertNotNull(instant)
    }

    @Test
    fun testGetInstant_startOfDay() {
        val instant = getInstant(2022, 1, 1, 0, 0)
        assertNotNull(instant)
    }

    @Test
    fun testGetInstant_endOfDay() {
        val instant = getInstant(2022, 12, 31, 23, 59)
        assertNotNull(instant)
    }

    // ===== getStringOfPattern Tests (on Instant) =====

    @Test
    fun testGetStringOfPattern_hourMinute() {
        val instant = getInstant(2022, 9, 23, 9, 20)!!
        assertEquals("09:20", instant.getStringOfPattern("HH:mm"))
    }

    @Test
    fun testGetStringOfPattern_dateFormat() {
        val instant = getInstant(2022, 9, 23, 12, 0)!!
        val result = instant.getStringOfPattern("yyyy-MM-dd")
        assertEquals("2022-09-23", result)
    }

    @Test
    fun testGetStringOfPattern_hourOnly() {
        val instant = getInstant(2022, 6, 15, 14, 30)!!
        val result = instant.getStringOfPattern("HH")
        assertEquals("14", result)
    }

    @Test
    fun testGetStringOfPattern_yearOnly() {
        val instant = getInstant(2025, 6, 15, 14, 30)!!
        val result = instant.getStringOfPattern("yyyy")
        assertEquals("2025", result)
    }

    // ===== getDateWithWeekdayText Tests =====

    @Test
    fun testGetDateWithWeekdayText_format() {
        val instant = getInstant(2022, 9, 23, 12, 0)!!
        val result = instant.getDateWithWeekdayText()
        // Should be in format "EEE dd MMM yyyy" which includes day of month and year
        assertNotNull(result)
        assertTrue(result.contains("23"))
        assertTrue(result.contains("2022"))
    }

    @Test
    fun testGetShortWeekdayText_returnsWeekday() {
        val instant = getInstant(2022, 9, 23, 12, 0)!!
        val result = instant.getShortWeekdayText()
        // Should be a weekday abbreviation like "Fri"
        assertNotNull(result)
        assertTrue(result.length >= 2)
    }

    // ===== getLocalDateTime / getInstant roundtrip Tests =====

    @Test
    fun testLocalDateTimeRoundtrip() {
        val original = getInstant(2022, 6, 15, 14, 30)!!
        val localDateTime = original.getLocalDateTime()
        val roundtrip = localDateTime.getInstant()
        
        // Should be within 1 second due to potential nanosecond differences
        val diff = java.time.Duration.between(original, roundtrip).abs()
        assertTrue(diff.seconds < 1)
    }

    // ===== getTimeDifferenceText Tests =====

    @Test
    fun testGetTimeDifferenceText_minutesDifference() {
        val fromDate = Instant.now().minus(30, ChronoUnit.MINUTES)
        val text = getTimeDifferenceText(fromDate, Instant.now())
        assertTrue(text.contains("minutes"))
    }

    @Test
    fun testGetTimeDifferenceText_hoursDifference() {
        val fromDate = Instant.now().minus(5, ChronoUnit.HOURS)
        val text = getTimeDifferenceText(fromDate, Instant.now())
        assertTrue(text.contains("hours"))
    }

    @Test
    fun testGetTimeDifferenceText_daysDifference() {
        val fromDate = Instant.now().minus(5, ChronoUnit.DAYS)
        val text = getTimeDifferenceText(fromDate, Instant.now())
        assertTrue(text.contains("days"))
    }

    @Test
    fun testGetTimeDifferenceText_weeksDifference() {
        val fromDate = Instant.now().minus(42, ChronoUnit.DAYS) // 6 weeks
        val text = getTimeDifferenceText(fromDate, Instant.now())
        assertTrue(text.contains("weeks"))
    }

    @Test
    fun testGetTimeDifferenceText_monthsDifference() {
        val fromDate = Instant.now().minus(100, ChronoUnit.DAYS)
        val text = getTimeDifferenceText(fromDate, Instant.now())
        assertTrue(text.contains("months"))
    }

    @Test
    fun testGetTimeDifferenceText_yearsDifference() {
        val fromDate = Instant.now().minus(900, ChronoUnit.DAYS)
        val text = getTimeDifferenceText(fromDate, Instant.now())
        assertTrue(text.contains("years"))
    }

    @Test
    fun testGetTimeDifferenceText_exactHours() {
        val fromDate = Instant.now().minus(2, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS)
        val text = getTimeDifferenceText(fromDate, Instant.now())
        assertEquals("45 hours", text)
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
