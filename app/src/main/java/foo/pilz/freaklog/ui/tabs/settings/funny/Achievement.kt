package foo.pilz.freaklog.ui.tabs.settings.funny

import android.content.Context
import foo.pilz.freaklog.data.room.experiences.entities.CustomRecipe
import foo.pilz.freaklog.data.room.experiences.entities.CustomUnit
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.entities.ShulginRating
import foo.pilz.freaklog.data.room.experiences.entities.TimedNote
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestions
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsTimedNotesAndRatings
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.InteractionType
import foo.pilz.freaklog.data.substances.classes.roa.DoseClass
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepositoryInterface
import foo.pilz.freaklog.ui.tabs.journal.addingestion.interactions.Interaction
import foo.pilz.freaklog.ui.tabs.journal.addingestion.interactions.InteractionChecker
import foo.pilz.freaklog.ui.tabs.settings.funny.condition.AchievementEngine
import foo.pilz.freaklog.ui.tabs.settings.funny.condition.EvalContext
import foo.pilz.freaklog.ui.tabs.settings.funny.condition.SubstanceInfo
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray

enum class AchievementTier {
  BRONZE,
  SILVER,
  GOLD,
}

private val PREDICATE_REGEX = Regex("""^(\w+)\((.*)\)$""")

data class AchievementDef(
    val id: String,
    val title: String,
    val description: String,
    val condition: String,
    val tier: AchievementTier? = null,
)

data class UnlockedAchievement(
    val def: AchievementDef,
    val unlocked: Boolean,
)

class RepositorySubstanceInfo(private val substanceRepo: SubstanceRepositoryInterface) :
    SubstanceInfo {
  private val categoriesCache = ConcurrentHashMap<String, Set<String>>()

  override fun categories(substanceName: String): Set<String> =
      categoriesCache.getOrPut(substanceName) {
        substanceRepo.getSubstance(substanceName)?.categories?.mapTo(HashSet()) {
          it.lowercase(Locale.ROOT)
        } ?: emptySet()
      }

  override fun doseClass(
      substanceName: String,
      route: AdministrationRoute,
      dose: Double?,
      units: String?,
  ): DoseClass? {
    val substance = substanceRepo.getSubstance(substanceName) ?: return null
    val roaDose = substance.getRoa(route)?.roaDose ?: return null
    return roaDose.getDoseClass(dose, units)
  }
}

/**
 * Everything the achievement DSL can see. Using a bundle keeps the recursive evaluator signature
 * small and future-proof as we add more predicates.
 */
data class AchievementContext(
    val experiences: List<ExperienceWithIngestionsTimedNotesAndRatings>,
    val allIngestions: List<Ingestion>,
    val allTimedNotes: List<TimedNote>,
    val allRatings: List<ShulginRating>,
    val customUnits: List<CustomUnit>,
    val customRecipes: List<CustomRecipe>,
    val substanceRepo: SubstanceRepositoryInterface,
    val interactionChecker: InteractionChecker,
    val checkActive: () -> Unit = {},
) {
  internal val substanceCounts: Map<String, Int> by lazy {
    allIngestions.groupingBy { it.substanceName }.eachCount()
  }

  internal val routeCounts: Map<AdministrationRoute, Int> by lazy {
    allIngestions.groupingBy { it.administrationRoute }.eachCount()
  }

  internal val routeCountsByName: Map<String, Int> by lazy {
    routeCounts.entries.associate { (route, count) -> route.name to count }
  }

  internal val sortedIngestionDates: List<LocalDate> by lazy {
    computeSortedIngestionDates(allIngestions)
  }

  internal val hasNonBlankIngestionNote: Boolean by lazy {
    allIngestions.any { !it.notes.isNullOrBlank() }
  }

  internal val nonBlankIngestionNoteCount: Int by lazy {
    allIngestions.count { !it.notes.isNullOrBlank() }
  }

  private val interactionsByExperience = ConcurrentHashMap<Int, List<Interaction>>()

  internal fun interactionsForExperience(
      exp: ExperienceWithIngestionsTimedNotesAndRatings
  ): List<Interaction> =
      interactionsByExperience.getOrPut(exp.experience.id) {
        val substances = exp.ingestions.map { it.substanceName }.distinct()
        interactionChecker.getInteractionsAmong(substances)
      }

  val evalContext: EvalContext by lazy {
    val experiencesById = experiences.associateBy { it.experience.id }
    EvalContext(
        experiences = experiences.map { ExperienceWithIngestions(it.experience, it.ingestions) },
        ingestions = allIngestions,
        substanceInfo = RepositorySubstanceInfo(substanceRepo),
        interactionCount = { experienceId, filter ->
          val exp = experiencesById[experienceId]
          if (exp == null) {
            0
          } else {
            val filterType = filter.name.lowercase(Locale.ROOT)
            interactionsForExperience(exp).count { it.matchesType(filterType) }
          }
        },
    )
  }
}

