/*
 * Copyright (c) 2022-2023. Isaak Hanimann.
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

import com.isaakhanimann.journal.ui.utils.AxisDrawableUtils;
import com.isaakhanimann.journal.ui.utils.DateUtils;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.*;

public class AxisDrawableUtilsTest {

    @Test
    public void testGetInstantsBetween() {
        Instant start = DateUtils.getInstant(2023, 5, 10, 8, 30);
        Instant end = DateUtils.getInstant(2023, 5, 10, 14, 30);
        
        List<Instant> instants = AxisDrawableUtils.getInstantsBetween(start, end, 1);
        
        assertNotNull(instants);
        assertTrue(instants.size() > 0);
        assertEquals(6, instants.size());
    }

    @Test
    public void testGetInstantsBetweenWithLargerStep() {
        Instant start = DateUtils.getInstant(2023, 5, 10, 0, 0);
        Instant end = DateUtils.getInstant(2023, 5, 11, 0, 0);
        
        List<Instant> instants = AxisDrawableUtils.getInstantsBetween(start, end, 3);
        
        assertNotNull(instants);
        assertTrue(instants.size() > 0);
        assertTrue(instants.size() <= 8); // 24 hours / 3 hour steps = 8 max
    }

    @Test
    public void testNearestFullHourInTheFuture() {
        Instant now = DateUtils.getInstant(2023, 5, 10, 14, 25);
        Instant nearestHour = AxisDrawableUtils.nearestFullHourInTheFuture(now);
        
        assertNotNull(nearestHour);
        LocalDateTime dateTime = DateUtils.getLocalDateTime(nearestHour);
        
        assertEquals(0, dateTime.getMinute());
        assertEquals(0, dateTime.getSecond());
        assertTrue(dateTime.getHour() == 15); // Next full hour after 14:25
    }

    @Test
    public void testNearestFullHourAtExactHour() {
        Instant exactHour = DateUtils.getInstant(2023, 5, 10, 14, 0);
        Instant nearestHour = AxisDrawableUtils.nearestFullHourInTheFuture(exactHour);
        
        assertNotNull(nearestHour);
        LocalDateTime dateTime = DateUtils.getLocalDateTime(nearestHour);
        
        assertEquals(0, dateTime.getMinute());
        assertEquals(0, dateTime.getSecond());
        assertEquals(15, dateTime.getHour()); // Next hour after 14:00 is 15:00
    }

    @Test
    public void testEmptyListWhenEndBeforeStart() {
        Instant start = DateUtils.getInstant(2023, 5, 10, 14, 0);
        Instant end = DateUtils.getInstant(2023, 5, 10, 10, 0);
        
        List<Instant> instants = AxisDrawableUtils.getInstantsBetween(start, end, 1);
        
        assertNotNull(instants);
        assertEquals(0, instants.size());
    }

    @Test
    public void testGetInstantsBetweenOverMidnight() {
        Instant start = DateUtils.getInstant(2023, 5, 10, 22, 0);
        Instant end = DateUtils.getInstant(2023, 5, 11, 4, 0);
        
        List<Instant> instants = AxisDrawableUtils.getInstantsBetween(start, end, 1);
        
        assertNotNull(instants);
        assertTrue(instants.size() > 0);
        assertEquals(5, instants.size()); // From 23:00 to 03:00 (before 04:00)
    }
}
