package foo.pilz.freaklog.ui.tabs.search.custom.customdurations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import foo.pilz.freaklog.data.anodyne.AnodyneRepository
import foo.pilz.freaklog.data.anodyne.model.AnodyneDosing
import foo.pilz.freaklog.data.anodyne.model.AnodyneTier
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoa
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDose
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDuration
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.Bioavailability
import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.DurationUnits
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomDurationEditorViewModelTest {
  private val testDispatcher = StandardTestDispatcher()
  private val experienceRepository = mockk<ExperienceRepository>(relaxed = true)
  private val anodyneRepository = mockk<AnodyneRepository>(relaxed = true)

  private val substanceId = 42
  private val substanceName = "Ketamine"
  private val route = AdministrationRoute.INSUFFLATED
  private val viewModels = mutableListOf<CustomDurationEditorViewModel>()

  private fun createDuration(
      route: AdministrationRoute,
      customSubstanceId: Int = substanceId,
      onset: DurationRange? = null,
      comeup: DurationRange? = null,
      peak: DurationRange? = null,
      offset: DurationRange? = null,
      total: DurationRange? = null,
  ) =
      CustomRoaDuration(
          route = route,
          customSubstanceId = customSubstanceId,
          onset = onset,
          comeup = comeup,
          peak = peak,
          offset = offset,
          total = total,
      )

  private fun createViewModel(
      substanceId: Int = this.substanceId,
      substanceName: String = this.substanceName,
      route: AdministrationRoute = this.route,
  ): CustomDurationEditorViewModel {
    val state =
        SavedStateHandle(
            mapOf(
                "substanceId" to substanceId,
                "substanceName" to substanceName,
                "route" to route,
            )
        )
    return CustomDurationEditorViewModel(
            experienceRepository = experienceRepository,
            anodyneRepository = anodyneRepository,
            state = state,
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher,
        )
        .also { viewModels.add(it) }
  }

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    viewModels.forEach { it.viewModelScope.cancel() }
    viewModels.clear()
    Dispatchers.resetMain()
  }

  @Test
  fun testInitializesWithExistingData() = runTest {
    val existingDuration =
        createDuration(
            route = route,
            onset = DurationRange(min = 5f, max = null, units = DurationUnits.MINUTES),
            total = DurationRange(min = 60f, max = null, units = DurationUnits.MINUTES),
        )
    val existingDose =
        CustomRoaDose(
            customSubstanceId = substanceId,
            route = route,
            units = "mg",
            commonMin = 50.0,
        )
    val existingRoa =
        CustomRoa(
            customSubstanceId = substanceId,
            route = route,
            bioavailability = Bioavailability(min = 50.0, max = 60.0),
        )

    coEvery { experienceRepository.getCustomSubstance(substanceName) } returns
        CustomSubstance(id = substanceId, name = substanceName, units = "mg", description = "")
    coEvery { experienceRepository.getCustomRoaDurations(substanceId) } returns
        listOf(existingDuration)
    coEvery { experienceRepository.getCustomRoaDose(substanceId, route) } returns existingDose
    coEvery { experienceRepository.getCustomRoa(substanceId, route) } returns existingRoa

    val vm = createViewModel()
    advanceUntilIdle()

    assertEquals("mg", vm.substanceUnitFlow.value)
    assertEquals(existingDuration, vm.roaDurationFlow.value)
    assertEquals(existingDose, vm.roaDoseFlow.value)
    assertEquals(existingRoa, vm.roaFlow.value)
  }

  @Test
  fun testInitializesWithDefaultsWhenNoData() = runTest {
    coEvery { experienceRepository.getCustomSubstance(substanceName) } returns null
    coEvery { experienceRepository.getCustomRoaDurations(substanceId) } returns emptyList()
    coEvery { experienceRepository.getCustomRoaDose(substanceId, route) } returns null
    coEvery { experienceRepository.getCustomRoa(substanceId, route) } returns null

    val vm = createViewModel()
    advanceUntilIdle()

    assertEquals("", vm.substanceUnitFlow.value)
    val duration = vm.roaDurationFlow.value
    assertNotNull(duration)
    assertEquals(route, duration?.route)
    assertEquals(substanceId, duration?.customSubstanceId)
    assertNull(duration?.onset)

    val dose = vm.roaDoseFlow.value
    assertNotNull(dose)
    assertEquals(route, dose?.route)
    assertEquals(substanceId, dose?.customSubstanceId)
    assertEquals("", dose?.units)

    val roa = vm.roaFlow.value
    assertNotNull(roa)
    assertEquals(route, roa?.route)
    assertNull(roa?.bioavailability)
  }

  @Test
  fun testLoadOtherRoutesFiltersCurrentRouteAndEmptyRoutes() = runTest {
    val oralDose =
        CustomRoaDose(
            customSubstanceId = substanceId,
            route = AdministrationRoute.ORAL,
            units = "mg",
            commonMin = 100.0,
        )
    val oralDuration =
        createDuration(
            route = AdministrationRoute.ORAL,
            total = DurationRange(min = 120f, max = null, units = DurationUnits.MINUTES),
        )
    val rectalEmptyDose =
        CustomRoaDose(
            customSubstanceId = substanceId,
            route = AdministrationRoute.RECTAL,
            units = "mg",
        )
    val rectalEmptyDuration = createDuration(route = AdministrationRoute.RECTAL)

    coEvery { experienceRepository.getCustomRoaDoses(substanceId) } returns
        listOf(oralDose, rectalEmptyDose)
    coEvery { experienceRepository.getCustomRoaDurations(substanceId) } returns
        listOf(oralDuration, rectalEmptyDuration)

    val vm = createViewModel()
    advanceUntilIdle()

    val otherRoutes = vm.otherRoutesFlow.value
    assertEquals(1, otherRoutes.size)
    val source = otherRoutes.single()
    assertEquals(AdministrationRoute.ORAL, source.route)
    assertEquals(oralDose, source.dose)
    assertEquals(oralDuration, source.duration)
    assertTrue(source.hasData)
  }

  @Test
  fun testCopyRouteReplacesDoseAndDuration() = runTest {
    coEvery { experienceRepository.getCustomSubstance(substanceName) } returns null
    coEvery { experienceRepository.getCustomRoaDurations(substanceId) } returns emptyList()
    coEvery { experienceRepository.getCustomRoaDose(substanceId, route) } returns null
    coEvery { experienceRepository.getCustomRoa(substanceId, route) } returns null

    val vm = createViewModel()
    advanceUntilIdle()

    val sourceDose =
        CustomRoaDose(
            customSubstanceId = substanceId,
            route = AdministrationRoute.ORAL,
            units = "µg",
            lightMin = 10.0,
            commonMin = 20.0,
            strongMin = 30.0,
            heavyMin = 40.0,
        )
    val sourceDuration =
        createDuration(
            route = AdministrationRoute.ORAL,
            onset = DurationRange(min = 10f, max = null, units = DurationUnits.MINUTES),
            comeup = DurationRange(min = 20f, max = null, units = DurationUnits.MINUTES),
            peak = DurationRange(min = 30f, max = null, units = DurationUnits.MINUTES),
            offset = DurationRange(min = 40f, max = null, units = DurationUnits.MINUTES),
            total = DurationRange(min = 100f, max = null, units = DurationUnits.MINUTES),
        )

    val source = RouteCopySource(AdministrationRoute.ORAL, sourceDose, sourceDuration)
    vm.copyRoute(source)

    val updatedDose = vm.roaDoseFlow.value!!
    assertEquals("µg", updatedDose.units)
    assertEquals(10.0, updatedDose.lightMin)
    assertEquals(20.0, updatedDose.commonMin)
    assertEquals(30.0, updatedDose.strongMin)
    assertEquals(40.0, updatedDose.heavyMin)

    val updatedDuration = vm.roaDurationFlow.value!!
    assertEquals(10f, updatedDuration.onset?.min)
    assertEquals(20f, updatedDuration.comeup?.min)
    assertEquals(30f, updatedDuration.peak?.min)
    assertEquals(40f, updatedDuration.offset?.min)
    assertEquals(100f, updatedDuration.total?.min)
  }

  @Test
  fun testCopyAnodyneDoseUpdatesDoseTiers() = runTest {
    coEvery { experienceRepository.getCustomSubstance(substanceName) } returns null
    coEvery { experienceRepository.getCustomRoaDurations(substanceId) } returns emptyList()
    coEvery { experienceRepository.getCustomRoaDose(substanceId, route) } returns null
    coEvery { experienceRepository.getCustomRoa(substanceId, route) } returns null

    val vm = createViewModel()
    advanceUntilIdle()

    val dosing =
        AnodyneDosing(
            method = "Insufflated",
            light = AnodyneTier(lower = 15.0, upper = 30.0, unit = "mg", entries = 1),
            common = AnodyneTier(lower = 30.0, upper = 75.0, unit = "mg", entries = 2),
            strong = AnodyneTier(lower = 75.0, upper = 150.0, unit = "mg", entries = 3),
            heavy = AnodyneTier(lower = 150.0, upper = 200.0, unit = "mg", entries = 4),
        )
    vm.copyAnodyneDose(dosing)

    val dose = vm.roaDoseFlow.value!!
    assertEquals("mg", dose.units)
    assertEquals(15.0, dose.lightMin)
    assertEquals(30.0, dose.commonMin)
    assertEquals(75.0, dose.strongMin)
    assertEquals(150.0, dose.heavyMin)
  }

  @Test
  fun testOnDoneInsertsPopulatedAndDeletesEmpty() = runTest {
    coEvery { experienceRepository.getCustomSubstance(substanceName) } returns null
    coEvery { experienceRepository.getCustomRoaDurations(substanceId) } returns emptyList()
    coEvery { experienceRepository.getCustomRoaDose(substanceId, route) } returns null
    coEvery { experienceRepository.getCustomRoa(substanceId, route) } returns null

    val vm = createViewModel()
    advanceUntilIdle()

    // 1. Populated duration and dose, empty roa
    val durationWithPhases =
        vm.roaDurationFlow.value!!.copy(
            onset = DurationRange(min = 10f, max = null, units = DurationUnits.MINUTES),
        )
    val doseWithTiers = vm.roaDoseFlow.value!!.copy(commonMin = 25.0)
    val emptyRoa = vm.roaFlow.value!!.copy(bioavailability = null)

    vm.roaDurationFlow.value = durationWithPhases
    vm.roaDoseFlow.value = doseWithTiers
    vm.roaFlow.value = emptyRoa

    var completed = false
    vm.onDone { completed = true }
    advanceUntilIdle()

    assertTrue(completed)
    coVerify { experienceRepository.insert(durationWithPhases) }
    coVerify { experienceRepository.upsertCustomRoaDose(substanceId, doseWithTiers) }
    coVerify { experienceRepository.delete(emptyRoa) }

    // 2. Clear all phases and tiers
    val emptyDuration = durationWithPhases.copy(onset = null)
    val emptyDose = doseWithTiers.copy(commonMin = null)
    val populatedRoa = emptyRoa.copy(bioavailability = Bioavailability(min = 80.0, max = null))

    vm.roaDurationFlow.value = emptyDuration
    vm.roaDoseFlow.value = emptyDose
    vm.roaFlow.value = populatedRoa

    vm.onDone()
    advanceUntilIdle()

    coVerify { experienceRepository.delete(emptyDuration) }
    coVerify { experienceRepository.delete(emptyDose) }
    coVerify { experienceRepository.insert(populatedRoa) }
  }

  @Test
  fun testOnDeleteDeletesAllEntities() = runTest {
    coEvery { experienceRepository.getCustomSubstance(substanceName) } returns null
    coEvery { experienceRepository.getCustomRoaDurations(substanceId) } returns emptyList()
    coEvery { experienceRepository.getCustomRoaDose(substanceId, route) } returns null
    coEvery { experienceRepository.getCustomRoa(substanceId, route) } returns null

    val vm = createViewModel()
    advanceUntilIdle()

    val dur = vm.roaDurationFlow.value!!
    val dose = vm.roaDoseFlow.value!!
    val roa = vm.roaFlow.value!!

    var completed = false
    vm.onDelete { completed = true }
    advanceUntilIdle()

    assertTrue(completed)
    coVerify { experienceRepository.delete(dur) }
    coVerify { experienceRepository.delete(dose) }
    coVerify { experienceRepository.delete(roa) }
  }
}