private val achievementCacheLock = Any()
@Volatile private var cachedAchievementDefs: List<AchievementDef>? = null

internal fun clearAchievementCacheForTest() {
  cachedAchievementDefs = null
}

fun loadAchievements(context: Context): List<AchievementDef> {
  cachedAchievementDefs?.let {
    return it
  }
  return synchronized(achievementCacheLock) {
    cachedAchievementDefs?.let {
      return@synchronized it
    }
    val json = context.assets.open("achievements.json").bufferedReader().use { it.readText() }
    val array = JSONArray(json)
    val parsed =
        (0 until array.length()).map { i ->
          val obj = array.getJSONObject(i)
          val tierStr = if (obj.has("tier") && !obj.isNull("tier")) obj.getString("tier") else null
          AchievementDef(
              id = obj.getString("id"),
              title = obj.getString("title"),
              description = obj.getString("description"),
              condition = obj.getString("condition"),
              tier =
                  tierStr?.let {
                    runCatching { AchievementTier.valueOf(it.uppercase(Locale.ROOT)) }.getOrNull()
                  },
          )
        }
    cachedAchievementDefs = parsed
    parsed
  }
}

private fun splitTopLevel(condition: String, delimiter: String): List<String>? {
  val parts = mutableListOf<String>()
  var depth = 0
  var current = StringBuilder()
  var i = 0
  while (i < condition.length) {
    if (condition[i] == '(') depth++ else if (condition[i] == ')') depth--

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
    if (condition[i] == '(') depth++ else if (condition[i] == ')') depth--
    if (depth == 0 && i >= prefix.length) {
      return if (i == condition.length - 1) {
        condition.drop(prefix.length + 1).dropLast(1)
      } else null
    }
  }
  return null
}

private val conditionEngine = AchievementEngine()
private val CONDITION_LANGUAGE_REGEX = Regex("""\b(ingestions|experiences)\b""")

/**
 * Conditions written in the upstream app's condition language always query the `ingestions` or
 * `experiences` collection; everything else is this app's older predicate syntax.
 */
internal fun usesConditionLanguage(condition: String): Boolean =
    CONDITION_LANGUAGE_REGEX.containsMatchIn(condition)

