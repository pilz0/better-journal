package foo.pilz.freaklog.ui.tabs.journal.experience.share

import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.testing.EntityBuilders
import foo.pilz.freaklog.ui.tabs.journal.experience.models.IngestionElement
import foo.pilz.freaklog.ui.utils.getShortTimeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ShareTextTest {

    private fun element(substance: String, dose: Double?, notes: String? = null) = IngestionElement(
        ingestionWithCompanionAndCustomUnit = IngestionWithCompanionAndCustomUnit(
            ingestion = EntityBuilders.ingestion(
                substanceName = substance,
                dose = dose,
                units = "mg",
                administrationRoute = AdministrationRoute.ORAL,
                time = Instant.parse("2026-01-01T14:30:00Z"),
                notes = notes,
            ),
            substanceCompanion = null,
            customUnit = null,
        ),
        roaDuration = null,
        numDots = null,
    )

    private fun model(location: String = "", elements: List<IngestionElement>) = ShareableExperienceModel(
        title = "New Year",
        firstIngestionTime = Instant.parse("2026-01-01T14:30:00Z"),
        locationName = location,
        ingestionElements = elements,
        timelineModel = null,
    )

    @Test
    fun `ingestion line shows time, dose, substance and route`() {
        // The time itself follows the device locale, so only its position is asserted.
        val time = Instant.parse("2026-01-01T14:30:00Z").getShortTimeText()
        assertEquals("$time  100 mg Caffeine, oral", ingestionShareLine(element("Caffeine", 100.0)))
    }

    @Test
    fun `unknown dose is spelled out`() {
        assertTrue(ingestionShareLine(element("Caffeine", null)).contains("Unknown dose Caffeine"))
    }

    @Test
    fun `share text lists the title, location and every ingestion`() {
        val text = buildShareText(model("Berlin", listOf(element("Caffeine", 100.0), element("MDMA", 80.0))))
        val lines = text.lines()
        assertEquals("New Year", lines[0])
        assertTrue(lines[1].endsWith(" · Berlin"))
        assertEquals(4, lines.size)
        assertTrue(lines[3].contains("80 mg MDMA"))
    }

    @Test
    fun `share text omits the location separator when there is none and never includes notes`() {
        val text = buildShareText(model(elements = listOf(element("Caffeine", 100.0, notes = "secret"))))
        assertFalse(text.contains("·"))
        assertFalse(text.contains("secret"))
    }
}
