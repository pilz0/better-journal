package foo.pilz.freaklog.ui.tabs.safer.tolerance

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
    val zeroDays: Float = 0f
) {
    val label: String get() = when {
        toleranceLevel < 0.1f -> "baseline"
        toleranceLevel < 0.3f -> "low tolerance"
        toleranceLevel < 0.6f -> "moderate tolerance"
        toleranceLevel < 0.85f -> "high tolerance"
        else -> "full tolerance"
    }

    val percentage: Int get() = (toleranceLevel * 100).toInt()
}
