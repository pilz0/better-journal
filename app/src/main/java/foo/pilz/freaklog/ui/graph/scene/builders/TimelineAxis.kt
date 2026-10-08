package foo.pilz.freaklog.ui.graph.scene.builders

import foo.pilz.freaklog.ui.utils.getStringOfPattern
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong
import kotlin.time.DurationUnit
import kotlin.time.toDuration

data class AxisLabel(val xFraction: Float, val label: String)

fun buildAxisLabels(startTime: Instant, widthInSeconds: Float, canvasWidthPx: Float): List<AxisLabel> {
    if (widthInSeconds <= 0f || canvasWidthPx <= 0f) return emptyList()
    val widthInWholeHours = widthInSeconds.toLong().toDuration(DurationUnit.SECONDS).inWholeHours
    if (widthInWholeHours <= 0L) return emptyList()
    val widthPerHour = canvasWidthPx / widthInWholeHours
    val minWidthPerHour = 80.0
    var stepSize = (minWidthPerHour / widthPerHour).roundToLong()
    if (stepSize == 0L) stepSize = 1
    val dates = instantsBetween(
        startTime = startTime,
        endTime = startTime.plusSeconds(widthInSeconds.toLong()),
        stepSizeInHours = stepSize,
    )
    return dates.map {
        val distanceInSec = Duration.between(startTime, it).seconds
        AxisLabel(xFraction = distanceInSec / widthInSeconds, label = it.getStringOfPattern("HH"))
    }
}

internal fun instantsBetween(startTime: Instant, endTime: Instant, stepSizeInHours: Long): List<Instant> {
    val firstDate = startTime.nearestFullHourInTheFuture()
    val result = mutableListOf<Instant>()
    var checkTime = firstDate
    while (checkTime.isBefore(endTime)) {
        result.add(checkTime)
        checkTime = checkTime.plus(stepSizeInHours, ChronoUnit.HOURS)
    }
    return result
}

private fun Instant.nearestFullHourInTheFuture(): Instant {
    val oneHourInFuture = this.plus(1, ChronoUnit.HOURS)
    val dateTime = oneHourInFuture.atZone(ZoneId.systemDefault())
    val seconds = dateTime.second
    val minutes = dateTime.minute
    val newDateTime = dateTime.minusMinutes(minutes.toLong()).minusSeconds(seconds.toLong())
    return newDateTime.toInstant()
}
