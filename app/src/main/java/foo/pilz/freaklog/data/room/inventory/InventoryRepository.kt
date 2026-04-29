/*
 * Copyright (c) 2026. FreakLog contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package foo.pilz.freaklog.data.room.inventory

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepository @Inject constructor(
    private val dao: InventoryDao
) {
    fun getAllFlow() = dao.getAllFlow()
    suspend fun getAll() = dao.getAll()
    suspend fun insert(item: InventoryItem) = dao.insert(item)
    suspend fun update(item: InventoryItem) = dao.update(item)
    suspend fun delete(item: InventoryItem) = dao.delete(item)
    suspend fun deleteAll() = dao.deleteAll()
}
