package foo.pilz.freaklog.data.ai

import android.util.Log
import foo.pilz.freaklog.data.room.experiences.ExperienceDao
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementations of the function-calling tools that the AI chatbot can invoke
 * to research the user's journal data. Each method receives the raw argument map
 * delivered by the Gemini SDK and returns a JSONObject that can be sent back to
 * the model as a `FunctionResponsePart`.
 */
@Singleton
class AiTools @Inject constructor(
    private val experienceDao: ExperienceDao
) {

    companion object {
        /** Default cap on the size of any single notes blob returned to the model. */
        const val DEFAULT_MAX_NOTES_CHARS = 2000

        /** Hard cap on the size of any single notes blob returned to the model. */
        const val ABSOLUTE_MAX_NOTES_CHARS = 8000
    }

    suspend fun execute(name: String, args: Map<String, Any?>): JSONObject {
        return try {
            when (name) {
                "list_recent_experiences" -> listRecentExperiences(
                    limit = (args["limit"] as? Number)?.toInt() ?: 10
                )
                "search_experiences" -> searchExperiences(
                    query = args["query"] as? String ?: "",
                    limit = (args["limit"] as? Number)?.toInt() ?: 10
                )
                "search_experiences_by_substance" -> searchExperiencesBySubstance(
                    substance = args["substance"] as? String ?: "",
                    limit = (args["limit"] as? Number)?.toInt() ?: 10
                )
                "get_experience_details" -> getExperienceDetails(
                    experienceId = (args["experience_id"] as? Number)?.toInt() ?: -1,
                    maxNotesChars = (args["max_notes_chars"] as? Number)?.toInt()
                        ?: DEFAULT_MAX_NOTES_CHARS
                )
                "get_recent_ingestions" -> getRecentIngestions(
                    daysBack = (args["days_back"] as? Number)?.toInt() ?: 30,
                    substanceFilter = args["substance"] as? String
                )
                "get_substance_usage_stats" -> getSubstanceUsageStats(
                    daysBack = (args["days_back"] as? Number)?.toInt() ?: 90
                )
                else -> errorResult("Unknown tool '$name'.")
            }
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
                    .put("dose", ing.dose ?: JSONObject.NULL)
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
                    .put("dose", ing.dose ?: JSONObject.NULL)
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

    private fun errorResult(message: String): JSONObject =
        JSONObject().put("status", "error").put("message", message)
}
