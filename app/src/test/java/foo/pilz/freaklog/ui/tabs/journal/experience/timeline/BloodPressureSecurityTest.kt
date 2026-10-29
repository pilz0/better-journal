package foo.pilz.freaklog.ui.tabs.journal.experience.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BloodPressureSecurityTest {
    @Test
    fun malformedPersistedPressuresCannotReachGraphScaling() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val invalid = listOf(Double.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY, Int.MAX_VALUE.toDouble(), -1.0)
        val readings = invalid.mapIndexed { index, value -> BloodPressureReading("$index", start, value, 80.0) }
        val graph = buildBloodPressurePolylines(readings, start, 3600f)
        assertTrue(graph.systolic.isEmpty())
        val valid = buildBloodPressurePolylines(readings + BloodPressureReading("ok", start, 120.0, 80.0), start, 3600f)
        assertEquals(1, valid.systolic.size)
    }
}
