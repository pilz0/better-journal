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

import foo.pilz.freaklog.data.room.experiences.ExperienceDao
import foo.pilz.freaklog.data.room.webhooks.entities.IngestionWebhookMessage
import foo.pilz.freaklog.data.room.webhooks.entities.Webhook
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Migrates the legacy single-webhook configuration (stored in DataStore as
 * three free-floating string keys plus the `Ingestion.webhookMessageId`
 * column) into the new multi-webhook tables.
 *
 * Idempotent: subsequent calls are no-ops once the `WEBHOOK_SEEDED` flag
 * has been written.
 */
@Singleton
class WebhookSeeder @Inject constructor(
    private val webhookRepository: WebhookRepository,
    private val ingestionWebhookMessageRepository: IngestionWebhookMessageRepository,
    private val experienceDao: ExperienceDao,
    private val userPreferences: UserPreferences
) {
    suspend fun seedIfNeeded() {
        if (userPreferences.isWebhookSeeded()) return

        // Only proceed if there isn't already at least one webhook in the new
        // table — otherwise the user has manually configured webhooks already
        // and we should not touch them.
        if (webhookRepository.count() > 0) {
            userPreferences.markWebhookSeeded()
            return
        }

        val legacyUrl = userPreferences.readWebhookURL().first()
        if (legacyUrl.isBlank()) {
            // Nothing to migrate.
            userPreferences.markWebhookSeeded()
            return
        }

        val legacyName = userPreferences.readWebhookName().first()
        val legacyTemplate = userPreferences.readWebhookTemplate().first()

        val seededWebhook = Webhook(
            name = legacyName.ifBlank { "Migrated webhook" },
            url = legacyUrl,
            displayName = legacyName,
            template = legacyTemplate,
            isHyperlinked = true,
            isEnabled = true,
            sortOrder = 0
        )
        val seededId = webhookRepository.insert(seededWebhook).toInt()

        val legacyIngestions = experienceDao.getIngestionsWithLegacyWebhookMessageId()
        for (ingestion in legacyIngestions) {
            val msgId = ingestion.webhookMessageId ?: continue
            ingestionWebhookMessageRepository.insert(
                IngestionWebhookMessage(
                    ingestionId = ingestion.id,
                    webhookId = seededId,
                    messageId = msgId
                )
            )
        }

        userPreferences.markWebhookSeeded()
    }
}
