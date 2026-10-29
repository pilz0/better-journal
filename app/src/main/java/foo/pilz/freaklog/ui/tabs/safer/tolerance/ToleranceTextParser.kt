package foo.pilz.freaklog.ui.tabs.safer.tolerance

import foo.pilz.freaklog.data.substances.classes.Tolerance

object ToleranceTextParser {

    /** Real tolerance texts are under 60 characters; longer (imported) text is not parsed. */
    const val MAX_TEXT_LENGTH = 256

    // `(?<!\d)` only lets a number start at the beginning of a digit run, so a long run of
    // digits without a unit is matched in linear rather than quadratic time.
    private val RANGE_PATTERN =
        Regex("""(?<!\d)(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)\s*(day|week|hour|month)s?""", RegexOption.IGNORE_CASE)
    private val SINGLE_PATTERN =
        Regex("""(?<!\d)(\d+(?:\.\d+)?)\s*(day|week|hour|month)s?""", RegexOption.IGNORE_CASE)

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
        if (text == null || text.length > MAX_TEXT_LENGTH) return null
        val trimmed = text.trim().lowercase()

        return DESCRIPTIVE_MAP[trimmed]
            ?: parseRangeDuration(trimmed)
            ?: parseSingleDuration(trimmed)
    }

    private fun parseRangeDuration(trimmed: String): Float? {
        val match = RANGE_PATTERN.find(trimmed) ?: return null
        val low = match.groupValues[1].toFloatOrNull() ?: return null
        val high = match.groupValues[2].toFloatOrNull() ?: return null
        val unit = match.groupValues[3].lowercase()
        return (low + high) / 2f * unitMultiplier(unit)
    }

    private fun parseSingleDuration(trimmed: String): Float? {
        val match = SINGLE_PATTERN.find(trimmed) ?: return null
        val value = match.groupValues[1].toFloatOrNull() ?: return null
        val unit = match.groupValues[2].lowercase()
        return value * unitMultiplier(unit)
    }

    fun parse(tolerance: Tolerance): ParsedTolerance? {
        val halfDays = parseDurationText(tolerance.half)
        val zeroDays = parseDurationText(tolerance.zero)

        if (halfDays == null && zeroDays == null) return null

        val effectiveHalf = halfDays ?: (zeroDays!! * 0.5f)
        val effectiveZero = (zeroDays ?: (halfDays!! * 2.5f)).coerceAtLeast(effectiveHalf)
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
