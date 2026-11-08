/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog (a fork of PsychonautWiki Journal).
 */

package foo.pilz.freaklog.ui.tabs.journal.addingestion.dose.customsubstance

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceProfiles
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomSubstanceChooseDoseViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val experienceRepository = mockk<ExperienceRepository>()
  private val customProfiles = mockk<CustomSubstanceProfiles>()
  private val userPreferences = mockk<UserPreferences>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    every { userPreferences.useIntranasalForRectalFlow } returns flowOf(false)
    every { customProfiles.getEffectiveRoa(any(), any(), any()) } returns null
    coEvery { experienceRepository.getCustomSubstance(any()) } returns
        CustomSubstance(
            name = "2C-B-FLY",
            units = "mg",
            description = "Custom test substance",
        )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `init loads custom substance and validates positive dose`() =
      runTest(testDispatcher) {
        val customSubstance =
            CustomSubstance(
                name = "2C-B-FLY",
                units = "mg",
                description = "Custom test substance",
            )
        coEvery { experienceRepository.getCustomSubstance("2C-B-FLY") } returns customSubstance

        val viewModel =
            CustomSubstanceChooseDoseViewModel(
                experienceRepository = experienceRepository,
                customProfiles = customProfiles,
                userPreferences = userPreferences,
                state =
                    SavedStateHandle(
                        mapOf(
                            "customSubstanceName" to "2C-B-FLY",
                            "administrationRoute" to AdministrationRoute.ORAL,
                        )
                    ),
            )
        advanceUntilIdle()

        assertEquals("2C-B-FLY", viewModel.substanceName)
        assertEquals("mg", viewModel.units)
        assertEquals(AdministrationRoute.ORAL, viewModel.administrationRoute)

        // Invalid initial dose
        assertFalse(viewModel.isValidDose)

        // Negative dose is rejected
        viewModel.onDoseTextChange("-5")
        assertFalse(viewModel.isValidDose)

        // Zero dose is rejected
        viewModel.onDoseTextChange("0")
        assertFalse(viewModel.isValidDose)

        // Valid positive dose is accepted
        viewModel.onDoseTextChange("10")
        assertTrue(viewModel.isValidDose)
        assertEquals(10.0, viewModel.dose ?: 0.0, 0.001)

        // When estimate is off, invalid deviation does not invalidate the dose
        viewModel.isEstimate = false
        viewModel.onEstimatedDoseStandardDeviationTextChange("-1")
        assertTrue(viewModel.isValidDose)

        // When estimate is on, negative standard deviation is rejected
        viewModel.isEstimate = true
        assertFalse(viewModel.isValidDose)

        // Valid deviation is accepted
        viewModel.onEstimatedDoseStandardDeviationTextChange("2")
        assertTrue(viewModel.isValidDose)

        // Impure dose calculation
        viewModel.purityText = "50"
        assertEquals("20 impure mg", viewModel.impureDoseWithUnit)

        viewModel.viewModelScope.cancel()
      }

  @Test
  fun `init decodes serialized route from SavedStateHandle without ClassCastException`() =
      runTest(testDispatcher) {
        val state =
            SavedStateHandle(
                mapOf(
                    "customSubstanceName" to "2C-B-FLY",
                    "administrationRoute" to "INSUFFLATED",
                )
            )
        val viewModel =
            CustomSubstanceChooseDoseViewModel(
                experienceRepository = experienceRepository,
                customProfiles = customProfiles,
                userPreferences = userPreferences,
                state = state,
            )
        advanceUntilIdle()

        assertEquals(AdministrationRoute.INSUFFLATED, viewModel.administrationRoute)
        viewModel.viewModelScope.cancel()
      }
}
