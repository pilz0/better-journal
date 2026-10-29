/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.data.room.webhooks

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import foo.pilz.freaklog.data.room.webhooks.entities.Webhook
import kotlinx.coroutines.flow.Flow

@Dao
interface WebhookDao {

    @Query("SELECT * FROM webhook ORDER BY sortOrder ASC, id ASC")
    fun getAllFlow(): Flow<List<Webhook>>

    @Query("SELECT * FROM webhook ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<Webhook>

    @Query("SELECT * FROM webhook WHERE isEnabled = 1 ORDER BY sortOrder ASC, id ASC")
    fun getEnabledFlow(): Flow<List<Webhook>>

    @Query("SELECT * FROM webhook WHERE isEnabled = 1 ORDER BY sortOrder ASC, id ASC")
    suspend fun getEnabled(): List<Webhook>

    @Query("SELECT * FROM webhook WHERE id = :id")
    suspend fun getById(id: Int): Webhook?

    @Query("SELECT COUNT(*) FROM webhook")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(webhook: Webhook): Long

    @Update
    suspend fun updateRow(webhook: Webhook)

    @Upsert
    suspend fun upsertRow(webhook: Webhook): Long

    @Query("DELETE FROM ingestion_webhook_message WHERE webhookId = :webhookId")
    suspend fun invalidateMessages(webhookId: Int)

    @Transaction
    suspend fun update(webhook: Webhook) {
        if (getById(webhook.id)?.url != webhook.url) invalidateMessages(webhook.id)
        updateRow(webhook)
    }

    @Transaction
    suspend fun upsert(webhook: Webhook): Long {
        if (getById(webhook.id)?.url != webhook.url) invalidateMessages(webhook.id)
        return upsertRow(webhook)
    }

    @Delete
    suspend fun delete(webhook: Webhook)

    @Query("DELETE FROM webhook")
    suspend fun deleteAll()
}
