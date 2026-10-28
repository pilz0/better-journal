package foo.pilz.freaklog.ui.utils

import foo.pilz.freaklog.ui.tabs.search.substance.roa.toReadableString

private val volumeUnitToMilliliters = mapOf(
    "µl" to 0.001,
    "ul" to 0.001,
    "mcl" to 0.001,
    "ml" to 1.0,
    "cl" to 10.0,
    "dl" to 100.0,
    "l" to 1000.0,
)

private val massUnitToMilligrams = mapOf(
    "µg" to 0.001,
    "ug" to 0.001,
    "mcg" to 0.001,
    "mg" to 1.0,
    "g" to 1000.0,
    "kg" to 1_000_000.0,
)

// Matches both the micro sign (U+00B5) and Greek small mu (U+03BC); units are normalized to the former.
private val trailingUnitRegex = Regex("^(.*?)([µμ]?[a-zA-Z]+)$")

private fun normalizeUnit(unit: String): String = unit.lowercase().replace('μ', 'µ')

private fun splitTrailingUnit(input: String): Pair<String, String>? {
    val text = input.trim().replace(',', '.')
    if (text.isEmpty()) return null
    val match = trailingUnitRegex.matchEntire(text) ?: return text to ""
    val expr = match.groupValues[1].trim()
    val unit = normalizeUnit(match.groupValues[2])
    return expr to unit
}

fun parseVolumeToMl(input: String): Double? {
    val (expr, unit) = splitTrailingUnit(input) ?: return null
    val value = evaluateNumericExpression(expr) ?: return null
    val factor = if (unit.isEmpty()) 1.0 else volumeUnitToMilliliters[unit] ?: return null
    return (value * factor).takeIf { it.isFinite() }
}

fun parseConcentrationToMgPerMl(input: String): Double? {
    val parts = input.split('/')
    return when (parts.size) {
        1 -> massInMg(input, "mg")
        2 -> parseConcentrationWithVolume(parts)
        else -> null
    }
}

internal fun massInMg(input: String, defaultUnit: String): Double? {
    val (expr, unit) = splitTrailingUnit(input) ?: return null
    val value = evaluateNumericExpression(expr) ?: return null
    val factor = massUnitToMilligrams[normalizeUnit(unit.ifEmpty { defaultUnit })] ?: return null
    return (value * factor).takeIf { it.isFinite() }
}

private fun parseConcentrationWithVolume(parts: List<String>): Double? {
    val mass = massInMg(parts[0], "mg") ?: return null
    val (volumeExpr, volumeUnit) = splitTrailingUnit(parts[1].ifBlank { "1" }) ?: return null
    val volume = evaluateNumericExpression(volumeExpr.ifBlank { "1" }) ?: return null
    val volumeFactor = if (volumeUnit.isEmpty()) 1.0 else volumeUnitToMilliliters[volumeUnit] ?: return null
    val divisor = volume * volumeFactor
    if (divisor <= 0.0 || !divisor.isFinite()) return null
    return (mass / divisor).takeIf { it.isFinite() }
}

fun formatVolume(ml: Double): String = when {
    !ml.isFinite() || ml < 0.0 -> ""
    ml >= 1000.0 -> "${(ml / 1000.0).toReadableString()}l"
    ml < 1.0 -> "${(ml * 1000.0).toReadableString()}µl"
    else -> "${ml.toReadableString()}ml"
}

fun formatConcentration(mgPerMl: Double): String {
    if (!mgPerMl.isFinite() || mgPerMl < 0.0) return ""
    val microgramsPerLiter = mgPerMl * 1_000_000.0
    return when {
        mgPerMl >= 1000.0 -> "${(mgPerMl / 1000.0).toReadableString()}g/ml"
        mgPerMl >= 1.0 -> "${mgPerMl.toReadableString()}mg/ml"
        mgPerMl >= 0.001 -> "${(mgPerMl * 1000.0).toReadableString()}µg/ml"
        microgramsPerLiter >= 1.0 -> "${microgramsPerLiter.toReadableString()}µg/l"
        else -> "${microgramsPerLiter.toReadableString()}µg/l"
    }
}
