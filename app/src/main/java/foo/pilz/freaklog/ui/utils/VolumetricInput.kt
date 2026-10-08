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

private val trailingUnitRegex = Regex("^(.*?)(µ?[a-zA-Z]+)$")

private fun splitTrailingUnit(input: String): Pair<String, String>? {
    val text = input.trim().replace(',', '.')
    if (text.isEmpty()) return null
    val match = trailingUnitRegex.matchEntire(text) ?: return text to ""
    val expr = match.groupValues[1].trim()
    val unit = match.groupValues[2].lowercase()
    return expr to unit
}

fun parseVolumeToMl(input: String): Double? {
    val (expr, unit) = splitTrailingUnit(input) ?: return null
    val value = evaluateNumericExpression(expr) ?: return null
    val factor = if (unit.isEmpty()) 1.0 else volumeUnitToMilliliters[unit] ?: return null
    return value * factor
}

fun parseConcentrationToMgPerMl(input: String): Double? {
    val parts = input.split('/')
    if (parts.size == 1) {
        val (expr, unit) = splitTrailingUnit(input) ?: return null
        val value = evaluateNumericExpression(expr) ?: return null
        val factor = if (unit.isEmpty()) massUnitToMilligrams["mg"]!! else massUnitToMilligrams[unit] ?: return null
        return value * factor
    }
    if (parts.size != 2) return null
    val (massExpr, massUnit) = splitTrailingUnit(parts[0]) ?: return null
    val (volumeExpr, volumeUnit) = splitTrailingUnit(parts[1].ifBlank { "1" }) ?: return null
    val mass = evaluateNumericExpression(massExpr) ?: return null
    val volume = evaluateNumericExpression(volumeExpr.ifBlank { "1" }) ?: return null
    val massFactor = if (massUnit.isEmpty()) massUnitToMilligrams["mg"]!! else massUnitToMilligrams[massUnit] ?: return null
    val volumeFactor = if (volumeUnit.isEmpty()) 1.0 else volumeUnitToMilliliters[volumeUnit] ?: return null
    if (volume * volumeFactor == 0.0) return null
    return mass * massFactor / (volume * volumeFactor)
}

fun formatVolume(ml: Double): String = when {
    ml >= 1000.0 -> "${(ml / 1000.0).toReadableString()}l"
    ml < 1.0 -> "${(ml * 1000.0).toReadableString()}µl"
    else -> "${ml.toReadableString()}ml"
}

fun formatConcentration(mgPerMl: Double): String {
    val microgramsPerLiter = mgPerMl * 1_000_000.0
    return when {
        mgPerMl >= 1000.0 -> "${(mgPerMl / 1000.0).toReadableString()}g/ml"
        mgPerMl >= 1.0 -> "${mgPerMl.toReadableString()}mg/ml"
        mgPerMl >= 0.001 -> "${(mgPerMl * 1000.0).toReadableString()}µg/ml"
        microgramsPerLiter >= 1.0 -> "${microgramsPerLiter.toReadableString()}µg/l"
        else -> "${(mgPerMl * 1000.0).toReadableString()}mg/l"
    }
}
