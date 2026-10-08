/*
 * Copyright (c) 2026. Freaklog.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Freaklog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Freaklog.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.data.room.experiences.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/** One manual blood pressure measurement (mmHg), optionally with a pulse (bpm), taken during an experience. */
@Entity(
    indices = [Index(value = ["experienceId"])],
    foreignKeys = [
        ForeignKey(
            entity = Experience::class,
            parentColumns = ["id"],
            childColumns = ["experienceId"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
data class BloodPressureReading(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val experienceId: Int,
    val time: Instant,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int? = null,
)
