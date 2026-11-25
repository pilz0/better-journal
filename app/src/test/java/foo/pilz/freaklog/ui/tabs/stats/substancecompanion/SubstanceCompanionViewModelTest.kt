package foo.pilz.freaklog.ui.tabs.stats.substancecompanion

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.startsWith
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceProfiles
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.room.experiences.entities.CustomCrossTolerance
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDose
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.experiences.entities.Experience
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithEverything
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithExperienceAndCustomUnit
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.Tolerance
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.testing.EntityBuilders
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class SubstanceCompanionViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val experienceRepo = mockk<ExperienceRepository>()
  private val substanceRepo = mockk<SubstanceRepository>()
  private val profilesFlow =
      MutableStateFlow<Map<String, CustomSubstanceWithEverything>>(emptyMap())
  private val customProfiles =
      mockk<CustomSubstanceProfiles> { every { byNameFlow } returns profilesFlow }
  private val viewModels = mutableListOf<SubstanceCompanionViewModel>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    every { substanceRepo.getSubstance(any()) } returns null
    every { experienceRepo.getSubstanceCompanionFlow(any()) } returns flowOf(null)
    every { experienceRepo.getSortedIngestionsWithExperienceAndCustomUnitFlow(any()) } returns
        flowOf(emptyList())
  }

  @After
  fun tearDown() {
    viewModels.forEach { it.viewModelScope.cancel() }
    Dispatchers.resetMain()
  }

  private fun createViewModel(
      substanceName: String = "MDMA",
      consumerName: String? = null,
  ): SubstanceCompanionViewModel {
    val state =
        SavedStateHandle(
            mapOf(
                "substanceName" to substanceName,
                "consumerName" to consumerName,
            )
        )
    return SubstanceCompanionViewModel(
            experienceRepo = experienceRepo,
            substanceRepo = substanceRepo,
            customProfiles = customProfiles,
            state = state,
        )
        .also { viewModels.add(it) }
  }

  @Test
  fun `missing companion falls back to default color instead of staying null`() = runTest {
    val vm = createViewModel("Mystery")
    vm.thisCompanionFlow.test {
      assertThat(awaitItem()).isEqualTo(null)
      val fallback = awaitItem()
      assertThat(fallback).isEqualTo(SubstanceCompanion("Mystery", AdaptiveColor.BLUE))
      cancelAndIgnoreRemainingEvents()
    }
  }

  private fun buildCustomStimProfile(): CustomSubstanceWithEverything =
      CustomSubstanceWithEverything(
          substance =
              CustomSubstance(
                  id = 17,
                  name = "Custom Stim",
                  units = "mg",
                  description = "",
                  toleranceFull = "3 days",
                  toleranceHalf = "5 days",
                  toleranceZero = "14 days",
              ),
          doses =
              listOf(
                  CustomRoaDose(
                      customSubstanceId = 17,
                      route = AdministrationRoute.ORAL,
                      units = "mg",
                      lightMin = 20.0,
                      commonMin = 40.0,
                      strongMin = 70.0,
                      heavyMin = 100.0,
                  ),
                  CustomRoaDose(
                      customSubstanceId = 17,
                      route = AdministrationRoute.INSUFFLATED,
                      units = "mg",
                      lightMin = 10.0,
                      commonMin = 25.0,
                      strongMin = 45.0,
                      heavyMin = 75.0,
                  ),
              ),
          crossTolerances =
              listOf(
                  CustomCrossTolerance(customSubstanceId = 17, categoryName = "stimulant"),
                  CustomCrossTolerance(customSubstanceId = 17, categoryName = "dopamine"),
              ),
      )

  private fun ingestionRow(
      id: Int,
      route: AdministrationRoute,
      experience: Experience,
  ) =
      IngestionWithExperienceAndCustomUnit(
          ingestion =
              EntityBuilders.ingestion(
                  id = id,
                  substanceName = "Custom Stim",
                  administrationRoute = route,
                  experienceId = experience.id,
              ),
          experience = experience,
          customUnit = null,
      )

  @Test
  fun `custom substance profile populates tolerance cross-tolerances info target and preferred route thresholds`() =
      runTest {
        profilesFlow.value = mapOf("Custom Stim" to buildCustomStimProfile())
        val exp1 = EntityBuilders.experience(id = 1, title = "S1")
        val exp2 = EntityBuilders.experience(id = 2, title = "S2")
        val ingestions =
            listOf(
                ingestionRow(1, AdministrationRoute.INSUFFLATED, exp1),
                ingestionRow(2, AdministrationRoute.INSUFFLATED, exp2),
                ingestionRow(3, AdministrationRoute.ORAL, exp2),
            )
        every {
          experienceRepo.getSortedIngestionsWithExperienceAndCustomUnitFlow("Custom Stim")
        } returns flowOf(ingestions)

        val vm = createViewModel("Custom Stim")
        runCurrent()

        assertThat(vm.toleranceFlow.value)
            .isEqualTo(Tolerance(full = "3 days", half = "5 days", zero = "14 days"))
        assertThat(vm.crossTolerancesFlow.value).containsExactly("stimulant", "dopamine")
        assertThat(vm.substanceInfoTargetFlow.value).isEqualTo(SubstanceInfoTarget.Custom(17))

        vm.doseThresholdsFlow.test {
          val thresholds = requireNotNull(awaitItem() ?: awaitItem())
          assertThat(thresholds.route).isEqualTo(AdministrationRoute.INSUFFLATED)
          assertThat(thresholds.lightMin).isEqualTo(10.0)
          cancelAndIgnoreRemainingEvents()
        }
      }

  @Test
  fun `doseDescription omits null or blank units cleanly`() {
    val withNullUnits =
        IngestionsBurst.IngestionAndCustomUnit(
            ingestion = EntityBuilders.ingestion(dose = 20.0, units = null),
            customUnit = null,
        )
    assertThat(withNullUnits.doseDescription).isEqualTo("20")

    val estimateWithBlankUnits =
        IngestionsBurst.IngestionAndCustomUnit(
            ingestion = EntityBuilders.ingestion(dose = 15.0, units = " ", isDoseAnEstimate = true),
            customUnit = null,
        )
    assertThat(estimateWithBlankUnits.doseDescription).isEqualTo("~15")
  }

  @Test
  fun `ingestionBursts are sorted newest first and format future-dated ingestions`() = runTest {
    val futureTime = Instant.now().plus(Duration.ofDays(3))
    val pastTime = Instant.now().minus(Duration.ofDays(5))
    val futureExp = EntityBuilders.experience(id = 10, title = "Future", sortDate = futureTime)
    val pastExp = EntityBuilders.experience(id = 11, title = "Past", sortDate = pastTime)
    val rows =
        listOf(
            IngestionWithExperienceAndCustomUnit(
                ingestion = EntityBuilders.ingestion(id = 1, experienceId = 11, time = pastTime),
                experience = pastExp,
                customUnit = null,
            ),
            IngestionWithExperienceAndCustomUnit(
                ingestion = EntityBuilders.ingestion(id = 2, experienceId = 10, time = futureTime),
                experience = futureExp,
                customUnit = null,
            ),
        )
    every { experienceRepo.getSortedIngestionsWithExperienceAndCustomUnitFlow(any()) } returns
        flowOf(rows)

    val vm = createViewModel("MDMA")
    vm.ingestionBurstsFlow.test {
      val bursts = requireNotNull(awaitItem() ?: awaitItem())
      assertThat(bursts.map { it.experience.id }).containsExactly(10, 11)
      assertThat(bursts.first().timeUntil).isNotNull()
      assertThat(bursts.first().timeUntil).startsWith("in ")
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `dosageStatsFlow and frequencyFlow start null before loading real data`() = runTest {
    val upstream = MutableSharedFlow<List<IngestionWithExperienceAndCustomUnit>>(replay = 1)
    every { experienceRepo.getSortedIngestionsWithExperienceAndCustomUnitFlow("MDMA") } returns
        upstream

    val vm = createViewModel("MDMA")
    assertThat(vm.dosageStatsFlow.value).isNull()
    assertThat(vm.frequencyFlow.value).isNull()

    val exp = EntityBuilders.experience(id = 1, title = "Weekend")
    val row =
        IngestionWithExperienceAndCustomUnit(
            ingestion =
                EntityBuilders.ingestion(
                    id = 1,
                    substanceName = "MDMA",
                    experienceId = exp.id,
                    time = Instant.now().minus(Duration.ofDays(1)),
                ),
            experience = exp,
            customUnit = null,
        )

    vm.frequencyFlow.test {
      assertThat(awaitItem()).isNull()
      upstream.emit(listOf(row))
      val freq = requireNotNull(awaitItem())
      assertThat(freq.allTime).isEqualTo(1)
      assertThat(freq.last30Days).isEqualTo(1)
      cancelAndIgnoreRemainingEvents()
    }

    vm.dosageStatsFlow.test {
      val stats = requireNotNull(awaitItem() ?: awaitItem())
      assertThat(stats.summary.totalSessions).isEqualTo(1)
      assertThat(stats.summary.totalIngestions).isEqualTo(1)
      cancelAndIgnoreRemainingEvents()
    }
  }
}
