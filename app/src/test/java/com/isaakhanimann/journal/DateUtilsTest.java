/*
 * Copyright (c) 2022. Isaak Hanimann.
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

package com.isaakhanimann.journal;

import com.isaakhanimann.journal.ui.utils.DateUtils;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.Assert.*;

public class DateUtilsTest {

    @Test
    public void testGetInstant() {
        Instant instant = DateUtils.getInstant(2023, 1, 15, 10, 30);
        assertNotNull(instant);
        
        // Verify the instant can be converted back to LocalDateTime
        LocalDateTime dateTime = DateUtils.getLocalDateTime(instant);
        assertEquals(2023, dateTime.getYear());
        assertEquals(1, dateTime.getMonthValue());
        assertEquals(15, dateTime.getDayOfMonth());
        assertEquals(10, dateTime.getHour());
        assertEquals(30, dateTime.getMinute());
    }

    @Test
    public void testGetStringOfPattern() {
        Instant instant = DateUtils.getInstant(2023, 5, 20, 14, 45);
        String formatted = DateUtils.getStringOfPattern(instant, "yyyy-MM-dd HH:mm");
        assertEquals("2023-05-20 14:45", formatted);
    }

    @Test
    public void testGetShortWeekdayText() {
        Instant instant = DateUtils.getInstant(2023, 10, 15, 12, 0);
        String weekday = DateUtils.getShortWeekdayText(instant);
        assertNotNull(weekday);
        assertTrue(weekday.length() == 3); // Short weekday format
    }

    @Test
    public void testLocalDateTimeConversion() {
        LocalDateTime localDateTime = LocalDateTime.of(2023, 6, 10, 8, 15);
        Instant instant = DateUtils.getInstant(localDateTime);
        LocalDateTime converted = DateUtils.getLocalDateTime(instant);
        
        assertEquals(localDateTime.getYear(), converted.getYear());
        assertEquals(localDateTime.getMonthValue(), converted.getMonthValue());
        assertEquals(localDateTime.getDayOfMonth(), converted.getDayOfMonth());
        assertEquals(localDateTime.getHour(), converted.getHour());
        assertEquals(localDateTime.getMinute(), converted.getMinute());
    }

    @Test
    public void testGetDateWithWeekdayText() {
        Instant instant = DateUtils.getInstant(2023, 12, 25, 0, 0);
        String dateWithWeekday = DateUtils.getDateWithWeekdayText(instant);
        assertNotNull(dateWithWeekday);
        assertTrue(dateWithWeekday.contains("25"));
        assertTrue(dateWithWeekday.contains("Dec") || dateWithWeekday.contains("12"));
        assertTrue(dateWithWeekday.contains("2023"));
    }

    @Test
    public void testEdgeCases() {
        // Test midnight
        Instant midnight = DateUtils.getInstant(2023, 1, 1, 0, 0);
        assertNotNull(midnight);
        
        // Test end of day
        Instant endOfDay = DateUtils.getInstant(2023, 12, 31, 23, 59);
        assertNotNull(endOfDay);
        
        // Test leap year date
        Instant leapDay = DateUtils.getInstant(2024, 2, 29, 12, 0);
        assertNotNull(leapDay);
    }
}
