package foo.pilz.freaklog.ui.tabs.safer.tolerance

import foo.pilz.freaklog.data.substances.classes.Tolerance

object ToleranceTextParser {

    private val RANGE_PATTERN = Regex("""(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)\s*(day|week|hour|month)s?""", RegexOption.IGNORE_CASE)
    private val SINGLE_PATTERN = Regex("""(\d+(?:\.\d+)?)\s*(day|week|hour|month)s?""", RegexOption.IGNORE_CASE)

    private val DESCRIPTIVE_MAP = mapOf(
        "immediately" to 0f,
        "almost immediately" to 0f,
        "almost immediately after ingestion" to 0f,
        "develops with prolonged and repeated use" to 14f,
        "with prolonged and repeated use" to 14f,
        "within several weeks of continuous use" to 21f,
        "after ingestion over the couse of multiple days" to 3f,
        "after ingestion over the course of multiple days" to 3f,
    )

    fun parseDurationText(text: String?): Float? {
        if (text == null) return null
        val trimmed = text.trim().lowercase()

        DESCRIPTIVE_MAP[trimmed]?.let { return it }

        RANGE_PATTERN.find(trimmed)?.let { match ->
            val low = match.groupValues[1].toFloat()
            val high = match.groupValues[2].toFloat()
            val unit = match.groupValues[3].lowercase()
            return (low + high) / 2f * unitMultiplier(unit)
        }

        SINGLE_PATTERN.find(trimmed)?.let { match ->
            val value = match.groupValues[1].toFloat()
            val unit = match.groupValues[2].lowercase()
            return value * unitMultiplier(unit)
        }

        return null
    }

    fun parse(tolerance: Tolerance): ParsedTolerance? {
        val halfDays = parseDurationText(tolerance.half)
        val zeroDays = parseDurationText(tolerance.zero)

        if (halfDays == null && zeroDays == null) return null

        val effectiveHalf = halfDays ?: (zeroDays!! * 0.5f)
        val effectiveZero = zeroDays ?: (halfDays!! * 2.5f)
        val effectiveFull = parseDurationText(tolerance.full) ?: 0f

        return ParsedTolerance(
            fullDays = effectiveFull,
            halfLifeDays = effectiveHalf,
            zeroDays = effectiveZero
        )
    }

    private fun unitMultiplier(unit: String): Float = when (unit) {
        "hour" -> 1f / 24f
        "day" -> 1f
        "week" -> 7f
        "month" -> 30f
        else -> 1f
    }
}
