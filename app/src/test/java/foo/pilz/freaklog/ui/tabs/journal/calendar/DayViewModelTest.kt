package foo.pilz.freaklog.ui.tabs.journal.calendar

import androidx.lifecycle.viewModelScope
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
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Test

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
          Instant.parse("2026-01-02T00:00:00Z"),
      )
    }
  }

  @Test
  fun `day deduplicates experience ids and preserves ingestion colors in order`() = runTest {
    coEvery { experienceRepo.getIngestionsWithCompanions(any(), any()) } returns
        listOf(
            ingestion(1, 7, AdaptiveColor.RED),
            ingestion(2, 3, AdaptiveColor.BLUE),
            ingestion(3, 7, AdaptiveColor.RED),
            ingestion(4, 9, null),
        )

    val info = viewModel.getExperienceInfo(day)

    assertThat(info.experienceIds).containsExactly(7, 3, 9)
    assertThat(info.colors)
        .containsExactly(AdaptiveColor.RED, AdaptiveColor.BLUE, AdaptiveColor.RED)
  }

  @Test
  fun `loading another day does not reuse the previous day info`() = runTest {
    val nextDay = day.copy(date = day.date.plusDays(1))
    coEvery {
      experienceRepo.getIngestionsWithCompanions(
          Instant.parse("2026-01-01T00:00:00Z"),
          Instant.parse("2026-01-02T00:00:00Z"),
      )
    } returns listOf(ingestion(1, 7, AdaptiveColor.RED))
    coEvery {
      experienceRepo.getIngestionsWithCompanions(
          Instant.parse("2026-01-02T00:00:00Z"),
          Instant.parse("2026-01-03T00:00:00Z"),
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
        "2026-03-29T22:00:00Z",
    )
  }

  @Test
  fun `autumn clock change uses a 25 hour calendar day`() = runTest {
    verifyDayBounds(
        LocalDate.of(2026, 10, 25),
        "2026-10-24T22:00:00Z",
        "2026-10-25T23:00:00Z",
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

  @Test
  fun `loadMonthInfo queries full month once across DST and groups by local date`() = runTest {
    val berlin = ZoneId.of("Europe/Berlin")
    val march28LateUtc = Instant.parse("2026-03-28T23:30:00Z") // 2026-03-29 00:30 CET
    val march29LateUtc = Instant.parse("2026-03-29T21:30:00Z") // 2026-03-29 23:30 CEST
    coEvery { experienceRepo.getIngestionsWithCompanions(any(), any()) } returns
        listOf(
            ingestion(1, 10, AdaptiveColor.RED, march28LateUtc),
            ingestion(2, 10, AdaptiveColor.BLUE, march29LateUtc),
            ingestion(3, 12, AdaptiveColor.GREEN, march29LateUtc),
        )

    val monthMap = viewModel.loadMonthInfo(YearMonth.of(2026, 3), berlin)

    coVerify(exactly = 1) {
      experienceRepo.getIngestionsWithCompanions(
          Instant.parse("2026-02-28T23:00:00Z"),
          Instant.parse("2026-03-31T22:00:00Z"),
      )
    }
    val day29Info = requireNotNull(monthMap[LocalDate.of(2026, 3, 29)])
    assertThat(day29Info.experienceIds).containsExactly(10, 12)
    assertThat(day29Info.colors)
        .containsExactly(AdaptiveColor.RED, AdaptiveColor.BLUE, AdaptiveColor.GREEN)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `ensureMonthLoaded caches result and avoids duplicate queries`() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    Dispatchers.setMain(dispatcher)
    val vm = DayViewModel(experienceRepo)
    try {
      val march = YearMonth.of(2026, 3)
      coEvery { experienceRepo.getIngestionsWithCompanions(any(), any()) } returns
          listOf(ingestion(1, 7, AdaptiveColor.RED, Instant.parse("2026-03-10T12:00:00Z")))

      vm.ensureMonthLoaded(march)
      vm.ensureMonthLoaded(march)

      val loaded =
          withContext(Dispatchers.Default) {
            withTimeout(5_000) { vm.monthData.first { it.containsKey(march) } }
          }
      val march10 = requireNotNull(loaded[march]?.get(LocalDate.of(2026, 3, 10)))
      assertThat(march10.experienceIds).containsExactly(7)
      coVerify(exactly = 1) { experienceRepo.getIngestionsWithCompanions(any(), any()) }
    } finally {
      vm.viewModelScope.cancel()
      Dispatchers.resetMain()
    }
  }

  private fun ingestion(
      id: Int,
      experienceId: Int,
      color: AdaptiveColor?,
      time: Instant = Instant.parse("2026-01-01T12:00:00Z"),
  ): IngestionWithCompanion =
      IngestionWithCompanion(
          ingestion = EntityBuilders.ingestion(id = id, experienceId = experienceId, time = time),
          substanceCompanion =
              color?.let { SubstanceCompanion(substanceName = "Caffeine", color = it) },
      )
}
