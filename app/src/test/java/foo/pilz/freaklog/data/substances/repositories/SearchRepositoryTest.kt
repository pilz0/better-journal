/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.data.substances.repositories

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import foo.pilz.freaklog.data.substances.classes.SubstanceWithCategories
import foo.pilz.freaklog.data.substances.parse.SubstanceParser
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

class SearchRepositoryTest {
    private lateinit var repository: SearchRepository

    @Before
    fun setUp() {
        val file = SubstanceParser().parseSubstanceFile(
            """
            {
                "categories": [
                    {"name": "common", "description": "Common"},
                    {"name": "psychedelic", "description": "Psychedelic"}
                ],
                "substances": [
                    {"name": "Beta", "url": "", "commonNames": ["2C Beta"], "categories": ["psychedelic"]},
                    {"name": "X2C Beta", "url": "", "categories": ["psychedelic"]},
                    {"name": "2C-B", "url": "", "commonNames": ["Pink-cloud", "2C Bee"], "categories": ["psychedelic"]},
                    {"name": "Common", "url": "", "categories": ["common", "psychedelic"]},
                    {"name": "Other", "url": "", "categories": []}
                ]
            }
            """.trimIndent()
        )
        val substances = file.substances.map { substance ->
            SubstanceWithCategories(substance, file.categories.filter { it.name in substance.categories })
        }
        val byName = substances.associateBy { it.substance.name }
        val substanceRepository = mockk<SubstanceRepository>()
        every { substanceRepository.getAllSubstancesWithCategories() } returns substances
        every { substanceRepository.getSubstanceWithCategories(any()) } answers { byName[firstArg<String>()] }
        repository = SearchRepository(substanceRepository)
    }

    @Test
    fun `primary prefixes precede alias prefixes and substring matches without duplicates`() {
        assertThat(search("2 - c")).isEqualTo(listOf("2C-B", "Beta", "X2C Beta"))
    }

    @Test
    fun `primary and alias matching ignores case hyphens and spaces`() {
        listOf("2cb", "2 C - b").forEach { query ->
            assertThat(search(query)).isEqualTo(listOf("2C-B", "Beta", "X2C Beta"))
        }
        listOf("PINK CLOUD", "pink-cloud", "cloud").forEach { query ->
            assertThat(search(query)).isEqualTo(listOf("2C-B"))
        }
    }

    @Test
    fun `unmatched text and tabs are not stripped into matches`() {
        assertThat(search("unknown")).isEmpty()
        assertThat(search("2\tc")).isEmpty()
    }

    @Test
    fun `empty and separator only queries retain common then source order`() {
        listOf("", " - ").forEach { query ->
            assertThat(search(query)).isEqualTo(listOf("Common", "Beta", "X2C Beta", "2C-B", "Other"))
        }
    }

    @Test
    fun `recent matches retain recency order ahead of common and remaining matches`() {
        val recentNames = listOf("Other", "2C-B", "Other", "unknown", "Common")

        assertThat(search(recentNames = recentNames)).isEqualTo(listOf("Other", "2C-B", "Common", "Beta", "X2C Beta"))
    }

    @Test
    fun `recent names cannot reintroduce substances excluded by text or categories`() {
        val recentNames = listOf("Other", "Common", "X2C Beta", "unknown", "Beta", "Beta")

        assertThat(search("2c", listOf("psychedelic"), recentNames)).isEqualTo(listOf("X2C Beta", "Beta", "2C-B"))
        assertThat(search(categories = listOf("common", "psychedelic"), recentNames = recentNames))
            .isEqualTo(listOf("Common"))
        assertThat(search(categories = listOf("unknown"), recentNames = recentNames)).isEmpty()
    }

    private fun search(
        text: String = "",
        categories: List<String> = emptyList(),
        recentNames: List<String> = emptyList()
    ): List<String> = repository.getMatchingSubstances(text, categories, recentNames).map { it.substance.name }
}