fun evaluateAchievement(condition: String, ctx: AchievementContext): Boolean {
  if (usesConditionLanguage(condition)) {
    return try {
      conditionEngine.evaluate(condition, ctx.evalContext, ctx.checkActive)
    } catch (e: kotlinx.coroutines.CancellationException) {
      throw e
    } catch (_: Exception) {
      false
    }
  }

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
              allRatings = exp.ratings,
          ),
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

    "substance" -> (ctx.substanceCounts[args.trim()] ?: 0) > 0

    "route" -> (ctx.routeCountsByName[args.trim()] ?: 0) > 0

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
      ctx.substanceCounts.size >= n
    }

    "min_combo" -> {
      val n = args.trim().toIntOrNull() ?: return false
      ctx.experiences.any { exp ->
        exp.ingestions.distinctBy { it.substanceName }.size >= n
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
      ctx.allIngestions.any {
        it.substanceName == substanceName && checkDoseLevel(it, level, ctx.substanceRepo)
      }
    }

    "category" -> {
      val cat = args.trim()
      ctx.substanceCounts.keys.any { substanceName ->
        val substance = ctx.substanceRepo.getSubstance(substanceName)
        substance?.categories?.any { it.equals(cat, ignoreCase = true) } == true
      }
    }

    "min_routes" -> {
      val n = args.trim().toIntOrNull() ?: return false
      ctx.routeCounts.size >= n
    }

    "substance_count" -> {
      val parts = args.split(",").map { it.trim() }
      if (parts.size != 2) return false
      val substanceName = parts[0]
      val n = parts[1].toIntOrNull() ?: return false
      (ctx.substanceCounts[substanceName] ?: 0) >= n
    }

    "any_substance_count" -> {
      val n = args.trim().toIntOrNull() ?: return false
      ctx.substanceCounts.any { it.value >= n }
    }

    "route_count" -> {
      val parts = args.split(",").map { it.trim() }
      if (parts.size != 2) return false
      val route = parts[0]
      val n = parts[1].toIntOrNull() ?: return false
      (ctx.routeCountsByName[route] ?: 0) >= n
    }

    "has_note" -> ctx.hasNonBlankIngestionNote

    "min_notes" -> {
      val n = args.trim().toIntOrNull() ?: return false
      ctx.nonBlankIngestionNoteCount >= n
    }

    "has_favorite" -> ctx.experiences.any { it.experience.isFavorite }

    "has_location" -> ctx.experiences.any { it.experience.location != null }

    "streak" -> {
      val n = args.trim().toIntOrNull() ?: return false
      longestConsecutiveDayStreakFromDates(ctx.sortedIngestionDates) >= n
    }

    "sober_streak" -> {
      val n = args.trim().toIntOrNull() ?: return false
      longestSoberStreakFromDates(ctx.sortedIngestionDates) >= n
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

    "has_transdermal_site" ->
        ctx.allIngestions.any {
          it.administrationRoute == AdministrationRoute.TRANSDERMAL &&
              !it.administrationSite.isNullOrBlank()
        }

    "has_repeated_substance" ->
        ctx.experiences.any { exp ->
          val seen = hashSetOf<String>()
          exp.ingestions.any { !seen.add(it.substanceName) }
        }
    "has_redose_in_peak" -> hasRedoseInPeak(ctx)

    "interaction" -> {
      val type = args.trim().lowercase(Locale.ROOT)
      ctx.experiences.any { exp ->
        ctx.interactionsForExperience(exp).any { it.matchesType(type) }
      }
    }

    "min_interactions" -> {
      val splitArgs = args.split(",").map { it.trim() }
      val n = splitArgs.getOrNull(0)?.toIntOrNull() ?: return false
      val type = splitArgs.getOrNull(1)?.lowercase(Locale.ROOT) ?: return false

      ctx.experiences.any { exp ->
        ctx.interactionsForExperience(exp).count { it.matchesType(type) } >= n
      }
    }

    "stomachFullness" -> {
      val fullness = args.trim()
      ctx.allIngestions.any { it.stomachFullness?.name == fullness }
    }

    else -> false
  }
}

internal fun countInteractionsInList(
    substances: List<String>,
    type: String,
    checker: InteractionChecker,
): Int = checker.getInteractionsAmong(substances).count { it.matchesType(type) }

internal fun Interaction.matchesType(type: String): Boolean =
    when (type) {
      "dangerous" -> interactionType == InteractionType.DANGEROUS
      "unsafe" -> interactionType == InteractionType.UNSAFE
      "uncertain" -> interactionType == InteractionType.UNCERTAIN
      "any" -> true
      else -> false
    }

internal fun computeSortedIngestionDates(ingestions: List<Ingestion>): List<LocalDate> {
  if (ingestions.isEmpty()) return emptyList()
  val zone = ZoneId.systemDefault()
  return ingestions
      .mapNotNull { runCatching { it.time.atZone(zone).toLocalDate() }.getOrNull() }
      .distinct()
      .sorted()
}

