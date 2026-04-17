package foo.pilz.freaklog.ui.tabs.settings.funny

import android.content.Context
import foo.pilz.freaklog.data.room.experiences.entities.CustomRecipe
import foo.pilz.freaklog.data.room.experiences.entities.CustomUnit
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.entities.ShulginRating
import foo.pilz.freaklog.data.room.experiences.entities.TimedNote
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsTimedNotesAndRatings
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.InteractionType
import foo.pilz.freaklog.ui.tabs.journal.addingestion.interactions.InteractionChecker
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepositoryInterface
import org.json.JSONArray
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class AchievementTier { BRONZE, SILVER, GOLD }

private val PREDICATE_REGEX = Regex("""^(\w+)\((.*)\)$""")


data class AchievementDef(
    val id: String,
    val title: String,
    val description: String,
    val condition: String,
    val tier: AchievementTier? = null
)

data class UnlockedAchievement(
    val def: AchievementDef,
    val unlocked: Boolean
)

/**
 * Everything the achievement DSL can see. Using a bundle keeps the recursive
 * evaluator signature small and future-proof as we add more predicates.
 */
data class AchievementContext(
    val experiences: List<ExperienceWithIngestionsTimedNotesAndRatings>,
    val allIngestions: List<Ingestion>,
    val allTimedNotes: List<TimedNote>,
    val allRatings: List<ShulginRating>,
    val customUnits: List<CustomUnit>,
    val customRecipes: List<CustomRecipe>,
    val substanceRepo: SubstanceRepositoryInterface,
    val interactionChecker: InteractionChecker
)

fun loadAchievements(context: Context): List<AchievementDef> {
    val json = context.assets.open("achievements.json").bufferedReader().readText()
    val array = JSONArray(json)
    return (0 until array.length()).map { i ->
        val obj = array.getJSONObject(i)
        val tierStr = if (obj.has("tier") && !obj.isNull("tier")) obj.getString("tier") else null
        AchievementDef(
            id = obj.getString("id"),
            title = obj.getString("title"),
            description = obj.getString("description"),
            condition = obj.getString("condition"),
            tier = tierStr?.let {
                runCatching { AchievementTier.valueOf(it.uppercase()) }.getOrNull()
            }
        )
    }
}

private fun splitTopLevel(condition: String, delimiter: String): List<String>? {
    val parts = mutableListOf<String>()
    var depth = 0
    var current = StringBuilder()
    var i = 0
    while (i < condition.length) {
        if (condition[i] == '(') depth++
        else if (condition[i] == ')') depth--

        if (depth == 0 && condition.startsWith(delimiter, i)) {
            parts.add(current.toString().trim())
            current = StringBuilder()
            i += delimiter.length
            continue
        }
        current.append(condition[i])
        i++
    }
    parts.add(current.toString().trim())
    return if (parts.size > 1) parts else null
}

private fun unwrapOuter(condition: String, prefix: String): String? {
    if (!condition.startsWith("$prefix(")) return null
    var depth = 0
    for (i in condition.indices) {
        if (condition[i] == '(') depth++
        else if (condition[i] == ')') depth--
        if (depth == 0 && i >= prefix.length) {
            return if (i == condition.length - 1) {
                condition.drop(prefix.length + 1).dropLast(1)
            } else null
        }
    }
    return null
}

