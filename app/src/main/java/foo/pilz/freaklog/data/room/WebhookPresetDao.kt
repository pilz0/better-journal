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
import foo.pilz.freaklog.data.room.experiences.entities.WebhookPreset
import kotlinx.coroutines.flow.Flow

@Dao
interface WebhookPresetDao {

    @Query("SELECT * FROM webhookpreset WHERE isArchived = 0 ORDER BY creationDate ASC")
    fun getActivePresetsFlow(): Flow<List<WebhookPreset>>

    @Query("SELECT * FROM webhookpreset WHERE isArchived = 1 ORDER BY creationDate DESC")
    fun getArchivedPresetsFlow(): Flow<List<WebhookPreset>>

    @Query("SELECT * FROM webhookpreset WHERE isEnabled = 1 AND isArchived = 0")
    suspend fun getEnabledPresets(): List<WebhookPreset>

    @Query("SELECT * FROM webhookpreset WHERE id = :id")
    suspend fun getPresetById(id: Int): WebhookPreset?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preset: WebhookPreset): Long

    @Update
    suspend fun update(preset: WebhookPreset)

    @Delete
    suspend fun delete(preset: WebhookPreset)

    @Query("UPDATE webhookpreset SET isArchived = 1 WHERE id = :id")
    suspend fun archivePreset(id: Int)

    @Query("UPDATE webhookpreset SET isArchived = 0 WHERE id = :id")
    suspend fun unarchivePreset(id: Int)

    @Query("UPDATE webhookpreset SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setEnabled(id: Int, isEnabled: Boolean)

    @Query("DELETE FROM webhookpreset")
    suspend fun deleteAll()
}
