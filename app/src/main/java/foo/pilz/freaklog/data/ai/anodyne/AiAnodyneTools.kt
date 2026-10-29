package foo.pilz.freaklog.data.ai.anodyne

import foo.pilz.freaklog.data.ai.ToolParam
import foo.pilz.freaklog.data.ai.provider.AiToolSpec
import foo.pilz.freaklog.data.ai.toolError
import foo.pilz.freaklog.data.ai.toolSpec
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tools that look substances up on AnodyneWiki. Only a recognized public substance name leaves
 * the device. They can be turned off, and pointed at another compatible server, in the AI settings.
 */
@Singleton
class AiAnodyneTools @Inject constructor(
    private val client: AnodyneWikiClient,
    private val userPreferences: UserPreferences,
    private val substances: foo.pilz.freaklog.data.substances.repositories.SubstanceRepository,
) {
    companion object {
        private const val SEARCH_LIMIT = 10
    }

    val specs: List<AiToolSpec> = listOf(
        toolSpec(
            "search_anodyne_wiki",
            "Search the AnodyneWiki substance index (a pharmacology and harm-reduction wiki) by name, " +
                "or abbreviation from the bundled public substance list. Use it to find the exact page title before lookup_anodyne_wiki.",
            ToolParam.string("query", "Recognized public substance name or abbreviation.", required = true),
        ),
        toolSpec(
            "lookup_anodyne_wiki",
            "Read a substance page on AnodyneWiki: user-reported dose tiers per route, duration, half-life, " +
                "interactions, adverse effects, warnings and subjective effects. Accepts titles and common " +
                "abbreviations. Cite anodyne.wiki when you use it.",
            ToolParam.string("substance", "Page title or abbreviation, e.g. 'MDMA' or 'Amphetamine'.", required = true),
        ),
    )

    private val names = specs.map { it.name }.toSet()

    suspend fun isEnabled(): Boolean = userPreferences.aiAnodyneEnabledFlow.first()

    private suspend fun baseUrl(): String =
        userPreferences.aiAnodyneBaseUrlFlow.first().ifBlank { AnodyneWikiClient.DEFAULT_BASE_URL }

    /** Runs [name] if it is one of these tools; returns null for other names. */
    suspend fun execute(name: String, args: Map<String, Any?>): JSONObject? {
        if (name !in names) return null
        if (!isEnabled()) return toolError("AnodyneWiki lookups are turned off in the app's AI settings.")
        val raw = args[if (name == "search_anodyne_wiki") "query" else "substance"] as? String ?: ""
        val approved = approvedPublicName(raw)
            ?: return toolError("Only recognized public substance names can be sent to AnodyneWiki.")
        return if (name == "search_anodyne_wiki") search(approved) else lookup(approved)
    }

    internal fun approvedPublicName(input: String): String? {
        val names = substances.getAllSubstances().map { it.name } +
            com.ndm4.freakquery.AnodyneAliases.map.values
        return names.firstOrNull { it.equals(input.trim(), ignoreCase = true) }
            ?: com.ndm4.freakquery.AnodyneAliases.map.entries
                .firstOrNull { it.key.equals(input.trim(), ignoreCase = true) }?.value
    }

    private suspend fun search(query: String): JSONObject {
        if (query.isBlank()) return toolError("Parameter 'query' is required.")
        val matches = client.search(baseUrl(), query, SEARCH_LIMIT)
        return JSONObject()
            .put("status", "success")
            .put("source", "anodyne.wiki")
            .put(
                "matches",
                JSONArray().apply {
                    matches.forEach { entry ->
                        put(
                            JSONObject()
                                .put("title", entry.title)
                                .put("other_names", JSONArray(entry.aliases))
                                .put("classes", JSONArray(entry.classes)),
                        )
                    }
                },
            )
    }

    private suspend fun lookup(substance: String): JSONObject {
        if (substance.isBlank()) return toolError("Parameter 'substance' is required.")
        val base = baseUrl()
        val page = client.substance(base, substance)
            ?: return toolError("AnodyneWiki has no page called '$substance'. Try search_anodyne_wiki first.")
        val pageName = page.optString("name", substance)
        return page
            .put("status", "success")
            .put("source", "anodyne.wiki")
            .put("url", base.removeSuffix("/").removeSuffix("/api") + "/substance/" + pageName.replace(" ", "_"))
    }
}
