package foo.pilz.freaklog.ui.tabs.search.anodyne

import foo.pilz.freaklog.data.anodyne.AnodyneRepository
import foo.pilz.freaklog.data.anodyne.model.AnodyneSubstance
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
class AnodyneSubstanceViewModelTest {
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
  fun testLoadsSubstance() = runTest {
    val repo =
        mockk<AnodyneRepository> {
          coEvery { lookup("Ketamine", any()) } returns Result.success(AnodyneSubstance("Ketamine"))
          coEvery { getUserNotes(any()) } returns Result.success(emptyList())
        }
    val vm =
        AnodyneSubstanceViewModel(
            substanceTitle = "Ketamine",
            repository = repo,
        )
    vm.load()
    advanceUntilIdle()
    assertEquals("Ketamine", vm.substance.value?.title)
  }
}
