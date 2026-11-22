package foo.pilz.freaklog.ui.tabs.search.anodyne

import foo.pilz.freaklog.data.anodyne.AnodyneRepository
import foo.pilz.freaklog.data.anodyne.model.AnodyneIndexEntry
import io.mockk.coEvery
import io.mockk.mockk
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

@OptIn(ExperimentalCoroutinesApi::class)
class AnodyneSearchViewModelTest {
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
  fun testFilterPrioritizesPrefixMatches() = runTest {
    val repo = mockk<AnodyneRepository> {
      coEvery { getIndexEntries() } returns Result.success(listOf(
          AnodyneIndexEntry("Lysergic acid diethylamide", listOf("LSD"), listOf("Psychedelic")),
          AnodyneIndexEntry("Ketamine", listOf("K"), listOf("Dissociative"))
      ))
    }
    val vm = AnodyneSearchViewModel(repo)
    advanceUntilIdle()

    vm.onSearchQueryChange("LSD")
    assertEquals("Lysergic acid diethylamide", vm.filteredEntries.value.first().title)
  }

  @Test
  fun testFilterByClass() = runTest {
    val repo = mockk<AnodyneRepository> {
      coEvery { getIndexEntries() } returns Result.success(listOf(
          AnodyneIndexEntry("Lysergic acid diethylamide", listOf("LSD"), listOf("Psychedelic")),
          AnodyneIndexEntry("Ketamine", listOf("K"), listOf("Dissociative"))
      ))
    }
    val vm = AnodyneSearchViewModel(repo)
    advanceUntilIdle()

    vm.onSearchQueryChange("Dissociative")
    assertEquals("Ketamine", vm.filteredEntries.value.first().title)
  }
}
