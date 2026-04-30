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

import foo.pilz.freaklog.data.room.webhooks.entities.Webhook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebhookRepository @Inject constructor(private val webhookDao: WebhookDao) {

    fun getAllFlow(): Flow<List<Webhook>> = webhookDao.getAllFlow()
        .flowOn(Dispatchers.IO)
        .conflate()

    fun getEnabledFlow(): Flow<List<Webhook>> = webhookDao.getEnabledFlow()
        .flowOn(Dispatchers.IO)
        .conflate()

    suspend fun getAll(): List<Webhook> = webhookDao.getAll()

    suspend fun getEnabled(): List<Webhook> = webhookDao.getEnabled()

    suspend fun getById(id: Int): Webhook? = webhookDao.getById(id)

    suspend fun count(): Int = webhookDao.count()

    suspend fun insert(webhook: Webhook): Long = webhookDao.insert(webhook)

    suspend fun update(webhook: Webhook) = webhookDao.update(webhook)

    suspend fun upsert(webhook: Webhook): Long = webhookDao.upsert(webhook)

    suspend fun delete(webhook: Webhook) = webhookDao.delete(webhook)

    suspend fun deleteAll() = webhookDao.deleteAll()
}
