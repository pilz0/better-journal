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

import com.isaakhanimann.journal.ui.utils.DoubleReadableUtils;

import org.junit.Test;

import static org.junit.Assert.*;

public class DoubleReadableUtilsTest {

    @Test
    public void testRoundToSignificantDigits() {
        assertEquals(12.3, DoubleReadableUtils.roundToSignificantDigits(12.345, 3), 0.001);
        assertEquals(1.23, DoubleReadableUtils.roundToSignificantDigits(1.234, 3), 0.001);
        assertEquals(123.0, DoubleReadableUtils.roundToSignificantDigits(123.45, 3), 0.1);
        assertEquals(0.0, DoubleReadableUtils.roundToSignificantDigits(0.0, 3), 0.0);
    }

    @Test
    public void testFormatToMaximumFractionDigits() {
        assertEquals("12.345", DoubleReadableUtils.formatToMaximumFractionDigits(12.345, 6));
        assertEquals("12.3", DoubleReadableUtils.formatToMaximumFractionDigits(12.3, 6));
        assertEquals("12", DoubleReadableUtils.formatToMaximumFractionDigits(12.0, 6));
        assertEquals("0.12345", DoubleReadableUtils.formatToMaximumFractionDigits(0.12345, 6));
    }

    @Test
    public void testToReadableStringWholeNumbers() {
        assertEquals("2", DoubleReadableUtils.toReadableString(2.0));
        assertEquals("20", DoubleReadableUtils.toReadableString(20.0));
        assertEquals("120", DoubleReadableUtils.toReadableString(120.0));
        assertEquals("1500", DoubleReadableUtils.toReadableString(1500.0));
    }

    @Test
    public void testToReadableStringDecimals() {
        assertEquals("1.5", DoubleReadableUtils.toReadableString(1.5));
        assertEquals("12.6", DoubleReadableUtils.toReadableString(12.6));
        assertEquals("0.25", DoubleReadableUtils.toReadableString(0.25));
    }

    @Test
    public void testToReadableStringFractions() {
        assertEquals("0.33", DoubleReadableUtils.toReadableString(1.0/3.0));
        assertEquals("3.33", DoubleReadableUtils.toReadableString(10.0/3.0));
        assertEquals("33.3", DoubleReadableUtils.toReadableString(100.0/3.0));
        assertEquals("333", DoubleReadableUtils.toReadableString(1000.0/3.0));
    }

    @Test
    public void testToReadableStringRounding() {
        assertEquals("1.67", DoubleReadableUtils.toReadableString(1.66666));
        assertEquals("123", DoubleReadableUtils.toReadableString(122.66666));
        assertEquals("122", DoubleReadableUtils.toReadableString(122.33333));
        assertEquals("44.4", DoubleReadableUtils.toReadableString(44.4444));
    }

    @Test
    public void testToReadableStringSmallNumbers() {
        assertEquals("0.023", DoubleReadableUtils.toReadableString(0.023));
        assertEquals("0.0023", DoubleReadableUtils.toReadableString(0.0023));
    }

    @Test
    public void testToPreservedString() {
        String result = DoubleReadableUtils.toPreservedString(12.345);
        assertTrue(result.contains("12"));
        assertTrue(result.contains("345"));
        assertFalse(result.contains(",")); // Should use US locale without grouping
    }

    @Test
    public void testToReadableStringZero() {
        assertEquals("0", DoubleReadableUtils.toReadableString(0.0));
    }

    @Test
    public void testToReadableStringVeryLargeNumbers() {
        String result = DoubleReadableUtils.toReadableString(1000000.0);
        assertTrue(result.startsWith("1"));
        assertTrue(result.length() <= 8); // Rounded to 3 significant digits
    }

    @Test
    public void testToReadableStringNegativeNumbers() {
        String result = DoubleReadableUtils.toReadableString(-12.345);
        assertTrue(result.startsWith("-"));
        assertTrue(result.contains("12"));
    }
}
