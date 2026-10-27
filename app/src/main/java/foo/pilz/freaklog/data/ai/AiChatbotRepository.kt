package foo.pilz.freaklog.data.ai

import android.util.Log
import foo.pilz.freaklog.data.ai.profiles.AiProfile
import foo.pilz.freaklog.data.ai.profiles.AiProfileStore
import foo.pilz.freaklog.data.ai.profiles.aiClientFor
import foo.pilz.freaklog.data.ai.provider.AiConversation
import foo.pilz.freaklog.data.ai.provider.AiEndpoint
import foo.pilz.freaklog.data.room.experiences.ExperienceDao
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsCompanionsAndRatings
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import kotlinx.coroutines.flow.firstOrNull
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AiChatSessionResult {
    /** [contextLabel] tells the user what the assistant already knows about. */
    data class Ready(
        val conversation: AiConversation,
        val profile: AiProfile,
        val contextLabel: String,
    ) : AiChatSessionResult

    data object Disabled : AiChatSessionResult

    /** Something is missing in Settings → AI assistant. */
    data class NeedsSetup(val message: String) : AiChatSessionResult
    data class Failed(val message: String) : AiChatSessionResult
}

/**
 * Builds [AiConversation]s for the active provider profile, configured with a journal-aware
 * system instruction and the function-calling tools the model may invoke.
 *
 * A fresh conversation is created on every call to [createChatSession] so that settings
 * changes (profile, API key, model) are picked up immediately.
 */
@Singleton
class AiChatbotRepository @Inject constructor(
    private val experienceDao: ExperienceDao,
    private val userPreferences: UserPreferences,
    private val tools: AiTools,
    private val profileStore: AiProfileStore,
) {
    companion object {
        private const val TAG = "AiChatbotRepository"
        private val NOW_FORMATTER = DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd HH:mm", Locale.US)
    }

    suspend fun createChatSession(experienceId: Int?): AiChatSessionResult {
        val enabled = userPreferences.aiAssistantEnabledFlow.firstOrNull() ?: false
        if (!enabled) {
            Log.d(TAG, "AI assistant is disabled – skipping session creation")
            return AiChatSessionResult.Disabled
        }
        profileStore.migrateLegacySettings()
        val profile = profileStore.activeProfile()
            ?: return AiChatSessionResult.NeedsSetup("Add an AI provider to start chatting.")
        val apiKey = profileStore.apiKey(profile.id)
        if (profile.preset.requiresApiKey && apiKey.isBlank()) {
            return AiChatSessionResult.NeedsSetup("The profile \"${profile.name}\" has no API key.")
        }
        if (profile.model.isBlank()) {
            return AiChatSessionResult.NeedsSetup("Choose a model for the profile \"${profile.name}\".")
        }
        if (profile.baseUrl.isBlank()) {
            return AiChatSessionResult.NeedsSetup("Enter the server address for \"${profile.name}\".")
        }

        return try {
            val context = loadContext(experienceId)
            val conversation = AiConversation(
                client = aiClientFor(profile.standard, AiEndpoint(profile.baseUrl, apiKey)),
                model = profile.model,
                system = buildSystemInstructionText(context),
                tools = tools.specs,
                maxOutputTokens = profile.maxOutputTokens,
                executeTool = { call -> tools.execute(call.name, call.args) },
            )
            val label = context?.experience?.title?.let { "This experience: $it" } ?: "Whole journal"
            AiChatSessionResult.Ready(conversation, profile, label)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialise chat session: ${e.message}", e)
            AiChatSessionResult.Failed(e.message ?: e::class.java.simpleName)
        }
    }

    private suspend fun loadContext(experienceId: Int?) =
        experienceId?.let { experienceDao.getExperienceWithIngestionsCompanionsAndRatings(it) }

    // -----------------------------------------------------------------------------------------
    // System instruction
    // -----------------------------------------------------------------------------------------

    private fun buildSystemInstructionText(current: ExperienceWithIngestionsCompanionsAndRatings?): String {
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
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        sb.append("Current local time: ${NOW_FORMATTER.format(now)} (${now.zone.id}).\n\n")
        if (current == null) {
            sb.append(
                "The user opened you from the journal overview, not from a specific experience. " +
                    "Use the tools to find the sessions they mean.\n\n"
            )
        } else {
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
                        val dose = ing.dose?.let { "$it ${ing.units.orEmpty()}".trim() } ?: "unknown dose"
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
        sb.append(
            "When the user asks about anything beyond what is summarised above (older experiences, " +
                "totals, search by name, etc.) you MUST use a tool to look it up rather than refusing or hallucinating.\n"
        )
        return sb.toString()
    }
}
