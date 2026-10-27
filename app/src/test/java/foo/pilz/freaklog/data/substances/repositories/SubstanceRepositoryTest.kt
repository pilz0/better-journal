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

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import foo.pilz.freaklog.data.substances.classes.SubstanceFile
import foo.pilz.freaklog.data.substances.parse.SubstanceParser
import foo.pilz.freaklog.data.substances.parse.SubstanceParserInterface
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SubstanceRepositoryTest {
    private lateinit var repository: SubstanceRepository

    @Before
    fun setUp() {
        repository = SubstanceRepository(ApplicationProvider.getApplicationContext<Context>(), SubstanceParser())
    }

    @Test
    fun `cached list preserves substances and each substance's category order from the asset`() {
        val results = repository.getAllSubstancesWithCategories()

        assertThat(results).isNotEmpty()
        assertThat(results.map { it.substance }).isEqualTo(repository.getAllSubstances())
        results.forEach { result ->
            val expectedCategories = result.substance.categories.mapNotNull { name ->
                repository.getAllCategories().find { it.name == name }
            }
            assertThat(result.categories).isEqualTo(expectedCategories)
        }
        assertThat(repository.getAllSubstancesWithCategories()).isSameInstanceAs(results)
    }

    @Test
    fun `substance lookup returns the same cached entry as the list`() {
        repository.getAllSubstancesWithCategories().forEach { result ->
            assertThat(repository.getSubstanceWithCategories(result.substance.name)).isSameInstanceAs(result)
            assertThat(repository.getSubstance(result.substance.name)).isSameInstanceAs(result.substance)
        }
        assertThat(repository.getSubstanceWithCategories("unknown substance")).isNull()
    }

    @Test
    fun `category lookup returns known categories and null for unknown names`() {
        assertThat(repository.getAllCategories()).isNotEmpty()
        repository.getAllCategories().forEach { category ->
            assertThat(repository.getCategory(category.name)).isSameInstanceAs(category)
        }
        assertThat(repository.getCategory("unknown category")).isNull()
    }

    @Test
    fun `empty substance file has empty caches and no matching lookups`() {
        val parser = mockk<SubstanceParserInterface>()
        every { parser.parseSubstanceFile(any()) } returns SubstanceFile(emptyList(), emptyList())
        val emptyRepository = SubstanceRepository(ApplicationProvider.getApplicationContext<Context>(), parser)

        assertThat(emptyRepository.getAllSubstancesWithCategories()).isEmpty()
        assertThat(emptyRepository.getSubstanceWithCategories("2C-B")).isNull()
        assertThat(emptyRepository.getCategory("common")).isNull()
    }
}
