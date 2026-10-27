package foo.pilz.freaklog.data.ai

import android.util.Log
import foo.pilz.freaklog.data.ai.provider.AiConversation
import foo.pilz.freaklog.data.ai.provider.AiEndpoint
import foo.pilz.freaklog.data.ai.provider.GeminiClient
import foo.pilz.freaklog.data.room.experiences.ExperienceDao
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AiChatSessionResult {
    data class Ready(val conversation: AiConversation, val modelName: String) : AiChatSessionResult
    data object Disabled : AiChatSessionResult
    data object MissingApiKey : AiChatSessionResult
    data class Failed(val message: String) : AiChatSessionResult
}

/**
 * Builds [AiConversation]s configured with a journal-aware system instruction and the
 * function-calling tools the model may invoke.
 *
 * A fresh conversation is created on every call to [createChatSession] so that settings
 * changes (API key, model name) are picked up immediately.
 */
@Singleton
class AiChatbotRepository @Inject constructor(
    private val experienceDao: ExperienceDao,
    private val userPreferences: UserPreferences,
    private val tools: AiTools,
) {
    companion object {
        const val DEFAULT_MODEL_NAME = "gemini-2.5-flash"
        const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        const val DEFAULT_MAX_OUTPUT_TOKENS = 16_000
        private const val TAG = "AiChatbotRepository"
    }

    suspend fun createChatSession(experienceId: Int?): AiChatSessionResult {
        val enabled = userPreferences.aiAssistantEnabledFlow.firstOrNull() ?: false
        if (!enabled) {
            Log.d(TAG, "AI assistant is disabled – skipping session creation")
            return AiChatSessionResult.Disabled
        }

        val apiKey = userPreferences.aiApiKeyFlow.firstOrNull().orEmpty()
        if (apiKey.isBlank()) {
            Log.w(TAG, "API key is empty – cannot create chat session")
            return AiChatSessionResult.MissingApiKey
        }

        val configuredName = userPreferences.aiModelNameFlow.firstOrNull().orEmpty()
        val modelName = configuredName.ifBlank { DEFAULT_MODEL_NAME }

        return try {
            val conversation = AiConversation(
                client = GeminiClient(AiEndpoint(GEMINI_BASE_URL, apiKey)),
                model = modelName,
                system = buildSystemInstructionText(experienceId),
                tools = tools.specs,
                maxOutputTokens = DEFAULT_MAX_OUTPUT_TOKENS,
                executeTool = { call -> tools.execute(call.name, call.args) },
            )
            AiChatSessionResult.Ready(conversation, modelName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialise chat session: ${e.message}", e)
            AiChatSessionResult.Failed(e.message ?: e::class.java.simpleName)
        }
    }

    // -----------------------------------------------------------------------------------------
    // System instruction
    // -----------------------------------------------------------------------------------------

    private suspend fun buildSystemInstructionText(experienceId: Int?): String {
        val sb = StringBuilder()
        sb.append(
            "You are the in-app assistant for FreakLog, a personal substance journal. " +
                "Your goals, in order of priority:\n" +
                "  1. Keep the user safe with harm-reduction information and warnings.\n" +
                "  2. Help them research their own past experiences and ingestion patterns.\n" +
                "  3. Suggest complementary activities or substances only when relevant and safe.\n\n" +
                "You have access to function-calling tools that query the user's journal database. " +
                "Prefer calling a tool over guessing - the user's data is private to this device and you " +
                "are the only way for them to query it conversationally. Always cite the dates and " +
                "experience titles you found when summarising past sessions. Use Markdown for formatting " +
                "(headings, lists, **bold**). Be concise.\n\n"
        )
        if (experienceId != null) {
            val current = experienceDao.getExperienceWithIngestionsCompanionsAndRatings(experienceId)
            if (current != null) {
                sb.append("## Current session\n")
                sb.append("- Title: ${current.experience.title}\n")
                sb.append("- Date: ${AI_DATE_FORMATTER.format(current.experience.sortDate)}\n")
                sb.append("- Experience id (for tools): ${current.experience.id}\n")
                if (current.ingestionsWithCompanions.isEmpty()) {
                    sb.append("- No ingestions logged in this session yet.\n")
                } else {
                    sb.append("- Ingestions:\n")
                    current.ingestionsWithCompanions
                        .sortedBy { it.ingestion.time }
                        .forEach { ic ->
                            val ing = ic.ingestion
                            val dose =
                                ing.dose?.let { "$it ${ing.units.orEmpty()}".trim() } ?: "unknown dose"
                            sb.append(
                                "    - ${AI_DATE_FORMATTER.format(ing.time)} • ${ing.substanceName}" +
                                    " • $dose • ${ing.administrationRoute.name}\n"
                            )
                        }
                }
                if (current.experience.text.isNotBlank()) {
                    sb.append("- Notes excerpt: ${current.experience.text.take(400)}\n")
                }
                sb.append('\n')
            }
        }
        sb.append(
            "When the user asks about anything beyond what is summarised above (older experiences, " +
                "totals, search by name, etc.) you MUST use a tool to look it up rather than refusing or hallucinating.\n"
        )
        return sb.toString()
    }
}
