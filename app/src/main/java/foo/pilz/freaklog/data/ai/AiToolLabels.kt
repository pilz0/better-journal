package foo.pilz.freaklog.data.ai

import org.json.JSONObject

/** Human-readable descriptions of tool calls for the chat transcript. */
object AiToolLabels {
    fun describe(name: String, args: Map<String, Any?>): String {
        fun arg(key: String) = args[key]?.toString()?.takeIf { it.isNotBlank() }
        return when (name) {
            "list_recent_experiences" -> "Listed recent experiences"
            "search_experiences" -> "Searched the journal for “${arg("query").orEmpty()}”"
            "search_experiences_by_substance" -> "Looked for experiences with ${arg("substance").orEmpty()}"
            "get_experience_details" -> "Opened experience #${arg("experience_id").orEmpty()}"
            "get_recent_ingestions" -> listOfNotNull(
                "Listed ingestions of the last ${arg("days_back") ?: "30"} days",
                arg("substance")?.let { "($it)" },
            ).joinToString(" ")
            "get_substance_usage_stats" -> "Counted use per substance (${arg("days_back") ?: "90"} days)"
            "get_substance_info" -> "Looked up ${arg("name").orEmpty()}"
            "check_interactions" -> "Checked interactions: ${stringListArg(args["substances"]).joinToString(" + ")}"
            "get_tolerance" -> "Estimated tolerance to ${arg("substance").orEmpty()}"
            "get_active_ingestions" -> "Checked what is still active (last ${arg("hours_back") ?: "24"} h)"
            "search_anodyne_wiki" -> "Searched anodyne.wiki for “${arg("query").orEmpty()}”"
            "lookup_anodyne_wiki" -> "Read anodyne.wiki on ${arg("substance").orEmpty()}"
            else -> name
        }
    }

    fun progress(name: String): String = when (name) {
        "search_anodyne_wiki", "lookup_anodyne_wiki" -> "Asking anodyne.wiki…"
        "get_substance_info", "check_interactions" -> "Checking substance data…"
        else -> "Looking through your journal…"
    }

    fun summarise(name: String, result: JSONObject): String {
        if (result.optString("status") == "error") {
            return result.optString("message", "tool failed")
        }
        return when (name) {
            "list_recent_experiences" -> "${result.optJSONArray("experiences")?.length() ?: 0} experiences"
            "search_experiences", "search_experiences_by_substance" ->
                "${result.optJSONArray("matches")?.length() ?: 0} matches"
            "get_experience_details" -> result.optString("title", "experience loaded")
            "get_recent_ingestions" -> "${result.optInt("count")} ingestions"
            "get_substance_usage_stats" ->
                "${result.optJSONArray("substances")?.length() ?: 0} substances, " +
                    "${result.optInt("total_ingestions")} ingestions"
            "get_substance_info", "lookup_anodyne_wiki" -> result.optString("name", "found")
            "check_interactions" -> {
                val found = result.optJSONArray("interactions")?.length() ?: 0
                if (found == 0) "no known interactions" else "$found known interactions"
            }
            "get_tolerance" -> result.optString("label", "estimated")
            "get_active_ingestions" -> {
                val list = result.optJSONArray("ingestions")
                val active = (0 until (list?.length() ?: 0))
                    .count { list?.optJSONObject(it)?.optString("status") == "probably still active" }
                "$active probably still active"
            }
            "search_anodyne_wiki" -> "${result.optJSONArray("matches")?.length() ?: 0} matches"
            else -> "done"
        }
    }
}
