package com.ndm4.freakquery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitsTest {

    @Test
    fun toMgOrNull_convertsMassUnits() {
        assertEquals(0.1, Units.toMgOrNull(100, "ug")!!, 1e-12)
        assertEquals(0.1, Units.toMgOrNull("100", "mcg")!!, 1e-12)
        assertEquals(25.0, Units.toMgOrNull(25.0, " MG ")!!, 1e-12)
        assertEquals(1500.0, Units.toMgOrNull("1.5", "g")!!, 1e-12)
        assertEquals(2_000_000.0, Units.toMgOrNull(2, "kg")!!, 1e-6)
    }

    @Test
    fun toMgOrNull_acceptsMicroSignAndGreekMu() {
        assertEquals(0.25, Units.toMgOrNull(250, "µg")!!, 1e-12)
        assertEquals(0.25, Units.toMgOrNull(250, "μg")!!, 1e-12)
        assertEquals(0.25, Units.toMgOrNull(250, "micrograms")!!, 1e-12)
        assertEquals(10.0, Units.toMgOrNull(10, "milligrams")!!, 1e-12)
        assertEquals(1500.0, Units.toMgOrNull(1.5, "grams")!!, 1e-12)
        assertEquals(2_000_000.0, Units.toMgOrNull(2, "kilograms")!!, 1e-6)
        assertTrue(Units.isMassUnit("µg"))
        assertTrue(Units.isMassUnit("μg"))
        assertTrue(Units.isMassUnit("micrograms"))
        assertTrue(Units.isMassUnit("milligrams"))
        assertEquals("µg", Units.cleanUnit("μg"))
        assertEquals("µg", Units.cleanUnit("micrograms"))
    }

    @Test
    fun toMgOrNull_rejectsNonMassUnitsAndInvalidValues() {
        assertNull(Units.toMgOrNull(5, "ml"))
        assertNull(Units.toMgOrNull(2, "drops"))
        assertNull(Units.toMgOrNull(2, null))
        assertNull(Units.toMgOrNull(null, "mg"))
        assertNull(Units.toMgOrNull("abc", "mg"))
        assertNull(Units.toMgOrNull(-1, "mg"))
        assertNull(Units.toMgOrNull(Double.NaN, "mg"))
        assertNull(Units.toMgOrNull(Double.POSITIVE_INFINITY, "mg"))
        assertNull(Units.toMgOrNull(Double.MAX_VALUE, "kg"))
        assertFalse(Units.isMassUnit("ml"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun toMg_throwsForNonMassUnits() {
        Units.toMg(5, "ml")
    }

    @Test
    fun sumDose_skipsNonMassRows() {
        val rows = listOf(
            log("A", 10, "mg"),
            log("A", 500, "μg"),
            log("B", 5, "ml"),
            log("B", 2, "drops"),
        )
        assertEquals("10.5", FreakQuery.query("sum_dose", rows))
    }

    @Test
    fun groupSum_skipsNonMassRows() {
        val rows = listOf(log("A", 10, "mg"), log("A", 5, "ml"))
        assertEquals("10", FreakQuery.query("group_sum", rows))
    }

    @Test
    fun substanceTotals_ignoresDosesWithoutMass() {
        val rows = listOf(log("A", 10, "mg"), log("A", 5, "ml"), log("B", 3, "drops"), log("C", 1, "g"))
        val result = FreakQuery.query("substance_totals", rows)
        assertFalse(result, result.startsWith("[error"))
        assertTrue(result, "C" in result && "1000" in result)
        assertTrue(result, "A" in result && "10" in result)
        assertFalse(result, "B" in result)
    }

    @Test
    fun cleanUnit_handlesSingularAndCommonVariants() {
        assertEquals("µg", Units.cleanUnit("microgram"))
        assertEquals("µg", Units.cleanUnit("micro gram"))
        assertEquals("mg", Units.cleanUnit("milligram"))
        assertEquals("mg", Units.cleanUnit("miligram"))
        assertEquals("g", Units.cleanUnit("gram"))
        assertEquals("kg", Units.cleanUnit("kilogram"))
        assertEquals("", Units.cleanUnit(null))
        assertEquals("", Units.cleanUnit("   "))
    }

    @Test
    fun toMgOrNull_handlesZeroDose() {
        assertEquals(0.0, Units.toMgOrNull(0, "mg")!!, 1e-12)
        assertEquals(0.0, Units.toMgOrNull("0.0", "g")!!, 1e-12)
        assertEquals(0.0, Units.toMgOrNull(0.0, "µg")!!, 1e-12)
    }

    @Test
    fun toMg_convertsValidMassSuccessfully() {
        assertEquals(50.0, Units.toMg(50, "mg"), 1e-12)
        assertEquals(0.5, Units.toMg(500, "ug"), 1e-12)
        assertEquals(2000.0, Units.toMg(2, "g"), 1e-12)
    }

    private fun log(substance: String, dose: Any, unit: String): Map<String, Any?> =
        mapOf("substance" to substance, "dose" to dose, "unit" to unit, "time" to 1_700_000_000_000L)
}
