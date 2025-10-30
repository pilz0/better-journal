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

import com.isaakhanimann.journal.ui.utils.AxisDrawableUtils;
import com.isaakhanimann.journal.ui.utils.DateUtils;
import com.isaakhanimann.journal.ui.utils.TimeDifferenceUtils;

import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See <a href="http://d.android.com/tools/testing">testing documentation</a>.
 */
public class TestDates {
    
    @Test
    public void datesBetweenAreCorrect() {
        Instant startTime = DateUtils.getInstant(2022, 6, 5, 14, 20);
        Instant endTime = DateUtils.getInstant(2022, 6, 5, 20, 20);
        List<Instant> fullHours = AxisDrawableUtils.getInstantsBetween(
            startTime,
            endTime,
            1
        );
        assertEquals(6, fullHours.size());
    }

    @Test
    public void dateDifferences() {
        Instant fromDate = Instant.now().minus(2, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS);
        String text = TimeDifferenceUtils.getTimeDifferenceText(fromDate, Instant.now());
        assertEquals("1,9 days", text);
    }

    @Test
    public void dateRange() {
        Instant firstIngestionTime = DateUtils.getInstant(2022, 9, 23, 14, 20);
        Instant lastIngestionTime = DateUtils.getInstant(2022, 9, 23, 23, 20);
        Instant selectedDate = DateUtils.getInstant(2022, 9, 21, 23, 20);
        assertFalse(
            selectedDate.minus(12, ChronoUnit.HOURS).isBefore(lastIngestionTime) &&
            selectedDate.plus(12, ChronoUnit.HOURS).isAfter(firstIngestionTime)
        );
    }

    @Test
    public void testTimeZone() {
        Instant instant = DateUtils.getInstant(2022, 9, 23, 9, 20);
        assertEquals("09:20", DateUtils.getStringOfPattern(instant, "HH:mm"));
    }
}
