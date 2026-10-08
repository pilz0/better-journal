package foo.pilz.freaklog.data.room.experiences

import foo.pilz.freaklog.data.room.experiences.entities.CustomInteraction
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionSeverity
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionSeverity.DANGEROUS
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionSeverity.UNCERTAIN
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionSeverity.UNSAFE
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionTargetType
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDose
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDuration
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithEverything
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.InteractionType
import foo.pilz.freaklog.data.substances.classes.roa.DoseClass
import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.DurationUnits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomSubstanceProfilesTest {

    private fun interaction(
        severity: CustomInteractionSeverity,
        target: String,
        type: CustomInteractionTargetType = CustomInteractionTargetType.SUBSTANCE,
    ) = CustomInteraction(severity = severity, targetType = type, targetName = target)

    private fun profile(name: String, vararg interactions: CustomInteraction) = name to CustomSubstanceWithEverything(
        substance = CustomSubstance(name = name, units = "mg", description = ""),
        doses = listOf(CustomRoaDose(route = AdministrationRoute.ORAL, units = "mg", lightMin = 5.0, commonMin = 10.0, strongMin = 20.0, heavyMin = 40.0)),
        durations = listOf(
            CustomRoaDuration(
                route = AdministrationRoute.ORAL, onset = null, comeup = null, peak = null, offset = null,
                total = DurationRange(4f, 6f, DurationUnits.HOURS)
            )
        ),
        interactions = interactions.toList(),
    )

    private val noCategories: (String) -> List<String> = { emptyList() }

    @Test
    fun `a stored route provides dose classes and durations like a built-in one`() {
        val roa = findRoa(mapOf(profile("Mystery")), "Mystery", AdministrationRoute.ORAL)
        assertEquals(DoseClass.COMMON, roa?.roaDose?.getDoseClass(ingestionDose = 15.0))
        assertEquals(14400f, roa?.roaDuration?.total?.minInSec)
    }

    @Test
    fun `unknown substances and routes have no roa`() {
        val profiles = mapOf(profile("Mystery"))
        assertNull(findRoa(profiles, "Mystery", AdministrationRoute.INSUFFLATED))
        assertNull(findRoa(profiles, "Other", AdministrationRoute.ORAL))
    }

    @Test
    fun `an interaction is found from either side and ignores case`() {
        val profiles = mapOf(profile("Mystery", interaction(DANGEROUS, "alcohol")))
        assertEquals(InteractionType.DANGEROUS, findCustomInteraction(profiles, "Mystery", "Alcohol", noCategories))
        assertEquals(InteractionType.DANGEROUS, findCustomInteraction(profiles, "Alcohol", "Mystery", noCategories))
        assertNull(findCustomInteraction(profiles, "Mystery", "Caffeine", noCategories))
    }

    @Test
    fun `the most severe declaration wins when both substances declare one`() {
        val profiles = mapOf(
            profile("A", interaction(UNCERTAIN, "B")),
            profile("B", interaction(UNSAFE, "A")),
        )
        assertEquals(InteractionType.UNSAFE, findCustomInteraction(profiles, "A", "B", noCategories))
    }

    @Test
    fun `category targets match the other substance's categories only`() {
        val profiles = mapOf(profile("Mystery", interaction(UNSAFE, "Stimulant", CustomInteractionTargetType.CATEGORY)))
        val categories: (String) -> List<String> = { if (it == "Caffeine") listOf("stimulant", "common") else emptyList() }

        assertEquals(InteractionType.UNSAFE, findCustomInteraction(profiles, "Mystery", "Caffeine", categories))
        assertNull(findCustomInteraction(profiles, "Mystery", "Alcohol", categories))
        // A substance literally named like the category is not a category match.
        assertNull(findCustomInteraction(profiles, "Mystery", "Stimulant", categories))
    }
}
