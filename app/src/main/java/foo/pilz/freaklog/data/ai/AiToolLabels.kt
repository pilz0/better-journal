package foo.pilz.freaklog.data.ai

import foo.pilz.freaklog.data.ai.vitals.VitalsTypes
import java.net.URI
import org.json.JSONObject

/** Human-readable descriptions of tool calls for the chat transcript. */
object AiToolLabels {
  fun describe(name: String, args: Map<String, Any?>): String {
    fun arg(key: String) = args[key]?.toString()?.takeIf { it.isNotBlank() }
    return describeJournalTool(name, ::arg)
        ?: describeSubstanceOrExternalTool(name, args, ::arg)
        ?: name
  }

  private fun describeJournalTool(name: String, arg: (String) -> String?): String? =
      when (name) {
        "list_recent_experiences" -> "Listed recent experiences"
        "search_experiences" -> "Searched the journal for “${arg("query").orEmpty()}”"
        "search_experiences_by_substance" ->
            "Looked for experiences with ${arg("substance").orEmpty()}"
        "get_experience_details" -> "Opened experience #${arg("experience_id").orEmpty()}"
        "get_recent_ingestions" ->
            listOfNotNull(
                    "Listed ingestions of the last ${arg("days_back") ?: "30"} days",
                    arg("substance")?.let { "($it)" },
                )
                .joinToString(" ")
        "get_substance_usage_stats" ->
            "Counted use per substance (${arg("days_back") ?: "90"} days)"
        else -> null
      }

  private fun describeSubstanceOrExternalTool(
      name: String,
      args: Map<String, Any?>,
      arg: (String) -> String?,
  ): String? =
      when (name) {
        "get_substance_info" -> "Looked up ${arg("name").orEmpty()}"
        "check_interactions" ->
            "Checked interactions: ${stringListArg(args["substances"]).joinToString(" + ")}"
        "get_tolerance" -> "Estimated tolerance to ${arg("substance").orEmpty()}"
        "get_active_ingestions" ->
            "Checked what is still active (last ${arg("hours_back") ?: "24"} h)"
        "search_anodyne_wiki" -> "Searched anodyne.wiki for “${arg("query").orEmpty()}”"
        "lookup_anodyne_wiki" -> "Read anodyne.wiki on ${arg("substance").orEmpty()}"
        "web_search" -> "Searched the web for “${arg("query").orEmpty()}”"
        "fetch_webpage" -> "Read ${hostOrRaw(arg("url").orEmpty())}"
        "get_vitals" ->
            arg("experience_id")?.let { "Checked vitals for experience #$it" }
                ?: "Checked vitals (last ${arg("hours_back") ?: "24"} h)"
        else -> null
      }

  fun progress(name: String): String =
      when (name) {
        "search_anodyne_wiki",
        "lookup_anodyne_wiki" -> "Asking anodyne.wiki…"
        "web_search" -> "Searching the web…"
        "fetch_webpage" -> "Reading webpage…"
        "get_vitals" -> "Reading your vitals…"
        "get_substance_info",
        "check_interactions" -> "Checking substance data…"
        else -> "Looking through your journal…"
      }

  fun summarise(name: String, result: JSONObject): String {
    if (result.optString("status") == "error") {
      return result.optString("message", "tool failed")
    }
    return when (name) {
      "list_recent_experiences" ->
          "${result.optJSONArray("experiences")?.length() ?: 0} experiences"
      "search_experiences",
      "search_experiences_by_substance" ->
          "${result.optJSONArray("matches")?.length() ?: 0} matches"
      "get_experience_details" -> result.optString("title", "experience loaded")
      "get_recent_ingestions" -> "${result.optInt("count")} ingestions"
      "get_substance_usage_stats" ->
          "${result.optJSONArray("substances")?.length() ?: 0} substances, " +
              "${result.optInt("total_ingestions")} ingestions"
      "get_substance_info",
      "lookup_anodyne_wiki" -> result.optString("name", "found")
      "check_interactions" -> summariseInteractions(result)
      "get_tolerance" -> result.optString("label", "estimated")
      "get_active_ingestions" -> {
        val list = result.optJSONArray("ingestions")
        val active =
            (0 until (list?.length() ?: 0)).count {
              list?.optJSONObject(it)?.optString("status") == "probably still active"
            }
        "$active probably still active"
      }
      "search_anodyne_wiki" -> "${result.optJSONArray("matches")?.length() ?: 0} matches"
      "web_search" -> "${result.optJSONArray("results")?.length() ?: 0} results"
      "fetch_webpage" -> result.optString("title").ifBlank { "page loaded" }.take(60)
      "get_vitals" -> summariseVitals(result)
      else -> "done"
    }
  }

  private fun summariseInteractions(result: JSONObject): String {
    val unrecognized = result.optJSONArray("unrecognized_substances")
    val found = result.optJSONArray("interactions")?.length() ?: 0
    return when {
      unrecognized != null && unrecognized.length() > 0 ->
          "unknown substance: " +
              (0 until unrecognized.length()).joinToString { unrecognized.optString(it) }
      found == 0 -> "no interactions on record"
      found == 1 -> "1 interaction found"
      else -> "$found interactions found"
    }
  }

  private fun summariseVitals(result: JSONObject): String {
    val types = result.optJSONArray("available_data_types") ?: return "no vitals recorded"
    val labels =
        (0 until types.length())
            .mapNotNull { idx -> types.optString(idx).takeIf { it.isNotBlank() } }
            .map(VitalsTypes::labelFor)
    return if (labels.isEmpty()) "no vitals recorded" else labels.joinToString(", ")
  }

  private fun hostOrRaw(rawUrl: String): String {
    if (rawUrl.isBlank()) return "webpage"
    return runCatching { URI(rawUrl.trim()).host?.takeIf { it.isNotBlank() } }.getOrNull()
        ?: rawUrl.take(40)
  }
}
