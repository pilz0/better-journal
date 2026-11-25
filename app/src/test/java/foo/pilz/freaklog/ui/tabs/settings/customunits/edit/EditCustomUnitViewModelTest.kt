package foo.pilz.freaklog.ui.tabs.settings.customunits.edit

import androidx.lifecycle.SavedStateHandle
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomUnit
import foo.pilz.freaklog.data.room.experiences.relations.CustomUnitWithIngestions
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class EditCustomUnitViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun sampleCustomUnit(
      dose: Double? = 125.5,
      stdDev: Double? = 12.25,
  ) =
      CustomUnit(
          id = 7,
          substanceName = "MDMA",
          name = "pill",
          administrationRoute = AdministrationRoute.ORAL,
          dose = dose,
          estimatedDoseStandardDeviation = stdDev,
          isEstimate = true,
          isArchived = false,
          unit = "pill",
          unitPlural = "pills",
          originalUnit = "mg",
          note = "initial",
      )

  @Test
  fun `init preserves decimal digits in doseText and standard deviation`() = runTest {
    val experienceRepo =
        mockk<ExperienceRepository>(relaxed = true) {
          coEvery { getCustomUnitWithIngestions(7) } returns
              CustomUnitWithIngestions(sampleCustomUnit(dose = 125.5, stdDev = 12.25), emptyList())
        }
    val substanceRepo =
        mockk<SubstanceRepository> {
          every { getSubstance("MDMA") } returns null
        }

    val viewModel =
        EditCustomUnitViewModel(
            experienceRepo = experienceRepo,
            substanceRepository = substanceRepo,
            state = SavedStateHandle(mapOf("customUnitId" to 7)),
        )
    advanceUntilIdle()

    assertEquals("125.5", viewModel.doseText)
    assertEquals("12.25", viewModel.estimatedDoseDeviationText)
  }

  @Test
  fun `init strips trailing dot zero on whole doses`() = runTest {
    val experienceRepo =
        mockk<ExperienceRepository>(relaxed = true) {
          coEvery { getCustomUnitWithIngestions(7) } returns
              CustomUnitWithIngestions(sampleCustomUnit(dose = 100.0, stdDev = 10.0), emptyList())
        }
    val substanceRepo = mockk<SubstanceRepository> { every { getSubstance("MDMA") } returns null }

    val viewModel =
        EditCustomUnitViewModel(
            experienceRepo = experienceRepo,
            substanceRepository = substanceRepo,
            state = SavedStateHandle(mapOf("customUnitId" to 7)),
        )
    advanceUntilIdle()

    assertEquals("100", viewModel.doseText)
    assertEquals("10", viewModel.estimatedDoseDeviationText)
  }

  @Test
  fun `updateAndDismissAfter guards against double tap and awaits update`() = runTest {
    val updateDeferred = CompletableDeferred<Unit>()
    val experienceRepo =
        mockk<ExperienceRepository>(relaxed = true) {
          coEvery { getCustomUnitWithIngestions(7) } returns
              CustomUnitWithIngestions(sampleCustomUnit(), emptyList())
          coEvery { update(any<CustomUnit>()) } coAnswers { updateDeferred.await() }
        }
    val substanceRepo = mockk<SubstanceRepository> { every { getSubstance("MDMA") } returns null }

    val viewModel =
        EditCustomUnitViewModel(
            experienceRepo = experienceRepo,
            substanceRepository = substanceRepo,
            state = SavedStateHandle(mapOf("customUnitId" to 7)),
        )
    advanceUntilIdle()

    viewModel.onChangeOfDose("150.25")
    var dismissCount = 0
    viewModel.updateAndDismissAfter { dismissCount++ }
    viewModel.updateAndDismissAfter { dismissCount++ }
    advanceUntilIdle()

    assertEquals(0, dismissCount)
    updateDeferred.complete(Unit)
    advanceUntilIdle()

    assertEquals(1, dismissCount)
    coVerify(exactly = 1) { experienceRepo.update(match<CustomUnit> { it.dose == 150.25 }) }
  }

  @Test
  fun `deleteCustomUnit awaits deletion and guards duplicate calls`() = runTest {
    val deleteDeferred = CompletableDeferred<Unit>()
    val experienceRepo =
        mockk<ExperienceRepository>(relaxed = true) {
          coEvery { getCustomUnitWithIngestions(7) } returns
              CustomUnitWithIngestions(sampleCustomUnit(), emptyList())
          coEvery { delete(any<CustomUnit>()) } coAnswers { deleteDeferred.await() }
        }
    val substanceRepo = mockk<SubstanceRepository> { every { getSubstance("MDMA") } returns null }

    val viewModel =
        EditCustomUnitViewModel(
            experienceRepo = experienceRepo,
            substanceRepository = substanceRepo,
            state = SavedStateHandle(mapOf("customUnitId" to 7)),
        )
    advanceUntilIdle()

    var dismissCount = 0
    viewModel.deleteCustomUnit { dismissCount++ }
    viewModel.deleteCustomUnit { dismissCount++ }
    advanceUntilIdle()

    assertEquals(0, dismissCount)
    deleteDeferred.complete(Unit)
    advanceUntilIdle()

    assertEquals(1, dismissCount)
    coVerify(exactly = 1) { experienceRepo.delete(any<CustomUnit>()) }
  }
}
