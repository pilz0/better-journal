package foo.pilz.freaklog.ui.tabs.safer.tolerance

import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.min

@Singleton
class ToleranceCalculator @Inject constructor(
    private val substanceRepo: SubstanceRepository,
    private val experienceRepo: ExperienceRepository
) {
    suspend fun calculate(substanceName: String): ToleranceEstimate? {
        val cleanedName = substanceName.trim()
        val substance = substanceRepo.getSubstance(cleanedName)
            ?: substanceRepo.getAllSubstances().firstOrNull { it.name.trim().equals(cleanedName, ignoreCase = true) }
            ?: return null
        val tolerance = substance.tolerance ?: return null
        val parsed = ToleranceTextParser.parse(tolerance) ?: return null

        val crossToleranceValues = substance.crossTolerances.map { it.trim().lowercase() }.toSet()
        val crossSubstanceNames = if (crossToleranceValues.isNotEmpty()) {
            substanceRepo.getAllSubstances()
                .filter { other ->
                    !other.name.trim().equals(substance.name.trim(), ignoreCase = true) &&
                    other.crossTolerances.any { it.trim().lowercase() in crossToleranceValues }
                }
                .map { it.name }
        } else {
            emptyList()
        }

        val allNames = listOf(substance.name) + crossSubstanceNames
        val now = Instant.now()
        val since = now.minus(ceil(parsed.zeroDays.toDouble()).toLong().coerceAtLeast(1L), ChronoUnit.DAYS)
        val ingestions = experienceRepo.getIngestionsSince(allNames, since)

        val level = computeToleranceLevel(ingestions.map { it.time }, parsed, now)

        val lastIngestion = experienceRepo.getLastIngestion(substance.name)
        val contributors = ingestions
            .filter { !it.substanceName.trim().equals(substance.name.trim(), ignoreCase = true) }
            .map { it.substanceName.trim() }
            .distinctBy { it.lowercase() }

        return ToleranceEstimate(
            toleranceLevel = level,
            substanceName = substance.name,
            lastIngestionTime = lastIngestion?.time,
            crossToleranceContributors = contributors,
            zeroDays = parsed.zeroDays,
            halfLifeDays = parsed.halfLifeDays,
        )
    }

    companion object {
        fun computeToleranceLevel(
            ingestionTimes: List<Instant>,
            parsedTolerance: ParsedTolerance,
            now: Instant
        ): Float {
            if (ingestionTimes.isEmpty()) return 0f
            val halfLifeSeconds = (parsedTolerance.halfLifeDays * 86400f).toDouble()
            if (halfLifeSeconds <= 0) return 0f

            val sum = ingestionTimes.sumOf { time ->
                val elapsedSeconds = Duration.between(time, now).seconds.toDouble().coerceAtLeast(0.0)
                exp(-0.693 * elapsedSeconds / halfLifeSeconds)
            }
            return min(1f, sum.toFloat())
        }
    }
}
