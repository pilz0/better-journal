package foo.pilz.freaklog.data.ai

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.TextPart
import com.google.ai.client.generativeai.type.Tool
import com.google.ai.client.generativeai.type.defineFunction
import foo.pilz.freaklog.data.room.experiences.ExperienceDao
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds [GenerativeModel] instances configured with a journal-aware system
 * instruction and the function-calling tools declared by [AiTools]. The model
 * itself is recreated on demand so settings changes (api key, model name) take
 * effect immediately.
 */
@Singleton
class AiChatbotRepository @Inject constructor(
    private val experienceDao: ExperienceDao,
    private val userPreferences: UserPreferences
) {
    companion object {
        const val DEFAULT_MODEL_NAME = "gemini-2.5-flash"
        private const val TAG = "AiChatbotRepository"
    }

    /** Result of a model-construction attempt, exposing the resolved model name to the UI. */
    data class ReadyModel(val model: GenerativeModel, val modelName: String)

    suspend fun getGenerativeModelReady(experienceId: Int?): ReadyModel? {
        val apiKey = userPreferences.aiApiKeyFlow.firstOrNull().orEmpty()
        val configuredName = userPreferences.aiModelNameFlow.firstOrNull().orEmpty()
        val modelName = configuredName.ifBlank { DEFAULT_MODEL_NAME }

        if (apiKey.isBlank()) {
            Log.w(TAG, "API key is empty - cannot create GenerativeModel")
            return null
        }

        return try {
            val model = GenerativeModel(
                modelName = modelName,
                apiKey = apiKey,
                systemInstruction = Content(
                    role = "system",
                    parts = listOf(TextPart(buildSystemInstruction(experienceId)))
                ),
                tools = listOf(buildToolset())
            )
            ReadyModel(model, modelName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize GenerativeModel: ${e.message}", e)
            null
        }
    }

    /** Builds the system instruction with a snapshot of the current session. */
    private suspend fun buildSystemInstruction(experienceId: Int?): String {
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
                            val dose = ing.dose?.let { "$it ${ing.units.orEmpty()}".trim() } ?: "unknown dose"
                            sb.append(
                                "    - ${AI_DATE_FORMATTER.format(ing.time)} • ${ing.substanceName} • $dose • ${ing.administrationRoute.name}\n"
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

    private fun buildToolset(): Tool = Tool(
        functionDeclarations = listOf(
            defineFunction(
                name = "list_recent_experiences",
                description = "List the most recent journal experiences (title, date, substances, id). " +
                    "Use this for an overview of recent sessions.",
                parameters = listOf(
                    Schema.int("limit", "Maximum number of experiences to return (default 10, max 50).")
                )
            ),
            defineFunction(
                name = "search_experiences",
                description = "Full-text search the user's experience titles and notes for the given query. " +
                    "Use this when the user asks 'find my experience about X' or references words likely in titles/notes.",
                parameters = listOf(
                    Schema.str("query", "Substring to search for in title or notes (case-insensitive)."),
                    Schema.int("limit", "Maximum number of results (default 10, max 50).")
                ),
                requiredParameters = listOf("query")
            ),
            defineFunction(
                name = "search_experiences_by_substance",
                description = "Find past experiences in which a given substance was logged. " +
                    "Use this for questions like 'when did I last take MDMA?' or 'show all my LSD trips'.",
                parameters = listOf(
                    Schema.str("substance", "Substance name to search for (case-insensitive substring match)."),
                    Schema.int("limit", "Maximum number of results (default 10, max 50).")
                ),
                requiredParameters = listOf("substance")
            ),
            defineFunction(
                name = "get_experience_details",
                description = "Fetch the full details of one past experience: notes, every ingestion, and Shulgin ratings. " +
                    "Call this after `search_experiences` or `list_recent_experiences` when more detail is needed.",
                parameters = listOf(
                    Schema.int("experience_id", "The experience id returned by another tool.")
                ),
                requiredParameters = listOf("experience_id")
            ),
            defineFunction(
                name = "get_recent_ingestions",
                description = "List individual ingestions across all experiences within the last N days, optionally filtered by substance.",
                parameters = listOf(
                    Schema.int("days_back", "How many days back to look (default 30, max 730)."),
                    Schema.str("substance", "Optional substance name filter (case-insensitive substring match).")
                )
            ),
            defineFunction(
                name = "get_substance_usage_stats",
                description = "Aggregate ingestion counts per substance over the last N days, sorted by frequency. " +
                    "Use this for usage-pattern questions ('what have I been using most this year?').",
                parameters = listOf(
                    Schema.int("days_back", "Window in days for the aggregation (default 90, max 1825).")
                )
            )
        )
    )
}
