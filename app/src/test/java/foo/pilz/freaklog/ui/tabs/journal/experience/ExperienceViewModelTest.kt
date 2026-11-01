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
import foo.pilz.freaklog.data.room.experiences.entities.CustomUnit
import foo.pilz.freaklog.data.room.experiences.entities.Experience
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithEverything
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.DurationUnits
import foo.pilz.freaklog.data.substances.classes.roa.RoaDose
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.ui.tabs.journal.addingestion.interactions.InteractionChecker
import foo.pilz.freaklog.ui.tabs.journal.experience.components.SavedTimeDisplayOption
import foo.pilz.freaklog.ui.tabs.journal.experience.models.IngestionElement
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
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

    @Suppress("LongParameterList")
    private fun ingestion(
        dose: Double? = 10.0,
        units: String? = "mg",
        deviation: Double? = null,
        isEstimate: Boolean = deviation != null,
        route: AdministrationRoute = AdministrationRoute.ORAL,
        substanceName: String = "Test substance",
        customUnit: CustomUnit? = null,
        time: Instant = now,
    ) = IngestionWithAssociatedData(
        ingestionWithCompanionAndCustomUnit = IngestionWithCompanionAndCustomUnit(
            ingestion = Ingestion(
                id = 1,
                experienceId = 42,
                substanceName = substanceName,
                administrationRoute = route,
                dose = dose,
                units = units,
                time = time,
                isDoseAnEstimate = isEstimate,
                estimatedDoseStandardDeviation = deviation,
                notes = null,
                stomachFullness = null,
                consumerName = null,
                customUnitId = customUnit?.id,
            ),
            substanceCompanion = null,
            customUnit = customUnit,
        ),
        roaDuration = null,
        roaDose = null,
    )

    private fun customUnit(deviation: Double? = null) = CustomUnit(
        id = 42,
        substanceName = "Test substance",
        name = "Drop",
        creationDate = Instant.EPOCH,
        administrationRoute = AdministrationRoute.ORAL,
        dose = 10.0,
        estimatedDoseStandardDeviation = deviation,
        isEstimate = deviation != null,
        isArchived = false,
        unit = "drop",
        originalUnit = "mg",
        note = "",
    )

    @Test
    fun `cumulative doses omit empty inputs and substances without repeated routes`() {
        assertTrue(ExperienceViewModel.getCumulativeDoses(emptyList()).isEmpty())
        assertTrue(ExperienceViewModel.getCumulativeDoses(listOf(ingestion())).isEmpty())
        val mixed = listOf(
            ingestion(),
            ingestion(route = AdministrationRoute.INSUFFLATED),
            ingestion(substanceName = "Other substance"),
        )
        assertTrue(ExperienceViewModel.getCumulativeDoses(mixed).isEmpty())
    }

    @Test
    fun `cumulative doses sum exact doses without inventing uncertainty`() {
        val ingestions = listOf(
            ingestion(dose = 0.0),
            ingestion(dose = 12.5),
            ingestion(dose = 7.5),
        )
        val cumulative = ExperienceViewModel.getCumulativeDoses(ingestions).single()
        val route = cumulative.cumulativeRouteAndDose.single()

        assertEquals("Test substance", cumulative.substanceName)
        assertEquals(20.0, route.cumulativeDose, 1e-9)
        assertEquals("mg", route.units)
        assertEquals(AdministrationRoute.ORAL, route.route)
        assertTrue(route.hasMoreThanOneIngestion)
        assertFalse(route.isEstimate)
        assertNull(route.cumulativeDoseStandardDeviation)
        assertNull(route.numDots)
    }

    @Test
    fun `cumulative dose uncertainty uses root sum square rather than sum or mean`() {
        val ingestions = listOf(
            ingestion(dose = 10.0, deviation = 2.0),
            ingestion(dose = 20.0, deviation = 3.0),
            ingestion(dose = 30.0, deviation = 6.0),
            ingestion(dose = 40.0),
        )
        val route = ExperienceViewModel.getCumulativeDoses(ingestions).single().cumulativeRouteAndDose.single()

        assertEquals(100.0, route.cumulativeDose, 1e-9)
        assertEquals(7.0, requireNotNull(route.cumulativeDoseStandardDeviation), 1e-9)
        assertTrue(route.isEstimate)
    }

    @Test
    fun `cumulative doses ignore zero negative and nonfinite deviations`() {
        val ingestions = listOf(
            ingestion(deviation = 3.0),
            ingestion(deviation = 4.0),
            ingestion(deviation = 0.0),
            ingestion(deviation = -12.0),
            ingestion(deviation = Double.NaN),
            ingestion(deviation = Double.POSITIVE_INFINITY),
            ingestion(deviation = Double.NEGATIVE_INFINITY),
        )
        val route = ExperienceViewModel.getCumulativeDoses(ingestions).single().cumulativeRouteAndDose.single()

        assertEquals(70.0, route.cumulativeDose, 1e-9)
        assertEquals(5.0, requireNotNull(route.cumulativeDoseStandardDeviation), 1e-9)
    }

    @Test
    fun `cumulative doses keep estimate flag when all deviations are absent or invalid`() {
        val ingestions = listOf(
            ingestion(isEstimate = true),
            ingestion(deviation = 0.0),
            ingestion(deviation = -1.0),
            ingestion(deviation = Double.NaN),
        )
        val route = ExperienceViewModel.getCumulativeDoses(ingestions).single().cumulativeRouteAndDose.single()

        assertTrue(route.isEstimate)
        assertNull(route.cumulativeDoseStandardDeviation)
    }

    @Test
    fun `cumulative doses exclude negative and nonfinite amounts from sum`() {
        val ingestions = listOf(
            ingestion(dose = 12.5),
            ingestion(dose = 0.0),
            ingestion(dose = -10.0),
            ingestion(dose = Double.NaN),
            ingestion(dose = Double.POSITIVE_INFINITY),
            ingestion(dose = Double.NEGATIVE_INFINITY),
        )
        val route = ExperienceViewModel.getCumulativeDoses(ingestions).single().cumulativeRouteAndDose.single()

        assertEquals(12.5, route.cumulativeDose, 1e-9)
        assertTrue(route.hasMoreThanOneIngestion)
    }

    @Test
    fun `cumulative doses omit a route containing unknown dose or units in either order`() {
        val known = ingestion()
        for (unknown in listOf(ingestion(dose = null), ingestion(units = null), ingestion(units = "ml"))) {
            assertTrue(ExperienceViewModel.getCumulativeDoses(listOf(known, unknown)).isEmpty())
            assertTrue(ExperienceViewModel.getCumulativeDoses(listOf(unknown, known)).isEmpty())
        }
    }

    @Test
    fun `cumulative doses normalize equivalent microgram units`() {
        val ingestions = listOf(
            ingestion(dose = 10.0, units = "ug"),
            ingestion(dose = 20.0, units = " μg "),
            ingestion(dose = 30.0, units = "MCG"),
        )
        val route = ExperienceViewModel.getCumulativeDoses(ingestions).single().cumulativeRouteAndDose.single()

        assertEquals(60.0, route.cumulativeDose, 1e-9)
        assertEquals("ug", route.units)
    }

    @Test
    fun `cumulative doses isolate substances routes and invalid route groups`() {
        val ingestions = listOf(
            ingestion(dose = 10.0, deviation = 3.0),
            ingestion(dose = 20.0, deviation = 4.0),
            ingestion(dose = 7.0, route = AdministrationRoute.INSUFFLATED),
            ingestion(dose = null, route = AdministrationRoute.RECTAL),
            ingestion(dose = 10.0, route = AdministrationRoute.RECTAL),
            ingestion(dose = 40.0, substanceName = "Other substance"),
            ingestion(dose = 50.0, substanceName = "Other substance"),
        )
        val results = ExperienceViewModel.getCumulativeDoses(ingestions).associateBy { it.substanceName }

        assertEquals(2, results.size)
        val routes = results.getValue("Test substance").cumulativeRouteAndDose.associateBy { it.route }
        assertEquals(setOf(AdministrationRoute.ORAL, AdministrationRoute.INSUFFLATED), routes.keys)
        assertEquals(30.0, routes.getValue(AdministrationRoute.ORAL).cumulativeDose, 1e-9)
        assertEquals(5.0, requireNotNull(routes.getValue(AdministrationRoute.ORAL).cumulativeDoseStandardDeviation), 1e-9)
        assertEquals(7.0, routes.getValue(AdministrationRoute.INSUFFLATED).cumulativeDose, 1e-9)
        assertFalse(routes.getValue(AdministrationRoute.INSUFFLATED).hasMoreThanOneIngestion)
        assertEquals(90.0, results.getValue("Other substance").cumulativeRouteAndDose.single().cumulativeDose, 1e-9)
    }

    @Test
    fun `cumulative doses convert fractional custom units before adding base doses and dots`() {
        val doseGuide = RoaDose("mg", 5.0, 10.0, 20.0, 30.0)
        val ingestions = listOf(
            ingestion(dose = 0.5, units = "drop", customUnit = customUnit()).copy(roaDose = doseGuide),
            ingestion(dose = 1.5, units = "drops", customUnit = customUnit()),
            ingestion(dose = 5.0),
        )
        val route = ExperienceViewModel.getCumulativeDoses(ingestions).single().cumulativeRouteAndDose.single()

        assertEquals(25.0, route.cumulativeDose, 1e-9)
        assertEquals("mg", route.units)
        assertEquals(3, route.numDots)
        assertFalse(route.isEstimate)
        assertNull(route.cumulativeDoseStandardDeviation)
    }

    @Test
    fun `cumulative doses scale quantity uncertainty for exact custom units`() {
        val ingestions = listOf(
            ingestion(dose = 2.0, units = "drops", deviation = 0.3, customUnit = customUnit()),
            ingestion(dose = 3.0, units = "drops", deviation = 0.4, customUnit = customUnit()),
        )
        val route = ExperienceViewModel.getCumulativeDoses(ingestions).single().cumulativeRouteAndDose.single()

        assertEquals(50.0, route.cumulativeDose, 1e-9)
        assertEquals(5.0, requireNotNull(route.cumulativeDoseStandardDeviation), 1e-9)
        assertTrue(route.isEstimate)
    }

    @Test
    fun `cumulative doses combine converted product variances per ingestion`() {
        val unit = customUnit(deviation = 2.0)
        val ingestions = listOf(
            ingestion(dose = 2.0, units = "drops", deviation = 0.3, customUnit = unit),
            ingestion(dose = 3.0, units = "drops", deviation = 0.4, customUnit = unit),
            ingestion(dose = 5.0, deviation = 4.0),
        )
        val route = ExperienceViewModel.getCumulativeDoses(ingestions).single().cumulativeRouteAndDose.single()

        // Per ingestion product variances are 25.36 and 52.64; base dose adds 16.
        assertEquals(55.0, route.cumulativeDose, 1e-9)
        assertEquals(sqrt(94.0), requireNotNull(route.cumulativeDoseStandardDeviation), 1e-9)
        assertTrue(route.isEstimate)
    }

    @Test
    fun `custom unit estimate alone marks cumulative dose as estimated`() {
        val unit = customUnit(deviation = 1.0)
        val ingestions = listOf(
            ingestion(dose = 3.0, units = "drops", customUnit = unit),
            ingestion(dose = 4.0, units = "drops", customUnit = unit),
        )
        val route = ExperienceViewModel.getCumulativeDoses(ingestions).single().cumulativeRouteAndDose.single()

        assertEquals(70.0, route.cumulativeDose, 1e-9)
        assertEquals(5.0, requireNotNull(route.cumulativeDoseStandardDeviation), 1e-9)
        assertTrue(route.isEstimate)
    }

    @Test
    fun `cumulative doses do not depend on negative epoch times or input order`() {
        val ingestions = listOf(
            ingestion(dose = 10.0, deviation = 3.0, time = Instant.ofEpochSecond(-3600)),
            ingestion(dose = 20.0, deviation = 4.0, time = Instant.ofEpochSecond(-1)),
        )
        val result = ExperienceViewModel.getCumulativeDoses(ingestions)
        val route = result.single().cumulativeRouteAndDose.single()

        assertEquals(30.0, route.cumulativeDose, 1e-9)
        assertEquals(5.0, requireNotNull(route.cumulativeDoseStandardDeviation), 1e-9)
        assertEquals(result, ExperienceViewModel.getCumulativeDoses(ingestions.reversed()))
    }

    @Test
    fun `effect timelines preserve negative timestamps and end times`() {
        val start = Instant.ofEpochSecond(-3600)
        val end = Instant.ofEpochSecond(-1800)
        val data = ingestion(time = start).ingestionWithCompanionAndCustomUnit
        val element = IngestionElement(
            ingestionWithCompanionAndCustomUnit = data.copy(ingestion = data.ingestion.copy(endTime = end)),
            roaDuration = null,
            numDots = null,
        )
        val line = ExperienceViewModel.getDataForEffectTimelines(listOf(element), emptyList()).single()

        assertEquals(start, line.startTime)
        assertEquals(end, line.endTime)
        assertEquals(1f, line.height, 0f)
        assertEquals(0.5f, line.horizontalWeight, 0f)
    }

    @Test
    fun `ingestion elements sort negative timestamps chronologically and exclude other consumers`() = runTest(testDispatcher) {
        val times = listOf(Instant.ofEpochSecond(-1), Instant.ofEpochSecond(-3600), Instant.EPOCH)
        val mine = times.map { ingestion(time = it).ingestionWithCompanionAndCustomUnit }
        val otherIngestion = mine.first().ingestion.copy(
            time = Instant.ofEpochSecond(-7200),
            consumerName = "Someone else",
        )
        val other = mine.first().copy(ingestion = otherIngestion)
        val experience = Experience(id = 42, title = "Before epoch", text = "", sortDate = times.min(), location = null)
        coEvery { experienceRepo.getExperience(42) } returns experience
        every { experienceRepo.getExperienceFlow(42) } returns flowOf(experience)
        every { experienceRepo.getIngestionsWithCompanionsFlow(42) } returns flowOf(mine + other)
        every { experienceRepo.getRatingsFlow(42) } returns flowOf(emptyList())
        every { experienceRepo.getTimedNotesFlowSorted(42) } returns flowOf(emptyList())

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
        try {
            val elements = viewModel.ingestionElementsFlow.first { it.isNotEmpty() }
            assertEquals(times.sorted(), elements.map { it.ingestionWithCompanionAndCustomUnit.ingestion.time })
            assertTrue(elements.all { it.ingestionWithCompanionAndCustomUnit.ingestion.consumerName == null })
            val lines = viewModel.dataForEffectTimelinesFlow.first { it.size == 3 }
            assertEquals(times.sorted(), lines.map { it.startTime })
        } finally {
            viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
        }
    }

    @Test
    fun `cumulative doses reject incompatible custom base units even with identical display units`() {
        val incompatible = listOf(
            ingestion(dose = 1.0, units = "drop", customUnit = customUnit()),
            ingestion(dose = 1.0, units = "drop", customUnit = customUnit().copy(id = 43, originalUnit = "ml")),
        )
        assertTrue(ExperienceViewModel.getCumulativeDoses(incompatible).isEmpty())
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
        viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
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
        viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
    }

    @Test
    fun `custom substance with rectal route leaves duration null when substitution disabled`() = runTest {
        val experience = Experience(
            id = 42,
            title = "Test Trip",
            sortDate = Instant.now(),
            text = "Notes",
            location = null,
        )
        val ingestion = Ingestion(
            id = 1,
            experienceId = 42,
            substanceName = "MysteryPowder",
            administrationRoute = AdministrationRoute.RECTAL,
            dose = 50.0,
            units = "mg",
            time = Instant.now(),
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
        useIntranasalForRectalFlow.value = false

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
        assertEquals(
            AdministrationRoute.RECTAL,
            element.ingestionWithCompanionAndCustomUnit.ingestion.administrationRoute,
        )
        val effectLines = viewModel.dataForEffectTimelinesFlow.first { it.isNotEmpty() }
        assertEquals(1, effectLines.size)
        assertNull("effect line roaDuration should remain null when substitution is disabled", effectLines.first().roaDuration)
        viewModel.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
    }
}
