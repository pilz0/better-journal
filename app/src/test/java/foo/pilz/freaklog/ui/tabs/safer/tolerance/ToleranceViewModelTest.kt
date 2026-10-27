package foo.pilz.freaklog.ui.tabs.safer.tolerance

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.substances.classes.Substance
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ToleranceViewModelTest {
    private val substanceRepo = mockk<SubstanceRepository>()
    private val experienceRepo = mockk<ExperienceRepository>()
    private lateinit var viewModel: ToleranceViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { experienceRepo.getLatestIngestionOfEverySubstanceSinceDate(any()) } returns emptyList()
        val names = listOf("LSD", "Caffeine", "1P-LSD", "Psilocybin", "1B-LSD", "ALD-52", "1cP-LSD", "1V-LSD", "1D-LSD")
        every { substanceRepo.getAllSubstances() } returns names.map { name ->
            mockk<Substance> { every { this@mockk.name } returns name }
        }
        viewModel = ToleranceViewModel(mockk(), substanceRepo, experienceRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `search suggestions return matching substance names`() {
        viewModel.onSearchTextChange("affe")

        assertThat(viewModel.searchSuggestions).isEqualTo(listOf("Caffeine"))
    }

    @Test
    fun `search suggestions ignore case`() {
        for (query in listOf("psilo", "PSILO", "pSiLo")) {
            viewModel.onSearchTextChange(query)

            assertThat(viewModel.searchSuggestions).isEqualTo(listOf("Psilocybin"))
        }
    }

    @Test
    fun `search suggestions return only the first five matches`() {
        viewModel.onSearchTextChange("lsd")

        assertThat(viewModel.searchSuggestions).isEqualTo(listOf("LSD", "1P-LSD", "1B-LSD", "1cP-LSD", "1V-LSD"))
    }

    @Test
    fun `search suggestions are empty for blank queries`() {
        for (query in listOf("", " ", "\t\n")) {
            viewModel.onSearchTextChange(query)

            assertThat(viewModel.searchSuggestions).isEmpty()
        }
        verify(exactly = 0) { substanceRepo.getAllSubstances() }
    }

    @Test
    fun `search suggestions are empty when nothing matches`() {
        viewModel.onSearchTextChange("unknown")

        assertThat(viewModel.searchSuggestions).isEmpty()
    }

    @Test
    fun `search suggestions reuse cached names across reads and query changes`() {
        viewModel.onSearchTextChange("caff")
        assertThat(viewModel.searchSuggestions).isEqualTo(listOf("Caffeine"))
        assertThat(viewModel.searchSuggestions).isEqualTo(listOf("Caffeine"))

        viewModel.onSearchTextChange("psilo")
        assertThat(viewModel.searchSuggestions).isEqualTo(listOf("Psilocybin"))

        verify(exactly = 1) { substanceRepo.getAllSubstances() }
    }
}
