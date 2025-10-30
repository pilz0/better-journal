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

import org.junit.Test;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestRegex {
    
    @Test
    public void testRegex() {
        String patternString = "5-MeO-xxT".replace("x", "[\\\\S]*");
        Pattern regex = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
        
        assertTrue(regex.matcher("5-MeO-DALT").matches());
        assertTrue(regex.matcher("5-MeO-DMT").matches());
        assertTrue(regex.matcher("5-MeO-DiPT").matches());
        assertTrue(regex.matcher("5-MeO-EiPT").matches());
        assertFalse(regex.matcher("something else").matches());
    }
}
