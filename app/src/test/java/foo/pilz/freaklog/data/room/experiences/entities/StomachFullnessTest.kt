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

package foo.pilz.freaklog.data.room.experiences.entities

import org.junit.Assert.assertEquals
import org.junit.Test

class StomachFullnessTest {

    @Test
    fun testEmpty_text() {
        assertEquals("Empty", StomachFullness.EMPTY.text)
    }

    @Test
    fun testQuarterFull_text() {
        assertEquals("Quarter full", StomachFullness.QUARTER_FULL.text)
    }

    @Test
    fun testHalfFull_text() {
        assertEquals("Half full", StomachFullness.HALF_FULL.text)
    }

    @Test
    fun testFull_text() {
        assertEquals("Full", StomachFullness.FULL.text)
    }

    @Test
    fun testVeryFull_text() {
        assertEquals("Very full", StomachFullness.VERY_FULL.text)
    }

    @Test
    fun testEmpty_serialized() {
        assertEquals("EMPTY", StomachFullness.EMPTY.serialized)
    }

    @Test
    fun testQuarterFull_serialized() {
        assertEquals("QUARTERFULL", StomachFullness.QUARTER_FULL.serialized)
    }

    @Test
    fun testHalfFull_serialized() {
        assertEquals("HALFFULL", StomachFullness.HALF_FULL.serialized)
    }

    @Test
    fun testFull_serialized() {
        assertEquals("FULL", StomachFullness.FULL.serialized)
    }

    @Test
    fun testVeryFull_serialized() {
        assertEquals("VERYFULL", StomachFullness.VERY_FULL.serialized)
    }

    @Test
    fun testEmpty_onsetDelay() {
        assertEquals(0.0, StomachFullness.EMPTY.onsetDelayForOralInHours, 0.01)
    }

    @Test
    fun testQuarterFull_onsetDelay() {
        assertEquals(0.75, StomachFullness.QUARTER_FULL.onsetDelayForOralInHours, 0.01)
    }

    @Test
    fun testHalfFull_onsetDelay() {
        assertEquals(1.5, StomachFullness.HALF_FULL.onsetDelayForOralInHours, 0.01)
    }

    @Test
    fun testFull_onsetDelay() {
        assertEquals(3.0, StomachFullness.FULL.onsetDelayForOralInHours, 0.01)
    }

    @Test
    fun testVeryFull_onsetDelay() {
        assertEquals(4.0, StomachFullness.VERY_FULL.onsetDelayForOralInHours, 0.01)
    }

    @Test
    fun testOnsetDelayIncreases() {
        val values = StomachFullness.entries.map { it.onsetDelayForOralInHours }
        for (i in 0 until values.size - 1) {
            assertTrue("Onset delay should increase with stomach fullness",
                values[i] < values[i + 1])
        }
    }

    @Test
    fun testAllEnumValues() {
        assertEquals(5, StomachFullness.entries.size)
    }

    @Test
    fun testFindBySerializedValue() {
        StomachFullness.entries.forEach { fullness ->
            val found = StomachFullness.entries.find { it.serialized == fullness.serialized }
            assertEquals(fullness, found)
        }
    }

    private fun assertTrue(message: String, condition: Boolean) {
        org.junit.Assert.assertTrue(message, condition)
    }
}
