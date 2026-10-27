package foo.pilz.freaklog.ui.tabs.journal

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsCompanionsAndRatings
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import foo.pilz.freaklog.data.substances.repositories.SearchRepository
import foo.pilz.freaklog.testing.EntityBuilders
import foo.pilz.freaklog.ui.tabs.journal.outlookskin.OutlookSkinController
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import foo.pilz.freaklog.ui.tabs.settings.funny.UltraFunSkinEligibility
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class JournalViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val experienceRepo = mockk<ExperienceRepository>()
    private val searchRepo = mockk<SearchRepository>()
    private val dataStore = mockk<DataStore<Preferences>>()
    private val userPreferences = mockk<UserPreferences>()
    private val skinEligibility = mockk<UltraFunSkinEligibility>()
    private val outlookSkinController = mockk<OutlookSkinController>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { userPreferences.aiAssistantEnabledFlow } returns flowOf(false)
        every { skinEligibility.eligibleFlow } returns flowOf(false)
        every { searchRepo.getMatchingSubstances(any(), any(), any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `experiences flow filters favorites and handles null companions`() = runTest {
        val exp1 = ExperienceWithIngestionsCompanionsAndRatings(
            experience = EntityBuilders.experience(id = 1, title = "First", isFavorite = false),
            ingestionsWithCompanions = listOf(
                IngestionWithCompanionAndCustomUnit(
                    ingestion = EntityBuilders.ingestion(id = 1, experienceId = 1, substanceName = "Custom Sub"),
                    substanceCompanion = null,
                    customUnit = null
                )
            ),
            ratings = emptyList()
        )
        val exp2 = ExperienceWithIngestionsCompanionsAndRatings(
            experience = EntityBuilders.experience(id = 2, title = "Second", isFavorite = true),
            ingestionsWithCompanions = emptyList(),
            ratings = emptyList()
        )

        every { experienceRepo.getSortedExperienceWithIngestionsCompanionsAndRatingsFlow() } returns
            flowOf(listOf(exp1, exp2))
        every { experienceRepo.getCustomSubstancesFlow() } returns flowOf(emptyList())

        val viewModel = JournalViewModel(
            experienceRepo = experienceRepo,
            searchRepository = searchRepo,
            dataStore = dataStore,
            userPreferences = userPreferences,
            skinEligibility = skinEligibility,
            outlookSkinController = outlookSkinController
        )

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.experiences.collect()
        }

        advanceUntilIdle()
        assertThat(viewModel.experiences.value).hasSize(2)

        viewModel.onChangeFavorite(true)
        advanceUntilIdle()
        assertThat(viewModel.experiences.value.map { it.experience.id }).containsExactly(2)

        viewModel.onChangeFavorite(false)
        viewModel.search("First")
        advanceUntilIdle()
        assertThat(viewModel.experiences.value.map { it.experience.id }).containsExactly(1)

        collectJob.cancel()
    }
}
