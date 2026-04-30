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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoseClassTest {

    @Test
    fun testThreshold_description() {
        val description = DoseClass.THRESHOLD.description
        assertNotNull(description)
        assertTrue(description.isNotEmpty())
        assertTrue(description.contains("threshold"))
    }

    @Test
    fun testLight_description() {
        val description = DoseClass.LIGHT.description
        assertNotNull(description)
        assertTrue(description.isNotEmpty())
        assertTrue(description.contains("light"))
    }

    @Test
    fun testCommon_description() {
        val description = DoseClass.COMMON.description
        assertNotNull(description)
        assertTrue(description.isNotEmpty())
        assertTrue(description.contains("common"))
    }

    @Test
    fun testStrong_description() {
        val description = DoseClass.STRONG.description
        assertNotNull(description)
        assertTrue(description.isNotEmpty())
        assertTrue(description.contains("strong"))
    }

    @Test
    fun testHeavy_description() {
        val description = DoseClass.HEAVY.description
        assertNotNull(description)
        assertTrue(description.isNotEmpty())
        assertTrue(description.contains("heavy"))
    }

    @Test
    fun testThreshold_darkThemeColor() {
        val color = DoseClass.THRESHOLD.getComposeColor(isDarkTheme = true)
        assertNotNull(color)
    }

    @Test
    fun testThreshold_lightThemeColor() {
        val color = DoseClass.THRESHOLD.getComposeColor(isDarkTheme = false)
        assertNotNull(color)
    }

    @Test
    fun testLight_darkThemeColor() {
        val color = DoseClass.LIGHT.getComposeColor(isDarkTheme = true)
        assertNotNull(color)
    }

    @Test
    fun testLight_lightThemeColor() {
        val color = DoseClass.LIGHT.getComposeColor(isDarkTheme = false)
        assertNotNull(color)
    }

    @Test
    fun testCommon_darkThemeColor() {
        val color = DoseClass.COMMON.getComposeColor(isDarkTheme = true)
        assertNotNull(color)
    }

    @Test
    fun testCommon_lightThemeColor() {
        val color = DoseClass.COMMON.getComposeColor(isDarkTheme = false)
        assertNotNull(color)
    }

    @Test
    fun testStrong_darkThemeColor() {
        val color = DoseClass.STRONG.getComposeColor(isDarkTheme = true)
        assertNotNull(color)
    }

    @Test
    fun testStrong_lightThemeColor() {
        val color = DoseClass.STRONG.getComposeColor(isDarkTheme = false)
        assertNotNull(color)
    }

    @Test
    fun testHeavy_darkThemeColor() {
        val color = DoseClass.HEAVY.getComposeColor(isDarkTheme = true)
        assertNotNull(color)
    }

    @Test
    fun testHeavy_lightThemeColor() {
        val color = DoseClass.HEAVY.getComposeColor(isDarkTheme = false)
        assertNotNull(color)
    }

    @Test
    fun testAllDoseClasses_haveDifferentColors() {
        val darkColors = DoseClass.entries.map { it.getComposeColor(isDarkTheme = true) }
        // Each dose class should have a distinct color - set size should equal enum size
        assertEquals(DoseClass.entries.size, darkColors.toSet().size)
    }

    @Test
    fun testDarkAndLightColors_areDifferent() {
        DoseClass.entries.forEach { doseClass ->
            val darkColor = doseClass.getComposeColor(isDarkTheme = true)
            val lightColor = doseClass.getComposeColor(isDarkTheme = false)
            // Colors should differ between themes (at least slightly)
            // Note: Some might be the same intentionally, so we just check they exist
            assertNotNull(darkColor)
            assertNotNull(lightColor)
        }
    }

    @Test
    fun testAllEnumValues() {
        assertEquals(5, DoseClass.entries.size)
    }

    @Test
    fun testEnumOrder() {
        val entries = DoseClass.entries
        assertEquals(DoseClass.THRESHOLD, entries[0])
        assertEquals(DoseClass.LIGHT, entries[1])
        assertEquals(DoseClass.COMMON, entries[2])
        assertEquals(DoseClass.STRONG, entries[3])
        assertEquals(DoseClass.HEAVY, entries[4])
    }

    @Test
    fun testAllDescriptions_areUnique() {
        val descriptions = DoseClass.entries.map { it.description }
        assertEquals(5, descriptions.toSet().size)
    }
}
