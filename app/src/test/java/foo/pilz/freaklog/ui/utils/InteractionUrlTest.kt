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
    fun testGetInteractionExplanationURL_psychonautwiki() {
        val url = getInteractionExplanationURLForSubstance("https://psychonautwiki.org/wiki/Kratom")
        assertEquals("https://psychonautwiki.org/wiki/Kratom#Dangerous_interactions", url)
    }

    @Test
    fun testGetInteractionExplanationURL_anodyne() {
        val url = getInteractionExplanationURLForSubstance(
            "https://anodyne.wiki/substance/Lysergic%20acid%20diethylamide"
        )
        assertEquals("https://anodyne.wiki/substance/Lysergic%20acid%20diethylamide", url)
    }

    @Test
    fun testGetInteractionExplanationURL_emptyString() {
        val url = getInteractionExplanationURLForSubstance("")
        assertEquals("", url)
    }

    @Test
    fun testGetInteractionExplanationURL_differentDomain() {
        val url = getInteractionExplanationURLForSubstance("https://example.com/substance")
        assertEquals("https://example.com/substance", url)
    }
}