fun evaluateAchievement(condition: String, ctx: AchievementContext): Boolean {
    splitTopLevel(condition, " & ")?.let { parts ->
        return parts.all { evaluateAchievement(it, ctx) }
    }

    splitTopLevel(condition, " | ")?.let { parts ->
        return parts.any { evaluateAchievement(it, ctx) }
    }

    unwrapOuter(condition, "not")?.let { inner ->
        return !evaluateAchievement(inner, ctx)
    }

    unwrapOuter(condition, "in_experience")?.let { inner ->
        return ctx.experiences.any { exp ->
            evaluateAchievement(
                inner,
                ctx.copy(
                    experiences = listOf(exp),
                    allIngestions = exp.ingestions,
                    allTimedNotes = exp.timedNotes,
                    allRatings = exp.ratings
                )
            )
        }
    }

    val match = PREDICATE_REGEX.matchEntire(condition.trim()) ?: return false
    val func = match.groupValues[1]
    val args = match.groupValues[2]

    return when (func) {
        "combo" -> {
            val substances = args.split(",").map { it.trim() }
            ctx.experiences.any { exp ->
                val names = exp.ingestions.map { it.substanceName }.distinct()
                substances.all { it in names }
            }
        }

        "substance" -> ctx.allIngestions.any { it.substanceName == args.trim() }

        "route" -> {
            val route = args.trim()
            ctx.allIngestions.any { it.administrationRoute.name == route }
        }

        "min_ingestions" -> {
            val n = args.trim().toIntOrNull() ?: return false
            ctx.allIngestions.size >= n
        }

        "min_experiences" -> {
            val n = args.trim().toIntOrNull() ?: return false
            ctx.experiences.size >= n
        }

        "min_substances" -> {
            val n = args.trim().toIntOrNull() ?: return false
            ctx.allIngestions.map { it.substanceName }.distinct().size >= n
        }

        "min_combo" -> {
            val n = args.trim().toIntOrNull() ?: return false
            ctx.experiences.any { exp ->
                exp.ingestions.map { it.substanceName }.distinct().size >= n
            }
        }

        "dose" -> {
            val level = args.trim()
            ctx.allIngestions.any { ingestion ->
                checkDoseLevel(ingestion, level, ctx.substanceRepo)
            }
        }

        "dose_substance" -> {
            val parts = args.split(",").map { it.trim() }
            if (parts.size != 2) return false
            val substanceName = parts[0]
            val level = parts[1]
            ctx.allIngestions
                .filter { it.substanceName == substanceName }
                .any { checkDoseLevel(it, level, ctx.substanceRepo) }
        }

        "category" -> {
            val cat = args.trim()
            ctx.allIngestions.any { ingestion ->
                val substance = ctx.substanceRepo.getSubstance(ingestion.substanceName)
                substance?.categories?.any { it.equals(cat, ignoreCase = true) } == true
            }
        }

        "min_routes" -> {
            val n = args.trim().toIntOrNull() ?: return false
            ctx.allIngestions.map { it.administrationRoute }.distinct().size >= n
        }

        "substance_count" -> {
            val parts = args.split(",").map { it.trim() }
            if (parts.size != 2) return false
            val substanceName = parts[0]
            val n = parts[1].toIntOrNull() ?: return false
            ctx.allIngestions.count { it.substanceName == substanceName } >= n
        }

        "any_substance_count" -> {
            val n = args.trim().toIntOrNull() ?: return false
            ctx.allIngestions.groupBy { it.substanceName }.any { it.value.size >= n }
        }

        "route_count" -> {
            val parts = args.split(",").map { it.trim() }
            if (parts.size != 2) return false
            val route = parts[0]
            val n = parts[1].toIntOrNull() ?: return false
            ctx.allIngestions.count { it.administrationRoute.name == route } >= n
        }

        "has_note" -> ctx.allIngestions.any { !it.notes.isNullOrBlank() }

        "min_notes" -> {
            val n = args.trim().toIntOrNull() ?: return false
            ctx.allIngestions.count { !it.notes.isNullOrBlank() } >= n
        }

        "has_favorite" -> ctx.experiences.any { it.experience.isFavorite }

        "has_location" -> ctx.experiences.any { it.experience.location != null }

        "streak" -> {
            val n = args.trim().toIntOrNull() ?: return false
            longestConsecutiveDayStreak(ctx.allIngestions) >= n
        }

        "sober_streak" -> {
            val n = args.trim().toIntOrNull() ?: return false
            longestSoberStreak(ctx.allIngestions) >= n
        }

        "journal_span_days" -> {
            val n = args.trim().toIntOrNull() ?: return false
            journalSpanDays(ctx.allIngestions) >= n
        }

        "min_ratings" -> {
            val n = args.trim().toIntOrNull() ?: return false
            ctx.allRatings.size >= n
        }

        "min_timed_notes" -> {
            val n = args.trim().toIntOrNull() ?: return false
            ctx.allTimedNotes.size >= n
        }

        "min_custom_units" -> {
            val n = args.trim().toIntOrNull() ?: return false
            ctx.customUnits.size >= n
        }

        "has_custom_recipe" -> ctx.customRecipes.isNotEmpty()

        "has_site" -> {
            // If args empty → any administration site used. Otherwise match exact.
            val arg = args.trim()
            if (arg.isEmpty()) {
                ctx.allIngestions.any { !it.administrationSite.isNullOrBlank() }
            } else {
                ctx.allIngestions.any { it.administrationSite == arg }
            }
        }

        "has_transdermal_site" -> ctx.allIngestions.any {
            it.administrationRoute == AdministrationRoute.TRANSDERMAL &&
                !it.administrationSite.isNullOrBlank()
        }

        "has_redose_in_peak" -> hasRedoseInPeak(ctx)

        "interaction" -> {
            val type = args.trim().lowercase()
            ctx.experiences.any { exp ->
                val substances = exp.ingestions.map { it.substanceName }.distinct()
                hasInteractionInList(substances, type, ctx.interactionChecker)
            }
        }

        "min_interactions" -> {
            val splitArgs = args.split(",").map { it.trim() }
            val n = splitArgs.getOrNull(0)?.toIntOrNull() ?: return false
            val type = splitArgs.getOrNull(1) ?: return false

            ctx.experiences.any { exp ->
                val substances = exp.ingestions.map { it.substanceName }.distinct()
                countInteractionsInList(substances, type, ctx.interactionChecker) >= n
            }
        }

        "stomachFullness" -> {
            val fullness = args.trim()
            ctx.allIngestions.any { it.stomachFullness?.name == fullness }
        }

        else -> false
    }
}

