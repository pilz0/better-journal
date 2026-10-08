package foo.pilz.freaklog.ui.tabs.search.custom.profile

import foo.pilz.freaklog.data.room.experiences.entities.CustomRoa
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDose
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDuration
import foo.pilz.freaklog.data.room.experiences.relations.CustomRoaInfo
import foo.pilz.freaklog.data.substances.AdministrationRoute.ORAL
import foo.pilz.freaklog.data.substances.classes.roa.Bioavailability
import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.DurationUnits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomRouteDraftTest {

    @Test
    fun `an empty draft is valid and stores only the bare route`() {
        val draft = CustomRouteDraft(route = ORAL)
        assertTrue(draft.isValid)
        assertEquals(CustomRoa(route = ORAL, bioavailability = null), draft.toRoa())
        assertNull(draft.toDose())
        assertNull(draft.toDuration())
    }

    @Test
    fun `dose thresholds are parsed, including expressions and decimal commas`() {
        val dose = CustomRouteDraft(route = ORAL, doseUnits = " mg ", lightMin = "2,5", commonMin = "10/2", heavyMin = "40")
            .toDose()
        assertEquals(CustomRoaDose(route = ORAL, units = "mg", lightMin = 2.5, commonMin = 5.0, strongMin = null, heavyMin = 40.0), dose)
    }

    @Test
    fun `thresholds must be numeric, non-negative and increasing`() {
        assertTrue(CustomRouteDraft(route = ORAL, lightMin = "5", strongMin = "20").areDosesValid)
        assertFalse(CustomRouteDraft(route = ORAL, lightMin = "abc").areDosesValid)
        assertFalse(CustomRouteDraft(route = ORAL, lightMin = "-1").areDosesValid)
        assertFalse(CustomRouteDraft(route = ORAL, commonMin = "20", strongMin = "10").areDosesValid)
    }

    @Test
    fun `a single duration value is used for both ends of the range`() {
        assertEquals(DurationRange(30f, 30f, DurationUnits.MINUTES), DurationDraft(min = "30").toRange())
        assertEquals(DurationRange(2f, 2f, DurationUnits.HOURS), DurationDraft(max = "2", units = DurationUnits.HOURS).toRange())
    }

    @Test
    fun `reversed, negative or non-numeric durations are invalid but blank ones are fine`() {
        assertTrue(DurationDraft().isValid)
        assertTrue(DurationDraft("20", "40").isValid)
        assertFalse(DurationDraft("40", "20").isValid)
        assertFalse(DurationDraft("-5", "").isValid)
        assertFalse(DurationDraft("soon", "").isValid)
        assertFalse(CustomRouteDraft(route = ORAL, peak = DurationDraft("3", "1")).isValid)
    }

    @Test
    fun `only the phases that were filled in are stored`() {
        val duration = CustomRouteDraft(
            route = ORAL,
            onset = DurationDraft("20", "40"),
            total = DurationDraft("5", "7", DurationUnits.HOURS),
        ).toDuration()
        assertEquals(DurationRange(20f, 40f, DurationUnits.MINUTES), duration?.onset)
        assertNull(duration?.peak)
        assertEquals(DurationRange(5f, 7f, DurationUnits.HOURS), duration?.total)
    }

    @Test
    fun `bioavailability must be an ordered percentage`() {
        assertTrue(CustomRouteDraft(route = ORAL, bioavailabilityMin = "40", bioavailabilityMax = "60").isBioavailabilityValid)
        assertTrue(CustomRouteDraft(route = ORAL, bioavailabilityMin = "50").isBioavailabilityValid)
        assertFalse(CustomRouteDraft(route = ORAL, bioavailabilityMin = "60", bioavailabilityMax = "40").isBioavailabilityValid)
        assertFalse(CustomRouteDraft(route = ORAL, bioavailabilityMax = "120").isBioavailabilityValid)
        assertFalse(CustomRouteDraft(route = ORAL, bioavailabilityMin = "x").isBioavailabilityValid)
        assertEquals(Bioavailability(50.0, 50.0), CustomRouteDraft(route = ORAL, bioavailabilityMin = "50").toRoa().bioavailability)
    }

    @Test
    fun `a stored route loads into a draft and saves back unchanged`() {
        val info = CustomRoaInfo(
            route = ORAL,
            roa = CustomRoa(route = ORAL, bioavailability = Bioavailability(40.0, 60.0)),
            dose = CustomRoaDose(route = ORAL, units = "µg", lightMin = 25.0, commonMin = 75.0, strongMin = 150.0, heavyMin = 300.0),
            duration = CustomRoaDuration(
                route = ORAL,
                onset = DurationRange(20f, 40f, DurationUnits.MINUTES),
                comeup = null,
                peak = DurationRange(2.5f, 3f, DurationUnits.HOURS),
                offset = null,
                total = DurationRange(8f, 12f, DurationUnits.HOURS),
            ),
        )
        val draft = CustomRouteDraft.from(info, defaultUnits = "mg")

        assertEquals("µg", draft.doseUnits)
        assertEquals("2.5", draft.peak.min)
        assertTrue(draft.isValid)
        assertEquals(info.roa, draft.toRoa())
        assertEquals(info.dose, draft.toDose())
        assertEquals(info.duration, draft.toDuration())
    }

    @Test
    fun `a route without a dose falls back to the substance units`() {
        assertEquals("mg", CustomRouteDraft.from(CustomRoaInfo(route = ORAL), defaultUnits = "mg").doseUnits)
    }

    @Test
    fun `cross tolerances are split, trimmed and de-duplicated`() {
        assertEquals(listOf("psychedelic", "LSD"), parseCrossTolerances(" psychedelic, LSD ,, Psychedelic"))
        assertTrue(parseCrossTolerances("  ").isEmpty())
    }
}
