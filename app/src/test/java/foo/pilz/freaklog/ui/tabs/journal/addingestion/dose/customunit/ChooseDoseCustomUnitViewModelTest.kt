/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog (a fork of PsychonautWiki Journal).
 */

package foo.pilz.freaklog.ui.tabs.journal.addingestion.dose.customunit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomUnit
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChooseDoseCustomUnitViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val experienceRepository = mockk<ExperienceRepository>()
    private val substanceRepository = mockk<SubstanceRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads custom unit and validates dose`() {
        val customUnit = CustomUnit(
            id = 5,
            substanceName = "LSD",
            name = "Drop",
            administrationRoute = AdministrationRoute.SUBLINGUAL,
            dose = 100.0,
            estimatedDoseStandardDeviation = null,
            isEstimate = false,
            isArchived = false,
            unit = "drop",
            unitPlural = "drops",
            originalUnit = "ug",
            note = "Dropper bottle",
        )
        coEvery { experienceRepository.getCustomUnit(5) } returns customUnit
        every { substanceRepository.getSubstance("LSD") } returns null

        val viewModel = ChooseDoseCustomUnitViewModel(
            experienceRepo = experienceRepository,
            substanceRepo = substanceRepository,
            state = SavedStateHandle(mapOf("customUnitId" to 5)),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(customUnit, viewModel.customUnit)

        // Invalid initial dose
        assertFalse(viewModel.isValidDose)

        // Negative dose is rejected
        viewModel.onDoseTextChange("-1")
        assertFalse(viewModel.isValidDose)

        // Zero dose is rejected
        viewModel.onDoseTextChange("0")
        assertFalse(viewModel.isValidDose)

        // Valid positive dose is accepted
        viewModel.onDoseTextChange("2")
        assertTrue(viewModel.isValidDose)
        assertEquals(2.0, viewModel.dose ?: 0.0, 0.001)

        // When estimate is off, invalid deviation does not invalidate the dose
        viewModel.isEstimate = false
        viewModel.onEstimatedDoseDeviationChange("-0.5")
        assertTrue(viewModel.isValidDose)

        // When estimate is on, negative deviation is rejected
        viewModel.isEstimate = true
        assertFalse(viewModel.isValidDose)

        // Valid deviation is accepted
        viewModel.onEstimatedDoseDeviationChange("0.5")
        assertTrue(viewModel.isValidDose)
        assertEquals("2±0.5 drops x 100 ug = 200±50 ug", viewModel.customUnitCalculationText)

        // Reset estimate to false for standard calculation text
        viewModel.isEstimate = false
        assertEquals("2 drops x 100 ug = 200 ug", viewModel.customUnitCalculationText)

        // Unknown dose custom unit produces null calculation text instead of concatenating "= null"
        val unknownDoseUnit = customUnit.copy(dose = null)
        coEvery { experienceRepository.getCustomUnit(6) } returns unknownDoseUnit
        val unknownDoseViewModel = ChooseDoseCustomUnitViewModel(
            experienceRepo = experienceRepository,
            substanceRepo = substanceRepository,
            state = SavedStateHandle(mapOf("customUnitId" to 6)),
        )
        testDispatcher.scheduler.advanceUntilIdle()
        unknownDoseViewModel.onDoseTextChange("2")
        assertNull(unknownDoseViewModel.customUnitCalculationText)

        unknownDoseViewModel.viewModelScope.cancel()
        viewModel.viewModelScope.cancel()
    }
}
