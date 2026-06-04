/*
 * Copyright (c) 2022-2024. Isaak Hanimann.
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

import android.util.Log
import androidx.room.TypeConverter
import foo.pilz.freaklog.data.substances.AdministrationRoute

/**
 * Persists [AdministrationRoute] as a TEXT column using [AdministrationRoute.name].
 *
 * Unlike Room's built-in enum converter, this converter is defensive on read:
 * unknown values (such as removed enum constants like KINECTEEN and MEDIKINET)
 * fall back to [AdministrationRoute.ORAL] instead of throwing an exception.
 */
class AdministrationRouteConverter {
    @TypeConverter
    fun fromAdministrationRoute(route: AdministrationRoute?): String? = route?.name

    @TypeConverter
    fun toAdministrationRoute(value: String?): AdministrationRoute? {
        if (value == null) return null
        return try {
            AdministrationRoute.valueOf(value)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Unknown AdministrationRoute value: $value, falling back to ORAL")
            AdministrationRoute.ORAL
        }
    }

    companion object {
        private const val TAG = "AdministrationRouteConverter"
    }
}
