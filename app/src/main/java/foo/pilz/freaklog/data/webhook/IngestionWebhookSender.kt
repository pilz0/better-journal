package foo.pilz.freaklog.data.webhook

import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.webhooks.IngestionWebhookMessageRepository
import foo.pilz.freaklog.data.room.webhooks.WebhookRepository
import foo.pilz.freaklog.data.room.webhooks.entities.IngestionWebhookMessage
import foo.pilz.freaklog.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "IngestionWebhookSender"

/**
 * Posts newly logged ingestions to every enabled webhook and remembers the returned message ids so
 * later edits and deletes can update the messages.
 */
@Singleton
class IngestionWebhookSender
@Inject
constructor(
    private val webhookService: WebhookService,
    private val webhookRepository: WebhookRepository,
    private val ingestionWebhookMessageRepository: IngestionWebhookMessageRepository,
    private val experienceRepo: ExperienceRepository,
    /** Remote deletes outlive the screen that asked for them. */
    @ApplicationScope private val remoteScope: CoroutineScope,
) {

  /**
   * Sends one message per ingestion, in order, to each enabled webhook. [selection] maps webhook id
   * -> "send to this webhook"; missing ids count as selected. Failures are logged and never thrown,
   * so they can't undo or block the logging itself.
   */
  suspend fun send(ingestions: List<Ingestion>, selection: Map<Int, Boolean>) {
    val hooks = loadSelectedHooks(selection)
    if (hooks.isEmpty()) return

    for (ingestion in ingestions) {
      val (displayDose, displayUnits) = resolveDisplayValues(ingestion)
      val route = ingestion.administrationRoute.displayText

      for (webhook in hooks) {
        val template = webhook.template.ifBlank { WebhookService.DEFAULT_TEMPLATE }
        val user = webhook.displayName.ifBlank { "User" }
        try {
          debugLog { "Sending webhook \"${webhook.name}\"" }
          val result =
              webhookService.sendWebhook(
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
                  saltForm = ingestion.saltForm,
              )
          if (result.success && result.messageId != null) {
            ingestionWebhookMessageRepository.insertForDestination(
                IngestionWebhookMessage(
                    ingestionId = ingestion.id,
                    webhookId = webhook.id,
                    messageId = result.messageId,
                ),
                webhook.url,
            )
          } else {
            android.util.Log.w(
                TAG,
                "Webhook \"${webhook.name}\" send failed: " +
                    (result.error?.message ?: "Unknown error"),
            )
          }
        } catch (ce: CancellationException) {
          throw ce
        } catch (e: Exception) {
          // Silently fail - don't block ingestion creation if webhook fails
          android.util.Log.w(TAG, "Webhook \"${webhook.name}\" exception: ${e.message}", e)
        }
      }
    }
  }

  private suspend fun loadSelectedHooks(
      selection: Map<Int, Boolean>
  ): List<foo.pilz.freaklog.data.room.webhooks.entities.Webhook> {
    val enabledHooks =
        try {
          webhookRepository.getEnabled()
        } catch (ce: CancellationException) {
          throw ce
        } catch (e: Exception) {
          android.util.Log.w(TAG, "Failed to load webhooks: ${e.message}", e)
          return emptyList()
        }
    val hooks = enabledHooks.filter { webhook ->
      (selection[webhook.id] ?: true).also { isSelected ->
        if (!isSelected) {
          debugLog { "Webhook \"${webhook.name}\" deselected for this ingestion" }
        }
      }
    }
    if (hooks.isEmpty()) {
      debugLog { "No enabled webhooks selected, skipping" }
    }
    return hooks
  }

  private suspend fun resolveDisplayValues(ingestion: Ingestion): Pair<Double?, String?> =
      try {
        experienceRepo.getWebhookDisplayValues(ingestion)
      } catch (ce: CancellationException) {
        throw ce
      } catch (e: Exception) {
        android.util.Log.w(TAG, "Failed to compute webhook display values: ${e.message}", e)
        Pair(ingestion.dose, ingestion.units)
      }

  /**
   * Deletes [ingestion] and its message on every webhook it was posted to; the remote deletes run
   * in the background.
   */
  suspend fun deleteWithMessages(ingestion: Ingestion) =
      deleteWithMessages(
          links = { ingestionWebhookMessageRepository.getByIngestion(ingestion.id) },
          deleteLocally = { experienceRepo.delete(ingestion) },
      )

  /** Deletes the experience with everything in it, and its ingestions' webhook messages. */
  suspend fun deleteExperienceWithMessages(experienceId: Int) =
      deleteWithMessages(
          links = { ingestionWebhookMessageRepository.getByExperience(experienceId) },
          deleteLocally = { experienceRepo.deleteEverythingOfExperience(experienceId) },
      )

  /**
   * The message links are read before [deleteLocally]: deleting ingestions cascades to their links,
   * which used to race the remote deletes so only the first webhook lost its message.
   */
  private suspend fun deleteWithMessages(
      links: suspend () -> List<IngestionWebhookMessage>,
      deleteLocally: suspend () -> Unit,
  ) {
    val targets =
        try {
          links().mapNotNull { link ->
            webhookRepository
                .getById(link.webhookId)
                // A changed URL is another destination that never got this message.
                ?.takeIf { ingestionWebhookMessageRepository.isCurrentDestination(link, it.url) }
                ?.let { it to link.messageId }
          }
        } catch (ce: CancellationException) {
          throw ce
        } catch (e: Exception) {
          android.util.Log.w(TAG, "Failed to load webhook messages: ${e.message}", e)
          emptyList()
        }
    deleteLocally()
    if (targets.isEmpty()) return
    remoteScope.launch {
      for ((webhook, messageId) in targets) {
        val deleted =
            try {
              webhookService.deleteWebhookMessage(url = webhook.url, messageId = messageId)
            } catch (ce: CancellationException) {
              throw ce
            } catch (e: Exception) {
              android.util.Log.w(TAG, "Webhook delete threw for \"${webhook.name}\"", e)
              false
            }
        if (!deleted) {
          android.util.Log.w(
              TAG,
              "Webhook delete failed for \"${webhook.name}\" message $messageId",
          )
        }
      }
    }
  }

  private inline fun debugLog(message: () -> String) {
    if (android.util.Log.isLoggable(TAG, android.util.Log.DEBUG)) {
      android.util.Log.d(TAG, message())
    }
  }
}
