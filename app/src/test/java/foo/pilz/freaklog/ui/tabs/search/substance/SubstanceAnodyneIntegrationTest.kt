package foo.pilz.freaklog.ui.tabs.search.substance

import androidx.lifecycle.SavedStateHandle
import foo.pilz.freaklog.data.anodyne.AnodyneRepository
import foo.pilz.freaklog.data.anodyne.model.AnodyneSubstance
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceRepository
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithEverything
import foo.pilz.freaklog.data.substances.classes.Substance
import foo.pilz.freaklog.data.substances.classes.SubstanceWithCategories
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.ui.tabs.search.custom.CustomSubstanceViewModel
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class SubstanceAnodyneIntegrationTest {
  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun testSubstanceViewModel_autoLoadFetchesAnodyneWhenEnabled() = runTest {
    val substanceRepo = mockk<SubstanceRepository>()
    val dummySubstance =
        mockk<Substance>(relaxed = true) {
          every { name } returns "Ketamine"
          every { commonNames } returns emptyList()
          every { roas } returns emptyList()
          every { url } returns "https://example.com"
        }
    every { substanceRepo.getSubstanceWithCategories("Ketamine") } returns
        SubstanceWithCategories(
            dummySubstance,
            emptyList(),
        )

    val experienceRepo =
        mockk<ExperienceRepository> {
          every { getUnArchivedCustomUnitsFlow(any()) } returns flowOf(emptyList())
        }

    val anodyneRepo =
        mockk<AnodyneRepository> {
          coEvery { lookup("Ketamine", any()) } returns
              Result.success(AnodyneSubstance("Ketamine", molarMass = "237.7 g/mol"))
        }

    val userPrefs =
        mockk<UserPreferences> {
          every { anodyneAutoLoadEnabledFlow } returns flowOf(true)
        }

    val savedStateHandle = SavedStateHandle(mapOf("substanceName" to "Ketamine"))

    val vm =
        SubstanceViewModel(
            substanceRepo = substanceRepo,
            experienceRepo = experienceRepo,
            anodyneRepo = anodyneRepo,
            userPreferences = userPrefs,
            state = savedStateHandle,
        )

    advanceUntilIdle()

    assertEquals("Ketamine", vm.anodyneSubstanceFlow.value?.title)
    assertEquals("237.7 g/mol", vm.anodyneSubstanceFlow.value?.molarMass)
  }

  @Test
  fun testSubstanceViewModel_manualLoadWhenAutoLoadDisabled() = runTest {
    val substanceRepo = mockk<SubstanceRepository>()
    val dummySubstance =
        mockk<Substance>(relaxed = true) {
          every { name } returns "Ketamine"
          every { commonNames } returns emptyList()
          every { roas } returns emptyList()
          every { url } returns "https://example.com"
        }
    every { substanceRepo.getSubstanceWithCategories("Ketamine") } returns
        SubstanceWithCategories(
            dummySubstance,
            emptyList(),
        )

    val experienceRepo =
        mockk<ExperienceRepository> {
          every { getUnArchivedCustomUnitsFlow(any()) } returns flowOf(emptyList())
        }

    val anodyneRepo =
        mockk<AnodyneRepository> {
          coEvery { lookup("Ketamine", any()) } returns
              Result.success(AnodyneSubstance("Ketamine", molarMass = "237.7 g/mol"))
        }

    val userPrefs =
        mockk<UserPreferences> {
          every { anodyneAutoLoadEnabledFlow } returns flowOf(false)
        }

    val savedStateHandle = SavedStateHandle(mapOf("substanceName" to "Ketamine"))

    val vm =
        SubstanceViewModel(
            substanceRepo = substanceRepo,
            experienceRepo = experienceRepo,
            anodyneRepo = anodyneRepo,
            userPreferences = userPrefs,
            state = savedStateHandle,
        )

    advanceUntilIdle()
    assertNull(vm.anodyneSubstanceFlow.value)

    vm.loadAnodyneData()
    advanceUntilIdle()

    assertEquals("Ketamine", vm.anodyneSubstanceFlow.value?.title)
  }

  @Test
  fun testCustomSubstanceViewModel_autoLoadFetchesAnodyneWhenEnabled() = runTest {
    val substanceRepo =
        mockk<SubstanceRepository> {
          every { getAllCategories() } returns emptyList()
        }
    val customSubstance =
        CustomSubstance(
            id = 42,
            name = "2-FDCK",
            units = "mg",
            description = "Fluorodeschloroketamine",
        )
    val customSubstanceWithEverything = CustomSubstanceWithEverything(substance = customSubstance)
    val customRepo =
        mockk<CustomSubstanceRepository> {
          every { getWithEverythingFlow(42) } returns flowOf(customSubstanceWithEverything)
          coEvery { getWithEverything(42) } returns customSubstanceWithEverything
        }
    val experienceRepo = mockk<ExperienceRepository>()

    val anodyneRepo =
        mockk<AnodyneRepository> {
          coEvery { lookup("2-FDCK", any()) } returns
              Result.success(AnodyneSubstance("2-FDCK", molarMass = "255.7 g/mol"))
        }

    val userPrefs =
        mockk<UserPreferences> {
          every { anodyneAutoLoadEnabledFlow } returns flowOf(true)
        }

    val savedStateHandle = SavedStateHandle(mapOf("customSubstanceId" to 42))

    val vm =
        CustomSubstanceViewModel(
            substanceRepo = substanceRepo,
            customRepo = customRepo,
            experienceRepo = experienceRepo,
            anodyneRepo = anodyneRepo,
            userPreferences = userPrefs,
            state = savedStateHandle,
        )

    advanceUntilIdle()

    assertEquals("2-FDCK", vm.anodyneSubstanceFlow.value?.title)
    assertEquals("255.7 g/mol", vm.anodyneSubstanceFlow.value?.molarMass)
  }

  @Test
  fun testCustomSubstanceViewModel_manualLoadWhenAutoLoadDisabled() = runTest {
    val substanceRepo =
        mockk<SubstanceRepository> {
          every { getAllCategories() } returns emptyList()
        }
    val customSubstance =
        CustomSubstance(
            id = 42,
            name = "2-FDCK",
            units = "mg",
            description = "Fluorodeschloroketamine",
        )
    val customSubstanceWithEverything = CustomSubstanceWithEverything(substance = customSubstance)
    val customRepo =
        mockk<CustomSubstanceRepository> {
          every { getWithEverythingFlow(42) } returns flowOf(customSubstanceWithEverything)
          coEvery { getWithEverything(42) } returns customSubstanceWithEverything
        }
    val experienceRepo = mockk<ExperienceRepository>()

    val anodyneRepo =
        mockk<AnodyneRepository> {
          coEvery { lookup("2-FDCK", any()) } returns
              Result.success(AnodyneSubstance("2-FDCK", molarMass = "255.7 g/mol"))
        }

    val userPrefs =
        mockk<UserPreferences> {
          every { anodyneAutoLoadEnabledFlow } returns flowOf(false)
        }

    val savedStateHandle = SavedStateHandle(mapOf("customSubstanceId" to 42))

    val vm =
        CustomSubstanceViewModel(
            substanceRepo = substanceRepo,
            customRepo = customRepo,
            experienceRepo = experienceRepo,
            anodyneRepo = anodyneRepo,
            userPreferences = userPrefs,
            state = savedStateHandle,
        )

    advanceUntilIdle()
    assertNull(vm.anodyneSubstanceFlow.value)

    vm.loadAnodyneData()
    advanceUntilIdle()

    assertEquals("2-FDCK", vm.anodyneSubstanceFlow.value?.title)
  }
}
