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
import org.junit.Assert.assertNull
import org.junit.Test

class BioavailabilityTest {

    @Test
    fun testBioavailability_bothValues() {
        val bio = Bioavailability(min = 20.0, max = 40.0)
        assertEquals(20.0, bio.min!!, 0.01)
        assertEquals(40.0, bio.max!!, 0.01)
    }

    @Test
    fun testBioavailability_onlyMin() {
        val bio = Bioavailability(min = 25.0, max = null)
        assertEquals(25.0, bio.min!!, 0.01)
        assertNull(bio.max)
    }

    @Test
    fun testBioavailability_onlyMax() {
        val bio = Bioavailability(min = null, max = 75.0)
        assertNull(bio.min)
        assertEquals(75.0, bio.max!!, 0.01)
    }

    @Test
    fun testBioavailability_bothNull() {
        val bio = Bioavailability(min = null, max = null)
        assertNull(bio.min)
        assertNull(bio.max)
    }

    @Test
    fun testBioavailability_fullRange() {
        val bio = Bioavailability(min = 0.0, max = 100.0)
        assertEquals(0.0, bio.min!!, 0.01)
        assertEquals(100.0, bio.max!!, 0.01)
    }

    @Test
    fun testBioavailability_sameValue() {
        val bio = Bioavailability(min = 50.0, max = 50.0)
        assertEquals(bio.min, bio.max)
    }

    @Test
    fun testBioavailability_typicalOral() {
        // Morphine oral bioavailability is typically 20-40%
        val bio = Bioavailability(min = 20.0, max = 40.0)
        assertEquals(20.0, bio.min!!, 0.01)
        assertEquals(40.0, bio.max!!, 0.01)
    }

    @Test
    fun testBioavailability_highBioavailability() {
        // Intravenous route typically has 100% bioavailability
        val bio = Bioavailability(min = 100.0, max = 100.0)
        assertEquals(100.0, bio.min!!, 0.01)
        assertEquals(100.0, bio.max!!, 0.01)
    }

    @Test
    fun testBioavailability_lowBioavailability() {
        // Some substances have very low bioavailability
        val bio = Bioavailability(min = 2.0, max = 8.0)
        assertEquals(2.0, bio.min!!, 0.01)
        assertEquals(8.0, bio.max!!, 0.01)
    }

    @Test
    fun testBioavailability_decimalPrecision() {
        val bio = Bioavailability(min = 33.33, max = 66.67)
        assertEquals(33.33, bio.min!!, 0.01)
        assertEquals(66.67, bio.max!!, 0.01)
    }
}
