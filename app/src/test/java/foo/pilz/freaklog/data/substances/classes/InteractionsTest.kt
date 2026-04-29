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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionsTest {

    @Test
    fun testInteractions_creation() {
        val interactions = Interactions(
            dangerous = listOf("MAOIs"),
            unsafe = listOf("Alcohol"),
            uncertain = listOf("Cannabis")
        )
        assertNotNull(interactions)
    }

    @Test
    fun testInteractions_dangerousListContents() {
        val interactions = Interactions(
            dangerous = listOf("MAOIs", "Lithium"),
            unsafe = emptyList(),
            uncertain = emptyList()
        )
        assertEquals(2, interactions.dangerous.size)
        assertTrue(interactions.dangerous.contains("MAOIs"))
        assertTrue(interactions.dangerous.contains("Lithium"))
    }

    @Test
    fun testInteractions_unsafeListContents() {
        val interactions = Interactions(
            dangerous = emptyList(),
            unsafe = listOf("Alcohol", "Tramadol", "DXM"),
            uncertain = emptyList()
        )
        assertEquals(3, interactions.unsafe.size)
        assertTrue(interactions.unsafe.contains("Alcohol"))
        assertTrue(interactions.unsafe.contains("Tramadol"))
        assertTrue(interactions.unsafe.contains("DXM"))
    }

    @Test
    fun testInteractions_uncertainListContents() {
        val interactions = Interactions(
            dangerous = emptyList(),
            unsafe = emptyList(),
            uncertain = listOf("Cannabis", "Caffeine")
        )
        assertEquals(2, interactions.uncertain.size)
        assertTrue(interactions.uncertain.contains("Cannabis"))
        assertTrue(interactions.uncertain.contains("Caffeine"))
    }

    @Test
    fun testInteractions_emptyLists() {
        val interactions = Interactions(
            dangerous = emptyList(),
            unsafe = emptyList(),
            uncertain = emptyList()
        )
        assertTrue(interactions.dangerous.isEmpty())
        assertTrue(interactions.unsafe.isEmpty())
        assertTrue(interactions.uncertain.isEmpty())
    }

    @Test
    fun testInteractions_allListsPopulated() {
        val interactions = Interactions(
            dangerous = listOf("MAOIs"),
            unsafe = listOf("Alcohol"),
            uncertain = listOf("Cannabis")
        )
        assertEquals(1, interactions.dangerous.size)
        assertEquals(1, interactions.unsafe.size)
        assertEquals(1, interactions.uncertain.size)
    }
}

class InteractionTypeTest {

    @Test
    fun testDangerous_color() {
        val color = InteractionType.DANGEROUS.color
        assertNotNull(color)
    }

    @Test
    fun testUnsafe_color() {
        val color = InteractionType.UNSAFE.color
        assertNotNull(color)
    }

    @Test
    fun testUncertain_color() {
        val color = InteractionType.UNCERTAIN.color
        assertNotNull(color)
    }

    @Test
    fun testDangerous_dangerCount() {
        assertEquals(3, InteractionType.DANGEROUS.dangerCount)
    }

    @Test
    fun testUnsafe_dangerCount() {
        assertEquals(2, InteractionType.UNSAFE.dangerCount)
    }

    @Test
    fun testUncertain_dangerCount() {
        assertEquals(1, InteractionType.UNCERTAIN.dangerCount)
    }

    @Test
    fun testDangerCountOrder() {
        assertTrue(InteractionType.DANGEROUS.dangerCount > InteractionType.UNSAFE.dangerCount)
        assertTrue(InteractionType.UNSAFE.dangerCount > InteractionType.UNCERTAIN.dangerCount)
    }

    @Test
    fun testAllEnumValues() {
        assertEquals(3, InteractionType.entries.size)
    }

    @Test
    fun testEnumOrder() {
        val entries = InteractionType.entries
        assertEquals(InteractionType.DANGEROUS, entries[0])
        assertEquals(InteractionType.UNSAFE, entries[1])
        assertEquals(InteractionType.UNCERTAIN, entries[2])
    }

    @Test
    fun testAllColorsAreDifferent() {
        val colors = InteractionType.entries.map { it.color }
        assertEquals(3, colors.toSet().size)
    }
}
