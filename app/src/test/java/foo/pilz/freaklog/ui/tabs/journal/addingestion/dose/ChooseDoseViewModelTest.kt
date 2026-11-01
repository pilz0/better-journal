/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog (a fork of PsychonautWiki Journal).
 */

package foo.pilz.freaklog.ui.tabs.journal.addingestion.dose

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.Substance
import foo.pilz.freaklog.data.substances.classes.roa.Roa
import foo.pilz.freaklog.data.substances.classes.roa.RoaDose
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChooseDoseViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val substanceRepository = mockk<SubstanceRepository>()
    private val experienceRepository = mockk<ExperienceRepository>()
    private val userPreferences = mockk<UserPreferences>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { userPreferences.useIntranasalForRectalFlow } returns flowOf(false)
        coEvery { experienceRepository.getIntakeLimitsForSubstance(any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init with known substance populates substance details and calculates purity`() {
        val oralDose = RoaDose(
            units = "mg",
            lightMin = 5.0,
            commonMin = 10.0,
            strongMin = 20.0,
            heavyMin = 30.0,
        )
        val roa = Roa(
            route = AdministrationRoute.ORAL,
            roaDose = oralDose,
            roaDuration = null,
            bioavailability = null,
        )
        val knownSubstance = mockk<Substance> {
            every { name } returns "Ketamine"
            every { dosageRemark } returns "Test remark"
            every { getEffectiveRoa(AdministrationRoute.ORAL, any()) } returns roa
            every { getRoa(AdministrationRoute.ORAL) } returns roa
        }
        every { substanceRepository.getSubstance("Ketamine") } returns knownSubstance

        val viewModel = ChooseDoseViewModel(
            repository = substanceRepository,
            experienceRepo = experienceRepository,
            userPreferences = userPreferences,
            state = SavedStateHandle(
                mapOf(
                    "substanceName" to "Ketamine",
                    "administrationRoute" to AdministrationRoute.ORAL,
                )
            ),
        )

        assertNotNull(viewModel.substance)
        assertEquals("Ketamine", viewModel.substanceName)
        assertEquals("Test remark", viewModel.doseRemark)
        assertEquals(AdministrationRoute.ORAL, viewModel.administrationRoute)
        assertEquals(oralDose, viewModel.roaDose)

        // Dose validation
        assertFalse(viewModel.isValidDose)
        viewModel.onDoseTextChange("15")
        assertTrue(viewModel.isValidDose)
        assertEquals(15.0, viewModel.dose ?: 0.0, 0.001)

        // Impure dose calculation
        viewModel.purityText = "75"
        assertEquals("20 impure mg", viewModel.impureDoseWithUnit)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `init with unknown or custom substance does not crash and handles null gracefully`() {
        every { substanceRepository.getSubstance("2C-B-FLY") } returns null

        val viewModel = ChooseDoseViewModel(
            repository = substanceRepository,
            experienceRepo = experienceRepository,
            userPreferences = userPreferences,
            state = SavedStateHandle(
                mapOf(
                    "substanceName" to "2C-B-FLY",
                    "administrationRoute" to AdministrationRoute.ORAL,
                )
            ),
        )

        assertNull(viewModel.substance)
        assertEquals("2C-B-FLY", viewModel.substanceName)
        assertNull(viewModel.doseRemark)
        assertNull(viewModel.roaDose)
        assertNull(viewModel.roaDuration)

        // User can still type doses
        viewModel.onDoseTextChange("10")
        assertTrue(viewModel.isValidDose)
        assertEquals(10.0, viewModel.dose ?: 0.0, 0.001)

        // Impure dose without unit or with fallback unit
        viewModel.purityText = "50"
        assertEquals("20 impure", viewModel.impureDoseWithUnit)
        viewModel.units = "mg"
        assertEquals("20 impure mg", viewModel.impureDoseWithUnit)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `useIntranasalForRectal substitutes intranasal dose for rectal administration`() {
        val sniffDose = RoaDose(
            units = "mg",
            lightMin = 10.0,
            commonMin = 20.0,
            strongMin = 40.0,
            heavyMin = 60.0,
        )
        val insufflatedRoa = Roa(
            route = AdministrationRoute.INSUFFLATED,
            roaDose = sniffDose,
            roaDuration = null,
            bioavailability = null,
        )
        val substance = mockk<Substance> {
            every { name } returns "Ketamine"
            every { dosageRemark } returns null
            every { getEffectiveRoa(AdministrationRoute.RECTAL, true) } returns insufflatedRoa
            every { getRoa(AdministrationRoute.RECTAL) } returns null
        }
        every { substanceRepository.getSubstance("Ketamine") } returns substance
        every { userPreferences.useIntranasalForRectalFlow } returns flowOf(true)

        val viewModel = ChooseDoseViewModel(
            repository = substanceRepository,
            experienceRepo = experienceRepository,
            userPreferences = userPreferences,
            state = SavedStateHandle(
                mapOf(
                    "substanceName" to "Ketamine",
                    "administrationRoute" to AdministrationRoute.RECTAL,
                )
            ),
        )

        assertEquals(AdministrationRoute.RECTAL, viewModel.administrationRoute)
        assertEquals(sniffDose, viewModel.roaDose)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `init decodes serialized ChooseDoseRoute from SavedStateHandle`() {
        val insufflatedDose = RoaDose(
            units = "mg",
            lightMin = 10.0,
            commonMin = 30.0,
            strongMin = 60.0,
            heavyMin = 100.0,
        )
        val insufflatedRoa = Roa(
            route = AdministrationRoute.INSUFFLATED,
            roaDose = insufflatedDose,
            roaDuration = null,
            bioavailability = null,
        )
        val substance = mockk<Substance> {
            every { name } returns "Ketamine"
            every { dosageRemark } returns null
            every { getEffectiveRoa(AdministrationRoute.INSUFFLATED, any()) } returns insufflatedRoa
            every { getRoa(AdministrationRoute.INSUFFLATED) } returns insufflatedRoa
        }
        every { substanceRepository.getSubstance("Ketamine") } returns substance

        // Navigation encodes enum arguments as String in the SavedStateHandle bundle.
        // This ensures the type-safe toRoute<ChooseDoseRoute>() decoding path is verified,
        // rather than the flat get<AdministrationRoute> fallback.
        val state = SavedStateHandle(
            mapOf(
                "substanceName" to "Ketamine",
                "administrationRoute" to AdministrationRoute.INSUFFLATED.name,
            )
        )
        val viewModel = ChooseDoseViewModel(
            repository = substanceRepository,
            experienceRepo = experienceRepository,
            userPreferences = userPreferences,
            state = state,
        )

        assertEquals("Ketamine", viewModel.substanceName)
        assertEquals(AdministrationRoute.INSUFFLATED, viewModel.administrationRoute)
        assertEquals(insufflatedDose, viewModel.roaDose)
        viewModel.viewModelScope.cancel()
    }
}
