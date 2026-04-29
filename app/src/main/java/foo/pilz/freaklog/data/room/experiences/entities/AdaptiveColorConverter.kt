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

package foo.pilz.freaklog.data.room.experiences.entities

import androidx.room.TypeConverter

/**
 * Persists [AdaptiveColor] as a TEXT column. Preset colors round-trip through their
 * stable [AdaptiveColor.name] (e.g. "RED"); user-picked colors round-trip through the
 * `CUSTOM_AARRGGBB` encoding produced by [AdaptiveColor.Custom.name]. This preserves
 * backwards compatibility with existing rows written when [AdaptiveColor] was an enum.
 */
class AdaptiveColorConverter {
    @TypeConverter
    fun fromAdaptiveColor(color: AdaptiveColor): String = color.name

    @TypeConverter
    fun toAdaptiveColor(value: String): AdaptiveColor = AdaptiveColor.valueOf(value)
}
