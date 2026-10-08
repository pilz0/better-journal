package foo.pilz.freaklog.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VolumetricInputTest {

    private fun assertVolume(expectedMl: Double, input: String) {
        assertEquals(expectedMl, parseVolumeToMl(input)!!, 1e-9)
    }

    private fun assertConcentration(expectedMgPerMl: Double, input: String) {
        assertEquals(expectedMgPerMl, parseConcentrationToMgPerMl(input)!!, 1e-12)
    }

    @Test
    fun volume_parsesPlainNumbersAsMl() {
        assertVolume(500.0, "500")
        assertVolume(0.4, "0.4")
    }

    @Test
    fun volume_parsesMicroliters() {
        assertVolume(0.1, "100µl")
        assertVolume(0.1, "100ul")
        assertVolume(0.1, "100mcl")
        assertVolume(0.25, "250µl")
        assertVolume(0.001, "1ul")
    }

    @Test
    fun volume_parsesUnitsAndExpressions() {
        assertVolume(1500.0, "1.5l")
        assertVolume(100.0, "1dl")
        assertVolume(100.0, "2*50ml")
        assertNull(parseVolumeToMl("5x"))
    }

    @Test
    fun concentration_parsesPlainNumbersAsMgPerMl() {
        assertConcentration(80.0, "80")
        assertConcentration(80.0, "80mg/ml")
    }

    @Test
    fun concentration_parsesRatios() {
        assertConcentration(1.0, "1g/1l")
        assertConcentration(0.5, "1g/2l")
        assertConcentration(0.000067, "67µg/l")
        assertConcentration(0.000067, "67mcg/l")
        assertConcentration(10.0, "1mg/100µl")
    }

    @Test
    fun concentration_rejectsInvalid() {
        assertNull(parseConcentrationToMgPerMl("1g/0l"))
        assertNull(parseConcentrationToMgPerMl("abc"))
    }

    @Test
    fun format_roundTripsNicely() {
        assertEquals("1.5l", formatVolume(1500.0))
        assertEquals("500ml", formatVolume(500.0))
        assertEquals("1ml", formatVolume(1.0))
        assertEquals("500µl", formatVolume(0.5))
        assertEquals("100µl", formatVolume(0.1))
        assertEquals("1µl", formatVolume(0.001))
        assertEquals("80mg/ml", formatConcentration(80.0))
        assertEquals("1g/ml", formatConcentration(1000.0))
        assertEquals("67µg/l", formatConcentration(0.000067))
        assertEquals("500µg/ml", formatConcentration(0.5))
    }
}
