package foo.pilz.freaklog.ui.graph.scene.builders

import androidx.compose.ui.graphics.Color

data class TimelineGroup(
    val color: Color?,
    val curve: NormalizedCurve,
    val timeRanges: List<NormalizedTimeRange>,
)

data class NormalizedTimeRange(
    val startFraction: Float,
    val endFraction: Float,
    val intersectionCount: Int,
)

data class RawTimeRange(
    val startSeconds: Float,
    val endSeconds: Float,
    val intersectionCount: Int,
)

fun buildRawTimeRanges(ingestions: List<RawIngestion>): List<RawTimeRange> {
  val bounded = if (ingestions.size > 2000) ingestions.take(2000) else ingestions
  val ranges =
      bounded
          .mapNotNull { ingestion ->
            val end = ingestion.endSeconds ?: return@mapNotNull null
            ingestion.startSeconds to end
          }
          .sortedBy { it.first }
  val activeEndTimes = java.util.PriorityQueue<Float>()
  return ranges.map { (start, end) ->
    while (!activeEndTimes.isEmpty() && (activeEndTimes.peek() ?: Float.MAX_VALUE) < start) {
      activeEndTimes.poll()
    }
    val intersectionCount = activeEndTimes.size
    activeEndTimes.add(end)
    RawTimeRange(start, end, intersectionCount)
  }
}
