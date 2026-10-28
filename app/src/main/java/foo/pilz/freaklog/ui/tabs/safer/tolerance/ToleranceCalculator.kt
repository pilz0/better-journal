package foo.pilz.freaklog.ui.tabs.safer.tolerance

import foo.pilz.freaklog.data.room.experiences.CustomSubstanceProfiles
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.findProfile
import foo.pilz.freaklog.data.room.experiences.toTolerance
import foo.pilz.freaklog.data.substances.classes.Tolerance
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
    private val experienceRepo: ExperienceRepository,
    private val customProfiles: CustomSubstanceProfiles,
) {
    /** What the calculation needs from either a built-in or a custom substance. */
    private class ToleranceSubject(
        val name: String,
        val tolerance: Tolerance?,
        val crossTolerances: List<String>,
    )

    suspend fun calculate(substanceName: String): ToleranceEstimate? {
        val cleanedName = substanceName.trim()
        val profiles = customProfiles.byNameFlow.value
        val substance = (substanceRepo.getSubstance(cleanedName)
            ?: substanceRepo.getAllSubstances().firstOrNull { it.name.trim().equals(cleanedName, ignoreCase = true) })
            ?.let { ToleranceSubject(it.name, it.tolerance, it.crossTolerances) }
            ?: findProfile(profiles, cleanedName)?.let { profile ->
                ToleranceSubject(
                    name = profile.substance.name,
                    tolerance = profile.substance.toTolerance(),
                    crossTolerances = profile.crossTolerances.map { it.categoryName },
                )
            }
            ?: return null
        val tolerance = substance.tolerance ?: return null
        val parsed = ToleranceTextParser.parse(tolerance) ?: return null

        val crossToleranceValues = substance.crossTolerances.map { it.trim().lowercase() }.toSet()
        val crossSubstanceNames = if (crossToleranceValues.isNotEmpty()) {
            val builtInNames = substanceRepo.getAllSubstances()
                .filter { other -> other.crossTolerances.any { it.trim().lowercase() in crossToleranceValues } }
                .map { it.name }
            val customNames = profiles.values
                .filter { other ->
                    other.crossTolerances.any { it.categoryName.trim().lowercase() in crossToleranceValues }
                }
                .map { it.substance.name }
            (builtInNames + customNames)
                .filter { !it.trim().equals(substance.name.trim(), ignoreCase = true) }
                .distinctBy { it.trim().lowercase() }
        } else {
            emptyList()
        }

        val allNames = listOf(substance.name) + crossSubstanceNames
        val now = Instant.now()
        val safeZeroDays = if (parsed.zeroDays.isFinite() && parsed.zeroDays > 0f) {
            parsed.zeroDays.toDouble().coerceIn(1.0, 36500.0)
        } else {
            36500.0
        }
        val since = now.minus(ceil(safeZeroDays).toLong(), ChronoUnit.DAYS)
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
            if (halfLifeSeconds <= 0.0 || !halfLifeSeconds.isFinite()) return 0f

            val sum = ingestionTimes.sumOf { time ->
                val elapsedSeconds = Duration.between(time, now).seconds.toDouble().coerceAtLeast(0.0)
                exp(-0.693 * elapsedSeconds / halfLifeSeconds)
            }
            return if (sum.isFinite()) min(1f, sum.toFloat()).coerceAtLeast(0f) else 0f
        }
    }
}
