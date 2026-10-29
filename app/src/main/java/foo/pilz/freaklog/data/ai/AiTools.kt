package foo.pilz.freaklog.data.ai

import android.util.Log
import foo.pilz.freaklog.data.ai.anodyne.AiAnodyneTools
import foo.pilz.freaklog.data.ai.provider.AiToolSpec
import foo.pilz.freaklog.data.room.experiences.ExperienceDao
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Declarations and implementations of the function-calling tools that the AI assistant can
 * invoke: journal lookups here, substance knowledge in [AiSubstanceTools]. [execute] receives
 * the argument map parsed from the model's tool call and returns a JSONObject that is sent
 * back as the tool result.
 */
@Singleton
class AiTools @Inject constructor(
    private val experienceDao: ExperienceDao,
    private val substanceTools: AiSubstanceTools,
    private val anodyneTools: AiAnodyneTools,
) {

    companion object {
        /** Default cap on the size of any single notes blob returned to the model. */
        const val DEFAULT_MAX_NOTES_CHARS = 2000

        /** Hard cap on the size of any single notes blob returned to the model. */
        const val ABSOLUTE_MAX_NOTES_CHARS = 8000
    }

    /** Declarations of every available tool, in the provider-neutral JSON Schema form. */
    suspend fun specs(): List<AiToolSpec> =
        journalSpecs + substanceTools.specs + (if (anodyneTools.isEnabled()) anodyneTools.specs else emptyList())

    private val journalSpecs: List<AiToolSpec> = listOf(
        toolSpec(
            name = "list_recent_experiences",
            description = "List the most recent journal experiences (title, date, substances, id). " +
                "Use this for an overview of recent sessions.",
            ToolParam.integer("limit", "Maximum number of experiences to return (default 10, max 50)."),
        ),
        toolSpec(
            name = "search_experiences",
            description = "Full-text search the user's experience titles and notes for the given query. " +
                "Use this when the user asks 'find my experience about X' or references words likely in titles/notes.",
            ToolParam.string("query", "Substring to search for in title or notes (case-insensitive).", required = true),
            ToolParam.integer("limit", "Maximum number of results (default 10, max 50)."),
        ),
        toolSpec(
            name = "search_experiences_by_substance",
            description = "Find past experiences in which a given substance was logged. " +
                "Use this for questions like 'when did I last take MDMA?' or 'show all my LSD trips'.",
            ToolParam.string("substance", "Substance name to search for (case-insensitive substring match).", required = true),
            ToolParam.integer("limit", "Maximum number of results (default 10, max 50)."),
        ),
        toolSpec(
            name = "get_experience_details",
            description = "Fetch the full details of one past experience: notes, every ingestion, and Shulgin ratings. " +
                "Call this after `search_experiences` or `list_recent_experiences` when more detail is needed. " +
                "The `notes` field is capped (default 2000 chars); the response sets `notes_truncated` and " +
                "`notes_total_chars` so you can decide whether to ask for a larger excerpt.",
            ToolParam.integer("experience_id", "The experience id returned by another tool.", required = true),
            ToolParam.integer(
                "max_notes_chars",
                "Optional cap on how many characters of the free-text notes to include " +
                    "(default 2000, max 8000). Use a smaller value for quick lookups.",
            ),
        ),
        toolSpec(
            name = "get_recent_ingestions",
            description = "List individual ingestions across all experiences within the last N days, " +
                "optionally filtered by substance.",
            ToolParam.integer("days_back", "How many days back to look (default 30, max 730)."),
            ToolParam.string("substance", "Optional substance name filter (case-insensitive substring match)."),
        ),
        toolSpec(
            name = "get_substance_usage_stats",
            description = "Aggregate ingestion counts per substance over the last N days, sorted by frequency. " +
                "Use this for usage-pattern questions ('what have I been using most this year?').",
            ToolParam.integer("days_back", "Window in days for the aggregation (default 90, max 1825)."),
        ),
    )

    suspend fun execute(name: String, args: Map<String, Any?>): JSONObject {
        return try {
            when (name) {
                "list_recent_experiences" -> listRecentExperiences(
                    limit = intArg(args["limit"], 10)
                )
                "search_experiences" -> searchExperiences(
                    query = args["query"] as? String ?: "",
                    limit = intArg(args["limit"], 10)
                )
                "search_experiences_by_substance" -> searchExperiencesBySubstance(
                    substance = args["substance"] as? String ?: "",
                    limit = intArg(args["limit"], 10)
                )
                "get_experience_details" -> getExperienceDetails(
                    experienceId = intArg(args["experience_id"], -1),
                    maxNotesChars = intArg(args["max_notes_chars"], DEFAULT_MAX_NOTES_CHARS)
                )
                "get_recent_ingestions" -> getRecentIngestions(
                    daysBack = intArg(args["days_back"], 30),
                    substanceFilter = args["substance"] as? String
                )
                "get_substance_usage_stats" -> getSubstanceUsageStats(
                    daysBack = intArg(args["days_back"], 90)
                )
                else -> substanceTools.execute(name, args)
                    ?: anodyneTools.execute(name, args)
                    ?: errorResult("Unknown tool '$name'.")
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.e("AiTools", "Error executing tool $name", t)
            errorResult("Tool '$name' failed: ${t.message}")
        }
    }

    private suspend fun listRecentExperiences(limit: Int): JSONObject {
        val safeLimit = limit.coerceIn(1, 50)
        val rows = experienceDao
            .getRecentExperiencesWithIngestionsTimedNotesAndRatingsSorted(safeLimit)
        val arr = JSONArray()
        rows.forEach { row ->
            arr.put(
                JSONObject()
                    .put("experience_id", row.experience.id)
                    .put("title", row.experience.title)
                    .put("date", AI_DATE_FORMATTER.format(row.experience.sortDate))
                    .put("is_favorite", row.experience.isFavorite)
                    .put("ingestion_count", row.ingestions.size)
                    .put(
                        "substances",
                        JSONArray(row.ingestions.map { it.substanceName }.distinct())
                    )
            )
        }
        return JSONObject().put("status", "success").put("experiences", arr)
    }

    private suspend fun searchExperiences(query: String, limit: Int): JSONObject {
        if (query.isBlank()) return errorResult("Parameter 'query' is required.")
        val rows = experienceDao.searchExperiences(query.trim(), limit.coerceIn(1, 50))
        val arr = JSONArray()
        rows.forEach { exp ->
            arr.put(
                JSONObject()
                    .put("experience_id", exp.id)
                    .put("title", exp.title)
                    .put("date", AI_DATE_FORMATTER.format(exp.sortDate))
                    .put("is_favorite", exp.isFavorite)
                    .put("notes_excerpt", exp.text.take(240))
            )
        }
        return JSONObject().put("status", "success").put("matches", arr)
    }

    private suspend fun searchExperiencesBySubstance(substance: String, limit: Int): JSONObject {
        if (substance.isBlank()) return errorResult("Parameter 'substance' is required.")
        val rows = experienceDao.searchExperiencesBySubstance(substance.trim(), limit.coerceIn(1, 50))
        val arr = JSONArray()
        rows.forEach { exp ->
            arr.put(
                JSONObject()
                    .put("experience_id", exp.id)
                    .put("title", exp.title)
                    .put("date", AI_DATE_FORMATTER.format(exp.sortDate))
                    .put("is_favorite", exp.isFavorite)
            )
        }
        return JSONObject().put("status", "success").put("matches", arr)
    }

    private suspend fun getExperienceDetails(experienceId: Int, maxNotesChars: Int): JSONObject {
        if (experienceId < 0) return errorResult("Parameter 'experience_id' is required.")
        val exp = experienceDao.getExperienceWithIngestionsCompanionsAndRatings(experienceId)
            ?: return errorResult("No experience found with id $experienceId.")
        val ingestions = JSONArray()
        exp.ingestionsWithCompanions.sortedBy { it.ingestion.time }.forEach { ic ->
            val ing = ic.ingestion
            ingestions.put(
                JSONObject()
                    .put("time", AI_DATE_FORMATTER.format(ing.time))
                    .put("substance", ing.substanceName)
                    .put("dose", ing.dose?.takeIf { it.isFinite() } ?: JSONObject.NULL)
                    .put("units", ing.units ?: JSONObject.NULL)
                    .put("route", ing.administrationRoute.name)
                    .put("is_estimate", ing.isDoseAnEstimate)
                    .put("notes", (ing.notes ?: "").take(500))
            )
        }
        val ratings = JSONArray()
        exp.ratings.forEach { rating ->
            ratings.put(
                JSONObject()
                    .put("time", rating.time?.let { AI_DATE_FORMATTER.format(it) } ?: "overall")
                    .put("rating", rating.option.name)
            )
        }
        val notesCap = maxNotesChars.coerceIn(0, ABSOLUTE_MAX_NOTES_CHARS)
        val originalNotes = exp.experience.text
        val notes = originalNotes.take(notesCap)
        val notesTruncated = originalNotes.length > notes.length
        return JSONObject()
            .put("status", "success")
            .put("experience_id", exp.experience.id)
            .put("title", exp.experience.title)
            .put("date", AI_DATE_FORMATTER.format(exp.experience.sortDate))
            .put("is_favorite", exp.experience.isFavorite)
            .put("notes", notes)
            .put("notes_truncated", notesTruncated)
            .put("notes_total_chars", originalNotes.length)
            .put("ingestions", ingestions)
            .put("ratings", ratings)
    }

    private suspend fun getRecentIngestions(daysBack: Int, substanceFilter: String?): JSONObject {
        val clampedDays = daysBack.coerceIn(1, 365 * 2)
        val from = Instant.now().minusSeconds(clampedDays * 24L * 60 * 60)
        val resultLimit = 100
        val filtered = if (substanceFilter.isNullOrBlank()) {
            experienceDao.getIngestionsSince(from, resultLimit)
        } else {
            experienceDao.getIngestionsSinceFiltered(from, substanceFilter.trim(), resultLimit)
        }
        val arr = JSONArray()
        filtered.forEach { ing ->
            arr.put(
                JSONObject()
                    .put("time", AI_DATE_FORMATTER.format(ing.time))
                    .put("substance", ing.substanceName)
                    .put("dose", ing.dose?.takeIf { it.isFinite() } ?: JSONObject.NULL)
                    .put("units", ing.units ?: JSONObject.NULL)
                    .put("route", ing.administrationRoute.name)
                    .put("experience_id", ing.experienceId)
            )
        }
        return JSONObject()
            .put("status", "success")
            .put("days_back", clampedDays)
            .put("count", filtered.size)
            .put("ingestions", arr)
    }

    private suspend fun getSubstanceUsageStats(daysBack: Int): JSONObject {
        val clampedDays = daysBack.coerceIn(1, 365 * 5)
        val from = Instant.now().minusSeconds(clampedDays * 24L * 60 * 60)
        // We need every ingestion in the window for grouping; cap the SQL fetch defensively at
        // 10k rows (5 years of ~5/day) so a runaway journal can't OOM the client.
        val all = experienceDao.getIngestionsSince(from, 10_000)
        val grouped = all.groupBy { it.substanceName }
        val arr = JSONArray()
        grouped.entries
            .sortedByDescending { it.value.size }
            .forEach { (name, list) ->
                arr.put(
                    JSONObject()
                        .put("substance", name)
                        .put("times_logged", list.size)
                        .put("first_time", list.minByOrNull { it.time }?.time?.let { AI_DATE_FORMATTER.format(it) })
                        .put("last_time", list.maxByOrNull { it.time }?.time?.let { AI_DATE_FORMATTER.format(it) })
                )
            }
        return JSONObject()
            .put("status", "success")
            .put("days_back", clampedDays)
            .put("total_ingestions", all.size)
            .put("substances", arr)
    }

    private fun errorResult(message: String): JSONObject = toolError(message)
}
