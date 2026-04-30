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

        val legacyUrl = userPreferences.readWebhookURL().first()
        if (legacyUrl.isBlank()) {
            // Nothing to migrate.
            userPreferences.markWebhookSeeded()
            return
        }

        // Find or create the seeded webhook. If the user already has webhooks
        // (e.g. a previous run inserted the seeded row but crashed before
        // setting WEBHOOK_SEEDED), reuse the one matching the legacy URL so
        // we still migrate any missing link rows.
        val existing = webhookRepository.getAll().firstOrNull { it.url == legacyUrl }
        val seededId = existing?.id ?: run {
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
            webhookRepository.insert(seededWebhook).toInt()
        }

        val legacyIngestions = experienceDao.getIngestionsWithLegacyWebhookMessageId()
        for (ingestion in legacyIngestions) {
            val msgId = ingestion.webhookMessageId
            // Skip ingestions without a legacy message id, or ones that
            // already have a link row (resumed migration after a crash).
            val alreadyLinked = msgId != null &&
                ingestionWebhookMessageRepository
                    .getByIngestionAndWebhook(ingestion.id, seededId) != null
            if (msgId == null || alreadyLinked) continue
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
