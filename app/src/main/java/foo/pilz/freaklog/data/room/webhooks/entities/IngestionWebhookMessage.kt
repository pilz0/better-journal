/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.data.room.webhooks.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import java.time.Instant

/**
 * Link table tracking which Discord message-id was returned for each
 * (ingestion, webhook) pair. Lets us edit and delete the message later.
 *
 * Replaces the legacy `Ingestion.webhookMessageId` column (kept around for
 * one release while existing data is seeded into this table).
 */
@Entity(
    tableName = "ingestion_webhook_message",
    foreignKeys = [
        ForeignKey(
            entity = Ingestion::class,
            parentColumns = ["id"],
            childColumns = ["ingestionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Webhook::class,
            parentColumns = ["id"],
            childColumns = ["webhookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["ingestionId", "webhookId"], unique = true),
        Index(value = ["webhookId"])
    ]
)
data class IngestionWebhookMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val ingestionId: Int,
    val webhookId: Int,
    /** Discord message id returned from the POST. */
    val messageId: String,
    val sentAt: Instant = Instant.now()
)
