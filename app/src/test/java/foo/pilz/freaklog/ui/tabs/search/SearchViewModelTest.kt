/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.ui.tabs.search

import androidx.compose.ui.graphics.Color
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.substances.classes.Category
import foo.pilz.freaklog.data.substances.classes.Substance
import foo.pilz.freaklog.data.substances.classes.SubstanceWithCategories
import foo.pilz.freaklog.data.substances.repositories.SearchRepository
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val experienceRepo = mockk<ExperienceRepository>()
    private val substanceRepo = mockk<SubstanceRepository>()
    private val searchRepo = mockk<SearchRepository>()

    private val recentsFlow = MutableStateFlow<List<String>>(emptyList())
    private val customSubstancesFlow = MutableStateFlow<List<CustomSubstance>>(emptyList())

    private val testDispatcher = UnconfinedTestDispatcher()

    private val categories = listOf(
        Category(name = "psychedelic", description = "Psychedelic", url = null, color = Color.Magenta),
        Category(name = "stimulant", description = "Stimulant", url = null, color = Color.Red)
    )

    private val mockSubstances = listOf(
        SubstanceWithCategories(
            substance = mockk<Substance> {
                every { name } returns "LSD"
                every { commonNames } returns listOf("Acid")
                every { saferUse } returns listOf("Test set & setting")
                every { hasInteractions } returns true
            },
            categories = listOf(categories[0])
        ),
        SubstanceWithCategories(
            substance = mockk<Substance> {
                every { name } returns "Caffeine"
                every { commonNames } returns emptyList()
                every { saferUse } returns emptyList()
                every { hasInteractions } returns false
            },
            categories = listOf(categories[1])
        )
    )

    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { experienceRepo.getSortedLastUsedSubstanceNamesFlow(limit = 200) } returns recentsFlow
        every { experienceRepo.getCustomSubstancesFlow() } returns customSubstancesFlow
        every { substanceRepo.getAllCategories() } returns categories
        every {
            searchRepo.getMatchingSubstances(
                searchText = any(),
                filterCategories = any(),
                recentlyUsedSubstanceNamesSorted = any()
            )
        } answers {
            val query = firstArg<String>()
            val filters = secondArg<List<String>>()
            mockSubstances.filter { item ->
                (query.isEmpty() || item.substance.name.contains(query, ignoreCase = true)) &&
                    (filters.isEmpty() || filters.all { f -> item.categories.any { it.name == f } })
            }
        }

        viewModel = SearchViewModel(
            experienceRepo = experienceRepo,
            substanceRepo = substanceRepo,
            searchRepository = searchRepo,
            defaultDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial searchText is empty and filterSubstances updates it`() = runTest {
        assertThat(viewModel.searchTextFlow.value).isEqualTo("")

        viewModel.filterSubstances("MDMA")
        advanceUntilIdle()

        assertThat(viewModel.searchTextFlow.value).isEqualTo("MDMA")
    }

    @Test
    fun `chipCategoriesFlow prepends custom chip and lists all categories`() = runTest {
        backgroundScope.launch(testDispatcher) {
            viewModel.chipCategoriesFlow.collect()
        }
        advanceUntilIdle()

        val chips = viewModel.chipCategoriesFlow.value
        assertThat(chips.size).isEqualTo(3)
        assertThat(chips[0].chipName).isEqualTo("custom")
        assertThat(chips[0].color).isEqualTo(customColor)
        assertThat(chips[0].isActive).isFalse()

        assertThat(chips[1].chipName).isEqualTo("psychedelic")
        assertThat(chips[1].color).isEqualTo(Color.Magenta)
        assertThat(chips[1].isActive).isFalse()

        assertThat(chips[2].chipName).isEqualTo("stimulant")
        assertThat(chips[2].color).isEqualTo(Color.Red)
        assertThat(chips[2].isActive).isFalse()
    }

    @Test
    fun `onFilterTapped toggles custom chip state`() = runTest {
        backgroundScope.launch(testDispatcher) {
            viewModel.chipCategoriesFlow.collect()
        }
        advanceUntilIdle()

        viewModel.onFilterTapped("custom")
        advanceUntilIdle()

        var customChip = viewModel.chipCategoriesFlow.value.first { it.chipName == "custom" }
        assertThat(customChip.isActive).isTrue()

        viewModel.onFilterTapped("custom")
        advanceUntilIdle()

        customChip = viewModel.chipCategoriesFlow.value.first { it.chipName == "custom" }
        assertThat(customChip.isActive).isFalse()
    }

    @Test
    fun `onFilterTapped toggles standard category filter`() = runTest {
        backgroundScope.launch(testDispatcher) {
            viewModel.chipCategoriesFlow.collect()
        }
        advanceUntilIdle()

        viewModel.onFilterTapped("psychedelic")
        advanceUntilIdle()

        var psychChip = viewModel.chipCategoriesFlow.value.first { it.chipName == "psychedelic" }
        assertThat(psychChip.isActive).isTrue()

        viewModel.onFilterTapped("psychedelic")
        advanceUntilIdle()

        psychChip = viewModel.chipCategoriesFlow.value.first { it.chipName == "psychedelic" }
        assertThat(psychChip.isActive).isFalse()
    }

    @Test
    fun `filteredCustomSubstancesFlow filters custom substances by search text case insensitively`() = runTest {
        customSubstancesFlow.value = listOf(
            CustomSubstance(id = 1, name = "Custom Kava", units = "mg", description = "Herbal"),
            CustomSubstance(id = 2, name = "2C-D-Fly", units = "mg", description = "Research")
        )

        backgroundScope.launch(testDispatcher) {
            viewModel.filteredCustomSubstancesFlow.collect()
        }
        advanceUntilIdle()

        assertThat(viewModel.filteredCustomSubstancesFlow.value.map { it.name })
            .containsExactly("Custom Kava", "2C-D-Fly")

        viewModel.filterSubstances("kava")
        advanceUntilIdle()

        assertThat(viewModel.filteredCustomSubstancesFlow.value.map { it.name })
            .containsExactly("Custom Kava")

        viewModel.filterSubstances("2c")
        advanceUntilIdle()

        assertThat(viewModel.filteredCustomSubstancesFlow.value.map { it.name })
            .containsExactly("2C-D-Fly")

        viewModel.filterSubstances("nonexistent")
        advanceUntilIdle()

        assertThat(viewModel.filteredCustomSubstancesFlow.value).isEmpty()
    }

    @Test
    fun `filteredSubstancesFlow queries search repository and maps to substance model`() = runTest {
        backgroundScope.launch(testDispatcher) {
            viewModel.filteredSubstancesFlow.collect()
        }
        advanceUntilIdle()

        val results = viewModel.filteredSubstancesFlow.value
        assertThat(results.map { it.name }).containsExactly("LSD", "Caffeine")

        val lsd = results.first { it.name == "LSD" }
        assertThat(lsd.commonNames).containsExactly("Acid")
        assertThat(lsd.hasSaferUse).isTrue()
        assertThat(lsd.hasInteractions).isTrue()
        assertThat(lsd.categories.map { it.name }).containsExactly("psychedelic")

        viewModel.filterSubstances("lsd")
        advanceUntilIdle()

        assertThat(viewModel.filteredSubstancesFlow.value.map { it.name }).containsExactly("LSD")
    }

    @Test
    fun `filteredSubstancesFlow reacts to category filters`() = runTest {
        backgroundScope.launch(testDispatcher) {
            viewModel.filteredSubstancesFlow.collect()
        }
        advanceUntilIdle()

        viewModel.onFilterTapped("stimulant")
        advanceUntilIdle()

        assertThat(viewModel.filteredSubstancesFlow.value.map { it.name }).containsExactly("Caffeine")
    }
}
