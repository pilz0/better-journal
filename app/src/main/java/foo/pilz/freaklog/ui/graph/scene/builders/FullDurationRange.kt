package foo.pilz.freaklog.ui.graph.scene.builders

import foo.pilz.freaklog.data.substances.classes.roa.DurationRange

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
  val min = minInSec ?: maxInSec ?: return null
  val max = maxInSec ?: minInSec ?: return null
  return FullDurationRange(min, max)
}
