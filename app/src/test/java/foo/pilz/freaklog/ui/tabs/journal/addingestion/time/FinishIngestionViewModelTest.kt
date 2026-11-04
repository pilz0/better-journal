package foo.pilz.freaklog.ui.tabs.journal.addingestion.time

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import foo.pilz.freaklog.data.room.experiences.CustomFormulationRepository
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.webhooks.WebhookRepository
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDateTime
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FinishIngestionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val externalScope = CoroutineScope(dispatcher)
    private val experienceRepo = mockk<ExperienceRepository>()
    private val userPreferences = mockk<UserPreferences>()
    private val webhookRepository = mockk<WebhookRepository>()
    private val customFormulationRepository = mockk<CustomFormulationRepository>()
    private val originalTimeZone = TimeZone.getDefault()
    private lateinit var viewModel: FinishIngestionScreenViewModel
    private val now = Instant.parse("2026-11-01T12:34:56Z")

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Dispatchers.setMain(dispatcher)
        every { experienceRepo.getSubstanceCompanionFlow(any()) } returns flowOf(null)
        every { experienceRepo.getAllSubstanceCompanionsFlow() } returns flowOf(emptyList())
        every { experienceRepo.getSortedExperiencesWithIngestionsFlow() } returns flowOf(emptyList())
        every { experienceRepo.getSortedIngestions(any()) } returns flowOf(emptyList())
        every { experienceRepo.getSortedIngestionsFlow(any(), any()) } returns flowOf(emptyList())
        coEvery {
            experienceRepo.getSortedExperiencesWithIngestionsWithSortDateBetween(any(), any())
        } returns emptyList()
        every { userPreferences.lastIngestionTimeOfExperienceFlow } returns flowOf(null)
        every { userPreferences.clonedIngestionTimeFlow } returns flowOf(null)
        every { webhookRepository.getEnabledFlow() } returns flowOf(emptyList())
        every { customFormulationRepository.getAll() } returns flowOf(emptyList())
        viewModel = FinishIngestionScreenViewModel(
            experienceRepo = experienceRepo,
            userPreferences = userPreferences,
            webhookRepository = webhookRepository,
            ingestionWebhookSender = mockk(),
            customFormulationRepository = customFormulationRepository,
            substanceRepository = mockk(),
            externalScope = externalScope,
            appContext = mockk(),
            state = SavedStateHandle(mapOf("substanceName" to "Caffeine"))
        )
        dispatcher.scheduler.runCurrent()
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) viewModel.viewModelScope.cancel()
        externalScope.cancel()
        Dispatchers.resetMain()
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `Now sets the current date and time`() {
        assertPreset(IngestionTimePreset.NOW, "2026-11-01T12:34:56")
    }

    @Test
    fun `15m ago sets fifteen minutes before now`() {
        assertPreset(IngestionTimePreset.FIFTEEN_MINUTES_AGO, "2026-11-01T12:19:56")
    }

    @Test
    fun `30m ago sets thirty minutes before now`() {
        assertPreset(IngestionTimePreset.THIRTY_MINUTES_AGO, "2026-11-01T12:04:56")
    }

    @Test
    fun `1h ago sets one hour before now`() {
        assertPreset(IngestionTimePreset.ONE_HOUR_AGO, "2026-11-01T11:34:56")
    }

    @Test
    fun `2h ago sets two hours before now`() {
        assertPreset(IngestionTimePreset.TWO_HOURS_AGO, "2026-11-01T10:34:56")
    }

    @Test
    fun `presets use current system time by default`() {
        IngestionTimePreset.entries.forEach { preset ->
            val before = LocalDateTime.now().minusMinutes(preset.minutesAgo)
            viewModel.applyTimePreset(preset)
            dispatcher.scheduler.runCurrent()
            val after = LocalDateTime.now().minusMinutes(preset.minutesAgo)

            assertThat(viewModel.localDateTimeStartFlow.value in before..after).isTrue()
        }
    }

    @Test
    fun `all past presets roll date back across midnight and year boundary`() {
        val midnightNow = Instant.parse("2026-01-01T00:05:00Z")
        val expectedTimes = mapOf(
            IngestionTimePreset.FIFTEEN_MINUTES_AGO to "2025-12-31T23:50",
            IngestionTimePreset.THIRTY_MINUTES_AGO to "2025-12-31T23:35",
            IngestionTimePreset.ONE_HOUR_AGO to "2025-12-31T23:05",
            IngestionTimePreset.TWO_HOURS_AGO to "2025-12-31T22:05"
        )
        expectedTimes.forEach { (preset, expected) ->
            viewModel.applyTimePreset(preset, midnightNow)
            dispatcher.scheduler.runCurrent()

            assertThat(viewModel.localDateTimeStartFlow.value).isEqualTo(LocalDateTime.parse(expected))
            assertThat(viewModel.enteredTitle).isEqualTo("31 December 2025")
        }
    }

    @Test
    fun `preset replaces previously selected date and refreshes nearby experiences`() {
        viewModel.onChangeStartDateOrTime(LocalDateTime.parse("2020-01-01T10:00"))
        dispatcher.scheduler.runCurrent()

        assertPreset(IngestionTimePreset.ONE_HOUR_AGO, "2026-11-01T11:34:56")

        coVerify {
            experienceRepo.getSortedExperiencesWithIngestionsWithSortDateBetween(
                Instant.parse("2026-10-29T11:34:56Z"),
                Instant.parse("2026-11-02T11:34:56Z")
            )
        }
        assertThat(viewModel.enteredTitle).isEqualTo("01 November 2026")
    }

    @Test
    fun `repeated presets are relative to now rather than the previous selection`() {
        assertPreset(IngestionTimePreset.TWO_HOURS_AGO, "2026-11-01T10:34:56")
        assertPreset(IngestionTimePreset.FIFTEEN_MINUTES_AGO, "2026-11-01T12:19:56")
        assertPreset(IngestionTimePreset.NOW, "2026-11-01T12:34:56")
    }

    @Test
    fun `preset preserves a manually entered experience title`() {
        viewModel.changeTitle("My evening")

        assertPreset(IngestionTimePreset.TWO_HOURS_AGO, "2026-11-01T10:34:56")

        assertThat(viewModel.enteredTitle).isEqualTo("My evening")
    }

    @Test
    fun `preset corrects range end when it precedes the new start`() {
        viewModel.onChangeTimePickerOption(IngestionTimePickerOption.TIME_RANGE)
        viewModel.localDateTimeEndFlow.value = LocalDateTime.parse("2026-10-31T23:00")

        assertPreset(IngestionTimePreset.NOW, "2026-11-01T12:34:56")

        assertThat(viewModel.localDateTimeEndFlow.value).isEqualTo(LocalDateTime.parse("2026-11-01T13:04:56"))
        assertThat(viewModel.ingestionTimePickerOptionFlow.value).isEqualTo(IngestionTimePickerOption.TIME_RANGE)
    }

    @Test
    fun `preset preserves a valid range end`() {
        val end = LocalDateTime.parse("2026-11-01T14:00")
        viewModel.onChangeTimePickerOption(IngestionTimePickerOption.TIME_RANGE)
        viewModel.localDateTimeEndFlow.value = end

        assertPreset(IngestionTimePreset.ONE_HOUR_AGO, "2026-11-01T11:34:56")

        assertThat(viewModel.localDateTimeEndFlow.value).isEqualTo(end)
    }

    @Test
    fun `preset subtracts elapsed time across daylight saving spring transition`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

        viewModel.applyTimePreset(IngestionTimePreset.THIRTY_MINUTES_AGO, Instant.parse("2026-03-08T07:10:00Z"))
        dispatcher.scheduler.runCurrent()

        assertThat(viewModel.localDateTimeStartFlow.value).isEqualTo(LocalDateTime.parse("2026-03-08T01:40"))
    }

    @Test
    fun `preset converts date and time together in a non UTC time zone`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kathmandu"))

        viewModel.applyTimePreset(IngestionTimePreset.ONE_HOUR_AGO, Instant.parse("2026-10-31T18:30:00Z"))
        dispatcher.scheduler.runCurrent()

        assertThat(viewModel.localDateTimeStartFlow.value).isEqualTo(LocalDateTime.parse("2026-10-31T23:15"))
    }

    private fun assertPreset(preset: IngestionTimePreset, expected: String) {
        viewModel.applyTimePreset(preset, now)
        dispatcher.scheduler.runCurrent()

        assertThat(viewModel.localDateTimeStartFlow.value).isEqualTo(LocalDateTime.parse(expected))
    }
}
