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

package foo.pilz.freaklog.data.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import foo.pilz.freaklog.data.room.experiences.entities.Spray
import kotlinx.coroutines.flow.Flow

@Dao
interface SprayDao {

    @Query("SELECT * FROM spray ORDER BY creationDate DESC")
    fun getAllSpraysFlow(): Flow<List<Spray>>

    @Query("SELECT * FROM spray ORDER BY creationDate DESC")
    suspend fun getAllSprays(): List<Spray>

    @Query("SELECT * FROM spray WHERE isPreferred = 1 LIMIT 1")
    suspend fun getPreferredSpray(): Spray?

    @Query("SELECT * FROM spray WHERE id = :id")
    suspend fun getSprayById(id: Int): Spray?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(spray: Spray): Long

    @Update
    suspend fun update(spray: Spray)

    @Delete
    suspend fun delete(spray: Spray)

    @Query("UPDATE spray SET isPreferred = 0")
    suspend fun clearAllPreferred()

    @Query("UPDATE spray SET isPreferred = 1 WHERE id = :id")
    suspend fun setPreferred(id: Int)

    @Query("DELETE FROM spray")
    suspend fun deleteAll()
}