private fun hasInteractionInList(
    substances: List<String>,
    type: String,
    checker: InteractionChecker
): Boolean {
    for (i in substances.indices) {
        for (j in (i + 1) until substances.size) {
            val interaction = checker.getInteractionBetween(substances[i], substances[j])
            val matches = when (type) {
                "dangerous" -> interaction?.interactionType == InteractionType.DANGEROUS
                "unsafe" -> interaction?.interactionType == InteractionType.UNSAFE
                "uncertain" -> interaction?.interactionType == InteractionType.UNCERTAIN
                "any" -> interaction != null
                else -> false
            }
            if (matches) return true
        }
    }
    return false
}

private fun countInteractionsInList(
    substances: List<String>,
    type: String,
    checker: InteractionChecker
): Int {
    var count = 0
    for (i in substances.indices) {
        for (j in (i + 1) until substances.size) {
            val interaction = checker.getInteractionBetween(substances[i], substances[j])
            val matches = when (type) {
                "dangerous" -> interaction?.interactionType == InteractionType.DANGEROUS
                "unsafe" -> interaction?.interactionType == InteractionType.UNSAFE
                "uncertain" -> interaction?.interactionType == InteractionType.UNCERTAIN
                "any" -> interaction != null
                else -> false
            }
            if (matches) count++
        }
    }
    return count
}

internal fun longestConsecutiveDayStreak(ingestions: List<Ingestion>): Int {
    val dates = ingestions
        .map { it.time.atZone(ZoneId.systemDefault()).toLocalDate() }
        .distinct()
        .sorted()
    if (dates.isEmpty()) return 0
    var longest = 1
    var current = 1
    for (i in 1 until dates.size) {
        if (ChronoUnit.DAYS.between(dates[i - 1], dates[i]) == 1L) {
            current++
            longest = maxOf(longest, current)
        } else {
            current = 1
        }
    }
    return longest
}

/** Longest gap, in days, between consecutive ingestion dates (i.e. longest sober streak). */
internal fun longestSoberStreak(ingestions: List<Ingestion>): Int {
    val dates = ingestions
        .map { it.time.atZone(ZoneId.systemDefault()).toLocalDate() }
        .distinct()
        .sorted()
    if (dates.size < 2) return 0
    var longest = 0L
    for (i in 1 until dates.size) {
        // gap of 1 day means consecutive; sober days in between = gap - 1
        val gap = ChronoUnit.DAYS.between(dates[i - 1], dates[i]) - 1
        if (gap > longest) longest = gap
    }
    return longest.toInt().coerceAtLeast(0)
}

internal fun journalSpanDays(ingestions: List<Ingestion>): Long {
    if (ingestions.size < 2) return 0
    val times = ingestions.map { it.time }
    val first = times.min()
    val last = times.max()
    return ChronoUnit.DAYS.between(first, last)
}

/**
 * A "redose in peak" is an ingestion of the same substance that happens after
 * an earlier one (same experience) but before that earlier ingestion's peak
 * window has ended. Uses the route-specific duration from PsychonautWiki.
 */
internal fun hasRedoseInPeak(ctx: AchievementContext): Boolean {
    for (exp in ctx.experiences) {
        val byKey = exp.ingestions.groupBy { it.substanceName to it.administrationRoute }
        for ((key, list) in byKey) {
            if (list.size < 2) continue
            val substance = ctx.substanceRepo.getSubstance(key.first) ?: continue
            val roa = substance.roas.firstOrNull { it.route == key.second } ?: continue
            val duration = roa.roaDuration ?: continue
            val onsetMax = duration.onset?.maxInSec ?: 0f
            val comeupMax = duration.comeup?.maxInSec ?: 0f
            val peakMax = duration.peak?.maxInSec ?: 0f
            val peakEndSec = (onsetMax + comeupMax + peakMax).toLong()
            if (peakEndSec <= 0L) continue
            val sorted = list.sortedBy { it.time }
            for (i in sorted.indices) {
                for (j in (i + 1) until sorted.size) {
                    val delta = ChronoUnit.SECONDS.between(sorted[i].time, sorted[j].time)
                    if (delta in 1..peakEndSec) return true
                }
            }
        }
    }
    return false
}

private fun checkDoseLevel(
    ingestion: Ingestion,
    level: String,
    substanceRepo: SubstanceRepositoryInterface
): Boolean {
    val dose = ingestion.dose ?: return false
    val substance = substanceRepo.getSubstance(ingestion.substanceName) ?: return false
    val roa = substance.roas.firstOrNull { it.route == ingestion.administrationRoute } ?: return false
    val roaDose = roa.roaDose ?: return false

    return when (level) {
        "heavy" -> roaDose.heavyMin != null && dose >= roaDose.heavyMin
        "strong" -> roaDose.strongMin != null && dose >= roaDose.strongMin
        "common" -> roaDose.commonMin != null && dose >= roaDose.commonMin
        "light" -> roaDose.lightMin != null && dose >= roaDose.lightMin
        else -> false
    }
}
