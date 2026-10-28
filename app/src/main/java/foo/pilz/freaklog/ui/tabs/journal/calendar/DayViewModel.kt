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
import com.kizitonwose.calendar.core.CalendarDay
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import java.time.ZoneId
import javax.inject.Inject


@HiltViewModel
class DayViewModel @Inject constructor(
    val experienceRepo: ExperienceRepository,
) : ViewModel() {

    suspend fun getExperienceInfo(day: CalendarDay): ExperienceInfo {
        val zone = ZoneId.systemDefault()
        val startOfDay = day.date.atStartOfDay(zone).toInstant()
        val endOfDay = day.date.plusDays(1).atStartOfDay(zone).toInstant()
        val ingestions = experienceRepo.getIngestionsWithCompanions(
            fromInstant = startOfDay,
            toInstant = endOfDay
        )
        return ExperienceInfo(
            experienceIds = ingestions.map { it.ingestion.experienceId }.toSet().toList(),
            colors = ingestions.mapNotNull { it.substanceCompanion?.color }
        )
    }
}

data class ExperienceInfo(
    val experienceIds: List<Int>,
    val colors: List<AdaptiveColor>
)
