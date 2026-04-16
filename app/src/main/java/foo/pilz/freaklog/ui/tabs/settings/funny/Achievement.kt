package foo.pilz.freaklog.ui.tabs.settings.funny

import android.content.Context
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestions
import foo.pilz.freaklog.data.substances.classes.InteractionType
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepositoryInterface
import foo.pilz.freaklog.ui.tabs.journal.addingestion.interactions.InteractionChecker
import org.json.JSONArray
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class AchievementDef(
    val id: String,
    val title: String,
    val description: String,
    val condition: String
)

data class UnlockedAchievement(
    val def: AchievementDef,
    val unlocked: Boolean
)

fun loadAchievements(context: Context): List<AchievementDef> {
    val json = context.assets.open("achievements.json").bufferedReader().readText()
    val array = JSONArray(json)
    return (0 until array.length()).map { i ->
        val obj = array.getJSONObject(i)
        AchievementDef(
            id = obj.getString("id"),
            title = obj.getString("title"),
            description = obj.getString("description"),
            condition = obj.getString("condition")
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

fun evaluateAchievement(
    condition: String,
    experiences: List<ExperienceWithIngestions>,
    allIngestions: List<Ingestion>,
    substanceRepo: SubstanceRepositoryInterface,
    interactionChecker: InteractionChecker
): Boolean {
    splitTopLevel(condition, " & ")?.let { parts ->
        return parts.all { evaluateAchievement(it, experiences, allIngestions, substanceRepo, interactionChecker) }
    }

    splitTopLevel(condition, " | ")?.let { parts ->
        return parts.any { evaluateAchievement(it, experiences, allIngestions, substanceRepo, interactionChecker) }
    }

    unwrapOuter(condition, "not")?.let { inner ->
        return !evaluateAchievement(inner, experiences, allIngestions, substanceRepo, interactionChecker)
    }

    unwrapOuter(condition, "in_experience")?.let { inner ->
        return experiences.any { exp ->
            evaluateAchievement(inner, listOf(exp), exp.ingestions, substanceRepo, interactionChecker)
        }
    }

    val match = Regex("""^(\w+)\((.+)\)$""").matchEntire(condition.trim()) ?: return false
    val func = match.groupValues[1]
    val args = match.groupValues[2]

    return when (func) {
        "combo" -> {
            val substances = args.split(",").map { it.trim() }
            experiences.any { exp ->
                val names = exp.ingestions.map { it.substanceName }.distinct()
                substances.all { it in names }
            }
        }

        "substance" -> {
            allIngestions.any { it.substanceName == args.trim() }
        }

        "route" -> {
            val route = args.trim()
            allIngestions.any { it.administrationRoute.name == route }
        }

        "min_ingestions" -> {
            val n = args.trim().toIntOrNull() ?: return false
            allIngestions.size >= n
        }

        "min_experiences" -> {
            val n = args.trim().toIntOrNull() ?: return false
            experiences.size >= n
        }

        "min_substances" -> {
            val n = args.trim().toIntOrNull() ?: return false
            allIngestions.map { it.substanceName }.distinct().size >= n
        }

        "min_combo" -> {
            val n = args.trim().toIntOrNull() ?: return false
            experiences.any { exp ->
                exp.ingestions.map { it.substanceName }.distinct().size >= n
            }
        }

        "dose" -> {
            val level = args.trim()
            allIngestions.any { ingestion ->
                checkDoseLevel(ingestion, level, substanceRepo)
            }
        }

        "dose_substance" -> {
            val parts = args.split(",").map { it.trim() }
            if (parts.size != 2) return false
            val substanceName = parts[0]
            val level = parts[1]
            allIngestions
                .filter { it.substanceName == substanceName }
                .any { checkDoseLevel(it, level, substanceRepo) }
        }

        "category" -> {
            val cat = args.trim()
            allIngestions.any { ingestion ->
                val substance = substanceRepo.getSubstance(ingestion.substanceName)
                substance?.categories?.any { it.equals(cat, ignoreCase = true) } == true
            }
        }

        "min_routes" -> {
            val n = args.trim().toIntOrNull() ?: return false
            allIngestions.map { it.administrationRoute }.distinct().size >= n
        }

        "substance_count" -> {
            val parts = args.split(",").map { it.trim() }
            if (parts.size != 2) return false
            val substanceName = parts[0]
            val n = parts[1].toIntOrNull() ?: return false
            allIngestions.count { it.substanceName == substanceName } >= n
        }

        "any_substance_count" -> {
            val n = args.trim().toIntOrNull() ?: return false
            allIngestions.groupBy { it.substanceName }.any { it.value.size >= n }
        }

        "route_count" -> {
            val parts = args.split(",").map { it.trim() }
            if (parts.size != 2) return false
            val route = parts[0]
            val n = parts[1].toIntOrNull() ?: return false
            allIngestions.count { it.administrationRoute.name == route } >= n
        }

        "has_note" -> {
            allIngestions.any { !it.notes.isNullOrBlank() }
        }

        "has_favorite" -> {
            experiences.any { it.experience.isFavorite }
        }

        "has_location" -> {
            experiences.any { it.experience.location != null }
        }

        "streak" -> {
            val n = args.trim().toIntOrNull() ?: return false
            val dates = allIngestions
                .map { it.time.atZone(ZoneId.systemDefault()).toLocalDate() }
                .distinct()
                .sorted()
            var longest = if (dates.isNotEmpty()) 1 else 0
            var current = 1
            for (i in 1 until dates.size) {
                if (ChronoUnit.DAYS.between(dates[i - 1], dates[i]) == 1L) {
                    current++
                    longest = maxOf(longest, current)
                } else {
                    current = 1
                }
            }
            longest >= n
        }

        "interaction" -> {
            val type = args.trim().lowercase()
            experiences.any { exp ->
                val substances = exp.ingestions.map { it.substanceName }.distinct()
                substances.indices.any { i ->
                    (i + 1 until substances.size).any { j ->
                        val interaction = interactionChecker.getInteractionBetween(substances[i], substances[j])
                        when (type) {
                            "dangerous" -> interaction?.interactionType == InteractionType.DANGEROUS
                            "unsafe" -> interaction?.interactionType == InteractionType.UNSAFE
                            "uncertain" -> interaction?.interactionType == InteractionType.UNCERTAIN
                            "any" -> interaction != null
                            else -> false
                        }
                    }
                }
            }
        }

        "min_interactions" -> {
            val splitArgs = args.split(",").map { it.trim() }
            val n = splitArgs.getOrNull(0)?.toIntOrNull() ?: return false
            val type = splitArgs.getOrNull(1) ?: return false

            experiences.any { exp ->
                val substances = exp.ingestions.map { it.substanceName }.distinct()
                val count = substances.indices.sumOf { i ->
                    (i + 1 until substances.size).count { j ->
                        val interaction = interactionChecker.getInteractionBetween(substances[i], substances[j])
                        when (type) {
                            "dangerous" -> interaction?.interactionType == InteractionType.DANGEROUS
                            "unsafe" -> interaction?.interactionType == InteractionType.UNSAFE
                            "uncertain" -> interaction?.interactionType == InteractionType.UNCERTAIN
                            "any" -> interaction != null
                            else -> false
                        }
                    }
                }
                count >= n
            }
        }

        "stomachFullness" -> {
            val fullness = args.trim()
            allIngestions.any { it.stomachFullness?.name == fullness }
        }

        else -> false
    }
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
