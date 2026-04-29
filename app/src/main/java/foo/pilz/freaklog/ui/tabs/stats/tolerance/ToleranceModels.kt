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

package foo.pilz.freaklog.ui.tabs.stats.tolerance

import androidx.compose.ui.graphics.Color
import java.time.Instant

enum class ToleranceType {
    FULL,
    HALF
}

data class ToleranceWindow(
    val substanceName: String,
    val start: Instant,
    val end: Instant,
    val toleranceType: ToleranceType,
    val substanceColor: Color
) {
    val barColor: Color
        get() = when (toleranceType) {
            ToleranceType.FULL -> substanceColor
            ToleranceType.HALF -> substanceColor.copy(alpha = 0.5f)
        }

    fun contains(date: Instant): Boolean {
        return date >= start && date <= end
    }
}

data class SubstanceAndDay(
    val substanceName: String,
    val day: Instant
)
