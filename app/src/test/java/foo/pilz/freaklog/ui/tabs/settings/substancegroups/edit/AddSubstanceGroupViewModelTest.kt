package foo.pilz.freaklog.ui.tabs.settings.substancegroups.edit

import foo.pilz.freaklog.data.room.experiences.SubstanceGroupRepository
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroup
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddSubstanceGroupViewModelTest {

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
  fun `name typed before draft creation completes is persisted once id arrives`() = runTest {
    val createDeferred = CompletableDeferred<Int>()
    val groupRepo =
        mockk<SubstanceGroupRepository>(relaxed = true) {
          coEvery { create(any()) } coAnswers { createDeferred.await() }
          every { getWithItemsFlow(any()) } returns flowOf(null)
        }
    val viewModel = AddSubstanceGroupViewModel(groupRepo)

    advanceUntilIdle()
    viewModel.onNameChange("  Weekend Stack  ")
    advanceUntilIdle()
    coVerify(exactly = 0) { groupRepo.update(any()) }

    createDeferred.complete(12)
    advanceUntilIdle()

    assertEquals(12, viewModel.idFlow.value)
    coVerify(exactly = 1) { groupRepo.update(SubstanceGroup(id = 12, name = "Weekend Stack")) }
  }

  @Test
  fun `deleteDraft awaits initJob prevents late update and invokes onDone once`() = runTest {
    val createDeferred = CompletableDeferred<Int>()
    val groupRepo =
        mockk<SubstanceGroupRepository>(relaxed = true) {
          coEvery { create(any()) } coAnswers { createDeferred.await() }
          every { getWithItemsFlow(any()) } returns flowOf(null)
        }
    val viewModel = AddSubstanceGroupViewModel(groupRepo)
    var doneCount = 0

    viewModel.deleteDraft { doneCount++ }
    viewModel.deleteDraft { doneCount++ }
    viewModel.onNameChange("Should not persist")
    advanceUntilIdle()
    assertEquals(0, doneCount)

    createDeferred.complete(44)
    advanceUntilIdle()

    assertEquals(1, doneCount)
    assertTrue(viewModel.idFlow.value == 44)
    coVerify(exactly = 1) { groupRepo.delete(SubstanceGroup(id = 44, name = "Should not persist")) }
    coVerify(exactly = 0) { groupRepo.update(any()) }
  }
}
