package foo.pilz.freaklog.ui.tabs.journal.experience.edit

import androidx.lifecycle.SavedStateHandle
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.Experience
import foo.pilz.freaklog.data.room.experiences.entities.Location
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EditExperienceViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<ExperienceRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init with null experience does not crash and onDoneTap is safe`() = runTest(testDispatcher) {
        coEvery { repository.getExperience(any()) } returns null

        val viewModel = EditExperienceViewModel(
            repository = repository,
            state = SavedStateHandle(mapOf("experienceId" to 42)),
        )
        advanceUntilIdle()

        assertNull(viewModel.experience)
        assertEquals("", viewModel.enteredTitle)
        assertEquals("", viewModel.enteredText)
        assertEquals("", viewModel.enteredLocation)

        // Calling onDoneTap when experience is null should return safely without updating or crashing
        viewModel.enteredTitle = "Non empty"
        viewModel.onDoneTap()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.update(experience = any<Experience>()) }
    }

    @Test
    fun `init with valid experience populates fields and onDoneTap saves updates`() = runTest(testDispatcher) {
        val experience = Experience(
            id = 42,
            title = "Forest Journey",
            text = "A calm evening walk.",
            sortDate = Instant.ofEpochMilli(1700000000000L),
            location = Location(name = "Black Forest", longitude = 8.2, latitude = 48.1),
        )
        coEvery { repository.getExperience(any()) } returns experience
        coEvery { repository.update(experience = any<Experience>()) } returns Unit

        val viewModel = EditExperienceViewModel(
            repository = repository,
            state = SavedStateHandle(mapOf("experienceId" to 42)),
        )
        advanceUntilIdle()

        assertEquals("Forest Journey", viewModel.enteredTitle)
        assertEquals("A calm evening walk.", viewModel.enteredText)
        assertEquals("Black Forest", viewModel.enteredLocation)

        viewModel.enteredTitle = "Updated Forest Journey"
        viewModel.enteredText = "New notes"
        viewModel.onDoneTap()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.update(
                experience = match<Experience> {
                    it.title == "Updated Forest Journey" &&
                        it.text == "New notes" &&
                        it.location?.name == "Black Forest" &&
                        it.location?.longitude == 8.2 &&
                        it.location?.latitude == 48.1
                }
            )
        }
    }

    @Test
    fun `changing location name clears stale gps coordinates`() = runTest(testDispatcher) {
        val experience = Experience(
            id = 42,
            title = "Forest Journey",
            text = "A calm evening walk.",
            sortDate = Instant.ofEpochMilli(1700000000000L),
            location = Location(name = "Black Forest", longitude = 8.2, latitude = 48.1),
        )
        coEvery { repository.getExperience(any()) } returns experience
        coEvery { repository.update(experience = any<Experience>()) } returns Unit

        val viewModel = EditExperienceViewModel(
            repository = repository,
            state = SavedStateHandle(mapOf("experienceId" to 42)),
        )
        advanceUntilIdle()

        viewModel.enteredLocation = "Different City"
        viewModel.onDoneTap()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.update(
                experience = match<Experience> {
                    it.location?.name == "Different City" &&
                        it.location?.longitude == null &&
                        it.location?.latitude == null
                }
            )
        }
    }

    @Test
    fun `blank title is invalid and cannot be saved`() = runTest(testDispatcher) {
        coEvery { repository.getExperience(any()) } returns Experience(
            id = 42,
            title = "Forest Journey",
            text = "",
            sortDate = Instant.EPOCH
        )
        val viewModel = EditExperienceViewModel(repository, SavedStateHandle(mapOf("experienceId" to 42)))
        advanceUntilIdle()

        viewModel.enteredTitle = " \t\n "
        assertFalse(viewModel.isEnteredTitleOk)
        viewModel.onDoneTap()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.update(any<Experience>()) }
    }

    @Test
    fun `saving trims title and preserves gps for whitespace and case changes in location`() = runTest(testDispatcher) {
        coEvery { repository.getExperience(any()) } returns Experience(
            id = 42,
            title = "Forest Journey",
            text = "",
            sortDate = Instant.EPOCH,
            location = Location(name = " Black Forest ", longitude = 8.2, latitude = 48.1)
        )
        coEvery { repository.update(any<Experience>()) } returns Unit
        val viewModel = EditExperienceViewModel(repository, SavedStateHandle(mapOf("experienceId" to 42)))
        advanceUntilIdle()

        viewModel.enteredTitle = " Updated Forest Journey "
        viewModel.enteredLocation = "  black forest  "
        viewModel.onDoneTap()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            repository.update(
                match<Experience> {
                    it.title == "Updated Forest Journey" &&
                        it.location == Location(name = "black forest", longitude = 8.2, latitude = 48.1)
                }
            )
        }
    }
}
