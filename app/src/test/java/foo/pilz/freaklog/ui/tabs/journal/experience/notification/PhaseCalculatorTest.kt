package foo.pilz.freaklog.ui.tabs.journal.experience.notification

import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.DurationUnits
import foo.pilz.freaklog.data.substances.classes.roa.RoaDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PhaseCalculatorTest {

    private val duration = RoaDuration(
        onset = DurationRange(2f, 4f, DurationUnits.MINUTES),
        comeup = DurationRange(4f, 6f, DurationUnits.MINUTES),
        peak = DurationRange(1f, 1f, DurationUnits.HOURS),
        offset = DurationRange(1f, 1f, DurationUnits.HOURS),
        total = null,
        afterglow = null,
    )

    @Test
    fun remainingIsTimeUntilEffectEndNotNextPhase() {
        val result = PhaseCalculator.currentPhase(
            ingestionTime = Instant.now().minusSeconds(60),
            roaDuration = duration,
        )
        assertEquals(Phase.ONSET, result.phase)
        assertEquals(127f * 60f, result.remainingSeconds!!, 5f)
    }

    @Test
    fun finishedHasNoRemaining() {
        val result = PhaseCalculator.currentPhase(
            ingestionTime = Instant.now().minusSeconds(129 * 60),
            roaDuration = duration,
        )
        assertEquals(Phase.FINISHED, result.phase)
        assertNull(result.remainingSeconds)
    }

    @Test
    fun rangedIngestionFinishesAfterEndTimePlusDuration() {
        val start = Instant.now().minusSeconds(189 * 60)
        val finished = PhaseCalculator.currentPhase(
            ingestionTime = start,
            roaDuration = duration,
            ingestionEndTime = start.plusSeconds(60 * 60),
        )
        assertEquals(Phase.FINISHED, finished.phase)

        val stillActive = PhaseCalculator.currentPhase(
            ingestionTime = start,
            roaDuration = duration,
            ingestionEndTime = start.plusSeconds(120 * 60),
        )
        assertEquals(Phase.OFFSET, stillActive.phase)
    }

    @Test
    fun unknownDurationFinishesAfterFallback() {
        val finished = PhaseCalculator.currentPhase(
            ingestionTime = Instant.now().minusSeconds(13 * 3600),
            roaDuration = null,
        )
        assertEquals(Phase.FINISHED, finished.phase)

        val stillActive = PhaseCalculator.currentPhase(
            ingestionTime = Instant.now().minusSeconds(11 * 3600),
            roaDuration = null,
        )
        assertEquals(Phase.UNKNOWN, stillActive.phase)
    }

    @Test
    fun futureIngestionHasNoNegativeElapsedAndCountsWaitInRemaining() {
        val result = PhaseCalculator.currentPhase(
            ingestionTime = Instant.now().plusSeconds(2 * 3600),
            roaDuration = duration,
        )
        assertEquals(Phase.ONSET, result.phase)
        assertEquals(0f, result.elapsedSeconds, 0f)
        assertEquals((120f + 128f) * 60f, result.remainingSeconds!!, 5f)
        assertEquals(1800L, result.nextUpdateIntervalSeconds)
    }

    @Test
    fun futureRangedIngestionCountsWaitAndRangeInRemaining() {
        val start = Instant.now().plusSeconds(10 * 60)
        val result = PhaseCalculator.currentPhase(
            ingestionTime = start,
            roaDuration = duration,
            ingestionEndTime = start.plusSeconds(60 * 60),
        )
        assertEquals(Phase.ONSET, result.phase)
        assertEquals(0f, result.elapsedSeconds, 0f)
        assertEquals((10f + 60f + 128f) * 60f, result.remainingSeconds!!, 5f)
    }

    @Test
    fun futureIngestionRefreshesWhenItStarts() {
        val result = PhaseCalculator.currentPhase(
            ingestionTime = Instant.now().plusSeconds(5 * 60),
            roaDuration = duration,
        )
        // A third of the 300 s until start, not of the 480 s until onset ends.
        assertTrue(result.nextUpdateIntervalSeconds in 99L..100L)
    }

    @Test
    fun futureIngestionWithUnknownDurationIsStillActive() {
        val result = PhaseCalculator.currentPhase(
            ingestionTime = Instant.now().plusSeconds(3600),
            roaDuration = null,
        )
        assertEquals(Phase.UNKNOWN, result.phase)
        assertEquals(0f, result.elapsedSeconds, 0f)
    }

    @Test
    fun smartIntervalFallsBackToMaxForNonFiniteTransitions() {
        assertEquals(1800L, PhaseCalculator.smartInterval(Float.NaN))
        assertEquals(1800L, PhaseCalculator.smartInterval(Float.POSITIVE_INFINITY))
        assertEquals(1800L, PhaseCalculator.smartInterval(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun smartIntervalClampsToBounds() {
        assertEquals(60L, PhaseCalculator.smartInterval(-600f))
        assertEquals(60L, PhaseCalculator.smartInterval(0f))
        assertEquals(100L, PhaseCalculator.smartInterval(300f))
        assertEquals(1800L, PhaseCalculator.smartInterval(Float.MAX_VALUE))
    }
}
