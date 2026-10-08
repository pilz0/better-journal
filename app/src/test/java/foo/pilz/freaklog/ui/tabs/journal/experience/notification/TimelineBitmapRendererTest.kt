package foo.pilz.freaklog.ui.tabs.journal.experience.notification

import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.ui.tabs.journal.experience.components.DataForOneEffectLine
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.AllTimelinesModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TimelineBitmapRendererTest {

    /**
     * The notification is built from a service, where no view is attached to a window. Rendering
     * through a ComposeView crashed there, so this has to work with nothing but a model.
     */
    @Test
    fun rendersWithoutAnyViewOrWindow() {
        val start = Instant.parse("2026-01-01T12:00:00Z")
        val model = AllTimelinesModel(
            dataForLines = listOf(
                DataForOneEffectLine(
                    substanceName = "Caffeine",
                    route = AdministrationRoute.ORAL,
                    roaDuration = null,
                    height = 1f,
                    horizontalWeight = 0.5f,
                    color = AdaptiveColor.BLUE,
                    startTime = start,
                    endTime = null,
                )
            ),
            dataForRatings = emptyList(),
            timedNotes = emptyList(),
            areSubstanceHeightsIndependent = false,
        )

        val bitmap = TimelineBitmapRenderer.render(
            model = model,
            widthPx = 400,
            heightPx = 120,
            currentTime = start.plusSeconds(600),
            density = 2f,
            isDarkTheme = true,
        )

        assertEquals(400, bitmap.width)
        assertEquals(120, bitmap.height)
    }
}
