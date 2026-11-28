package foo.pilz.freaklog.ui.tabs.settings.anodyne

import foo.pilz.freaklog.data.anodyne.AnodyneRepository
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnodyneSettingsViewModelTest {
  private val testDispatcher = StandardTestDispatcher()
  private val prefs = mockk<UserPreferences>(relaxed = true)
  private val repo = mockk<AnodyneRepository>(relaxed = true)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    every { prefs.anodyneBaseUrlFlow } returns flowOf("https://anodyne.wiki")
    every { prefs.anodyneAutoLoadEnabledFlow } returns flowOf(true)
    every { repo.getCacheSizeBytes() } returns 1024L
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun testAutoLoadEnabledByDefaultBeforeSubscription() {
    val vm = AnodyneSettingsViewModel(prefs, repo)
    assertTrue(vm.isAutoLoadEnabled.value)
  }

  @Test
  fun testDraftUrlValidation() {
    val vm = AnodyneSettingsViewModel(prefs, repo)
    testDispatcher.scheduler.advanceUntilIdle()

    vm.onDraftUrlChange("wiki.example.org")
    assertTrue(vm.isDraftValid.value)

    vm.onDraftUrlChange("invalid url ://")
    assertFalse(vm.isDraftValid.value)
  }

  @Test
  fun testResetToDefaultUrl() {
    val vm = AnodyneSettingsViewModel(prefs, repo)
    testDispatcher.scheduler.advanceUntilIdle()

    vm.resetToDefaultUrl()
    testDispatcher.scheduler.advanceUntilIdle()

    coVerify { prefs.saveAnodyneBaseUrl(AnodyneRepository.DEFAULT_INSTANCE_URL) }
    verify { repo.clearCache() }
  }
}
