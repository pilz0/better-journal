package foo.pilz.freaklog.ui.tabs.settings.health

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.VitalKind
import foo.pilz.freaklog.ui.tabs.settings.combinations.BloodPressureDisplay
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HealthSettingsViewModelTest {
  private val testDispatcher = StandardTestDispatcher()
  private val prefs = mockk<UserPreferences>(relaxed = true)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    every { prefs.isVitalsEnabledFlow } returns flowOf(true)
    every { prefs.areSleepStagesEnabledFlow } returns flowOf(true)
    every { prefs.bloodPressureDisplayFlow } returns flowOf(BloodPressureDisplay.SEPARATE)
    every { prefs.extraVitalsFlow } returns flowOf(setOf(VitalKind.OXYGEN_SATURATION))
    every { prefs.aiAssistantEnabledFlow } returns flowOf(true)
    every { prefs.aiVitalsEnabledFlow } returns flowOf(true)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun exposesFlowsFromUserPreferences() = runTest {
    val vm = HealthSettingsViewModel(prefs)

    vm.isVitalsEnabledFlow.test {
      assertThat(awaitItem()).isEqualTo(false)
      assertThat(awaitItem()).isEqualTo(true)
      cancelAndIgnoreRemainingEvents()
    }
    vm.sleepStagesEnabledFlow.test {
      assertThat(awaitItem()).isEqualTo(false)
      assertThat(awaitItem()).isEqualTo(true)
      cancelAndIgnoreRemainingEvents()
    }
    vm.bloodPressureDisplayFlow.test {
      assertThat(awaitItem()).isEqualTo(BloodPressureDisplay.COMBINED)
      assertThat(awaitItem()).isEqualTo(BloodPressureDisplay.SEPARATE)
      cancelAndIgnoreRemainingEvents()
    }
    vm.extraVitalsFlow.test {
      assertThat(awaitItem()).isEqualTo(emptySet())
      assertThat(awaitItem()).isEqualTo(setOf(VitalKind.OXYGEN_SATURATION))
      cancelAndIgnoreRemainingEvents()
    }
    vm.aiAssistantEnabledFlow.test {
      assertThat(awaitItem()).isEqualTo(false)
      assertThat(awaitItem()).isEqualTo(true)
      cancelAndIgnoreRemainingEvents()
    }
    vm.aiVitalsEnabledFlow.test {
      assertThat(awaitItem()).isEqualTo(false)
      assertThat(awaitItem()).isEqualTo(true)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun delegatesSavesToUserPreferences() = runTest {
    val vm = HealthSettingsViewModel(prefs)

    vm.saveIsVitalsEnabled(true)
    vm.saveSleepStagesEnabled(false)
    vm.saveBloodPressureDisplay(BloodPressureDisplay.OFF)
    vm.saveExtraVitalEnabled(VitalKind.HEART_RATE_VARIABILITY, true)
    vm.saveAiVitalsEnabled(true)
    testDispatcher.scheduler.advanceUntilIdle()

    coVerify { prefs.saveIsVitalsEnabled(true) }
    coVerify { prefs.saveAreSleepStagesEnabled(false) }
    coVerify { prefs.saveBloodPressureDisplay(BloodPressureDisplay.OFF) }
    coVerify { prefs.saveExtraVitalEnabled(VitalKind.HEART_RATE_VARIABILITY, true) }
    coVerify { prefs.saveAiVitalsEnabled(true) }
  }
}
