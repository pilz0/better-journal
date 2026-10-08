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
    val ranges = ingestions.mapNotNull { ingestion ->
        val end = ingestion.endSeconds ?: return@mapNotNull null
        ingestion.startSeconds to end
    }.sortedBy { it.first }
    return ranges.mapIndexed { index, (start, end) ->
        val intersectionCount = ranges.subList(0, index).count { (earlierStart, earlierEnd) ->
            earlierStart <= end && earlierEnd >= start
        }
        RawTimeRange(start, end, intersectionCount)
    }
}
