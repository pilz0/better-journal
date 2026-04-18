/*
 * Copyright (c) 2024. Isaak Hanimann.
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

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveColorSerializerTest {

    private val json = Json

    @Test
    fun testSerialize_preset_writesNameAsString() {
        val encoded = json.encodeToString(AdaptiveColorSerializer, AdaptiveColor.RED)
        assertEquals("\"RED\"", encoded)
    }

    @Test
    fun testDeserialize_presetByName_backwardsCompat() {
        // JSON exports written when AdaptiveColor was an enum used the bare name string.
        val decoded = json.decodeFromString(AdaptiveColorSerializer, "\"BLUE\"")
        assertEquals(AdaptiveColor.BLUE, decoded)
    }

    @Test
    fun testSerialize_custom_writesCustomPrefixedHex() {
        val encoded = json.encodeToString(
            AdaptiveColorSerializer,
            AdaptiveColor.Custom(0xFF112233.toInt())
        )
        assertEquals("\"CUSTOM_FF112233\"", encoded)
    }

    @Test
    fun testRoundTrip_custom() {
        val original = AdaptiveColor.Custom(0xFFABCDEF.toInt())
        val encoded = json.encodeToString(AdaptiveColorSerializer, original)
        val decoded = json.decodeFromString(AdaptiveColorSerializer, encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun testRoundTrip_preset() {
        val original = AdaptiveColor.MAROON
        val encoded = json.encodeToString(AdaptiveColorSerializer, original)
        val decoded = json.decodeFromString(AdaptiveColorSerializer, encoded)
        assertEquals(original, decoded)
    }
}
