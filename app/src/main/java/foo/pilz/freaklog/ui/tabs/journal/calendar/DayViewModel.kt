/*
 * Copyright (c) 2024. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.ui.tabs.journal.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kizitonwose.calendar.core.CalendarDay
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DayViewModel
@Inject
constructor(
    val experienceRepo: ExperienceRepository,
) : ViewModel() {

  private val _monthData =
      MutableStateFlow<Map<YearMonth, Map<LocalDate, ExperienceInfo>>>(emptyMap())
  val monthData: StateFlow<Map<YearMonth, Map<LocalDate, ExperienceInfo>>> =
      _monthData.asStateFlow()

  private val requestedMonths = ConcurrentHashMap.newKeySet<YearMonth>()

  fun ensureMonthLoaded(yearMonth: YearMonth) {
    if (!requestedMonths.add(yearMonth)) return
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val loaded = loadMonthInfo(yearMonth)
        _monthData.update { it + (yearMonth to loaded) }
      } catch (ce: CancellationException) {
        requestedMonths.remove(yearMonth)
        throw ce
      } catch (e: Exception) {
        requestedMonths.remove(yearMonth)
        android.util.Log.w("DayViewModel", "Failed to load month $yearMonth", e)
      }
    }
  }

  internal suspend fun loadMonthInfo(
      yearMonth: YearMonth,
      zone: ZoneId = ZoneId.systemDefault(),
  ): Map<LocalDate, ExperienceInfo> {
    val startOfMonth = yearMonth.atDay(1).atStartOfDay(zone).toInstant()
    val startOfNextMonth = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()
    val ingestions =
        experienceRepo.getIngestionsWithCompanions(
            fromInstant = startOfMonth,
            toInstant = startOfNextMonth,
        )
    if (ingestions.isEmpty()) return emptyMap()
    return ingestions
        .groupBy { it.ingestion.time.atZone(zone).toLocalDate() }
        .mapValues { (_, entries) ->
          ExperienceInfo(
              experienceIds = entries.map { it.ingestion.experienceId }.distinct(),
              colors = entries.mapNotNull { it.substanceCompanion?.color },
          )
        }
  }

  suspend fun getExperienceInfo(day: CalendarDay): ExperienceInfo {
    val zone = ZoneId.systemDefault()
    val startOfDay = day.date.atStartOfDay(zone).toInstant()
    val endOfDay = day.date.plusDays(1).atStartOfDay(zone).toInstant()
    val ingestions =
        experienceRepo.getIngestionsWithCompanions(
            fromInstant = startOfDay,
            toInstant = endOfDay,
        )
    return ExperienceInfo(
        experienceIds = ingestions.map { it.ingestion.experienceId }.toSet().toList(),
        colors = ingestions.mapNotNull { it.substanceCompanion?.color },
    )
  }
}

data class ExperienceInfo(
    val experienceIds: List<Int>,
    val colors: List<AdaptiveColor>,
)
