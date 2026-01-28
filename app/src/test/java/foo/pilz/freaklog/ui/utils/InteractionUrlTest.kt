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

package foo.pilz.freaklog.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class InteractionUrlTest {

    @Test
    fun testGetInteractionExplanationURL_basic() {
        val url = getInteractionExplanationURLForSubstance("https://psychonautwiki.org/wiki/LSD")
        assertEquals("https://psychonautwiki.org/wiki/LSD#Dangerous_interactions", url)
    }

    @Test
    fun testGetInteractionExplanationURL_mdma() {
        val url = getInteractionExplanationURLForSubstance("https://psychonautwiki.org/wiki/MDMA")
        assertEquals("https://psychonautwiki.org/wiki/MDMA#Dangerous_interactions", url)
    }

    @Test
    fun testGetInteractionExplanationURL_cocaine() {
        val url = getInteractionExplanationURLForSubstance("https://psychonautwiki.org/wiki/Cocaine")
        assertEquals("https://psychonautwiki.org/wiki/Cocaine#Dangerous_interactions", url)
    }

    @Test
    fun testGetInteractionExplanationURL_cannabis() {
        val url = getInteractionExplanationURLForSubstance("https://psychonautwiki.org/wiki/Cannabis")
        assertEquals("https://psychonautwiki.org/wiki/Cannabis#Dangerous_interactions", url)
    }

    @Test
    fun testGetInteractionExplanationURL_emptyString() {
        val url = getInteractionExplanationURLForSubstance("")
        assertEquals("#Dangerous_interactions", url)
    }

    @Test
    fun testGetInteractionExplanationURL_differentDomain() {
        val url = getInteractionExplanationURLForSubstance("https://example.com/substance")
        assertEquals("https://example.com/substance#Dangerous_interactions", url)
    }
}
