package foo.pilz.freaklog.ui.tabs.settings.intakelimits

import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceRepository
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.IntakeLimit
import foo.pilz.freaklog.data.room.experiences.entities.IntakeLimitType
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class EditIntakeLimitViewModelTest {
  private val experienceRepo = mockk<ExperienceRepository>(relaxed = true)
  private val substanceRepo = mockk<SubstanceRepository>(relaxed = true)
  private val customSubstanceRepo = mockk<CustomSubstanceRepository>(relaxed = true)
  private val testDispatcher = StandardTestDispatcher()

  private lateinit var viewModel: EditIntakeLimitViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    viewModel =
        EditIntakeLimitViewModel(
            experienceRepo = experienceRepo,
            substanceRepo = substanceRepo,
            customSubstanceRepo = customSubstanceRepo,
            state = SavedStateHandle(mapOf("limitId" to -1, "substanceName" to "Caffeine")),
        )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `custom window exceeding 10 years marks isValid as false`() {
    viewModel.windowMode = WindowMode.CUSTOM
    viewModel.customWindowUnit = CustomWindowUnit.DAYS
    viewModel.maxDoseText = "100"
    viewModel.unit = "mg"
    viewModel.limitType = IntakeLimitType.DOSE

    // 10 years in days is 3650 days. 3651 exceeds the 10-year cap.
    viewModel.customWindowValueText = "3651"
    assertThat(viewModel.isValid).isFalse()

    viewModel.customWindowValueText = "3650"
    assertThat(viewModel.isValid).isTrue()
  }

  @Test
  fun `max dose with comma is accepted as valid`() {
    viewModel.limitType = IntakeLimitType.DOSE
    viewModel.windowMode = WindowMode.DAY
    viewModel.unit = "mg"

    viewModel.maxDoseText = "0,5"
    assertThat(viewModel.isValid).isTrue()
  }

  @Test
  fun `max dose with infinity or NaN is rejected as invalid`() {
    viewModel.limitType = IntakeLimitType.DOSE
    viewModel.windowMode = WindowMode.DAY
    viewModel.unit = "mg"

    viewModel.maxDoseText = "Infinity"
    assertThat(viewModel.isValid).isFalse()

    viewModel.maxDoseText = "NaN"
    assertThat(viewModel.isValid).isFalse()
  }

  @Test
  fun `editing existing limit preserves decimals and guards double tap on save and delete`() =
      runTest {
        val existing =
            IntakeLimit(
                id = 5,
                substanceName = "MDMA",
                limitType = IntakeLimitType.DOSE,
                maxDose = 125.5,
                unit = "mg",
                maxCount = null,
                windowSeconds = 86_400,
                warningPercent = 80,
            )
        val updateDeferred = CompletableDeferred<Unit>()
        val deleteDeferred = CompletableDeferred<Unit>()
        coEvery { experienceRepo.getIntakeLimit(5) } returns existing
        coEvery { experienceRepo.update(any<IntakeLimit>()) } coAnswers { updateDeferred.await() }
        coEvery { experienceRepo.delete(any<IntakeLimit>()) } coAnswers { deleteDeferred.await() }

        val editVm =
            EditIntakeLimitViewModel(
                experienceRepo = experienceRepo,
                substanceRepo = substanceRepo,
                customSubstanceRepo = customSubstanceRepo,
                state = SavedStateHandle(mapOf("limitId" to 5, "substanceName" to "MDMA")),
            )
        advanceUntilIdle()

        assertThat(editVm.maxDoseText).isEqualTo("125.5")

        var saveDismisses = 0
        editVm.saveAndDismiss { saveDismisses++ }
        editVm.saveAndDismiss { saveDismisses++ }
        advanceUntilIdle()
        assertThat(saveDismisses).isEqualTo(0)

        updateDeferred.complete(Unit)
        advanceUntilIdle()
        assertThat(saveDismisses).isEqualTo(1)
        coVerify(exactly = 1) { experienceRepo.update(any<IntakeLimit>()) }

        var deleteDismisses = 0
        editVm.deleteAndDismiss { deleteDismisses++ }
        editVm.deleteAndDismiss { deleteDismisses++ }
        advanceUntilIdle()
        assertThat(deleteDismisses).isEqualTo(0)

        deleteDeferred.complete(Unit)
        advanceUntilIdle()
        assertThat(deleteDismisses).isEqualTo(1)
        coVerify(exactly = 1) { experienceRepo.delete(existing) }
      }
}
