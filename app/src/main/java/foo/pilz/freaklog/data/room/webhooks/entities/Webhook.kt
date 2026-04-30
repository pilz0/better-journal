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
import androidx.room.PrimaryKey

/**
 * A user-configured webhook destination (Discord-compatible).
 *
 * Multiple webhooks can be configured; users choose per-ingestion which
 * ones receive a notification.
 */
@Entity(tableName = "webhook")
data class Webhook(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    /** User-visible label, e.g. "Friends server". */
    val name: String,
    /** Discord webhook URL. */
    val url: String,
    /** Value bound to the `{user}` placeholder in the message template. */
    val displayName: String = "",
    /**
     * Per-webhook message template. When blank,
     * [foo.pilz.freaklog.data.webhook.WebhookService.DEFAULT_TEMPLATE] is used.
     */
    val template: String = "",
    /** When true, render the substance name as a hyperlink to the wiki. */
    val isHyperlinked: Boolean = true,
    /** Global on/off switch without deleting. */
    val isEnabled: Boolean = true,
    /** Stable ordering for the settings list and per-ingestion checklist. */
    val sortOrder: Int = 0
)
