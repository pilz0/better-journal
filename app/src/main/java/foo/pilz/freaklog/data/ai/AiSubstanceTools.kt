package foo.pilz.freaklog.data.ai

import foo.pilz.freaklog.data.ai.provider.AiToolSpec
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceProfiles
import foo.pilz.freaklog.data.room.experiences.ExperienceDao
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.Substance
import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.Roa
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepositoryInterface
import foo.pilz.freaklog.ui.tabs.journal.addingestion.interactions.InteractionChecker
import foo.pilz.freaklog.ui.tabs.safer.tolerance.ToleranceCalculator
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Harm-reduction tools backed by data that is already on the device: the bundled
 * PsychonautWiki substance file, the user's custom substances, the interaction checker and
 * the tolerance estimate. Nothing here leaves the device except the results sent to the model.
 */
@Singleton
class AiSubstanceTools @Inject constructor(
    private val substances: SubstanceRepositoryInterface,
    private val customProfiles: CustomSubstanceProfiles,
    private val interactionChecker: InteractionChecker,
    private val toleranceCalculator: ToleranceCalculator,
    private val experienceDao: ExperienceDao,
) {
    companion object {
        private const val MAX_INTERACTION_SUBSTANCES = 8
        private const val MAX_TEXT_CHARS = 800
        private const val MAX_ACTIVE_HOURS = 72
        private const val SECONDS_PER_HOUR = 3600L

        /** Chemical or everyday names models tend to use that the bundled data doesn't list. */
        private val ALIASES = mapOf(
            "ethanol" to "Alcohol",
            "ethyl alcohol" to "Alcohol",
            "beer" to "Alcohol",
            "wine" to "Alcohol",
            "thc" to "Cannabis",
            "weed" to "Cannabis",
        )
    }

    val specs: List<AiToolSpec> = listOf(
        toolSpec(
            "get_substance_info",
            "Look up a substance in the PsychonautWiki data bundled with the app (or the user's own custom " +
                "substances): summary, dose ranges and durations per route, tolerance, cross-tolerances, " +
                "toxicity, risks, safer-use tips and listed interactions. Accepts common names and abbreviations.",
            ToolParam.string("name", "Substance name, common name or abbreviation, e.g. 'MDMA' or 'Molly'.", required = true),
        ),
        toolSpec(
            "check_interactions",
            "Check every pair of the given substances for known interactions (dangerous, unsafe, uncertain) " +
                "using the app's interaction checker. Pairs without data are listed as unknown, which does not mean safe.",
            ToolParam.stringArray(
                "substances",
                "Two to eight substance names as PsychonautWiki names them, e.g. 'Alcohol' (not 'ethanol'), 'MDMA'.",
                required = true,
            ),
        ),
        toolSpec(
            "get_tolerance",
            "Estimate the user's current tolerance to a substance from their journal (including cross-tolerant " +
                "substances), with the time until tolerance is expected to be back to baseline.",
            ToolParam.string("substance", "Substance name.", required = true),
        ),
        toolSpec(
            "get_active_ingestions",
            "List the user's ingestions from the last N hours with the expected end of their effects, based on " +
                "each route's duration. Use it for 'what is still active?', redosing and combination questions.",
            ToolParam.integer("hours_back", "How many hours back to look (default 24, max 72)."),
        ),
    )

    private val names = specs.map { it.name }.toSet()

    /** Runs [name] if it is one of these tools; returns null for other names. */
    suspend fun execute(name: String, args: Map<String, Any?>): JSONObject? {
        if (name !in names) return null
        return when (name) {
            "get_substance_info" -> substanceInfo(args["name"] as? String ?: "")
            "check_interactions" -> checkInteractions(stringListArg(args["substances"]))
            "get_tolerance" -> tolerance(args["substance"] as? String ?: "")
            else -> activeIngestions((args["hours_back"] as? Number)?.toInt() ?: 24)
        }
    }

    /** Finds a bundled substance by exact name, then case-insensitive name or common name. */
    private fun resolve(query: String): Substance? {
        val q = query.trim()
        substances.getSubstance(q)?.let { return it }
        val all = substances.getAllSubstances()
        return all.firstOrNull { it.name.equals(q, ignoreCase = true) }
            ?: all.firstOrNull { s -> s.commonNames.any { it.equals(q, ignoreCase = true) } }
            ?: ALIASES[q.lowercase()]?.let { alias -> substances.getSubstance(alias) }
    }

    private fun isKnown(query: String): Boolean = customName(query) != null || resolve(query) != null

    private fun customName(query: String): String? =
        customProfiles.byNameFlow.value.keys.firstOrNull { it.equals(query.trim(), ignoreCase = true) }

    private fun canonicalName(query: String): String = customName(query) ?: resolve(query)?.name ?: query.trim()

    private fun substanceInfo(query: String): JSONObject {
        if (query.isBlank()) return toolError("Parameter 'name' is required.")
        customName(query)?.let { custom ->
            return JSONObject()
                .put("status", "success")
                .put("name", custom)
                .put("is_custom_substance", true)
                .put("source", "The user's own custom substance in this app")
                .put("routes", routesJson(AdministrationRoute.entries.mapNotNull { customProfiles.getRoa(custom, it) }))
        }
        val substance = resolve(query) ?: return toolError(
            "No substance called '$query' in the bundled data. Similar names: " +
                substances.getAllSubstances().map { it.name }
                    .filter { it.contains(query.trim(), ignoreCase = true) }
                    .take(5).joinToString().ifEmpty { "none" } +
                ". You can also try search_anodyne_wiki if it is available.",
        )
        return JSONObject()
            .put("status", "success")
            .put("name", substance.name)
            .put("source", "PsychonautWiki data bundled with the app")
            .put("url", substance.url)
            .put("common_names", JSONArray(substance.commonNames.take(10)))
            .put("categories", JSONArray(substance.categories))
            .put("summary", substance.summary?.take(MAX_TEXT_CHARS) ?: JSONObject.NULL)
            .put("dosage_remark", substance.dosageRemark ?: JSONObject.NULL)
            .put(
                "tolerance",
                substance.tolerance?.let {
                    JSONObject().put("full", it.full).put("half", it.half).put("zero", it.zero)
                } ?: JSONObject.NULL,
            )
            .put("cross_tolerances", JSONArray(substance.crossTolerances))
            .put("addiction_potential", substance.addictionPotential ?: JSONObject.NULL)
            .put("toxicity", JSONArray(substance.toxicities))
            .put("general_risks", substance.generalRisks?.take(MAX_TEXT_CHARS) ?: JSONObject.NULL)
            .put("long_term_risks", substance.longtermRisks?.take(MAX_TEXT_CHARS) ?: JSONObject.NULL)
            .put("safer_use", JSONArray(substance.saferUse))
            .put(
                "interactions",
                substance.interactions?.let {
                    JSONObject()
                        .put("dangerous", JSONArray(it.dangerous))
                        .put("unsafe", JSONArray(it.unsafe))
                        .put("uncertain", JSONArray(it.uncertain))
                } ?: JSONObject.NULL,
            )
            .put("routes", routesJson(substance.roas))
    }

    private fun routesJson(roas: List<Roa>): JSONArray = JSONArray().apply {
        roas.forEach { roa ->
            val json = JSONObject().put("route", roa.route.name)
            roa.roaDose?.let { dose ->
                json.put("dose_units", dose.units)
                    .put("light_from", dose.lightMin ?: JSONObject.NULL)
                    .put("common_from", dose.commonMin ?: JSONObject.NULL)
                    .put("strong_from", dose.strongMin ?: JSONObject.NULL)
                    .put("heavy_from", dose.heavyMin ?: JSONObject.NULL)
            }
            roa.roaDuration?.let { d ->
                listOf(
                    "onset" to d.onset, "comeup" to d.comeup, "peak" to d.peak,
                    "offset" to d.offset, "total" to d.total, "afterglow" to d.afterglow,
                ).forEach { (key, range) -> range?.let { json.put(key, it.text) } }
            }
            roa.bioavailability?.let { json.put("bioavailability_percent", "${it.min}-${it.max}") }
            put(json)
        }
    }

    private fun checkInteractions(requested: List<String>): JSONObject {
        val unrecognized = requested.filterNot(::isKnown)
        val names = requested.map(::canonicalName).distinctBy { it.lowercase() }
        if (names.size < 2) return toolError("Give at least two different substances.")
        if (names.size > MAX_INTERACTION_SUBSTANCES) {
            return toolError("Check at most $MAX_INTERACTION_SUBSTANCES substances at once.")
        }
        val found = JSONArray()
        val unknown = JSONArray()
        for (i in names.indices) {
            for (j in i + 1 until names.size) {
                val interaction = interactionChecker.getInteractionBetween(names[i], names[j])
                if (interaction == null) {
                    unknown.put("${names[i]} + ${names[j]}")
                } else {
                    found.put(
                        JSONObject()
                            .put("a", names[i])
                            .put("b", names[j])
                            .put("risk", interaction.interactionType.name.lowercase()),
                    )
                }
            }
        }
        val result = JSONObject()
            .put("status", "success")
            .put("substances", JSONArray(names))
            .put("interactions", found)
            .put("no_data", unknown)
            .put(
                "note",
                "Based on PsychonautWiki/TripSit interaction data and the user's custom substances. " +
                    "No data does not mean the combination is safe.",
            )
        if (unrecognized.isNotEmpty()) {
            result.put("unrecognized_substances", JSONArray(unrecognized)).put(
                "warning",
                "These names are not in the app's substance data, so their pairs could not be checked: " +
                    unrecognized.joinToString() + ". Look up the name the app uses (get_substance_info) and " +
                    "check again; do not tell the user the combination has no known interactions.",
            )
        }
        return result
    }

    private suspend fun tolerance(query: String): JSONObject {
        if (query.isBlank()) return toolError("Parameter 'substance' is required.")
        val name = canonicalName(query)
        val estimate = toleranceCalculator.calculate(name)
            ?: return toolError("There is no tolerance data for '$name'.")
        return JSONObject()
            .put("status", "success")
            .put("substance", estimate.substanceName)
            .put("tolerance_level_0_to_1", (estimate.toleranceLevel * 100).roundToInt() / 100.0)
            .put("label", estimate.label)
            .put("last_ingestion", estimate.lastIngestionTime?.let { AI_DATE_FORMATTER.format(it) } ?: JSONObject.NULL)
            .put("cross_tolerance_from", JSONArray(estimate.crossToleranceContributors))
            .put("days_until_baseline_after_last_use", estimate.zeroDays.toDouble())
            .put("half_life_days", estimate.halfLifeDays.toDouble())
            .put("note", "A rough estimate from logged ingestions; tolerance varies between people.")
    }

    private suspend fun activeIngestions(hoursBack: Int): JSONObject {
        val hours = hoursBack.coerceIn(1, MAX_ACTIVE_HOURS)
        val now = Instant.now()
        val ingestions = experienceDao.getIngestionsSince(now.minusSeconds(hours * SECONDS_PER_HOUR), 200)
        val result = JSONArray()
        ingestions.sortedBy { it.time }.forEach { ing ->
            val roa = customProfiles.getRoa(ing.substanceName, ing.administrationRoute)
                ?: resolve(ing.substanceName)?.getRoa(ing.administrationRoute)
            val duration = roa?.roaDuration
            val totalSeconds = duration?.total?.maxInSec
                ?: duration?.let { phasesMaxSeconds(it.onset, it.comeup, it.peak, it.offset) }
            val json = JSONObject()
                .put("time", AI_DATE_FORMATTER.format(ing.time))
                .put("substance", ing.substanceName)
                .put("dose", ing.dose ?: JSONObject.NULL)
                .put("units", ing.units ?: JSONObject.NULL)
                .put("route", ing.administrationRoute.name)
                .put("experience_id", ing.experienceId)
            if (totalSeconds == null) {
                json.put("status", "unknown duration")
            } else {
                val end = ing.time.plusSeconds(totalSeconds.toLong())
                json.put("expected_end", AI_DATE_FORMATTER.format(end))
                    .put("status", if (now.isBefore(end)) "probably still active" else "probably worn off")
            }
            result.put(json)
        }
        return JSONObject()
            .put("status", "success")
            .put("now", AI_DATE_FORMATTER.format(now))
            .put("hours_back", hours)
            .put("ingestions", result)
            .put("note", "Expected ends use the longest listed total duration; after-effects can last longer.")
    }

    private fun phasesMaxSeconds(vararg phases: DurationRange?): Float? {
        val known = phases.mapNotNull { it?.maxInSec }
        return if (known.isEmpty()) null else known.sum()
    }
}
