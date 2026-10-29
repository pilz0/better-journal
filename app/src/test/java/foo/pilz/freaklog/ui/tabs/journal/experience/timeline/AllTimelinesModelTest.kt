package foo.pilz.freaklog.ui.tabs.journal.experience.timeline

import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.DurationUnits
import foo.pilz.freaklog.data.substances.classes.roa.RoaDuration
import foo.pilz.freaklog.ui.tabs.journal.experience.components.DataForOneEffectLine
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

    @Test
    fun `custom substance ingestion with custom duration renders non-empty curve`() {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val customDuration = RoaDuration(
            onset = null,
            comeup = DurationRange(30f, 30f, DurationUnits.MINUTES),
            peak = DurationRange(2f, 2f, DurationUnits.HOURS),
            offset = DurationRange(1f, 1f, DurationUnits.HOURS),
            total = null,
            afterglow = null,
        )
        val effectLine = DataForOneEffectLine(
            substanceName = "2C-B-FLY",
            route = AdministrationRoute.ORAL,
            roaDuration = customDuration,
            height = 1f,
            horizontalWeight = 0.5f,
            color = AdaptiveColor.GREEN,
            startTime = now,
            endTime = null,
        )
        val model = AllTimelinesModel(
            dataForLines = listOf(effectLine),
            dataForRatings = emptyList(),
            timedNotes = emptyList(),
            areSubstanceHeightsIndependent = false,
        )

        val groups = model.timelineGroups(isDarkTheme = false)
        assertTrue(groups.isNotEmpty())
        val curve = groups.first().curve
        assertTrue("Custom substance curve should have segments", curve.segments.isNotEmpty())
        assertTrue("Segments should contain points", curve.segments.first().points.isNotEmpty())
        assertTrue("Points should have non-zero heights", curve.segments.first().points.any { it.height > 0f })
    }
}
