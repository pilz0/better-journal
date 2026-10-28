package foo.pilz.freaklog.ui.tabs.safer.tolerance

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceProfiles
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithEverything
import foo.pilz.freaklog.data.substances.classes.Substance
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.testing.EntityBuilders
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ToleranceViewModelTest {
    private val substanceRepo = mockk<SubstanceRepository>()
    private val experienceRepo = mockk<ExperienceRepository>()
    private val profiles = MutableStateFlow<Map<String, CustomSubstanceWithEverything>>(emptyMap())
    private val customProfiles = mockk<CustomSubstanceProfiles> { every { byNameFlow } returns profiles }
    private lateinit var viewModel: ToleranceViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { experienceRepo.getLatestIngestionOfEverySubstanceSinceDate(any()) } returns emptyList()
        val names = listOf("LSD", "Caffeine", "1P-LSD", "Psilocybin", "1B-LSD", "ALD-52", "1cP-LSD", "1V-LSD", "1D-LSD")
        every { substanceRepo.getAllSubstances() } returns names.map { name ->
            mockk<Substance> {
                every { this@mockk.name } returns name
                every { tolerance } returns null
                every { crossTolerances } returns emptyList()
            }
        }
        every { substanceRepo.getSubstance(any()) } returns null
        viewModel = ToleranceViewModel(mockk(), substanceRepo, experienceRepo, customProfiles)
    }

    private fun customProfile(name: String, half: String? = null, zero: String? = null) =
        CustomSubstanceWithEverything(
            substance = CustomSubstance(
                name = name, units = "mg", description = "", toleranceHalf = half, toleranceZero = zero,
            ),
        )

    private fun setProfiles(vararg profiles: CustomSubstanceWithEverything) {
        this.profiles.value = profiles.associateBy { it.substance.name }
    }

    private fun viewModelWithRealCalculator(): ToleranceViewModel {
        val calculator = ToleranceCalculator(substanceRepo, experienceRepo, customProfiles)
        return ToleranceViewModel(calculator, substanceRepo, experienceRepo, customProfiles)
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

    @Test
    fun `search suggestions include custom substances after built-in ones`() {
        setProfiles(customProfile("Homebrew LSD"), customProfile("Mystery"))

        viewModel.onSearchTextChange("lsd")
        assertThat(viewModel.searchSuggestions).isEqualTo(listOf("LSD", "1P-LSD", "1B-LSD", "1cP-LSD", "1V-LSD"))

        viewModel.onSearchTextChange("homebrew")
        assertThat(viewModel.searchSuggestions).isEqualTo(listOf("Homebrew LSD"))

        viewModel.onSearchTextChange("MYST")
        assertThat(viewModel.searchSuggestions).isEqualTo(listOf("Mystery"))
    }

    @Test
    fun `search suggestions pick up custom substances loaded later`() {
        viewModel.onSearchTextChange("myst")
        assertThat(viewModel.searchSuggestions).isEmpty()

        setProfiles(customProfile("Mystery"))
        assertThat(viewModel.searchSuggestions).isEqualTo(listOf("Mystery"))

        profiles.value = emptyMap()
        assertThat(viewModel.searchSuggestions).isEmpty()
        verify(exactly = 1) { substanceRepo.getAllSubstances() }
    }

    @Test
    fun `search suggestions do not repeat a custom substance named like a built-in one`() {
        setProfiles(customProfile("caffeine"))

        viewModel.onSearchTextChange("caff")

        assertThat(viewModel.searchSuggestions).isEqualTo(listOf("Caffeine"))
    }

    @Test
    fun `recent tolerances include recently taken custom substances`() {
        setProfiles(
            customProfile("Mystery", half = "1 week", zero = "2 weeks"),
            customProfile("NoTolerance"),
        )
        val now = Instant.now()
        val mystery = EntityBuilders.ingestion(substanceName = "Mystery", time = now.minus(Duration.ofDays(2)))
        val noTolerance = EntityBuilders.ingestion(substanceName = "NoTolerance", time = now.minus(Duration.ofDays(1)))
        coEvery { experienceRepo.getLatestIngestionOfEverySubstanceSinceDate(any()) } returns listOf(mystery, noTolerance)
        coEvery { experienceRepo.getIngestionsSince(listOf("Mystery"), any()) } returns listOf(mystery)
        coEvery { experienceRepo.getLastIngestion("Mystery") } returns mystery

        val vm = viewModelWithRealCalculator()

        val estimate = vm.recentTolerances.value.single()
        assertThat(estimate.substanceName).isEqualTo("Mystery")
        assertThat(estimate.lastIngestionTime).isEqualTo(mystery.time)
        assertThat(estimate.toleranceLevel).isGreaterThan(0.7f)
        assertThat(vm.isLoading.value).isFalse()
    }

    @Test
    fun `selecting a custom substance computes its estimate`() {
        setProfiles(customProfile("Mystery", half = "3 days", zero = "1 week"))
        coEvery { experienceRepo.getIngestionsSince(any(), any()) } returns emptyList()
        coEvery { experienceRepo.getLastIngestion(any()) } returns null
        val vm = viewModelWithRealCalculator()

        vm.onSelectSubstance("mystery")

        val estimate = vm.searchedTolerance.value
        assertThat(estimate?.substanceName).isEqualTo("Mystery")
        assertThat(estimate?.halfLifeDays).isEqualTo(3f)
        assertThat(estimate?.zeroDays).isEqualTo(7f)
        assertThat(estimate?.toleranceLevel).isEqualTo(0f)
        assertThat(vm.searchText).isEqualTo("mystery")
    }
}
