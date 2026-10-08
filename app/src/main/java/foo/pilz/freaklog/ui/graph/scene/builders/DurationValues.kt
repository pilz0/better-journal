package foo.pilz.freaklog.ui.graph.scene.builders

import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.RoaDuration

data class FullDurationRange(
    val minInSeconds: Float,
    val maxInSeconds: Float,
) {
    fun interpolateAtValueInSeconds(value: Float): Float {
        val diff = maxInSeconds - minInSeconds
        return minInSeconds + diff.times(value)
    }
}

fun DurationRange.toFullDurationRange(): FullDurationRange? {
    val min = minInSec ?: return null
    return FullDurationRange(min, maxInSec ?: min)
}

data class FullTimelineDurations(
    val onsetInSeconds: Float,
    val comeupInSeconds: Float,
    val peakInSeconds: Float,
    val offsetInSeconds: Float,
)

fun RoaDuration.toFullTimelineDurations(): FullTimelineDurations? {
    val onsetInSeconds = onset?.interpolateAtValueInSeconds(0.5f)
    val comeupInSeconds = comeup?.interpolateAtValueInSeconds(0.5f)
    val peakInSeconds = peak?.interpolateAtValueInSeconds(0.5f)
    val offsetInSeconds = offset?.interpolateAtValueInSeconds(0.5f)
    return if (onsetInSeconds != null && comeupInSeconds != null && peakInSeconds != null && offsetInSeconds != null) {
        FullTimelineDurations(onsetInSeconds, comeupInSeconds, peakInSeconds, offsetInSeconds)
    } else {
        null
    }
}
