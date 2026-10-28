package foo.pilz.freaklog.ui.tabs.inventory

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.inventory.InventoryRepository
import foo.pilz.freaklog.data.substances.classes.Substance
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class InventoryViewModelTest {
    private val inventoryRepo = mockk<InventoryRepository>()
    private val experienceRepo = mockk<ExperienceRepository>()
    private val substanceRepo = mockk<SubstanceRepository>()
    private lateinit var viewModel: InventoryViewModel

    @Before
    fun setUp() {
        every { inventoryRepo.getAllFlow() } returns flowOf(emptyList())
        val builtin = listOf("Caffeine", "Cannabis", "MDMA", "Ketamine", "LSD")
        every { substanceRepo.getAllSubstances() } returns builtin.map { name ->
            mockk<Substance> { every { this@mockk.name } returns name }
        }
        val custom = listOf("Custom Herbal Mix", "Cannabis Extract")
        coEvery { experienceRepo.getAllCustomSubstances() } returns custom.map { name ->
            mockk<CustomSubstance> { every { this@mockk.name } returns name }
        }
        viewModel = InventoryViewModel(inventoryRepo, experienceRepo, substanceRepo)
    }

    @Test
    fun `suggestions are empty for blank search text`() = runTest {
        assertThat(viewModel.suggestions("")).isEmpty()
        assertThat(viewModel.suggestions("   ")).isEmpty()
    }

    @Test
    fun `suggestions return matching custom and builtin substances`() = runTest {
        val results = viewModel.suggestions("canna")
        assertThat(results).containsExactly(
            InventoryViewModel.Suggestion(name = "Cannabis Extract", isCustom = true),
            InventoryViewModel.Suggestion(name = "Cannabis", isCustom = false)
        )
    }

    @Test
    fun `suggestions deduplicate substances with same name prioritizing custom`() = runTest {
        coEvery { experienceRepo.getAllCustomSubstances() } returns listOf(
            mockk { every { name } returns "MDMA" }
        )
        val results = viewModel.suggestions("mdma")
        assertThat(results).containsExactly(
            InventoryViewModel.Suggestion(name = "MDMA", isCustom = true)
        )
    }

    @Test
    fun `suggestions cache builtin substances and do not query substance repository on every call`() = runTest {
        viewModel.suggestions("caff")
        viewModel.suggestions("ket")
        viewModel.suggestions("lsd")

        // SubstanceRepository.getAllSubstances() should only be called once when initialized lazily
        verify(exactly = 1) { substanceRepo.getAllSubstances() }
    }

    @Test
    fun `suggestions respect limit parameter`() = runTest {
        val results = viewModel.suggestions("a", limit = 2)
        assertThat(results.size).isEqualTo(2)
    }
}
