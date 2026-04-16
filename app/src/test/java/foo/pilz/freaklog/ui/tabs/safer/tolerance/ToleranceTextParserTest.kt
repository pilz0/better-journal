package foo.pilz.freaklog.ui.tabs.safer.tolerance

import foo.pilz.freaklog.data.substances.classes.Tolerance
import org.junit.Test
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull

class ToleranceTextParserTest {

    @Test
    fun testParseDurationText() {
        assertEquals(14f, ToleranceTextParser.parseDurationText("14 days"))
        assertEquals(6f, ToleranceTextParser.parseDurationText("5-7 days"))
        assertEquals(6f, ToleranceTextParser.parseDurationText("5 - 7 days"))
        assertEquals(7f, ToleranceTextParser.parseDurationText("1 week"))
        assertEquals(10.5f, ToleranceTextParser.parseDurationText("1 - 2 weeks"))
        assertEquals(1f, ToleranceTextParser.parseDurationText("24 hours"))
        assertEquals(45f, ToleranceTextParser.parseDurationText("1 - 2 months"))
        assertEquals(0f, ToleranceTextParser.parseDurationText("almost immediately after ingestion"))
        assertEquals(14f, ToleranceTextParser.parseDurationText("develops with prolonged and repeated use"))
        assertEquals(21f, ToleranceTextParser.parseDurationText("within several weeks of continuous use"))
        assertNull(ToleranceTextParser.parseDurationText(null))
        assertNull(ToleranceTextParser.parseDurationText("something completely unknown xyz"))
    }

    @Test
    fun testParseFullTolerance() {
        val parsed = ToleranceTextParser.parse(Tolerance(full = "almost immediately after ingestion", half = "5-7 days", zero = "14 days"))
        assertNotNull(parsed)
        assertEquals(0f, parsed!!.fullDays)
        assertEquals(6f, parsed.halfLifeDays)
        assertEquals(14f, parsed.zeroDays)
    }

    @Test
    fun testEstimatesMissingFields() {
        val missingHalf = ToleranceTextParser.parse(Tolerance(full = null, half = null, zero = "14 days"))
        assertNotNull(missingHalf)
        assertEquals(7f, missingHalf!!.halfLifeDays)

        val missingZero = ToleranceTextParser.parse(Tolerance(full = null, half = "5 days", zero = null))
        assertNotNull(missingZero)
        assertEquals(12.5f, missingZero!!.zeroDays)

        assertNull(ToleranceTextParser.parse(Tolerance(full = null, half = null, zero = null)))
    }
}
