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

package com.isaakhanimann.journal.ui.tabs.search.substance.roa;

import com.isaakhanimann.journal.ui.utils.DoubleReadableUtils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DoubleReadableExtensionTest {

    @Test
    public void testToReadableString() {
        assertEquals("2", DoubleReadableUtils.toReadableString(2.0));
        assertEquals("20", DoubleReadableUtils.toReadableString(20.0));
        assertEquals("0.33", DoubleReadableUtils.toReadableString(1.0/3.0));
        assertEquals("120", DoubleReadableUtils.toReadableString(120.0));
        assertEquals("1.5", DoubleReadableUtils.toReadableString(1.5));
        assertEquals("1500", DoubleReadableUtils.toReadableString(1500.0));
        assertEquals("12.6", DoubleReadableUtils.toReadableString(12.6));
        assertEquals("0.25", DoubleReadableUtils.toReadableString(0.25));
        assertEquals("3.33", DoubleReadableUtils.toReadableString(10.0/3.0));
        assertEquals("333", DoubleReadableUtils.toReadableString(1000.0/3.0));
        assertEquals("33.3", DoubleReadableUtils.toReadableString(100.0/3.0));
        assertEquals("1.67", DoubleReadableUtils.toReadableString(1.66666));
        assertEquals("123", DoubleReadableUtils.toReadableString(122.66666));
        assertEquals("122", DoubleReadableUtils.toReadableString(122.33333));
        assertEquals("44.4", DoubleReadableUtils.toReadableString(44.4444));
        assertEquals("0.023", DoubleReadableUtils.toReadableString(0.023));
    }
}
