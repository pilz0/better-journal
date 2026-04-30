/*
 * Copyright (c) 2026. Freaklog contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package foo.pilz.freaklog.data.room.webhooks

import foo.pilz.freaklog.data.room.experiences.ExperienceDao
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.webhooks.entities.IngestionWebhookMessage
import foo.pilz.freaklog.data.room.webhooks.entities.Webhook
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WebhookSeederTest {

    private val userPrefs = mockk<UserPreferences>(relaxed = true)
    private val experienceDao = mockk<ExperienceDao>(relaxed = true)

    private val webhookStore = mutableListOf<Webhook>()
    private val ingestionLinks = mutableListOf<IngestionWebhookMessage>()
    private var seededFlag = false

    private val webhookRepository: WebhookRepository = run {
        val dao = mockk<WebhookDao>(relaxed = true)
        coEvery { dao.count() } answers { webhookStore.size }
        coEvery { dao.insert(any()) } answers {
            val w = firstArg<Webhook>()
            val newId = webhookStore.size + 1
            webhookStore.add(w.copy(id = newId))
            newId.toLong()
        }
        coEvery { dao.getAll() } answers { webhookStore.toList() }
        WebhookRepository(dao)
    }

    private val ingestionWebhookMessageRepository: IngestionWebhookMessageRepository = run {
        val dao = mockk<IngestionWebhookMessageDao>(relaxed = true)
        coEvery { dao.insert(any()) } answers {
            val m = firstArg<IngestionWebhookMessage>()
            val newId = ingestionLinks.size + 1
            ingestionLinks.add(m.copy(id = newId))
            newId.toLong()
        }
        coEvery { dao.getByIngestion(any()) } answers {
            val ingestionId = firstArg<Int>()
            ingestionLinks.filter { it.ingestionId == ingestionId }
        }
        IngestionWebhookMessageRepository(dao)
    }

    private fun seeder() = WebhookSeeder(
        webhookRepository,
        ingestionWebhookMessageRepository,
        experienceDao,
        userPrefs
    )

    private fun stubPrefs(
        seeded: Boolean = false,
        url: String = "",
        name: String = "",
        template: String = ""
    ) {
        seededFlag = seeded
        coEvery { userPrefs.isWebhookSeeded() } answers { seededFlag }
        coEvery { userPrefs.markWebhookSeeded() } answers { seededFlag = true }
        coEvery { userPrefs.readWebhookURL() } returns flowOf(url)
        coEvery { userPrefs.readWebhookName() } returns flowOf(name)
        coEvery { userPrefs.readWebhookTemplate() } returns flowOf(template)
    }

    @Test
    fun `does nothing when already seeded`() = runTest {
        stubPrefs(seeded = true, url = "https://discord.com/api/webhooks/x/y")
        seeder().seedIfNeeded()
        assertTrue(webhookStore.isEmpty())
        assertTrue(ingestionLinks.isEmpty())
    }

    @Test
    fun `marks seeded when there is no legacy URL`() = runTest {
        stubPrefs(seeded = false, url = "")
        seeder().seedIfNeeded()
        assertTrue(webhookStore.isEmpty())
        assertTrue(seededFlag)
    }

    @Test
    fun `seeds a webhook from legacy preferences and migrates message ids`() = runTest {
        stubPrefs(
            seeded = false,
            url = "https://discord.com/api/webhooks/123/abc",
            name = "Friends",
            template = "{user}: {substance}"
        )
        val ing1 = ingestion(id = 11, msgId = "msg-aaa")
        val ing2 = ingestion(id = 22, msgId = "msg-bbb")
        coEvery { experienceDao.getIngestionsWithLegacyWebhookMessageId() } returns listOf(ing1, ing2)

        seeder().seedIfNeeded()

        assertEquals(1, webhookStore.size)
        val webhook = webhookStore.single()
        assertEquals("Friends", webhook.name)
        assertEquals("https://discord.com/api/webhooks/123/abc", webhook.url)
        assertEquals("Friends", webhook.displayName)
        assertEquals("{user}: {substance}", webhook.template)
        assertTrue(webhook.isEnabled)

        assertEquals(2, ingestionLinks.size)
        assertEquals(
            setOf(11 to "msg-aaa", 22 to "msg-bbb"),
            ingestionLinks.map { it.ingestionId to it.messageId }.toSet()
        )
        assertTrue(ingestionLinks.all { it.webhookId == webhook.id })

        assertTrue(seededFlag)
    }

    @Test
    fun `is idempotent on re-run`() = runTest {
        stubPrefs(
            seeded = false,
            url = "https://discord.com/api/webhooks/123/abc",
            name = "Personal"
        )
        coEvery { experienceDao.getIngestionsWithLegacyWebhookMessageId() } returns emptyList()

        seeder().seedIfNeeded()
        seeder().seedIfNeeded()

        assertEquals(1, webhookStore.size)
        coVerify(atLeast = 1) { userPrefs.markWebhookSeeded() }
    }

    @Test
    fun `does not seed when the new table already has webhooks`() = runTest {
        stubPrefs(
            seeded = false,
            url = "https://discord.com/api/webhooks/123/abc",
            name = "Friends"
        )
        webhookStore += Webhook(id = 1, name = "manual", url = "https://x")

        seeder().seedIfNeeded()

        assertEquals(1, webhookStore.size)
        assertEquals("manual", webhookStore.single().name)
        assertTrue(seededFlag)
    }

    private fun ingestion(id: Int, msgId: String?) = Ingestion(
        id = id,
        substanceName = "LSD",
        time = Instant.parse("2024-01-01T00:00:00Z"),
        endTime = null,
        creationDate = Instant.parse("2024-01-01T00:00:00Z"),
        administrationRoute = AdministrationRoute.ORAL,
        dose = null,
        isDoseAnEstimate = false,
        estimatedDoseStandardDeviation = null,
        units = null,
        experienceId = 1,
        notes = null,
        stomachFullness = null,
        consumerName = null,
        customUnitId = null,
        administrationSite = null
    ).also { it.webhookMessageId = msgId }
}
