package foo.pilz.freaklog.data.substances.classes

import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.Roa
import org.junit.Assert.*
import org.junit.Test

class SubstanceClassTest {

    private fun createSubstance(
        categories: List<String> = emptyList(),
        interactions: Interactions? = null,
        roas: List<Roa> = emptyList(),
        url: String = "https://psychonautwiki.org/wiki/Test"
    ) = Substance(
        name = "TestSubstance",
        commonNames = emptyList(),
        url = url,
        isApproved = true,
        tolerance = null,
        crossTolerances = emptyList(),
        addictionPotential = null,
        toxicities = emptyList(),
        categories = categories,
        summary = null,
        effectsSummary = null,
        dosageRemark = null,
        generalRisks = null,
        longtermRisks = null,
        saferUse = emptyList(),
        interactions = interactions,
        roas = roas
    )

    @Test
    fun testHasInteractions_null() {
        val substance = createSubstance(interactions = null)
        assertFalse(substance.hasInteractions)
    }

    @Test
    fun testHasInteractions_emptyLists() {
        val substance = createSubstance(
            interactions = Interactions(dangerous = emptyList(), unsafe = emptyList(), uncertain = emptyList())
        )
        assertFalse(substance.hasInteractions)
    }

    @Test
    fun testHasInteractions_withDangerous() {
        val substance = createSubstance(
            interactions = Interactions(dangerous = listOf("Alcohol"), unsafe = emptyList(), uncertain = emptyList())
        )
        assertTrue(substance.hasInteractions)
    }

    @Test
    fun testHasInteractions_withUnsafe() {
        val substance = createSubstance(
            interactions = Interactions(dangerous = emptyList(), unsafe = listOf("Cannabis"), uncertain = emptyList())
        )
        assertTrue(substance.hasInteractions)
    }

    @Test
    fun testHasInteractions_withUncertain() {
        val substance = createSubstance(
            interactions = Interactions(dangerous = emptyList(), unsafe = emptyList(), uncertain = listOf("Caffeine"))
        )
        assertTrue(substance.hasInteractions)
    }

    @Test
    fun testIsHallucinogen_withHallucinogen() {
        assertTrue(createSubstance(categories = listOf("hallucinogen")).isHallucinogen)
    }

    @Test
    fun testIsHallucinogen_withPsychedelic() {
        assertTrue(createSubstance(categories = listOf("psychedelic")).isHallucinogen)
    }

    @Test
    fun testIsHallucinogen_withDissociative() {
        assertTrue(createSubstance(categories = listOf("dissociative")).isHallucinogen)
    }

    @Test
    fun testIsHallucinogen_withDeliriant() {
        assertTrue(createSubstance(categories = listOf("deliriant")).isHallucinogen)
    }

    @Test
    fun testIsHallucinogen_caseInsensitive() {
        assertTrue(createSubstance(categories = listOf("Hallucinogen")).isHallucinogen)
        assertTrue(createSubstance(categories = listOf("PSYCHEDELIC")).isHallucinogen)
    }

    @Test
    fun testIsHallucinogen_notHallucinogen() {
        assertFalse(createSubstance(categories = listOf("stimulant")).isHallucinogen)
        assertFalse(createSubstance(categories = emptyList()).isHallucinogen)
    }

    @Test
    fun testIsStimulant_withStimulant() {
        assertTrue(createSubstance(categories = listOf("stimulant")).isStimulant)
    }

    @Test
    fun testIsStimulant_caseInsensitive() {
        assertTrue(createSubstance(categories = listOf("Stimulant")).isStimulant)
    }

    @Test
    fun testIsStimulant_notStimulant() {
        assertFalse(createSubstance(categories = listOf("depressant")).isStimulant)
        assertFalse(createSubstance(categories = emptyList()).isStimulant)
    }

    @Test
    fun testInteractionExplanationURL() {
        val substance = createSubstance(url = "https://psychonautwiki.org/wiki/LSD")
        assertEquals("https://psychonautwiki.org/wiki/LSD#Dangerous_interactions", substance.interactionExplanationURL)
    }

    @Test
    fun testGetRoa_returnsMatchingRoa() {
        val oralRoa = Roa(route = AdministrationRoute.ORAL, roaDose = null, roaDuration = null, bioavailability = null)
        val substance = createSubstance(roas = listOf(oralRoa))
        assertEquals(oralRoa, substance.getRoa(AdministrationRoute.ORAL))
    }

    @Test
    fun testGetRoa_returnsNullForMissingRoute() {
        val oralRoa = Roa(route = AdministrationRoute.ORAL, roaDose = null, roaDuration = null, bioavailability = null)
        val substance = createSubstance(roas = listOf(oralRoa))
        assertNull(substance.getRoa(AdministrationRoute.INSUFFLATED))
    }

    @Test
    fun testGetRoa_emptyRoaList() {
        val substance = createSubstance(roas = emptyList())
        assertNull(substance.getRoa(AdministrationRoute.ORAL))
    }
}
