package foo.pilz.freaklog.ui.tabs.journal.experience.timeline

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AllTimelinesModelTest {

    @Test
    fun `fixedTimeRange with identical start and end has width of at least one second`() {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val model = AllTimelinesModel(
            dataForLines = emptyList(),
            dataForRatings = emptyList(),
            timedNotes = emptyList(),
            areSubstanceHeightsIndependent = false,
            fixedTimeRange = now..now,
        )

        assertTrue(model.widthInSeconds >= 1f)
    }
}
