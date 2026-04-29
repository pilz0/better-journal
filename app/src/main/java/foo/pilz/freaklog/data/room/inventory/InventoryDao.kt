/*
 * Copyright (c) 2026. FreakLog contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package foo.pilz.freaklog.data.room.inventory

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {

    @Query("SELECT * FROM InventoryItem ORDER BY addedAt DESC")
    fun getAllFlow(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM InventoryItem ORDER BY addedAt DESC")
    suspend fun getAll(): List<InventoryItem>

    @Insert
    suspend fun insert(item: InventoryItem): Long

    @Update
    suspend fun update(item: InventoryItem)

    @Delete
    suspend fun delete(item: InventoryItem)

    @Query("DELETE FROM InventoryItem")
    suspend fun deleteAll()
}
