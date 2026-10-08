package foo.pilz.freaklog.ui.tabs.settings.intakelimits

import foo.pilz.freaklog.data.room.experiences.entities.IntakeLimit
import foo.pilz.freaklog.data.room.experiences.entities.IntakeLimitType
import foo.pilz.freaklog.ui.tabs.search.substance.roa.toReadableString
import java.time.Instant
import kotlin.math.roundToInt

data class LimitIngestion(
    val effectiveDose: Double?,
    val unit: String?,
    val time: Instant,
)

data class LimitContribution(
    val value: Double,
    val exitTime: Instant,
)

data class IntakeLimitStatus(
    val limit: IntakeLimit,
    val now: Instant,
    val currentValue: Double,
    val pendingValue: Double,
    val maxValue: Double,
    val unitLabel: String,
    val ignoredIngestionCount: Int,
    val contributions: List<LimitContribution>,
) {
    val projectedValue: Double get() = currentValue + pendingValue
    val currentPercent: Double get() = if (maxValue <= 0.0) 0.0 else currentValue / maxValue * 100.0
    val projectedPercent: Double get() = if (maxValue <= 0.0) 0.0 else projectedValue / maxValue * 100.0
    val exceedsLimit: Boolean get() = projectedValue > maxValue
    val crossesWarning: Boolean get() = projectedPercent >= limit.warningPercent
}

private val massUnitToMilligrams: Map<String, Double> = mapOf(
    "µg" to 0.001,
    "ug" to 0.001,
    "mcg" to 0.001,
    "mg" to 1.0,
    "g" to 1000.0,
    "kg" to 1_000_000.0,
)

fun convertDose(value: Double, fromUnit: String?, toUnit: String?): Double? {
    if (fromUnit == null || toUnit == null) return null
    if (fromUnit.trim().equals(toUnit.trim(), ignoreCase = true)) return value
    val from = massUnitToMilligrams[fromUnit.trim().lowercase()] ?: return null
    val to = massUnitToMilligrams[toUnit.trim().lowercase()] ?: return null
    return value * from / to
}

fun evaluateIntakeLimit(
    limit: IntakeLimit,
    now: Instant,
    pastIngestions: List<LimitIngestion>,
    pending: LimitIngestion?,
): IntakeLimitStatus {
    val windowStart = now.minusSeconds(limit.windowSeconds)
    val inWindow = pastIngestions.filter { it.time.isAfter(windowStart) }
    return when (limit.limitType) {
        IntakeLimitType.COUNT -> {
            val contributions = inWindow.map {
                LimitContribution(1.0, it.time.plusSeconds(limit.windowSeconds))
            } + listOfNotNull(
                pending?.let { LimitContribution(1.0, now.plusSeconds(limit.windowSeconds)) }
            )
            IntakeLimitStatus(
                limit = limit,
                now = now,
                currentValue = inWindow.size.toDouble(),
                pendingValue = if (pending != null) 1.0 else 0.0,
                maxValue = (limit.maxCount ?: 0).toDouble(),
                unitLabel = "ingestions",
                ignoredIngestionCount = 0,
                contributions = contributions,
            )
        }

        IntakeLimitType.DOSE -> {
            var sum = 0.0
            var ignored = 0
            val contributions = mutableListOf<LimitContribution>()
            for (ingestion in inWindow) {
                val converted =
                    ingestion.effectiveDose?.let { convertDose(it, ingestion.unit, limit.unit) }
                if (converted == null) {
                    ignored++
                } else {
                    sum += converted
                    contributions.add(
                        LimitContribution(
                            converted,
                            ingestion.time.plusSeconds(limit.windowSeconds)
                        )
                    )
                }
            }
            val pendingValue =
                pending?.effectiveDose?.let { convertDose(it, pending.unit, limit.unit) } ?: 0.0
            if (pendingValue > 0.0) {
                contributions.add(
                    LimitContribution(
                        pendingValue,
                        now.plusSeconds(limit.windowSeconds)
                    )
                )
            }
            IntakeLimitStatus(
                limit = limit,
                now = now,
                currentValue = sum,
                pendingValue = pendingValue,
                maxValue = limit.maxDose ?: 0.0,
                unitLabel = limit.unit ?: "",
                ignoredIngestionCount = ignored,
                contributions = contributions,
            )
        }
    }
}

private fun IntakeLimitStatus.instantUnder(target: Double): Instant? {
    if (projectedValue <= target) return null
    val sorted = contributions.sortedBy { it.exitTime }
    var remaining = projectedValue
    for (contribution in sorted) {
        remaining -= contribution.value
        if (remaining <= target) return contribution.exitTime
    }
    return sorted.lastOrNull()?.exitTime
}

fun IntakeLimitStatus.instantUnderLimit(): Instant? = instantUnder(maxValue)

fun IntakeLimitStatus.instantUnderWarning(): Instant? =
    instantUnder(maxValue * limit.warningPercent / 100.0)

fun IntakeLimitStatus.instantFullReset(): Instant? = instantUnder(0.0)

fun IntakeLimit.windowDescription(): String {
    val seconds = windowSeconds
    return when {
        seconds == 86_400L -> "24 hours"
        seconds == 604_800L -> "7 days"
        seconds == 2_592_000L -> "30 days"
        seconds % 86_400L == 0L -> "${seconds / 86_400L} days"
        seconds % 3_600L == 0L -> "${seconds / 3_600L} hours"
        else -> "${seconds / 60L} minutes"
    }
}

fun IntakeLimit.maxDescription(): String = when (limitType) {
    IntakeLimitType.DOSE -> "${(maxDose ?: 0.0).toReadableString()} ${unit.orEmpty()}".trim()
    IntakeLimitType.COUNT -> {
        val count = maxCount ?: 0
        "$count ${if (count == 1) "ingestion" else "ingestions"}"
    }
}

fun IntakeLimit.summaryDescription(): String = "Max ${maxDescription()} per ${windowDescription()}"

fun IntakeLimitStatus.currentLine(): String =
    "Currently ${currentValue.toReadableString()} $unitLabel".trim() + " (${currentPercent.roundToInt()}%)"

fun IntakeLimitStatus.projectedLine(): String =
    "After this ingestion: ${projectedValue.toReadableString()} $unitLabel".trim() + " (${projectedPercent.roundToInt()}%)"

fun IntakeLimitStatus.warningLine(): String =
    "Last ${limit.windowDescription()}: ${projectedValue.toReadableString()} of ${maxValue.toReadableString()} $unitLabel".trim() + " (${projectedPercent.roundToInt()}%)"

fun IntakeLimitStatus.listUsageLine(): String =
    "${currentValue.toReadableString()} of ${maxValue.toReadableString()} $unitLabel in last ${limit.windowDescription()}".trim() + " (${currentPercent.roundToInt()}%)"

val STANDARD_DOSE_UNITS: List<String> = listOf("µg", "mg", "g", "mL")

fun suggestedDoseUnits(substanceUnits: List<String>): List<String> {
    val ordered = LinkedHashSet<String>()
    substanceUnits.map { it.trim() }.filter { it.isNotEmpty() }.forEach { ordered.add(it) }
    STANDARD_DOSE_UNITS.forEach { ordered.add(it) }
    return ordered.toList()
}

fun defaultDoseUnit(substanceUnits: List<String>): String =
    substanceUnits.map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: "mg"
