package foo.pilz.freaklog.ui.tabs.settings.funny

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RedoseSearchTest {
    @Test(timeout = 5000)
    fun identicalTimestampsDoNotCausePairwiseScanning() {
        val time = Instant.parse("2026-01-01T00:00:00Z")
        assertFalse(hasPairWithinPeak(List(100_000) { time }, 3600))
        assertTrue(hasPairWithinPeak(List(100_000) { time } + time.plusSeconds(1), 3600))
    }

    @Test
    fun searchPreservesWholeSecondBoundarySemantics() {
        val time = Instant.parse("2026-01-01T00:00:00Z")
        assertFalse(hasPairWithinPeak(listOf(time, time.plusNanos(999_999_999)), 1))
        assertTrue(hasPairWithinPeak(listOf(time, time.plusNanos(1_999_999_999)), 1))
        assertFalse(hasPairWithinPeak(listOf(time, time.plusSeconds(2)), 1))
    }
}
