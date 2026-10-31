/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog (a fork of PsychonautWiki Journal).
 */

package foo.pilz.freaklog.ui.tabs.journal.experience

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceProfiles
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDuration
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.experiences.entities.Experience
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithEverything
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.DurationUnits
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.ui.tabs.journal.addingestion.interactions.InteractionChecker
import foo.pilz.freaklog.ui.tabs.journal.experience.components.SavedTimeDisplayOption
import foo.pilz.freaklog.ui.tabs.journal.experience.teamsskin.TeamsSkinController
import foo.pilz.freaklog.ui.tabs.settings.combinations.BloodPressureDisplay
import foo.pilz.freaklog.ui.tabs.settings.combinations.CombinationSettingsStorage
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import foo.pilz.freaklog.ui.tabs.settings.funny.FunnyConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ExperienceViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val experienceRepo = mockk<ExperienceRepository>()
    private val substanceRepo = mockk<SubstanceRepository>()
    private val interactionChecker = mockk<InteractionChecker>()
    private val userPreferences = mockk<UserPreferences>()
    private val customProfiles = mockk<CustomSubstanceProfiles>()
    private val teamsSkinController = mockk<TeamsSkinController>(relaxed = true)
    private val combinationSettingsStorage = mockk<CombinationSettingsStorage>()
    private val context = mockk<Context>(relaxed = true)

    private val customProfilesFlow = MutableStateFlow<Map<String, CustomSubstanceWithEverything>>(emptyMap())
    private val useIntranasalForRectalFlow = MutableStateFlow(false)
    private val now = Instant.parse("2026-01-01T12:00:00Z")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { userPreferences.areSubstanceHeightsIndependentFlow } returns flowOf(false)
        every { userPreferences.isTimelineHiddenFlow } returns flowOf(false)
        every { userPreferences.aiAssistantEnabledFlow } returns flowOf(false)
        every { userPreferences.isOralDisclaimerHiddenFlow } returns flowOf(true)
        every { userPreferences.areDosageDotsHiddenFlow } returns flowOf(false)
        every { userPreferences.savedTimeDisplayOptionFlow } returns flowOf(SavedTimeDisplayOption.REGULAR)
        every { userPreferences.useIntranasalForRectalFlow } returns useIntranasalForRectalFlow
        every { userPreferences.activeNotificationExperienceIdFlow } returns flowOf(null)
        every { userPreferences.areSleepStagesEnabledFlow } returns flowOf(false)
        every { userPreferences.isVitalsEnabledFlow } returns flowOf(false)
        every { userPreferences.bloodPressureDisplayFlow } returns flowOf(BloodPressureDisplay.COMBINED)
        every { userPreferences.funnyConfigFlow } returns flowOf(FunnyConfig())

        every { customProfiles.byNameFlow } returns customProfilesFlow
        every { combinationSettingsStorage.enabledInteractionsFlow } returns flowOf(emptySet())
        every { interactionChecker.getInteractionsAmong(any()) } returns emptyList()
        every { substanceRepo.getSubstance(any()) } returns null
        every { experienceRepo.getBloodPressureReadingsFlow(any()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `custom substance profile resolves roaDuration in flows`() = runTest(testDispatcher) {
        val experience = Experience(
            id = 42,
            title = "Testing Custom Curves",
            text = "",
            sortDate = now,
            location = null,
        )
        val ingestion = Ingestion(
            id = 1,
            experienceId = 42,
            substanceName = "2C-B-FLY",
            administrationRoute = AdministrationRoute.ORAL,
            dose = 10.0,
            units = "mg",
            time = now,
            isDoseAnEstimate = false,
            estimatedDoseStandardDeviation = null,
            notes = null,
            stomachFullness = null,
            consumerName = null,
            customUnitId = null,
        )
        val ingestionWithData = IngestionWithCompanionAndCustomUnit(
            ingestion = ingestion,
            substanceCompanion = null,
            customUnit = null,
        )

        coEvery { experienceRepo.getExperience(any()) } returns experience
        every { experienceRepo.getExperienceFlow(any()) } returns flowOf(experience)
        every { experienceRepo.getIngestionsWithCompanionsFlow(any()) } returns flowOf(listOf(ingestionWithData))
        every { experienceRepo.getRatingsFlow(any()) } returns flowOf(emptyList())
        every { experienceRepo.getTimedNotesFlowSorted(any()) } returns flowOf(emptyList())

        val customDuration = CustomRoaDuration(
            route = AdministrationRoute.ORAL,
            onset = DurationRange(30f, 45f, DurationUnits.MINUTES),
            comeup = DurationRange(45f, 60f, DurationUnits.MINUTES),
            peak = DurationRange(2f, 3f, DurationUnits.HOURS),
            offset = DurationRange(2f, 4f, DurationUnits.HOURS),
            total = null,
        )
        val customProfile = CustomSubstanceWithEverything(
            substance = CustomSubstance(name = "2C-B-FLY", units = "mg", description = ""),
            durations = listOf(customDuration),
        )
        customProfilesFlow.value = mapOf("2c-b-fly" to customProfile)

        val viewModel = ExperienceViewModel(
            experienceRepo = experienceRepo,
            substanceRepo = substanceRepo,
            interactionChecker = interactionChecker,
            userPreferences = userPreferences,
            customProfiles = customProfiles,
            teamsSkinController = teamsSkinController,
            combinationSettingsStorage = combinationSettingsStorage,
            state = SavedStateHandle(mapOf("experienceId" to 42)),
            context = context,
        )

        val elements = viewModel.ingestionElementsFlow.first { it.isNotEmpty() }
        assertEquals(1, elements.size)
        val element = elements.first()
        assertEquals("2C-B-FLY", element.ingestionWithCompanionAndCustomUnit.ingestion.substanceName)
        assertNotNull("roaDuration must be resolved from custom substance profile", element.roaDuration)
        assertEquals(customDuration.toRoaDuration(), element.roaDuration)

        val effectLines = viewModel.dataForEffectTimelinesFlow.first { it.isNotEmpty() }
        assertEquals(1, effectLines.size)
        val line = effectLines.first()
        assertEquals("2C-B-FLY", line.substanceName)
        assertEquals(element.roaDuration, line.roaDuration)
        assertTrue("Height should be positive", line.height > 0f)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `useIntranasalForRectal substitutes insufflated roaDuration for rectal ingestion`() = runTest(testDispatcher) {
        val experience = Experience(
            id = 42,
            title = "Testing Rectal Substitution",
            text = "",
            sortDate = now,
            location = null,
        )
        val ingestion = Ingestion(
            id = 1,
            experienceId = 42,
            substanceName = "MysteryPowder",
            administrationRoute = AdministrationRoute.RECTAL,
            dose = 20.0,
            units = "mg",
            time = now,
            isDoseAnEstimate = false,
            estimatedDoseStandardDeviation = null,
            notes = null,
            stomachFullness = null,
            consumerName = null,
            customUnitId = null,
        )
        val ingestionWithData = IngestionWithCompanionAndCustomUnit(
            ingestion = ingestion,
            substanceCompanion = null,
            customUnit = null,
        )

        coEvery { experienceRepo.getExperience(any()) } returns experience
        every { experienceRepo.getExperienceFlow(any()) } returns flowOf(experience)
        every { experienceRepo.getIngestionsWithCompanionsFlow(any()) } returns flowOf(listOf(ingestionWithData))
        every { experienceRepo.getRatingsFlow(any()) } returns flowOf(emptyList())
        every { experienceRepo.getTimedNotesFlowSorted(any()) } returns flowOf(emptyList())

        val insufflatedDuration = CustomRoaDuration(
            route = AdministrationRoute.INSUFFLATED,
            onset = DurationRange(5f, 10f, DurationUnits.MINUTES),
            comeup = DurationRange(10f, 15f, DurationUnits.MINUTES),
            peak = DurationRange(1f, 1.5f, DurationUnits.HOURS),
            offset = DurationRange(1f, 2f, DurationUnits.HOURS),
            total = null,
        )
        val customProfile = CustomSubstanceWithEverything(
            substance = CustomSubstance(name = "MysteryPowder", units = "mg", description = ""),
            durations = listOf(insufflatedDuration),
        )
        customProfilesFlow.value = mapOf("mysterypowder" to customProfile)
        useIntranasalForRectalFlow.value = true

        val viewModel = ExperienceViewModel(
            experienceRepo = experienceRepo,
            substanceRepo = substanceRepo,
            interactionChecker = interactionChecker,
            userPreferences = userPreferences,
            customProfiles = customProfiles,
            teamsSkinController = teamsSkinController,
            combinationSettingsStorage = combinationSettingsStorage,
            state = SavedStateHandle(mapOf("experienceId" to 42)),
            context = context,
        )

        val elements = viewModel.ingestionElementsFlow.first { it.isNotEmpty() }
        assertEquals(1, elements.size)
        val element = elements.first()
        // Logged route is preserved as RECTAL
        assertEquals(
            AdministrationRoute.RECTAL,
            element.ingestionWithCompanionAndCustomUnit.ingestion.administrationRoute,
        )
        // But roaDuration is substituted from insufflated data
        assertNotNull("roaDuration should be substituted from insufflated", element.roaDuration)
        assertEquals(insufflatedDuration.toRoaDuration(), element.roaDuration)

        val effectLines = viewModel.dataForEffectTimelinesFlow.first { it.isNotEmpty() }
        assertEquals(1, effectLines.size)
        assertEquals(insufflatedDuration.toRoaDuration(), effectLines.first().roaDuration)
        viewModel.viewModelScope.cancel()
    }
}
