package foo.pilz.freaklog.data.anodyne

import foo.pilz.freaklog.data.anodyne.model.AnodyneUserNote
import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.DurationUnits
import foo.pilz.freaklog.data.substances.classes.roa.RoaDuration
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object AnodyneTiming {
  private val RANGE_REGEX =
      Regex(
          """(\d+(?:[.,]\d+)?)\s*(?:(?:–|-|to)\s*(\d+(?:[.,]\d+)?))?\s*(seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h|days?|d)\b""",
          RegexOption.IGNORE_CASE,
      )

  fun rangeIn(text: String): DurationRange? {
    val match = RANGE_REGEX.find(text) ?: return null
    val lowStr = match.groups[1]?.value?.replace(",", ".") ?: return null
    val highStr = match.groups[2]?.value?.replace(",", ".")
    val unitStr = match.groups[3]?.value?.lowercase() ?: return null

    val low = lowStr.toFloatOrNull() ?: return null
    val high = highStr?.toFloatOrNull() ?: low

    val units =
        when {
          unitStr.startsWith("s") -> DurationUnits.SECONDS
          unitStr.startsWith("m") -> DurationUnits.MINUTES
          unitStr.startsWith("d") -> DurationUnits.DAYS
          else -> DurationUnits.HOURS
        }
    return DurationRange(min = min(low, high), max = max(low, high), units = units)
  }

  fun combinedDuration(routes: List<AnodyneUserNote.Route>): RoaDuration? {
    fun combined(extract: (AnodyneUserNote.Route) -> String?): DurationRange? {
      val ranges = routes.mapNotNull(extract).mapNotNull { rangeIn(it) }
      val minSec = ranges.mapNotNull { it.minInSec }.minOrNull() ?: return null
      val maxSec = ranges.mapNotNull { it.maxInSec }.maxOrNull() ?: return null
      return readableRange(minSec, maxSec)
    }

    val onset = combined { it.onset }
    val comeup = combined { it.comeup }
    val total = combined { it.duration }

    if (onset == null && comeup == null && total == null) return null
    return RoaDuration(
        onset = onset,
        comeup = comeup,
        peak = null,
        offset = null,
        total = total,
        afterglow = null,
    )
  }

  private fun readableRange(lowSec: Float, highSec: Float): DurationRange {
    val unitsWithDivisor =
        listOf(
            DurationUnits.HOURS to 3600f,
            DurationUnits.MINUTES to 60f,
            DurationUnits.SECONDS to 1f,
        )
    val (unit, divisor) =
        unitsWithDivisor.firstOrNull { lowSec / it.second >= 2f } ?: (DurationUnits.SECONDS to 1f)

    fun roundToHalf(value: Float): Float = ((value / divisor) * 2f).roundToInt() / 2f

    return DurationRange(
        min = roundToHalf(lowSec),
        max = roundToHalf(highSec),
        units = unit,
    )
  }
}
