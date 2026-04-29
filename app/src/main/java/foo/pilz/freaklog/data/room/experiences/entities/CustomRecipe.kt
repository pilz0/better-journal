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

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity
data class CustomRecipe(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val creationDate: Instant = Instant.now(),
    val lastUsedDate: Instant? = null,
    val isArchived: Boolean = false,
    val note: String = ""
)
