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

import foo.pilz.freaklog.data.room.experiences.entities.Spray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SprayRepository @Inject constructor(private val sprayDao: SprayDao) {
    
    fun getAllSpraysFlow(): Flow<List<Spray>> = sprayDao.getAllSpraysFlow()
        .flowOn(Dispatchers.IO)
        .conflate()

    suspend fun getAllSprays(): List<Spray> = sprayDao.getAllSprays()

    suspend fun getPreferredSpray(): Spray? = sprayDao.getPreferredSpray()

    suspend fun getSprayById(id: Int): Spray? = sprayDao.getSprayById(id)

    suspend fun insert(spray: Spray): Long = sprayDao.insert(spray)

    suspend fun update(spray: Spray) = sprayDao.update(spray)

    suspend fun delete(spray: Spray) = sprayDao.delete(spray)

    suspend fun setPreferred(id: Int) {
        sprayDao.clearAllPreferred()
        sprayDao.setPreferred(id)
    }

    suspend fun deleteAll() = sprayDao.deleteAll()
}
