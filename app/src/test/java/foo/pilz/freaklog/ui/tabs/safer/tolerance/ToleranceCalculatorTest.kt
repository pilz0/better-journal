package foo.pilz.freaklog.ui.tabs.safer.tolerance

import org.junit.Test
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import java.time.Instant
import java.time.temporal.ChronoUnit

class ToleranceCalculatorTest {

    private val parsed = ParsedTolerance(fullDays = 0f, halfLifeDays = 7f, zeroDays = 14f)

    @Test
    fun testNoIngestions() {
        assertEquals(0f, ToleranceCalculator.computeToleranceLevel(emptyList(), parsed, Instant.now()))
    }

    @Test
    fun testRecentIngestion() {
        val now = Instant.now()
        val level = ToleranceCalculator.computeToleranceLevel(listOf(now.minus(1, ChronoUnit.HOURS)), parsed, now)
        assertTrue(level > 0.9f)
    }

    @Test
    fun testHalfLifeDecay() {
        val now = Instant.now()
        val level = ToleranceCalculator.computeToleranceLevel(listOf(now.minus(7, ChronoUnit.DAYS)), parsed, now)
        assertEquals(0.5f, level, 0.05f)
    }

    @Test
    fun testOldIngestion() {
        val now = Instant.now()
        val level = ToleranceCalculator.computeToleranceLevel(listOf(now.minus(21, ChronoUnit.DAYS)), parsed, now)
        assertTrue(level < 0.15f)
    }

    @Test
    fun testMultipleIngestionsCombine() {
        val now = Instant.now()
        val single = ToleranceCalculator.computeToleranceLevel(listOf(now.minus(5, ChronoUnit.DAYS)), parsed, now)
        val double = ToleranceCalculator.computeToleranceLevel(listOf(now.minus(5, ChronoUnit.DAYS), now.minus(3, ChronoUnit.DAYS)), parsed, now)
        assertTrue(double > single)
    }

    @Test
    fun testCapsAtOne() {
        val now = Instant.now()
        val level = ToleranceCalculator.computeToleranceLevel((1..20).map { now.minus(it.toLong(), ChronoUnit.HOURS) }, parsed, now)
        assertEquals(1f, level, 0.001f)
    }

    @Test
    fun testFutureIngestionDoesNotBlowUp() {
        val now = Instant.now()
        val future = now.plus(5, ChronoUnit.DAYS)
        val level = ToleranceCalculator.computeToleranceLevel(listOf(future), parsed, now)
        assertEquals(1f, level, 0.001f)
    }

    @Test
    fun testNonFiniteHalfLifeReturnsZero() {
        val now = Instant.now()
        val recent = listOf(now.minus(1, ChronoUnit.HOURS))
        val nanParsed = ParsedTolerance(fullDays = 0f, halfLifeDays = Float.NaN, zeroDays = 14f)
        val infParsed = ParsedTolerance(fullDays = 0f, halfLifeDays = Float.POSITIVE_INFINITY, zeroDays = 14f)
        val zeroParsed = ParsedTolerance(fullDays = 0f, halfLifeDays = 0f, zeroDays = 14f)
        val negParsed = ParsedTolerance(fullDays = 0f, halfLifeDays = -5f, zeroDays = 14f)

        assertEquals(0f, ToleranceCalculator.computeToleranceLevel(recent, nanParsed, now))
        assertEquals(0f, ToleranceCalculator.computeToleranceLevel(recent, infParsed, now))
        assertEquals(0f, ToleranceCalculator.computeToleranceLevel(recent, zeroParsed, now))
        assertEquals(0f, ToleranceCalculator.computeToleranceLevel(recent, negParsed, now))
    }
}
