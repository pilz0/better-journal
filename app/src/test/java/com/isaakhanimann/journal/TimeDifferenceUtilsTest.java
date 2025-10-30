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

import com.isaakhanimann.journal.ui.utils.TimeDifferenceUtils;

import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.Assert.*;

public class TimeDifferenceUtilsTest {

    @Test
    public void testMinutesDifference() {
        Instant from = Instant.now();
        Instant to = from.plus(45, ChronoUnit.MINUTES);
        String result = TimeDifferenceUtils.getTimeDifferenceText(from, to);
        assertTrue(result.contains("minutes"));
        assertTrue(result.contains("45"));
    }

    @Test
    public void testHoursDifference() {
        Instant from = Instant.now();
        Instant to = from.plus(5, ChronoUnit.HOURS);
        String result = TimeDifferenceUtils.getTimeDifferenceText(from, to);
        assertTrue(result.contains("hours"));
    }

    @Test
    public void testDaysDifference() {
        Instant from = Instant.now();
        Instant to = from.plus(5, ChronoUnit.DAYS);
        String result = TimeDifferenceUtils.getTimeDifferenceText(from, to);
        assertTrue(result.contains("days"));
    }

    @Test
    public void testWeeksDifference() {
        Instant from = Instant.now();
        Instant to = from.plus(35, ChronoUnit.DAYS);
        String result = TimeDifferenceUtils.getTimeDifferenceText(from, to);
        assertTrue(result.contains("weeks"));
    }

    @Test
    public void testMonthsDifference() {
        Instant from = Instant.now();
        Instant to = from.plus(120, ChronoUnit.DAYS);
        String result = TimeDifferenceUtils.getTimeDifferenceText(from, to);
        assertTrue(result.contains("months"));
    }

    @Test
    public void testYearsDifference() {
        Instant from = Instant.now();
        Instant to = from.plus(900, ChronoUnit.DAYS);
        String result = TimeDifferenceUtils.getTimeDifferenceText(from, to);
        assertTrue(result.contains("years"));
    }

    @Test
    public void testShortTimeDifference() {
        Instant from = Instant.now();
        Instant to = from.plus(30, ChronoUnit.MINUTES);
        String result = TimeDifferenceUtils.getTimeDifferenceText(from, to);
        assertTrue(result.contains("minutes"));
    }

    @Test
    public void testNoDifference() {
        Instant now = Instant.now();
        String result = TimeDifferenceUtils.getTimeDifferenceText(now, now);
        assertTrue(result.contains("0") || result.contains("minutes"));
    }
}
