package foo.pilz.freaklog.data.ai

import android.util.Log
import foo.pilz.freaklog.data.room.experiences.ExperienceDao
import kotlinx.coroutines.flow.firstOrNull
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
                    experienceId = (args["experience_id"] as? Number)?.toInt() ?: -1
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
        val rows = experienceDao.getAllExperiencesWithIngestionsTimedNotesAndRatingsSorted()
            .sortedByDescending { it.experience.sortDate }
            .take(limit.coerceIn(1, 50))
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

    private suspend fun getExperienceDetails(experienceId: Int): JSONObject {
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
                    .put("notes", ing.notes ?: "")
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
        return JSONObject()
            .put("status", "success")
            .put("experience_id", exp.experience.id)
            .put("title", exp.experience.title)
            .put("date", AI_DATE_FORMATTER.format(exp.experience.sortDate))
            .put("is_favorite", exp.experience.isFavorite)
            .put("notes", exp.experience.text)
            .put("ingestions", ingestions)
            .put("ratings", ratings)
    }

    private suspend fun getRecentIngestions(daysBack: Int, substanceFilter: String?): JSONObject {
        val clampedDays = daysBack.coerceIn(1, 365 * 2)
        val from = Instant.now().minusSeconds(clampedDays * 24L * 60 * 60)
        val all = experienceDao.getSortedIngestionsFlow().firstOrNull().orEmpty()
        val filtered = all
            .filter { it.time.isAfter(from) }
            .filter { substanceFilter.isNullOrBlank() || it.substanceName.contains(substanceFilter, ignoreCase = true) }
            .take(100)
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
        val all = experienceDao.getSortedIngestionsFlow().firstOrNull().orEmpty()
            .filter { it.time.isAfter(from) }
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
