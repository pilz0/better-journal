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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveColorTest {

    // ===== isPreferred Tests for Primary Colors =====

    @Test
    fun testRed_isPreferred() {
        assertTrue(AdaptiveColor.RED.isPreferred)
    }

    @Test
    fun testOrange_isPreferred() {
        assertTrue(AdaptiveColor.ORANGE.isPreferred)
    }

    @Test
    fun testYellow_isPreferred() {
        assertTrue(AdaptiveColor.YELLOW.isPreferred)
    }

    @Test
    fun testGreen_isPreferred() {
        assertTrue(AdaptiveColor.GREEN.isPreferred)
    }

    @Test
    fun testMint_isPreferred() {
        assertTrue(AdaptiveColor.MINT.isPreferred)
    }

    @Test
    fun testTeal_isPreferred() {
        assertTrue(AdaptiveColor.TEAL.isPreferred)
    }

    @Test
    fun testCyan_isPreferred() {
        assertTrue(AdaptiveColor.CYAN.isPreferred)
    }

    @Test
    fun testBlue_isPreferred() {
        assertTrue(AdaptiveColor.BLUE.isPreferred)
    }

    @Test
    fun testIndigo_isPreferred() {
        assertTrue(AdaptiveColor.INDIGO.isPreferred)
    }

    @Test
    fun testPurple_isPreferred() {
        assertTrue(AdaptiveColor.PURPLE.isPreferred)
    }

    @Test
    fun testPink_isPreferred() {
        assertTrue(AdaptiveColor.PINK.isPreferred)
    }

    @Test
    fun testBrown_isPreferred() {
        assertTrue(AdaptiveColor.BROWN.isPreferred)
    }

    // ===== isPreferred Tests for Non-Preferred Colors =====

    @Test
    fun testFireEngineRed_isNotPreferred() {
        assertFalse(AdaptiveColor.FIRE_ENGINE_RED.isPreferred)
    }

    @Test
    fun testCoral_isNotPreferred() {
        assertFalse(AdaptiveColor.CORAL.isPreferred)
    }

    @Test
    fun testMagenta_isNotPreferred() {
        assertFalse(AdaptiveColor.MAGENTA.isPreferred)
    }

    @Test
    fun testGold_isNotPreferred() {
        assertFalse(AdaptiveColor.GOLD.isPreferred)
    }

    // ===== getComposeColor Tests =====

    @Test
    fun testGetComposeColor_darkTheme_returnsColor() {
        val color = AdaptiveColor.RED.getComposeColor(isDarkTheme = true)
        assertNotNull(color)
    }

    @Test
    fun testGetComposeColor_lightTheme_returnsColor() {
        val color = AdaptiveColor.RED.getComposeColor(isDarkTheme = false)
        assertNotNull(color)
    }

    @Test
    fun testGetComposeColor_differentForThemes() {
        val darkColor = AdaptiveColor.BLUE.getComposeColor(isDarkTheme = true)
        val lightColor = AdaptiveColor.BLUE.getComposeColor(isDarkTheme = false)
        assertNotEquals(darkColor, lightColor)
    }

    @Test
    fun testAllColors_haveDarkThemeColor() {
        AdaptiveColor.entries.forEach { color ->
            assertNotNull("$color should have dark theme color",
                color.getComposeColor(isDarkTheme = true))
        }
    }

    @Test
    fun testAllColors_haveLightThemeColor() {
        AdaptiveColor.entries.forEach { color ->
            assertNotNull("$color should have light theme color",
                color.getComposeColor(isDarkTheme = false))
        }
    }

    // ===== Count Tests =====

    @Test
    fun testPreferredColorsCount() {
        val preferredCount = AdaptiveColor.entries.count { it.isPreferred }
        assertEquals(12, preferredCount)
    }

    @Test
    fun testNonPreferredColorsCount() {
        val nonPreferredCount = AdaptiveColor.entries.count { !it.isPreferred }
        assertEquals(AdaptiveColor.entries.size - 12, nonPreferredCount)
    }

    @Test
    fun testTotalColorsCount() {
        // Make sure we have a reasonable number of colors
        assertTrue(AdaptiveColor.entries.size >= 12)
    }

    // ===== Specific Color Value Tests =====

    @Test
    fun testRed_darkTheme_values() {
        val color = AdaptiveColor.RED.getComposeColor(isDarkTheme = true)
        // Dark theme red should have RGB(255, 69, 58)
        assertNotNull(color)
    }

    @Test
    fun testRed_lightTheme_values() {
        val color = AdaptiveColor.RED.getComposeColor(isDarkTheme = false)
        // Light theme red should have RGB(255, 59, 48)
        assertNotNull(color)
    }

    @Test
    fun testGreen_darkTheme_values() {
        val color = AdaptiveColor.GREEN.getComposeColor(isDarkTheme = true)
        // Dark theme green should have RGB(48, 209, 88)
        assertNotNull(color)
    }

    @Test
    fun testBlue_darkTheme_values() {
        val color = AdaptiveColor.BLUE.getComposeColor(isDarkTheme = true)
        // Dark theme blue should have RGB(10, 132, 255)
        assertNotNull(color)
    }

    // ===== Custom color tests =====

    @Test
    fun testCustomColor_isNotPreferred() {
        val custom = AdaptiveColor.Custom(0xFF00AAFF.toInt())
        assertFalse(custom.isPreferred)
    }

    @Test
    fun testCustomColor_nameRoundTripsThroughValueOf() {
        val argb = 0xFF22AAFF.toInt()
        val custom = AdaptiveColor.Custom(argb)
        val parsed = AdaptiveColor.valueOf(custom.name)
        assertEquals(custom, parsed)
    }

    @Test
    fun testCustomColor_namePreservesAllChannels() {
        val argb = 0x80123456.toInt() // includes a non-opaque alpha
        val custom = AdaptiveColor.Custom(argb)
        assertEquals("CUSTOM_80123456", custom.name)
        val parsed = AdaptiveColor.valueOf(custom.name) as AdaptiveColor.Custom
        assertEquals(argb, parsed.argb)
    }

    @Test
    fun testValueOf_presetByName() {
        assertEquals(AdaptiveColor.RED, AdaptiveColor.valueOf("RED"))
        assertEquals(AdaptiveColor.MAROON, AdaptiveColor.valueOf("MAROON"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testValueOf_unknownNameThrows() {
        AdaptiveColor.valueOf("NOT_A_REAL_COLOR")
    }

    @Test
    fun testEntries_doesNotContainCustomVariants() {
        // entries should expose only the preset singletons, never Custom values.
        AdaptiveColor.entries.forEach { color ->
            assertFalse("entries should not contain Custom: $color",
                color is AdaptiveColor.Custom)
        }
    }
}
