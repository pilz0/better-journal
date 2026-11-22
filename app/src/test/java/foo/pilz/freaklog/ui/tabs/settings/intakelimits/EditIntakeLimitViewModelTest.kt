package foo.pilz.freaklog.ui.tabs.settings.intakelimits

import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceRepository
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.IntakeLimitType
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

class EditIntakeLimitViewModelTest {
  private val experienceRepo = mockk<ExperienceRepository>(relaxed = true)
  private val substanceRepo = mockk<SubstanceRepository>(relaxed = true)
  private val customSubstanceRepo = mockk<CustomSubstanceRepository>(relaxed = true)

  private lateinit var viewModel: EditIntakeLimitViewModel

  @Before
  fun setUp() {
    viewModel =
        EditIntakeLimitViewModel(
            experienceRepo = experienceRepo,
            substanceRepo = substanceRepo,
            customSubstanceRepo = customSubstanceRepo,
            state = SavedStateHandle(mapOf("limitId" to -1, "substanceName" to "Caffeine")),
        )
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
}
