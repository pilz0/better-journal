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

import foo.pilz.freaklog.data.room.webhooks.entities.IngestionWebhookMessage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IngestionWebhookMessageRepository @Inject constructor(
    private val dao: IngestionWebhookMessageDao
) {
    suspend fun getByIngestion(ingestionId: Int): List<IngestionWebhookMessage> =
        dao.getByIngestion(ingestionId)

    suspend fun getByIngestionAndWebhook(
        ingestionId: Int,
        webhookId: Int
    ): IngestionWebhookMessage? = dao.getByIngestionAndWebhook(ingestionId, webhookId)

    suspend fun insert(message: IngestionWebhookMessage): Long = dao.insert(message)

    suspend fun delete(message: IngestionWebhookMessage) = dao.delete(message)

    suspend fun deleteByIngestion(ingestionId: Int) = dao.deleteByIngestion(ingestionId)

    suspend fun deleteAll() = dao.deleteAll()
}
