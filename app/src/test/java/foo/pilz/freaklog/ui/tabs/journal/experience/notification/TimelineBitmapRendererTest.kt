package foo.pilz.freaklog.ui.tabs.journal.experience.notification

import android.graphics.Color
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.ui.tabs.journal.experience.components.DataForOneEffectLine
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.AllTimelinesModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
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

    @Test
    fun rendersWithCurrentTimeAtExactBoundaries() {
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

        // At exact start time
        val atStart = TimelineBitmapRenderer.render(
            model = model,
            widthPx = 200,
            heightPx = 100,
            currentTime = start,
            density = 1f,
            isDarkTheme = false,
        )
        assertEquals(200, atStart.width)

        // At exact end time
        val endTime = model.startTime.plusSeconds(model.widthInSeconds.toLong())
        val atEnd = TimelineBitmapRenderer.render(
            model = model,
            widthPx = 200,
            heightPx = 100,
            currentTime = endTime,
            density = 1f,
            isDarkTheme = false,
        )
        assertEquals(200, atEnd.width)

        // Outside boundary (before start and after end)
        val beforeStart = TimelineBitmapRenderer.render(
            model = model,
            widthPx = 200,
            heightPx = 100,
            currentTime = start.minusSeconds(10),
            density = 1f,
            isDarkTheme = false,
        )
        assertEquals(200, beforeStart.width)

        val afterEnd = TimelineBitmapRenderer.render(
            model = model,
            widthPx = 200,
            heightPx = 100,
            currentTime = endTime.plusSeconds(10),
            density = 1f,
            isDarkTheme = false,
        )
        assertEquals(200, afterEnd.width)
    }

    @Test
    fun returnsMinimalBitmapForNonPositiveDimensions() {
        val start = Instant.parse("2026-01-01T12:00:00Z")
        val model = caffeineModel(start)
        val sizes = listOf(0 to 100, 100 to 0, 0 to 0, -1 to 100, 100 to -5, -3 to -3)

        sizes.forEach { (widthPx, heightPx) ->
            val bitmap = TimelineBitmapRenderer.render(
                model = model,
                widthPx = widthPx,
                heightPx = heightPx,
                currentTime = start.plusSeconds(600),
                density = 1f,
                isDarkTheme = false,
            )
            assertEquals("width for $widthPx x $heightPx", 1, bitmap.width)
            assertEquals("height for $widthPx x $heightPx", 1, bitmap.height)
        }
    }

    // Robolectric's legacy graphics mode doesn't rasterize, so pixel checks need native graphics.
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun rendersVisibleCurveOntoBitmap() {
        val start = Instant.parse("2026-01-01T12:00:00Z")

        val bitmap = TimelineBitmapRenderer.render(
            model = caffeineModel(start),
            widthPx = 300,
            heightPx = 100,
            currentTime = start.plusSeconds(600),
            density = 1f,
            isDarkTheme = true,
        )

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue("expected some painted pixels", pixels.any { Color.alpha(it) != 0 })
    }

    @Test
    fun rendersEmptyModel() {
        val model = AllTimelinesModel(
            dataForLines = emptyList(),
            dataForRatings = emptyList(),
            timedNotes = emptyList(),
            areSubstanceHeightsIndependent = false,
        )

        val bitmap = TimelineBitmapRenderer.render(
            model = model,
            widthPx = 50,
            heightPx = 20,
            currentTime = Instant.parse("2026-01-01T12:00:00Z"),
            density = 3f,
            isDarkTheme = false,
        )

        assertEquals(50, bitmap.width)
        assertEquals(20, bitmap.height)
    }

    private fun caffeineModel(start: Instant) = AllTimelinesModel(
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
}
