package foo.pilz.freaklog.data.webhook

import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.webhooks.IngestionWebhookMessageRepository
import foo.pilz.freaklog.data.room.webhooks.WebhookRepository
import foo.pilz.freaklog.data.room.webhooks.entities.IngestionWebhookMessage
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "IngestionWebhookSender"

/**
 * Posts newly logged ingestions to every enabled webhook and remembers the
 * returned message ids so later edits and deletes can update the messages.
 */
@Singleton
class IngestionWebhookSender @Inject constructor(
    private val webhookService: WebhookService,
    private val webhookRepository: WebhookRepository,
    private val ingestionWebhookMessageRepository: IngestionWebhookMessageRepository,
    private val experienceRepo: ExperienceRepository,
) {

    /**
     * Sends one message per ingestion, in order, to each enabled webhook.
     * [selection] maps webhook id -> "send to this webhook"; missing ids count as selected.
     * Failures are logged and never thrown, so they can't undo or block the logging itself.
     */
    suspend fun send(ingestions: List<Ingestion>, selection: Map<Int, Boolean>) {
        val enabledHooks = try {
            webhookRepository.getEnabled()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to load webhooks: ${e.message}", e)
            return
        }
        val hooks = enabledHooks.filter { webhook ->
            (selection[webhook.id] ?: true).also { isSelected ->
                if (!isSelected) {
                    android.util.Log.d(TAG, "Webhook \"${webhook.name}\" deselected for this ingestion")
                }
            }
        }
        if (hooks.isEmpty()) {
            android.util.Log.d(TAG, "No enabled webhooks selected, skipping")
            return
        }

        for (ingestion in ingestions) {
            val (displayDose, displayUnits) = try {
                experienceRepo.getWebhookDisplayValues(ingestion)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to compute webhook display values: ${e.message}", e)
                Pair(ingestion.dose, ingestion.units)
            }
            val route = ingestion.administrationRoute.displayText

            for (webhook in hooks) {
                val template = webhook.template.ifBlank { WebhookService.DEFAULT_TEMPLATE }
                val user = webhook.displayName.ifBlank { "User" }
                try {
                    android.util.Log.d(TAG, "Sending webhook \"${webhook.name}\"")
                    val result = webhookService.sendWebhook(
                        url = webhook.url,
                        user = user,
                        substance = ingestion.substanceName,
                        dose = displayDose,
                        units = displayUnits,
                        isEstimate = ingestion.isDoseAnEstimate,
                        route = route,
                        site = ingestion.administrationSite,
                        note = ingestion.notes,
                        template = template,
                        isHyperlinked = webhook.isHyperlinked,
                        formulation = ingestion.formulationName,
                        saltForm = ingestion.saltForm
                    )
                    if (result.success && result.messageId != null) {
                        ingestionWebhookMessageRepository.insertForDestination(
                            IngestionWebhookMessage(
                                ingestionId = ingestion.id,
                                webhookId = webhook.id,
                                messageId = result.messageId
                            ),
                            webhook.url
                        )
                    } else {
                        android.util.Log.w(
                            TAG,
                            "Webhook \"${webhook.name}\" send failed: " +
                                (result.error?.message ?: "Unknown error")
                        )
                    }
                } catch (e: Exception) {
                    // Silently fail - don't block ingestion creation if webhook fails
                    android.util.Log.w(TAG, "Webhook \"${webhook.name}\" exception: ${e.message}", e)
                }
            }
        }
    }
}
