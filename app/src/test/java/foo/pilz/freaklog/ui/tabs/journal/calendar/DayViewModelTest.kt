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

    private fun ingestion(id: Int, experienceId: Int, color: AdaptiveColor?): IngestionWithCompanion =
        IngestionWithCompanion(
            ingestion = EntityBuilders.ingestion(id = id, experienceId = experienceId),
            substanceCompanion = color?.let { SubstanceCompanion(substanceName = "Caffeine", color = it) }
        )
}
