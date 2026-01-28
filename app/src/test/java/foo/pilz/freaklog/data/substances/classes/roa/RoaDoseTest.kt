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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoaDoseTest {

    // Standard dose ranges for testing (e.g., LSD-like substance)
    private val standardRoaDose = RoaDose(
        units = "µg",
        lightMin = 25.0,
        commonMin = 75.0,
        strongMin = 150.0,
        heavyMin = 300.0
    )

    // Dose with mg units (e.g., MDMA-like)
    private val mgRoaDose = RoaDose(
        units = "mg",
        lightMin = 40.0,
        commonMin = 75.0,
        strongMin = 125.0,
        heavyMin = 175.0
    )

    // Dose with small mg values that should use volumetric dosing
    private val smallMgRoaDose = RoaDose(
        units = "mg",
        lightMin = 2.0,
        commonMin = 5.0,
        strongMin = 10.0,
        heavyMin = 15.0
    )

    // Dose with partial data (some nulls)
    private val partialRoaDose = RoaDose(
        units = "mg",
        lightMin = 50.0,
        commonMin = null,
        strongMin = 100.0,
        heavyMin = null
    )

    // Dose with all nulls
    private val emptyRoaDose = RoaDose(
        units = "mg",
        lightMin = null,
        commonMin = null,
        strongMin = null,
        heavyMin = null
    )

    // ===== getDoseClass Tests =====

    @Test
    fun testGetDoseClass_threshold() {
        val result = standardRoaDose.getDoseClass(20.0)
        assertEquals(DoseClass.THRESHOLD, result)
    }

    @Test
    fun testGetDoseClass_light() {
        val result = standardRoaDose.getDoseClass(50.0)
        assertEquals(DoseClass.LIGHT, result)
    }

    @Test
    fun testGetDoseClass_common() {
        val result = standardRoaDose.getDoseClass(100.0)
        assertEquals(DoseClass.COMMON, result)
    }

    @Test
    fun testGetDoseClass_strong() {
        val result = standardRoaDose.getDoseClass(200.0)
        assertEquals(DoseClass.STRONG, result)
    }

    @Test
    fun testGetDoseClass_heavy() {
        val result = standardRoaDose.getDoseClass(400.0)
        assertEquals(DoseClass.HEAVY, result)
    }

    @Test
    fun testGetDoseClass_exactlyAtLightMin() {
        val result = standardRoaDose.getDoseClass(25.0)
        assertEquals(DoseClass.LIGHT, result)
    }

    @Test
    fun testGetDoseClass_exactlyAtCommonMin() {
        val result = standardRoaDose.getDoseClass(75.0)
        assertEquals(DoseClass.COMMON, result)
    }

    @Test
    fun testGetDoseClass_exactlyAtStrongMin() {
        val result = standardRoaDose.getDoseClass(150.0)
        assertEquals(DoseClass.STRONG, result)
    }

    @Test
    fun testGetDoseClass_exactlyAtHeavyMin() {
        val result = standardRoaDose.getDoseClass(300.0)
        assertEquals(DoseClass.HEAVY, result)
    }

    @Test
    fun testGetDoseClass_nullDose() {
        val result = standardRoaDose.getDoseClass(null)
        assertNull(result)
    }

    @Test
    fun testGetDoseClass_wrongUnits() {
        val result = standardRoaDose.getDoseClass(100.0, "mg")
        assertNull(result)
    }

    @Test
    fun testGetDoseClass_correctUnits() {
        val result = standardRoaDose.getDoseClass(100.0, "µg")
        assertEquals(DoseClass.COMMON, result)
    }

    @Test
    fun testGetDoseClass_partialData_belowLight() {
        val result = partialRoaDose.getDoseClass(30.0)
        assertEquals(DoseClass.THRESHOLD, result)
    }

    @Test
    fun testGetDoseClass_allNull() {
        val result = emptyRoaDose.getDoseClass(100.0)
        assertNull(result)
    }

    // ===== getNumDots Tests =====

    @Test
    fun testGetNumDots_threshold() {
        val result = standardRoaDose.getNumDots(20.0)
        assertEquals(0, result)
    }

    @Test
    fun testGetNumDots_light() {
        val result = standardRoaDose.getNumDots(50.0)
        assertEquals(1, result)
    }

    @Test
    fun testGetNumDots_common() {
        val result = standardRoaDose.getNumDots(100.0)
        assertEquals(2, result)
    }

    @Test
    fun testGetNumDots_strong() {
        val result = standardRoaDose.getNumDots(200.0)
        assertEquals(3, result)
    }

    @Test
    fun testGetNumDots_heavy_oneX() {
        val result = standardRoaDose.getNumDots(300.0)
        assertEquals(4, result)
    }

    @Test
    fun testGetNumDots_heavy_twoX() {
        val result = standardRoaDose.getNumDots(600.0)
        assertEquals(8, result)
    }

    @Test
    fun testGetNumDots_heavy_withRemainder() {
        // 350 = 1x300 (4 dots) + 50 (light = 1 dot) = 5 dots
        val result = standardRoaDose.getNumDots(350.0)
        assertEquals(5, result)
    }

    @Test
    fun testGetNumDots_nullDose() {
        val result = standardRoaDose.getNumDots(null)
        assertNull(result)
    }

    @Test
    fun testGetNumDots_wrongUnits() {
        val result = standardRoaDose.getNumDots(100.0, "mg")
        assertNull(result)
    }

    @Test
    fun testGetNumDots_allNull() {
        val result = emptyRoaDose.getNumDots(100.0)
        assertNull(result)
    }

    // ===== shouldUseVolumetricDosing Tests =====

    @Test
    fun testShouldUseVolumetricDosing_micrograms() {
        assertTrue(standardRoaDose.shouldUseVolumetricDosing)
    }

    @Test
    fun testShouldUseVolumetricDosing_largeMg() {
        assertFalse(mgRoaDose.shouldUseVolumetricDosing)
    }

    @Test
    fun testShouldUseVolumetricDosing_smallMg() {
        assertTrue(smallMgRoaDose.shouldUseVolumetricDosing)
    }

    @Test
    fun testShouldUseVolumetricDosing_nullCommonAndStrong() {
        val dose = RoaDose(
            units = "mg",
            lightMin = 5.0,
            commonMin = null,
            strongMin = null,
            heavyMin = 20.0
        )
        assertFalse(dose.shouldUseVolumetricDosing)
    }

    // ===== averageCommonDose Tests =====

    @Test
    fun testAverageCommonDose_withBothValues() {
        val result = standardRoaDose.averageCommonDose
        assertEquals(112.5, result!!, 0.01) // (75 + 150) / 2
    }

    @Test
    fun testAverageCommonDose_withNullCommon() {
        val result = partialRoaDose.averageCommonDose
        assertNull(result)
    }

    @Test
    fun testAverageCommonDose_withNullStrong() {
        val dose = RoaDose(
            units = "mg",
            lightMin = 10.0,
            commonMin = 50.0,
            strongMin = null,
            heavyMin = 100.0
        )
        assertNull(dose.averageCommonDose)
    }

    // ===== getStrengthRelativeToCommonDose Tests =====

    @Test
    fun testStrengthRelativeToCommon_atCommon() {
        val result = standardRoaDose.getStrengthRelativeToCommonDose(112.5)
        assertEquals(1.0, result!!, 0.01)
    }

    @Test
    fun testStrengthRelativeToCommon_double() {
        val result = standardRoaDose.getStrengthRelativeToCommonDose(225.0)
        assertEquals(2.0, result!!, 0.01)
    }

    @Test
    fun testStrengthRelativeToCommon_half() {
        val result = standardRoaDose.getStrengthRelativeToCommonDose(56.25)
        assertEquals(0.5, result!!, 0.01)
    }

    @Test
    fun testStrengthRelativeToCommon_nullAverage() {
        val result = partialRoaDose.getStrengthRelativeToCommonDose(100.0)
        assertNull(result)
    }
}
