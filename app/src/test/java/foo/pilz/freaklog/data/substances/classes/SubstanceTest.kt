/*
 * Copyright (c) 2022-2024. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.data.substances.classes

import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.Roa
import foo.pilz.freaklog.data.substances.classes.roa.RoaDose
import foo.pilz.freaklog.data.substances.classes.roa.RoaDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubstanceTest {

    private fun createTestSubstance(
        name: String = "TestSubstance",
        categories: List<String> = emptyList(),
        interactions: Interactions? = null,
        roas: List<Roa> = emptyList(),
        url: String = "https://example.com/TestSubstance"
    ): Substance {
        return Substance(
            name = name,
            commonNames = listOf("Test", "Tester"),
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
    }

    // ===== isHallucinogen Tests =====

    @Test
    fun testIsHallucinogen_withHallucinogen() {
        val substance = createTestSubstance(categories = listOf("Hallucinogen", "Other"))
        assertTrue(substance.isHallucinogen)
    }

    @Test
    fun testIsHallucinogen_withPsychedelic() {
        val substance = createTestSubstance(categories = listOf("Psychedelic"))
        assertTrue(substance.isHallucinogen)
    }

    @Test
    fun testIsHallucinogen_withDissociative() {
        val substance = createTestSubstance(categories = listOf("Dissociative"))
        assertTrue(substance.isHallucinogen)
    }

    @Test
    fun testIsHallucinogen_withDeliriant() {
        val substance = createTestSubstance(categories = listOf("Deliriant"))
        assertTrue(substance.isHallucinogen)
    }

    @Test
    fun testIsHallucinogen_caseInsensitive() {
        val substance = createTestSubstance(categories = listOf("PSYCHEDELIC"))
        assertTrue(substance.isHallucinogen)
    }

    @Test
    fun testIsHallucinogen_mixedCase() {
        val substance = createTestSubstance(categories = listOf("PsYcHeDeLiC"))
        assertTrue(substance.isHallucinogen)
    }

    @Test
    fun testIsHallucinogen_withStimulant() {
        val substance = createTestSubstance(categories = listOf("Stimulant"))
        assertFalse(substance.isHallucinogen)
    }

    @Test
    fun testIsHallucinogen_emptyCategories() {
        val substance = createTestSubstance(categories = emptyList())
        assertFalse(substance.isHallucinogen)
    }

    @Test
    fun testIsHallucinogen_withMultipleCategoriesIncludingHallucinogen() {
        val substance = createTestSubstance(categories = listOf("Stimulant", "Psychedelic"))
        assertTrue(substance.isHallucinogen)
    }

    // ===== isStimulant Tests =====

    @Test
    fun testIsStimulant_withStimulant() {
        val substance = createTestSubstance(categories = listOf("Stimulant"))
        assertTrue(substance.isStimulant)
    }

    @Test
    fun testIsStimulant_caseInsensitive() {
        val substance = createTestSubstance(categories = listOf("STIMULANT"))
        assertTrue(substance.isStimulant)
    }

    @Test
    fun testIsStimulant_withPsychedelic() {
        val substance = createTestSubstance(categories = listOf("Psychedelic"))
        assertFalse(substance.isStimulant)
    }

    @Test
    fun testIsStimulant_emptyCategories() {
        val substance = createTestSubstance(categories = emptyList())
        assertFalse(substance.isStimulant)
    }

    @Test
    fun testIsStimulant_withMultipleCategoriesIncludingStimulant() {
        val substance = createTestSubstance(categories = listOf("Psychedelic", "Stimulant"))
        assertTrue(substance.isStimulant)
    }

    // ===== hasInteractions Tests =====

    @Test
    fun testHasInteractions_withDangerousInteractions() {
        val interactions = Interactions(
            dangerous = listOf("Alcohol"),
            unsafe = emptyList(),
            uncertain = emptyList()
        )
        val substance = createTestSubstance(interactions = interactions)
        assertTrue(substance.hasInteractions)
    }

    @Test
    fun testHasInteractions_withUnsafeInteractions() {
        val interactions = Interactions(
            dangerous = emptyList(),
            unsafe = listOf("Cannabis"),
            uncertain = emptyList()
        )
        val substance = createTestSubstance(interactions = interactions)
        assertTrue(substance.hasInteractions)
    }

    @Test
    fun testHasInteractions_withUncertainInteractions() {
        val interactions = Interactions(
            dangerous = emptyList(),
            unsafe = emptyList(),
            uncertain = listOf("Caffeine")
        )
        val substance = createTestSubstance(interactions = interactions)
        assertTrue(substance.hasInteractions)
    }

    @Test
    fun testHasInteractions_withAllInteractionTypes() {
        val interactions = Interactions(
            dangerous = listOf("Alcohol"),
            unsafe = listOf("Cannabis"),
            uncertain = listOf("Caffeine")
        )
        val substance = createTestSubstance(interactions = interactions)
        assertTrue(substance.hasInteractions)
    }

    @Test
    fun testHasInteractions_withEmptyInteractions() {
        val interactions = Interactions(
            dangerous = emptyList(),
            unsafe = emptyList(),
            uncertain = emptyList()
        )
        val substance = createTestSubstance(interactions = interactions)
        assertFalse(substance.hasInteractions)
    }

    @Test
    fun testHasInteractions_nullInteractions() {
        val substance = createTestSubstance(interactions = null)
        assertFalse(substance.hasInteractions)
    }

    // ===== getRoa Tests =====

    @Test
    fun testGetRoa_existingRoute() {
        val oralRoa = Roa(
            route = AdministrationRoute.ORAL,
            roaDose = RoaDose("mg", 10.0, 25.0, 50.0, 100.0),
            roaDuration = null,
            bioavailability = null
        )
        val substance = createTestSubstance(roas = listOf(oralRoa))

        val result = substance.getRoa(AdministrationRoute.ORAL)
        assertEquals(oralRoa, result)
    }

    @Test
    fun testGetRoa_nonExistingRoute() {
        val oralRoa = Roa(
            route = AdministrationRoute.ORAL,
            roaDose = RoaDose("mg", 10.0, 25.0, 50.0, 100.0),
            roaDuration = null,
            bioavailability = null
        )
        val substance = createTestSubstance(roas = listOf(oralRoa))

        val result = substance.getRoa(AdministrationRoute.INSUFFLATED)
        assertNull(result)
    }

    @Test
    fun testGetRoa_emptyRoas() {
        val substance = createTestSubstance(roas = emptyList())

        val result = substance.getRoa(AdministrationRoute.ORAL)
        assertNull(result)
    }

    @Test
    fun testGetRoa_multipleRoutes() {
        val oralRoa = Roa(
            route = AdministrationRoute.ORAL,
            roaDose = RoaDose("mg", 10.0, 25.0, 50.0, 100.0),
            roaDuration = null,
            bioavailability = null
        )
        val insufflatedRoa = Roa(
            route = AdministrationRoute.INSUFFLATED,
            roaDose = RoaDose("mg", 5.0, 15.0, 30.0, 60.0),
            roaDuration = null,
            bioavailability = null
        )
        val substance = createTestSubstance(roas = listOf(oralRoa, insufflatedRoa))

        assertEquals(oralRoa, substance.getRoa(AdministrationRoute.ORAL))
        assertEquals(insufflatedRoa, substance.getRoa(AdministrationRoute.INSUFFLATED))
        assertNull(substance.getRoa(AdministrationRoute.SMOKED))
    }

    // ===== interactionExplanationURL Tests =====

    @Test
    fun testInteractionExplanationURL() {
        val substance = createTestSubstance(url = "https://psychonautwiki.org/wiki/LSD")
        assertEquals(
            "https://psychonautwiki.org/wiki/LSD#Dangerous_interactions",
            substance.interactionExplanationURL
        )
    }

    @Test
    fun testInteractionExplanationURL_differentURL() {
        val substance = createTestSubstance(url = "https://psychonautwiki.org/wiki/MDMA")
        assertEquals(
            "https://psychonautwiki.org/wiki/MDMA#Dangerous_interactions",
            substance.interactionExplanationURL
        )
    }
}
