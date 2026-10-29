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
import foo.pilz.freaklog.data.room.webhooks.entities.IngestionWebhookMessage

@Dao
interface IngestionWebhookMessageDao {

    @Query("SELECT * FROM ingestion_webhook_message WHERE ingestionId = :ingestionId")
    suspend fun getByIngestion(ingestionId: Int): List<IngestionWebhookMessage>

    @Query(
        "SELECT * FROM ingestion_webhook_message " +
            "WHERE ingestionId = :ingestionId AND webhookId = :webhookId LIMIT 1"
    )
    suspend fun getByIngestionAndWebhook(
        ingestionId: Int,
        webhookId: Int
    ): IngestionWebhookMessage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: IngestionWebhookMessage): Long

    @Query("SELECT url FROM webhook WHERE id = :webhookId")
    suspend fun destination(webhookId: Int): String?

    @Transaction
    suspend fun insertForDestination(message: IngestionWebhookMessage, url: String): Long? =
        if (destination(message.webhookId) == url) insert(message) else null

    @Query(
        "SELECT COUNT(*) FROM ingestion_webhook_message m JOIN webhook w ON w.id = m.webhookId " +
            "WHERE m.id = :id AND m.messageId = :messageId AND w.url = :url"
    )
    suspend fun isCurrentDestination(id: Int, messageId: String, url: String): Boolean

    @Delete
    suspend fun delete(message: IngestionWebhookMessage)

    @Query("DELETE FROM ingestion_webhook_message WHERE ingestionId = :ingestionId")
    suspend fun deleteByIngestion(ingestionId: Int)

    @Query("DELETE FROM ingestion_webhook_message")
    suspend fun deleteAll()
}
