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

package foo.pilz.freaklog.data.substances

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdministrationRouteTest {

    // ===== displayText Tests =====

    @Test
    fun testOral_displayText() {
        assertEquals("Oral", AdministrationRoute.ORAL.displayText)
    }

    @Test
    fun testSublingual_displayText() {
        assertEquals("Sublingual", AdministrationRoute.SUBLINGUAL.displayText)
    }

    @Test
    fun testBuccal_displayText() {
        assertEquals("Buccal", AdministrationRoute.BUCCAL.displayText)
    }

    @Test
    fun testInsufflated_displayText() {
        assertEquals("Insufflated", AdministrationRoute.INSUFFLATED.displayText)
    }

    @Test
    fun testRectal_displayText() {
        assertEquals("Rectal", AdministrationRoute.RECTAL.displayText)
    }

    @Test
    fun testTransdermal_displayText() {
        assertEquals("Transdermal", AdministrationRoute.TRANSDERMAL.displayText)
    }

    @Test
    fun testSubcutaneous_displayText() {
        assertEquals("Subcutaneous", AdministrationRoute.SUBCUTANEOUS.displayText)
    }

    @Test
    fun testIntramuscular_displayText() {
        assertEquals("Intramuscular", AdministrationRoute.INTRAMUSCULAR.displayText)
    }

    @Test
    fun testIntravenous_displayText() {
        assertEquals("Intravenous", AdministrationRoute.INTRAVENOUS.displayText)
    }

    @Test
    fun testSmoked_displayText() {
        assertEquals("Smoked", AdministrationRoute.SMOKED.displayText)
    }

    @Test
    fun testInhaled_displayText() {
        assertEquals("Inhaled", AdministrationRoute.INHALED.displayText)
    }

    // ===== isInjectionMethod Tests =====

    @Test
    fun testOral_isNotInjectionMethod() {
        assertFalse(AdministrationRoute.ORAL.isInjectionMethod)
    }

    @Test
    fun testSublingual_isNotInjectionMethod() {
        assertFalse(AdministrationRoute.SUBLINGUAL.isInjectionMethod)
    }

    @Test
    fun testBuccal_isNotInjectionMethod() {
        assertFalse(AdministrationRoute.BUCCAL.isInjectionMethod)
    }

    @Test
    fun testInsufflated_isNotInjectionMethod() {
        assertFalse(AdministrationRoute.INSUFFLATED.isInjectionMethod)
    }

    @Test
    fun testRectal_isNotInjectionMethod() {
        assertFalse(AdministrationRoute.RECTAL.isInjectionMethod)
    }

    @Test
    fun testTransdermal_isNotInjectionMethod() {
        assertFalse(AdministrationRoute.TRANSDERMAL.isInjectionMethod)
    }

    @Test
    fun testSubcutaneous_isInjectionMethod() {
        assertTrue(AdministrationRoute.SUBCUTANEOUS.isInjectionMethod)
    }

    @Test
    fun testIntramuscular_isInjectionMethod() {
        assertTrue(AdministrationRoute.INTRAMUSCULAR.isInjectionMethod)
    }

    @Test
    fun testIntravenous_isInjectionMethod() {
        assertTrue(AdministrationRoute.INTRAVENOUS.isInjectionMethod)
    }

    @Test
    fun testSmoked_isNotInjectionMethod() {
        assertFalse(AdministrationRoute.SMOKED.isInjectionMethod)
    }

    @Test
    fun testInhaled_isNotInjectionMethod() {
        assertFalse(AdministrationRoute.INHALED.isInjectionMethod)
    }

    // ===== showSiteSelection Tests =====

    @Test
    fun testInsufflated_showsSiteSelection() {
        assertTrue(AdministrationRoute.INSUFFLATED.showSiteSelection)
    }

    @Test
    fun testIntravenous_showsSiteSelection() {
        assertTrue(AdministrationRoute.INTRAVENOUS.showSiteSelection)
    }

    @Test
    fun testIntramuscular_showsSiteSelection() {
        assertTrue(AdministrationRoute.INTRAMUSCULAR.showSiteSelection)
    }

    @Test
    fun testSubcutaneous_showsSiteSelection() {
        assertTrue(AdministrationRoute.SUBCUTANEOUS.showSiteSelection)
    }

    @Test
    fun testOral_doesNotShowSiteSelection() {
        assertFalse(AdministrationRoute.ORAL.showSiteSelection)
    }

    @Test
    fun testSmoked_doesNotShowSiteSelection() {
        assertFalse(AdministrationRoute.SMOKED.showSiteSelection)
    }

    @Test
    fun testRectal_doesNotShowSiteSelection() {
        assertFalse(AdministrationRoute.RECTAL.showSiteSelection)
    }

    // ===== siteOptions Tests =====

    @Test
    fun testInsufflated_hasNostrilOptions() {
        val options = AdministrationRoute.INSUFFLATED.siteOptions
        assertEquals(3, options.size)
        assertTrue(options.contains("Left nostril"))
        assertTrue(options.contains("Right nostril"))
        assertTrue(options.contains("Both nostrils"))
    }

    @Test
    fun testIntravenous_hasIntravenousSiteOptions() {
        val options = AdministrationRoute.INTRAVENOUS.siteOptions
        assertEquals(10, options.size)
        assertTrue(options.contains("Left Median Cubital"))
        assertTrue(options.contains("Right Median Cubital"))
    }

    @Test
    fun testIntramuscular_hasIntramuscularSiteOptions() {
        val options = AdministrationRoute.INTRAMUSCULAR.siteOptions
        assertEquals(8, options.size)
        assertTrue(options.contains("Left Deltoid"))
        assertTrue(options.contains("Right Vastus Lateralis"))
    }

    @Test
    fun testSubcutaneous_hasSubcutaneousSiteOptions() {
        val options = AdministrationRoute.SUBCUTANEOUS.siteOptions
        assertEquals(8, options.size)
        assertTrue(options.contains("Left abdomen"))
        assertTrue(options.contains("Right thigh"))
    }

    @Test
    fun testOral_hasEmptySiteOptions() {
        val options = AdministrationRoute.ORAL.siteOptions
        assertTrue(options.isEmpty())
    }

    @Test
    fun testSmoked_hasEmptySiteOptions() {
        val options = AdministrationRoute.SMOKED.siteOptions
        assertTrue(options.isEmpty())
    }

    // ===== Companion Object Tests =====

    @Test
    fun testNostrilOptionsCount() {
        assertEquals(3, AdministrationRoute.NOSTRIL_OPTIONS.size)
    }

    @Test
    fun testIntravenousSiteOptionsCount() {
        assertEquals(10, AdministrationRoute.INTRAVENOUS_SITE_OPTIONS.size)
    }

    @Test
    fun testIntramuscularSiteOptionsCount() {
        assertEquals(8, AdministrationRoute.INTRAMUSCULAR_SITE_OPTIONS.size)
    }

    @Test
    fun testSubcutaneousSiteOptionsCount() {
        assertEquals(8, AdministrationRoute.SUBCUTANEOUS_SITE_OPTIONS.size)
    }

    @Test
    fun testIntramuscularSitesAreMuscleBased() {
        val options = AdministrationRoute.INTRAMUSCULAR_SITE_OPTIONS
        assertTrue(
            options.all {
                it.contains("Deltoid") ||
                    it.contains("Vastus Lateralis") ||
                    it.contains("Ventrogluteal") ||
                    it.contains("Dorsogluteal")
            }
        )
    }

    @Test
    fun testSubcutaneousSitesAreTissueBased() {
        val options = AdministrationRoute.SUBCUTANEOUS_SITE_OPTIONS
        assertTrue(
            options.all {
                it.contains("abdomen") ||
                    it.contains("thigh") ||
                    it.contains("upper arm") ||
                    it.contains("lower back")
            }
        )
    }

    @Test
    fun testIntravenousSiteOptionsAreVeinBased() {
        val options = AdministrationRoute.INTRAVENOUS_SITE_OPTIONS
        assertTrue(
            options.all {
                it.contains("Median Cubital") ||
                    it.contains("Cephalic") ||
                    it.contains("Basilic") ||
                    it.contains("Dorsal Hand") ||
                    it.contains("Median Antebrachial")
            }
        )
    }

    @Test
    fun testPsychonautWikiArticleUrl() {
        assertEquals(
            "https://psychonautwiki.org/wiki/Route_of_administration",
            AdministrationRoute.PSYCHONAUT_WIKI_ARTICLE_URL
        )
    }

    @Test
    fun testSaferInjectionArticleUrl() {
        assertEquals(
            "https://psychonautwiki.org/wiki/Safer_injection_guide",
            AdministrationRoute.SAFER_INJECTION_ARTICLE_URL
        )
    }

    @Test
    fun testSaferPluggingArticleUrl() {
        assertEquals(
            "https://wiki.tripsit.me/wiki/Quick_Guide_to_Plugging",
            AdministrationRoute.SAFER_PLUGGING_ARTICLE_URL
        )
    }

    // ===== All Enum Values Test =====

    @Test
    fun testAllRouteEnumValues() {
        val allRoutes = AdministrationRoute.entries
        assertEquals(11, allRoutes.size)
    }

    @Test
    fun testAllRoutesHaveDisplayText() {
        AdministrationRoute.entries.forEach { route ->
            assertTrue("Route $route should have non-empty displayText", route.displayText.isNotEmpty())
        }
    }

    @Test
    fun testAllRoutesHaveDescription() {
        AdministrationRoute.entries.forEach { route ->
            assertTrue("Route $route should have non-empty description", route.description.isNotEmpty())
        }
    }

    @Test
    fun testAllRoutesHaveArticleText() {
        AdministrationRoute.entries.forEach { route ->
            assertTrue("Route $route should have non-empty articleText", route.articleText.isNotEmpty())
        }
    }
}
