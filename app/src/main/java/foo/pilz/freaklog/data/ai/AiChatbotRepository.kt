package foo.pilz.freaklog.data.ai

import android.util.Log
import foo.pilz.freaklog.data.room.experiences.ExperienceDao
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds [GeminiChatSession] instances configured with a journal-aware system instruction
 * and the function-calling tools the model may invoke.
 *
 * A fresh session is created on every call to [getGenerativeModelReady] so that settings
 * changes (API key, model name) are picked up immediately.
 *
 * ### Why we no longer use the `generative-ai-android` SDK
 * The archived SDK (v0.9.0) strips the `thoughtSignature` field that Gemini 2.5 and 3
 * models attach to every `functionCall` part.  Omitting that signature on the next turn
 * causes the API to return HTTP 400 ("Function call is missing a thought_signature").
 * [GeminiChatSession] stores conversation history as raw JSON and therefore preserves
 * every field automatically.
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

    /** Result of a successful session initialisation, exposing the resolved model name to the UI. */
    data class ReadySession(val session: GeminiChatSession, val modelName: String)

    suspend fun getGenerativeModelReady(experienceId: Int?): ReadySession? {
        val apiKey = userPreferences.aiApiKeyFlow.firstOrNull().orEmpty()
        val configuredName = userPreferences.aiModelNameFlow.firstOrNull().orEmpty()
        val modelName = configuredName.ifBlank { DEFAULT_MODEL_NAME }

        if (apiKey.isBlank()) {
            Log.w(TAG, "API key is empty – cannot create chat session")
            return null
        }

        return try {
            val systemInstruction = buildSystemInstructionJson(experienceId)
            val tools = buildToolsJson()
            val session = GeminiChatSession(apiKey, modelName, systemInstruction, tools)
            ReadySession(session, modelName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialise chat session: ${e.message}", e)
            null
        }
    }

    // -----------------------------------------------------------------------------------------
    // System instruction
    // -----------------------------------------------------------------------------------------

    private suspend fun buildSystemInstructionJson(experienceId: Int?): JSONObject =
        JSONObject().apply {
            put("parts", JSONArray().apply {
                put(JSONObject().put("text", buildSystemInstructionText(experienceId)))
            })
        }

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

    // -----------------------------------------------------------------------------------------
    // Tool / function-declaration JSON
    // -----------------------------------------------------------------------------------------

    private fun buildToolsJson(): JSONArray {
        val decls = JSONArray().apply {
            put(functionDecl(
                name = "list_recent_experiences",
                description = "List the most recent journal experiences (title, date, substances, id). " +
                    "Use this for an overview of recent sessions.",
                params = listOf(
                    Param("limit", "INTEGER", "Maximum number of experiences to return (default 10, max 50).")
                )
            ))
            put(functionDecl(
                name = "search_experiences",
                description = "Full-text search the user's experience titles and notes for the given query. " +
                    "Use this when the user asks 'find my experience about X' or references words likely in titles/notes.",
                params = listOf(
                    Param("query", "STRING", "Substring to search for in title or notes (case-insensitive).", required = true),
                    Param("limit", "INTEGER", "Maximum number of results (default 10, max 50).")
                )
            ))
            put(functionDecl(
                name = "search_experiences_by_substance",
                description = "Find past experiences in which a given substance was logged. " +
                    "Use this for questions like 'when did I last take MDMA?' or 'show all my LSD trips'.",
                params = listOf(
                    Param("substance", "STRING", "Substance name to search for (case-insensitive substring match).", required = true),
                    Param("limit", "INTEGER", "Maximum number of results (default 10, max 50).")
                )
            ))
            put(functionDecl(
                name = "get_experience_details",
                description = "Fetch the full details of one past experience: notes, every ingestion, and Shulgin ratings. " +
                    "Call this after `search_experiences` or `list_recent_experiences` when more detail is needed. " +
                    "The `notes` field is capped (default 2000 chars); the response sets `notes_truncated` and " +
                    "`notes_total_chars` so you can decide whether to ask for a larger excerpt.",
                params = listOf(
                    Param("experience_id", "INTEGER", "The experience id returned by another tool.", required = true),
                    Param(
                        "max_notes_chars", "INTEGER",
                        "Optional cap on how many characters of the free-text notes to include " +
                            "(default 2000, max 8000). Use a smaller value for quick lookups."
                    )
                )
            ))
            put(functionDecl(
                name = "get_recent_ingestions",
                description = "List individual ingestions across all experiences within the last N days, optionally filtered by substance.",
                params = listOf(
                    Param("days_back", "INTEGER", "How many days back to look (default 30, max 730)."),
                    Param("substance", "STRING", "Optional substance name filter (case-insensitive substring match).")
                )
            ))
            put(functionDecl(
                name = "get_substance_usage_stats",
                description = "Aggregate ingestion counts per substance over the last N days, sorted by frequency. " +
                    "Use this for usage-pattern questions ('what have I been using most this year?').",
                params = listOf(
                    Param("days_back", "INTEGER", "Window in days for the aggregation (default 90, max 1825).")
                )
            ))
        }
        return JSONArray().apply {
            put(JSONObject().put("functionDeclarations", decls))
        }
    }

    /** Lightweight descriptor for a single function parameter. */
    private data class Param(
        val name: String,
        val type: String,   // Gemini schema type: "STRING", "INTEGER", etc.
        val description: String,
        val required: Boolean = false
    )

    private fun functionDecl(
        name: String,
        description: String,
        params: List<Param>
    ): JSONObject {
        val properties = JSONObject()
        val required = JSONArray()
        params.forEach { p ->
            properties.put(p.name, JSONObject().apply {
                put("type", p.type)
                put("description", p.description)
            })
            if (p.required) required.put(p.name)
        }
        val parameters = JSONObject().apply {
            put("type", "OBJECT")
            put("properties", properties)
            if (required.length() > 0) put("required", required)
        }
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", parameters)
        }
    }
}
