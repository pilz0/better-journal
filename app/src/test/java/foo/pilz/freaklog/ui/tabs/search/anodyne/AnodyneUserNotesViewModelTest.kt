package foo.pilz.freaklog.ui.tabs.search.anodyne

import androidx.lifecycle.SavedStateHandle
import foo.pilz.freaklog.data.anodyne.AnodyneRepository
import foo.pilz.freaklog.data.anodyne.model.AnodyneSubstance
import foo.pilz.freaklog.data.anodyne.model.AnodyneUserNote
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class AnodyneUserNotesViewModelTest {
  private val testDispatcher = StandardTestDispatcher()

  @Before fun setUp() = Dispatchers.setMain(testDispatcher)

  @After fun tearDown() = Dispatchers.resetMain()

  /** Regression: notes are filed under the wiki title, not the bundled name. */
  @Test
  fun matchesNotesFiledUnderTheWikiTitle() = runTest {
    val names = slot<List<String>>()
    val note = AnodyneUserNote("alice", "Lysergic acid diethylamide", notes = listOf("x"))
    val repo =
        mockk<AnodyneRepository> {
          coEvery { lookup("LSD", any()) } returns
              Result.success(
                  AnodyneSubstance("Lysergic acid diethylamide", abbreviations = listOf("LSD-25"))
              )
          coEvery { getUserNotes(capture(names)) } returns Result.success(listOf(note))
        }
    val vm = AnodyneUserNotesViewModel(repo, SavedStateHandle(mapOf("substanceName" to "LSD")))
    advanceUntilIdle()

    assertTrue(names.captured.containsAll(listOf("LSD", "Lysergic acid diethylamide", "LSD-25")))
    assertEquals(listOf(note), vm.notes.value)
  }
}
