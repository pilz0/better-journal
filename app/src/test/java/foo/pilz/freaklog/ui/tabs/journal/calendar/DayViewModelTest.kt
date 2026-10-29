package foo.pilz.freaklog.ui.tabs.journal.calendar

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanion
import foo.pilz.freaklog.testing.EntityBuilders
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone

class DayViewModelTest {
    private val experienceRepo = mockk<ExperienceRepository>()
    private val viewModel = DayViewModel(experienceRepo)
    private val day = CalendarDay(LocalDate.of(2026, 1, 1), DayPosition.MonthDate)

    @Test
    fun `empty day has no experiences or colors`() = runTest {
        coEvery { experienceRepo.getIngestionsWithCompanions(any(), any()) } returns emptyList()

        val info = viewModel.getExperienceInfo(day)

        assertThat(info.experienceIds).isEmpty()
        assertThat(info.colors).isEmpty()
        coVerify(exactly = 1) {
            experienceRepo.getIngestionsWithCompanions(
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
            )
        }
    }

    @Test
    fun `day deduplicates experience ids and preserves ingestion colors in order`() = runTest {
        coEvery { experienceRepo.getIngestionsWithCompanions(any(), any()) } returns listOf(
            ingestion(1, 7, AdaptiveColor.RED),
            ingestion(2, 3, AdaptiveColor.BLUE),
            ingestion(3, 7, AdaptiveColor.RED),
            ingestion(4, 9, null)
        )

        val info = viewModel.getExperienceInfo(day)

        assertThat(info.experienceIds).containsExactly(7, 3, 9)
        assertThat(info.colors).containsExactly(AdaptiveColor.RED, AdaptiveColor.BLUE, AdaptiveColor.RED)
    }

    @Test
    fun `loading another day does not reuse the previous day info`() = runTest {
        val nextDay = day.copy(date = day.date.plusDays(1))
        coEvery {
            experienceRepo.getIngestionsWithCompanions(
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
            )
        } returns listOf(ingestion(1, 7, AdaptiveColor.RED))
        coEvery {
            experienceRepo.getIngestionsWithCompanions(
                Instant.parse("2026-01-02T00:00:00Z"),
                Instant.parse("2026-01-03T00:00:00Z")
            )
        } returns emptyList()

        val firstInfo = viewModel.getExperienceInfo(day)
        val nextInfo = viewModel.getExperienceInfo(nextDay)

        assertThat(firstInfo.experienceIds).containsExactly(7)
        assertThat(firstInfo.colors).containsExactly(AdaptiveColor.RED)
        assertThat(nextInfo.experienceIds).isEmpty()
        assertThat(nextInfo.colors).isEmpty()
    }

    @Test
    fun `spring clock change uses a 23 hour calendar day`() = runTest {
        verifyDayBounds(
            LocalDate.of(2026, 3, 29),
            "2026-03-28T23:00:00Z",
            "2026-03-29T22:00:00Z"
        )
    }

    @Test
    fun `autumn clock change uses a 25 hour calendar day`() = runTest {
        verifyDayBounds(
            LocalDate.of(2026, 10, 25),
            "2026-10-24T22:00:00Z",
            "2026-10-25T23:00:00Z"
        )
    }

    private suspend fun verifyDayBounds(date: LocalDate, start: String, end: String) {
        val originalZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
            coEvery { experienceRepo.getIngestionsWithCompanions(any(), any()) } returns emptyList()

            viewModel.getExperienceInfo(day.copy(date = date))

            coVerify(exactly = 1) {
                experienceRepo.getIngestionsWithCompanions(Instant.parse(start), Instant.parse(end))
            }
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }

    private fun ingestion(id: Int, experienceId: Int, color: AdaptiveColor?): IngestionWithCompanion =
        IngestionWithCompanion(
            ingestion = EntityBuilders.ingestion(id = id, experienceId = experienceId),
            substanceCompanion = color?.let { SubstanceCompanion(substanceName = "Caffeine", color = it) }
        )
}
