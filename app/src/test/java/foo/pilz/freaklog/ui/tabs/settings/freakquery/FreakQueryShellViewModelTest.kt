package foo.pilz.freaklog.ui.tabs.settings.freakquery

import foo.pilz.freaklog.data.freakquery.FreakQueryRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FreakQueryShellViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<FreakQueryRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.getLogs() } returns listOf(mapOf("id" to 1L))
        coEvery { repository.query(any(), any()) } returns "query-result"
        coEvery { repository.render(any(), any()) } returns "rendered-template"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `repeated queries have distinct keys and preserve existing history entries`() = runTest {
        val viewModel = FreakQueryShellViewModel(repository)
        viewModel.runExample("count")
        advanceUntilIdle()
        val first = viewModel.state.value.history.single()

        viewModel.runExample("count")
        advanceUntilIdle()
        val history = viewModel.state.value.history
        assertSame(first, history.first())
        assertNotEquals(first.id, history.last().id)
        assertEquals(first.id, first.copy(output = "Updated").id)
    }

    @Test
    fun `runCurrentInput executes bare tag using query`() = runTest {
        val viewModel = FreakQueryShellViewModel(repository)
        advanceUntilIdle()

        viewModel.updateInput("today|sum_dose")
        viewModel.runCurrentInput()
        advanceUntilIdle()

        coVerify { repository.query("today|sum_dose") }
        assertEquals("query-result", viewModel.state.value.history.last().output)
    }

    @Test
    fun `runCurrentInput executes template with curly braces using render`() = runTest {
        val viewModel = FreakQueryShellViewModel(repository)
        advanceUntilIdle()

        viewModel.updateInput("Dose: {{today|sum_dose}} mg")
        viewModel.runCurrentInput()
        advanceUntilIdle()

        coVerify { repository.render("Dose: {{today|sum_dose}} mg") }
        assertEquals("rendered-template", viewModel.state.value.history.last().output)
    }

    @Test
    fun `history navigation cycles up and down through sent queries`() = runTest {
        val viewModel = FreakQueryShellViewModel(repository)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.canNavigateHistoryUp)
        assertFalse(viewModel.state.value.canNavigateHistoryDown)

        viewModel.updateInput("query 1")
        viewModel.runCurrentInput()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.canNavigateHistoryUp)
        assertFalse(viewModel.state.value.canNavigateHistoryDown)

        viewModel.updateInput("query 2")
        viewModel.runCurrentInput()
        advanceUntilIdle()

        viewModel.updateInput("draft")

        // Navigate up to query 2
        viewModel.navigateHistoryUp()
        assertEquals("query 2", viewModel.state.value.input)
        assertTrue(viewModel.state.value.canNavigateHistoryUp)
        assertTrue(viewModel.state.value.canNavigateHistoryDown)

        // Navigate up to query 1
        viewModel.navigateHistoryUp()
        assertEquals("query 1", viewModel.state.value.input)
        assertFalse(viewModel.state.value.canNavigateHistoryUp)
        assertTrue(viewModel.state.value.canNavigateHistoryDown)

        // Navigate down to query 2
        viewModel.navigateHistoryDown()
        assertEquals("query 2", viewModel.state.value.input)
        assertTrue(viewModel.state.value.canNavigateHistoryUp)
        assertTrue(viewModel.state.value.canNavigateHistoryDown)

        // Navigate down to draft
        viewModel.navigateHistoryDown()
        assertEquals("draft", viewModel.state.value.input)
        assertTrue(viewModel.state.value.canNavigateHistoryUp)
        assertFalse(viewModel.state.value.canNavigateHistoryDown)
    }
}
