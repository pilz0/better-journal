package foo.pilz.freaklog.ui.tabs.safer.tolerance

import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.min

@Singleton
class ToleranceCalculator @Inject constructor(
    private val substanceRepo: SubstanceRepository,
    private val experienceRepo: ExperienceRepository
) {
    suspend fun calculate(substanceName: String): ToleranceEstimate? {
        val substance = substanceRepo.getSubstance(substanceName) ?: return null
        val tolerance = substance.tolerance ?: return null
        val parsed = ToleranceTextParser.parse(tolerance) ?: return null

        val crossToleranceValues = substance.crossTolerances
        val crossSubstanceNames = if (crossToleranceValues.isNotEmpty()) {
            substanceRepo.getAllSubstances()
                .filter { other ->
                    other.name != substanceName &&
                    other.crossTolerances.any { ct -> crossToleranceValues.any { it.equals(ct, ignoreCase = true) } }
                }
                .map { it.name }
        } else {
            emptyList()
        }

        val allNames = listOf(substanceName) + crossSubstanceNames
        val since = Instant.now().minus(parsed.zeroDays.toLong().coerceAtLeast(1), ChronoUnit.DAYS)
        val ingestions = experienceRepo.getIngestionsSince(allNames, since)

        val now = Instant.now()
        val level = computeToleranceLevel(ingestions.map { it.time }, parsed, now)

        val lastIngestion = experienceRepo.getLastIngestion(substanceName)
        val contributors = ingestions
            .filter { it.substanceName != substanceName }
            .map { it.substanceName }
            .distinct()

        return ToleranceEstimate(
            toleranceLevel = level,
            substanceName = substanceName,
            lastIngestionTime = lastIngestion?.time,
            crossToleranceContributors = contributors,
            zeroDays = parsed.zeroDays
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
                val elapsedSeconds = Duration.between(time, now).seconds.toDouble()
                exp(-0.693 * elapsedSeconds / halfLifeSeconds)
            }
            return min(1f, sum.toFloat())
        }
    }
}