internal fun longestConsecutiveDayStreak(ingestions: List<Ingestion>): Int =
    longestConsecutiveDayStreakFromDates(computeSortedIngestionDates(ingestions))

private fun longestConsecutiveDayStreakFromDates(dates: List<LocalDate>): Int {
  if (dates.isEmpty()) return 0
  var longest = 1
  var current = 1
  for (i in 1 until dates.size) {
    val daysBetween =
        runCatching { ChronoUnit.DAYS.between(dates[i - 1], dates[i]) }.getOrDefault(0L)
    if (daysBetween == 1L) {
      current++
      longest = maxOf(longest, current)
    } else {
      current = 1
    }
  }
  return longest
}

/** Longest gap, in days, between consecutive ingestion dates (i.e. longest sober streak). */
internal fun longestSoberStreak(ingestions: List<Ingestion>): Int =
    longestSoberStreakFromDates(computeSortedIngestionDates(ingestions))

private fun longestSoberStreakFromDates(dates: List<LocalDate>): Int {
  if (dates.size < 2) return 0
  var longest = 0L
  for (i in 1 until dates.size) {
    // gap of 1 day means consecutive; sober days in between = gap - 1
    val gap = runCatching { ChronoUnit.DAYS.between(dates[i - 1], dates[i]) - 1 }.getOrDefault(0L)
    if (gap > longest) longest = gap
  }
  return longest.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

internal fun journalSpanDays(ingestions: List<Ingestion>): Long {
  if (ingestions.size < 2) return 0
  val times = ingestions.map { it.time }
  val first = times.minOrNull() ?: return 0
  val last = times.maxOrNull() ?: return 0
  return runCatching { ChronoUnit.DAYS.between(first, last).coerceAtLeast(0L) }.getOrDefault(0L)
}

/**
 * A "redose in peak" is an ingestion of the same substance that happens after an earlier one (same
 * experience) but before that earlier ingestion's peak window has ended. Uses the route-specific
 * duration from PsychonautWiki.
 */
internal fun hasRedoseInPeak(ctx: AchievementContext): Boolean {
  for (exp in ctx.experiences) {
    val byKey = exp.ingestions.groupBy { it.substanceName to it.administrationRoute }
    for ((key, list) in byKey) {
      if (list.size < 2) continue
      val substance = ctx.substanceRepo.getSubstance(key.first) ?: continue
      val roa = substance.roas.firstOrNull { it.route == key.second } ?: continue
      val duration = roa.roaDuration ?: continue
      val onsetMax = duration.onset?.maxInSec?.takeIf { it.isFinite() && it >= 0f } ?: 0f
      val comeupMax = duration.comeup?.maxInSec?.takeIf { it.isFinite() && it >= 0f } ?: 0f
      val peakMax = duration.peak?.maxInSec?.takeIf { it.isFinite() && it >= 0f } ?: 0f
      val sum = onsetMax.toDouble() + comeupMax.toDouble() + peakMax.toDouble()
      val peakEndSec =
          if (sum.isFinite() && sum > 0.0) {
            sum.coerceAtMost(Long.MAX_VALUE.toDouble()).toLong()
          } else {
            0L
          }
      if (peakEndSec <= 0L) continue
      if (hasPairWithinPeak(list.map { it.time }.sorted(), peakEndSec)) return true
    }
  }
  return false
}

internal fun hasPairWithinPeak(times: List<java.time.Instant>, peakEndSec: Long): Boolean {
  var left = 0
  for (right in times.indices) {
    while (left < right) {
      val delta =
          runCatching {
                ChronoUnit.SECONDS.between(times[left], times[right])
              }
              .getOrDefault(Long.MAX_VALUE)
      if (delta > peakEndSec) {
        left++
      } else {
        if (delta >= 1) return true
        break
      }
    }
  }
  return false
}

private fun checkDoseLevel(
    ingestion: Ingestion,
    level: String,
    substanceRepo: SubstanceRepositoryInterface,
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
