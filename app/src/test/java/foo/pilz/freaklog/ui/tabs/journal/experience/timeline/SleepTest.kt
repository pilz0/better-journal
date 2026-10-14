package foo.pilz.freaklog.ui.tabs.journal.experience.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SleepTest {

    @Test
    fun `empty stages has null start and end without throwing`() {
        val session = SleepSessionSample(emptyList())
        assertNull(session.start)
        assertNull(session.end)
    }

    @Test
    fun `zero or negative widthInSeconds returns empty shapes`() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val session = SleepSessionSample(
            listOf(
                SleepStageSample(
                    start = start,
                    end = start.plusSeconds(3600),
                    stage = SleepStageType.Sleeping
                )
            )
        )
        val shapesZero = SleepHealthConnect.buildGraphShapes(listOf(session), start, 0f)
        assertTrue(shapesZero.isEmpty())

        val shapesNegative = SleepHealthConnect.buildGraphShapes(listOf(session), start, -10f)
        assertTrue(shapesNegative.isEmpty())
    }

    @Test
    fun `session with stages builds properly clamped shapes`() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val session = SleepSessionSample(
            listOf(
                SleepStageSample(
                    start = start,
                    end = start.plusSeconds(3600),
                    stage = SleepStageType.Sleeping
                )
            )
        )
        val shapes = SleepHealthConnect.buildGraphShapes(
            sessions = listOf(session),
            startTime = start,
            widthInSeconds = 7200f,
        )
        assertEquals(1, shapes.size)
        assertEquals(0f, shapes[0].x1, 0.001f)
        assertEquals(0.5f, shapes[0].x2, 0.001f)
        assertTrue(shapes[0].roundLeft)
        assertTrue(shapes[0].roundRight)
    }
}
