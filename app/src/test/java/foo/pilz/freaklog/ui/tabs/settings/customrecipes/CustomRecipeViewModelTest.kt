package foo.pilz.freaklog.ui.tabs.settings.customrecipes

import foo.pilz.freaklog.data.room.experiences.CustomRecipeRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomRecipeComponent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomRecipeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<CustomRecipeRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.getActiveRecipesWithComponentsFlow() } returns flowOf(emptyList())
        every { repository.getArchivedRecipesWithComponentsFlow() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updateComponent ignores unknown IDs`() {
        val viewModel = CustomRecipeViewModel(repository)
        viewModel.addComponent()
        val original = viewModel.components.value

        viewModel.updateComponent("missing", RecipeComponentState(substanceName = "A"))
        viewModel.updateComponent("other", RecipeComponentState(substanceName = "B"))

        assertEquals(original, viewModel.components.value)

        viewModel.updateComponent(original.single().id, RecipeComponentState(substanceName = "C"))
        assertEquals("C", viewModel.components.value.single().substanceName)
    }

    @Test
    fun `removeComponent ignores unknown IDs`() {
        val viewModel = CustomRecipeViewModel(repository)
        viewModel.removeComponent("missing")
        viewModel.addComponent()

        viewModel.removeComponent("missing")
        viewModel.removeComponent("other")
        assertEquals(1, viewModel.components.value.size)

        viewModel.removeComponent(viewModel.components.value.single().id)
        assertEquals(0, viewModel.components.value.size)
    }

    @Test
    fun `component identity survives additions updates and removal of an earlier row`() {
        val viewModel = CustomRecipeViewModel(repository)
        viewModel.addComponent()
        val first = viewModel.components.value.single()
        viewModel.addComponent()
        val second = viewModel.components.value.last()

        assertNotEquals(first.id, second.id)
        assertSame(first, viewModel.components.value.first())
        val edited = second.copy(substanceName = "Custom", dose = 5.0)
        assertEquals(second.id, edited.id)

        viewModel.removeComponent(first.id)
        viewModel.updateComponent(second.id, edited)
        assertEquals(edited, viewModel.components.value.single())

        // Replacing the row's values must also preserve its existing list key.
        viewModel.updateComponent(second.id, RecipeComponentState(substanceName = "Updated"))
        assertEquals(second.id, viewModel.components.value.single().id)
        assertEquals("Updated", viewModel.components.value.single().substanceName)

        // A stale callback for a removed row must not affect the surviving row.
        viewModel.updateComponent(first.id, first.copy(substanceName = "Removed"))
        viewModel.removeComponent(first.id)
        assertEquals(second.id, viewModel.components.value.single().id)
        assertEquals("Updated", viewModel.components.value.single().substanceName)
    }

    @Test
    fun `saveRecipe sanitizes NaN and negative doses to null`() = runTest(testDispatcher) {
        val savedComponents = slot<List<CustomRecipeComponent>>()
        coEvery { repository.insertRecipeWithComponents(any(), capture(savedComponents)) } returns 1L

        val viewModel = CustomRecipeViewModel(repository)
        viewModel.openCreateDialog()
        viewModel.updateRecipeName("Recipe")
        repeat(3) { viewModel.addComponent() }
        viewModel.updateComponent(
            viewModel.components.value[0].id,
            RecipeComponentState(substanceName = "A", dose = Double.NaN, estimatedDoseStandardDeviation = Double.NaN)
        )
        viewModel.updateComponent(
            viewModel.components.value[1].id,
            RecipeComponentState(substanceName = "B", dose = -5.0, estimatedDoseStandardDeviation = -1.0)
        )
        viewModel.updateComponent(
            viewModel.components.value[2].id,
            RecipeComponentState(substanceName = "C", dose = 10.0, estimatedDoseStandardDeviation = 2.0)
        )

        viewModel.saveRecipe()
        advanceUntilIdle()

        val components = savedComponents.captured
        assertNull(components[0].dose)
        assertNull(components[0].estimatedDoseStandardDeviation)
        assertNull(components[1].dose)
        assertNull(components[1].estimatedDoseStandardDeviation)
        assertEquals(10.0, components[2].dose!!, 0.0)
        assertEquals(2.0, components[2].estimatedDoseStandardDeviation!!, 0.0)
    }
}
