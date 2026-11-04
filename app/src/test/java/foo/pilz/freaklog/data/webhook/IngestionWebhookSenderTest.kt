package foo.pilz.freaklog.data.webhook

import assertk.assertThat
import assertk.assertions.containsExactly
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.webhooks.IngestionWebhookMessageRepository
import foo.pilz.freaklog.data.room.webhooks.WebhookRepository
import foo.pilz.freaklog.data.room.webhooks.entities.IngestionWebhookMessage
import foo.pilz.freaklog.data.room.webhooks.entities.Webhook
import foo.pilz.freaklog.data.substances.AdministrationRoute
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class IngestionWebhookSenderTest {
    private val webhookService = mockk<WebhookService>()
    private val webhookRepository = mockk<WebhookRepository>()
    private val messageRepository = mockk<IngestionWebhookMessageRepository>()
    private val experienceRepo = mockk<ExperienceRepository>()
    private val sender = IngestionWebhookSender(
        webhookService,
        webhookRepository,
        messageRepository,
        experienceRepo,
    )

    private val friends = Webhook(id = 1, name = "Friends", url = "https://discord.com/api/webhooks/1/a")
    private val personal = Webhook(id = 2, name = "Personal", url = "https://discord.com/api/webhooks/2/b")

    private fun ingestion(id: Int, substance: String) = Ingestion(
        id = id,
        substanceName = substance,
        time = Instant.parse("2026-11-04T12:00:00Z"),
        endTime = null,
        administrationRoute = AdministrationRoute.ORAL,
        dose = 100.0,
        isDoseAnEstimate = false,
        estimatedDoseStandardDeviation = null,
        units = "mg",
        experienceId = 7,
        notes = null,
        stomachFullness = null,
        consumerName = null,
        customUnitId = null,
    )

    private fun stubSendReturningIdsFor(substances: Map<String, String>) {
        coEvery {
            webhookService.sendWebhook(
                url = any(), user = any(), substance = any(), dose = any(), units = any(),
                isEstimate = any(), route = any(), site = any(), note = any(), template = any(),
                isHyperlinked = any(), substanceInfoUrl = any(), formulation = any(), saltForm = any(),
            )
        } answers {
            WebhookResult(success = true, messageId = substances[thirdArg()], error = null)
        }
    }

    @Test
    fun `sends every ingestion of a group to each selected webhook and records the messages`() = runTest {
        coEvery { webhookRepository.getEnabled() } returns listOf(friends, personal)
        coEvery { experienceRepo.getWebhookDisplayValues(any()) } answers {
            firstArg<Ingestion>().let { it.dose to it.units }
        }
        stubSendReturningIdsFor(mapOf("MDMA" to "m1", "Caffeine" to "m2"))
        val stored = mutableListOf<IngestionWebhookMessage>()
        coEvery { messageRepository.insertForDestination(capture(stored), any()) } returns 1L

        sender.send(
            listOf(ingestion(10, "MDMA"), ingestion(11, "Caffeine")),
            selection = mapOf(friends.id to true, personal.id to false),
        )

        coVerify(exactly = 2) {
            webhookService.sendWebhook(
                url = friends.url, user = any(), substance = any(), dose = any(), units = any(),
                isEstimate = any(), route = any(), site = any(), note = any(), template = any(),
                isHyperlinked = any(), substanceInfoUrl = any(), formulation = any(), saltForm = any(),
            )
        }
        coVerify(exactly = 0) {
            webhookService.sendWebhook(
                url = personal.url, user = any(), substance = any(), dose = any(), units = any(),
                isEstimate = any(), route = any(), site = any(), note = any(), template = any(),
                isHyperlinked = any(), substanceInfoUrl = any(), formulation = any(), saltForm = any(),
            )
        }
        assertThat(stored.map { Triple(it.ingestionId, it.webhookId, it.messageId) }).containsExactly(
            Triple(10, friends.id, "m1"),
            Triple(11, friends.id, "m2"),
        )
    }

    @Test
    fun `webhooks missing from the selection are sent to`() = runTest {
        coEvery { webhookRepository.getEnabled() } returns listOf(friends)
        coEvery { experienceRepo.getWebhookDisplayValues(any()) } returns (100.0 to "mg")
        stubSendReturningIdsFor(mapOf("MDMA" to "m1"))
        coEvery { messageRepository.insertForDestination(any(), any()) } returns 1L

        sender.send(listOf(ingestion(10, "MDMA")), selection = emptyMap())

        coVerify(exactly = 1) { messageRepository.insertForDestination(any(), friends.url) }
    }

    @Test
    fun `a failing webhook does not stop the remaining ingestions`() = runTest {
        coEvery { webhookRepository.getEnabled() } returns listOf(friends)
        coEvery { experienceRepo.getWebhookDisplayValues(any()) } returns (100.0 to "mg")
        coEvery {
            webhookService.sendWebhook(
                url = any(), user = any(), substance = "MDMA", dose = any(), units = any(),
                isEstimate = any(), route = any(), site = any(), note = any(), template = any(),
                isHyperlinked = any(), substanceInfoUrl = any(), formulation = any(), saltForm = any(),
            )
        } throws IllegalStateException("boom")
        coEvery {
            webhookService.sendWebhook(
                url = any(), user = any(), substance = "Caffeine", dose = any(), units = any(),
                isEstimate = any(), route = any(), site = any(), note = any(), template = any(),
                isHyperlinked = any(), substanceInfoUrl = any(), formulation = any(), saltForm = any(),
            )
        } returns WebhookResult(success = true, messageId = "m2", error = null)
        val stored = mutableListOf<IngestionWebhookMessage>()
        coEvery { messageRepository.insertForDestination(capture(stored), any()) } returns 1L

        sender.send(listOf(ingestion(10, "MDMA"), ingestion(11, "Caffeine")), selection = emptyMap())

        assertThat(stored.map { it.ingestionId to it.messageId }).containsExactly(11 to "m2")
    }

    @Test
    fun `nothing is sent without enabled webhooks`() = runTest {
        coEvery { webhookRepository.getEnabled() } returns emptyList()

        sender.send(listOf(ingestion(10, "MDMA")), selection = emptyMap())

        coVerify(exactly = 0) { experienceRepo.getWebhookDisplayValues(any()) }
    }
}
