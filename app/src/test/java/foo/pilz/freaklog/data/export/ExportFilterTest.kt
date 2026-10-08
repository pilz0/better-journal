package foo.pilz.freaklog.data.export

import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.experiences.entities.CustomUnit
import foo.pilz.freaklog.data.room.experiences.entities.IntakeLimit
import foo.pilz.freaklog.data.room.experiences.entities.IntakeLimitType
import foo.pilz.freaklog.data.room.experiences.entities.Location
import foo.pilz.freaklog.data.room.experiences.entities.ShulginRating
import foo.pilz.freaklog.data.room.experiences.entities.ShulginRatingOption
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.room.experiences.entities.TimedNote
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsTimedNotesAndRatings
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.IngestionCategory
import foo.pilz.freaklog.testing.EntityBuilders
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFilterTest {

    private val now = EntityBuilders.FIXED_INSTANT

    private val tabUnit = CustomUnit(
        id = 7,
        substanceName = "LSD",
        name = "Tab",
        administrationRoute = AdministrationRoute.SUBLINGUAL,
        dose = 100.0,
        estimatedDoseStandardDeviation = null,
        isEstimate = false,
        isArchived = false,
        unit = "tab",
        unitPlural = "tabs",
        originalUnit = "µg",
        note = "from the blue sheet",
    )

    private val data = ExportData(
        experiences = listOf(
            ExperienceWithIngestionsTimedNotesAndRatings(
                experience = EntityBuilders.experience(
                    id = 1,
                    title = "Trip",
                    text = "private thoughts",
                    location = Location(name = "Home", longitude = 1.0, latitude = 2.0),
                ),
                ingestions = listOf(
                    EntityBuilders.ingestion(
                        id = 1, substanceName = "LSD", experienceId = 1, customUnitId = 7, notes = "strong"
                    ),
                    EntityBuilders.ingestion(
                        id = 2, substanceName = "Cannabis", experienceId = 1, consumerName = "Alex"
                    ),
                ),
                timedNotes = listOf(
                    TimedNote(
                        experienceId = 1, time = now, creationDate = now, note = "peaking",
                        color = AdaptiveColor.BLUE, isPartOfTimeline = true
                    )
                ),
                ratings = listOf(
                    ShulginRating(time = now, creationDate = now, option = ShulginRatingOption.TWO_PLUS, experienceId = 1)
                ),
            ),
            ExperienceWithIngestionsTimedNotesAndRatings(
                experience = EntityBuilders.experience(id = 2, title = "Morning"),
                ingestions = listOf(
                    EntityBuilders.ingestion(id = 3, substanceName = "Caffeine", experienceId = 2)
                ),
                timedNotes = emptyList(),
                ratings = emptyList(),
            ),
        ),
        customUnits = listOf(tabUnit),
        substanceCompanions = listOf(
            SubstanceCompanion("LSD", AdaptiveColor.BLUE),
            SubstanceCompanion("Cannabis", AdaptiveColor.GREEN),
            SubstanceCompanion("Caffeine", AdaptiveColor.BROWN, defaultCategory = IngestionCategory.MEDICINAL),
        ),
        customSubstances = listOf(CustomSubstance(name = "Mystery", units = "mg", description = "")),
        intakeLimits = listOf(
            IntakeLimit(
                substanceName = "Caffeine", limitType = IntakeLimitType.DOSE, maxDose = 400.0, unit = "mg",
                maxCount = null, windowSeconds = 86_400, warningPercent = 80
            ),
            IntakeLimit(
                substanceName = "LSD", limitType = IntakeLimitType.COUNT, maxDose = null, unit = null,
                maxCount = 1, windowSeconds = 604_800, warningPercent = 80
            ),
        ),
    )

    private fun ExportData.substances() = experiences.flatMap { it.ingestions }.map { it.substanceName }

    @Test
    fun `the default filter is inactive and returns the data untouched`() {
        assertTrue(!ExportFilter().isActive)
        assertSame(data, data.filtered(ExportFilter()))
    }

    @Test
    fun `substance filter keeps only matching ingestions and their dependants`() {
        val result = data.filtered(ExportFilter(substanceNames = setOf("LSD")))

        assertEquals(listOf("LSD"), result.substances())
        assertEquals(listOf("Trip"), result.experiences.map { it.experience.title })
        assertEquals(listOf("LSD"), result.substanceCompanions.map { it.substanceName })
        assertEquals(listOf(7), result.customUnits.map { it.id })
        assertEquals(listOf("LSD"), result.intakeLimits.map { it.substanceName })
        assertTrue(result.customSubstances.isEmpty())
    }

    @Test
    fun `custom units no kept ingestion uses are pruned`() {
        val result = data.filtered(ExportFilter(substanceNames = setOf("Caffeine")))
        assertTrue(result.customUnits.isEmpty())
    }

    @Test
    fun `excluding other consumers drops their ingestions`() {
        val result = data.filtered(ExportFilter(includeOtherConsumers = false))
        assertEquals(listOf("LSD", "Caffeine"), result.substances())
    }

    @Test
    fun `category filter uses the inherited category`() {
        val medicinal = data.filtered(ExportFilter(category = IngestionCategory.MEDICINAL))
        assertEquals(listOf("Caffeine"), medicinal.substances())

        val recreational = data.filtered(ExportFilter(category = IngestionCategory.RECREATIONAL))
        assertEquals(listOf("LSD", "Cannabis"), recreational.substances())
    }

    @Test
    fun `excluding notes scrubs every free-text field but keeps the rows`() {
        val result = data.filtered(ExportFilter(includeNotes = false))

        assertEquals(3, result.substances().size)
        assertEquals("", result.experiences.first().experience.text)
        assertNull(result.experiences.first().ingestions.first().notes)
        assertEquals("", result.customUnits.single().note)
        assertEquals(2, result.intakeLimits.size)
    }

    @Test
    fun `timed notes, ratings and locations can each be left out`() {
        val result = data.filtered(
            ExportFilter(includeTimedNotes = false, includeRatings = false, includeLocations = false)
        )
        val trip = result.experiences.first()
        assertTrue(trip.timedNotes.isEmpty())
        assertTrue(trip.ratings.isEmpty())
        assertNull(trip.experience.location)
        assertEquals("private thoughts", trip.experience.text)
    }

    @Test
    fun `a filtered export serializes and decodes back to the kept rows`() {
        val export = data.filtered(ExportFilter(substanceNames = setOf("LSD"), includeNotes = false)).toJournalExport()
        val decoded = Json.decodeFromString<JournalExport>(Json.encodeToString(export))

        assertEquals(listOf("LSD"), decoded.experiences.flatMap { it.ingestions }.map { it.substanceName })
        assertNull(decoded.experiences.single().ingestions.single().notes)
        assertEquals(1, decoded.intakeLimits.size)
    }
}
