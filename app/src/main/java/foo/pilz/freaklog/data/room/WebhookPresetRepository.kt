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

import foo.pilz.freaklog.data.room.experiences.entities.WebhookPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebhookPresetRepository @Inject constructor(private val webhookPresetDao: WebhookPresetDao) {
    
    fun getActivePresetsFlow(): Flow<List<WebhookPreset>> = webhookPresetDao.getActivePresetsFlow()
        .flowOn(Dispatchers.IO)
        .conflate()

    fun getArchivedPresetsFlow(): Flow<List<WebhookPreset>> = webhookPresetDao.getArchivedPresetsFlow()
        .flowOn(Dispatchers.IO)
        .conflate()

    suspend fun getEnabledPresets(): List<WebhookPreset> = webhookPresetDao.getEnabledPresets()

    suspend fun getPresetById(id: Int): WebhookPreset? = webhookPresetDao.getPresetById(id)

    suspend fun insert(preset: WebhookPreset): Long = webhookPresetDao.insert(preset)

    suspend fun update(preset: WebhookPreset) = webhookPresetDao.update(preset)

    suspend fun delete(preset: WebhookPreset) = webhookPresetDao.delete(preset)

    suspend fun archivePreset(id: Int) = webhookPresetDao.archivePreset(id)

    suspend fun unarchivePreset(id: Int) = webhookPresetDao.unarchivePreset(id)

    suspend fun setEnabled(id: Int, isEnabled: Boolean) = webhookPresetDao.setEnabled(id, isEnabled)

    suspend fun deleteAll() = webhookPresetDao.deleteAll()
}
