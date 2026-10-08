package foo.pilz.freaklog.data.substanceshare

import foo.pilz.freaklog.data.room.experiences.entities.CustomCrossTolerance
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteraction
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionSeverity
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionTargetType
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoa
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDose
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDuration
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithEverything
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.Bioavailability
import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.DurationUnits
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SharedSubstanceMapperTest {

    private val full = CustomSubstanceWithEverything(
        substance = CustomSubstance(
            id = 5,
            name = "Mystery",
            units = "mg",
            description = "found it",
            summary = "A test substance",
            toleranceFull = "1 day",
            generalRisks = "unknown",
        ),
        roas = listOf(CustomRoa(5, AdministrationRoute.ORAL, Bioavailability(40.0, 60.0))),
        doses = listOf(
            CustomRoaDose(5, AdministrationRoute.ORAL, "mg", lightMin = 5.0, commonMin = 10.0, strongMin = 20.0, heavyMin = 40.0),
            CustomRoaDose(5, AdministrationRoute.INSUFFLATED, "mg", commonMin = 4.0),
        ),
        durations = listOf(
            CustomRoaDuration(
                route = AdministrationRoute.ORAL,
                customSubstanceId = 5,
                onset = DurationRange(20f, 40f, DurationUnits.MINUTES),
                comeup = null,
                peak = DurationRange(2f, 3f, DurationUnits.HOURS),
                offset = null,
                total = DurationRange(5f, 7f, DurationUnits.HOURS),
            )
        ),
        interactions = listOf(
            CustomInteraction(
                id = 9, customSubstanceId = 5, severity = CustomInteractionSeverity.DANGEROUS,
                targetType = CustomInteractionTargetType.SUBSTANCE, targetName = "Alcohol"
            )
        ),
        crossTolerances = listOf(CustomCrossTolerance(5, "psychedelic")),
    )

    @Test
    fun `a route that only has a dose still gets its own shared entry`() {
        val shared = full.toShared()
        assertEquals(listOf(AdministrationRoute.ORAL, AdministrationRoute.INSUFFLATED), shared.roas.map { it.route })
        assertNull(shared.roas[1].duration)
        assertNull(shared.roas[1].bioavailability)
        assertEquals(4.0, shared.roas[1].dose?.commonMin)
    }

    @Test
    fun `sharing and expanding keeps every detail and drops database ids`() {
        val expansion = full.toShared().expand()

        assertEquals(full.substance.copy(id = 0), expansion.substance)
        assertEquals(40.0, expansion.roas.single { it.route == AdministrationRoute.ORAL }.bioavailability?.min)
        assertEquals(full.doses.map { it.copy(customSubstanceId = 0) }, expansion.doses)
        assertEquals(full.durations.map { it.copy(customSubstanceId = 0) }, expansion.durations)
        assertEquals(
            listOf(Triple(CustomInteractionSeverity.DANGEROUS, CustomInteractionTargetType.SUBSTANCE, "Alcohol")),
            expansion.interactions.map { Triple(it.severity, it.targetType, it.targetName) }
        )
        assertEquals(0, expansion.interactions.single().id)
        assertEquals(listOf("psychedelic"), expansion.crossTolerances.map { it.categoryName })
    }

    @Test
    fun `expanding under a new name renames only the substance`() {
        val expansion = full.toShared().expand(targetName = "Mystery (imported)")
        assertEquals("Mystery (imported)", expansion.substance.name)
        assertEquals(2, expansion.doses.size)
    }

    @Test
    fun `the file envelope survives a json round trip without derived or id fields`() {
        val envelope = SharedSubstanceEnvelope(SHARED_SUBSTANCE_FORMAT, SHARED_SUBSTANCE_VERSION, full.toShared())
        val text = Json.encodeToString(SharedSubstanceEnvelope.serializer(), envelope)

        assertFalse(text.contains("minInSec"))
        assertFalse(text.contains("customSubstanceId"))
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString(SharedSubstanceEnvelope.serializer(), text)
        assertEquals(envelope, decoded)
        assertEquals(1200f, decoded.substance.roas.first().duration?.onset?.minInSec)
    }

    @Test
    fun `files written by the upstream app with categories are accepted`() {
        val text = """{"format":"gay.cybercrime.journal.substance","version":1,"substance":
            {"name":"X","units":"mg","description":"","categories":["stimulant"],"futureField":1}}"""
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString(SharedSubstanceEnvelope.serializer(), text)
        assertEquals("X", decoded.substance.name)
        assertEquals(true, decoded.format in SHARED_SUBSTANCE_FORMATS)
    }
}
