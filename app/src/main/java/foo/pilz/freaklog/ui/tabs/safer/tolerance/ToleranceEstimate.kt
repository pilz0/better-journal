package foo.pilz.freaklog.ui.tabs.safer.tolerance

import java.time.Duration
import java.time.Instant

data class ParsedTolerance(
    val fullDays: Float,
    val halfLifeDays: Float,
    val zeroDays: Float
)

data class ToleranceEstimate(
    val toleranceLevel: Float,
    val substanceName: String,
    val lastIngestionTime: Instant?,
    val crossToleranceContributors: List<String>,
    val zeroDays: Float = 0f,
    val halfLifeDays: Float = 0f,
) {
    val label: String get() = when {
        toleranceLevel < 0.1f -> "baseline"
        toleranceLevel < 0.3f -> "low tolerance"
        toleranceLevel < 0.6f -> "moderate tolerance"
        toleranceLevel < 0.85f -> "high tolerance"
        else -> "full tolerance"
    }

    val percentage: Int get() = (toleranceLevel * 100).toInt()

    /** Days remaining until tolerance reaches baseline (0), or 0 if already cleared. */
    fun daysUntilClear(now: Instant = Instant.now()): Int {
        val elapsedDays = elapsedDaysSinceLastUse(now) ?: return 0
        return (zeroDays - elapsedDays).coerceAtLeast(0f).toInt()
    }

    /** Days remaining until tolerance drops to roughly half, or 0 if already past that. */
    fun daysUntilHalf(now: Instant = Instant.now()): Int {
        val elapsedDays = elapsedDaysSinceLastUse(now) ?: return 0
        return (halfLifeDays - elapsedDays).coerceAtLeast(0f).toInt()
    }

    private fun elapsedDaysSinceLastUse(now: Instant): Float? {
        val lastUse = lastIngestionTime ?: return null
        return Duration.between(lastUse, now).seconds / 86400f
    }
}
