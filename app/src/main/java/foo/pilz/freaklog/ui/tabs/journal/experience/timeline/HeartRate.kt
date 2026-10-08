package foo.pilz.freaklog.ui.tabs.journal.experience.timeline

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import foo.pilz.freaklog.ui.graph.scene.Vec2
import foo.pilz.freaklog.ui.utils.isHealthConnectAvailable
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

data class HeartRateSample(val time: Instant, val bpm: Long)

object HeartRateHealthConnect {
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
    )

    fun isAvailable(context: Context) = isHealthConnectAvailable(context)

    suspend fun isPermissionGranted(context: Context): Boolean =
        HealthConnectClient.getOrCreate(context)
            .permissionController
            .getGrantedPermissions()
            .containsAll(permissions)

    suspend fun read(context: Context, start: Instant, end: Instant): List<HeartRateSample> {
        val client = HealthConnectClient.getOrCreate(context)
        val samples = mutableListOf<HeartRateSample>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        start.minus(Duration.ofHours(24)),
                        end,
                    ),
                    pageSize = 5000,
                    pageToken = pageToken,
                )
            )
            response.records.forEach { record ->
                record.samples.forEach { sample ->
                    if (!sample.time.isBefore(start) && !sample.time.isAfter(end)) {
                        samples.add(HeartRateSample(sample.time, sample.beatsPerMinute))
                    }
                }
            }
            pageToken = response.pageToken
        } while (pageToken != null)
        return downsampleHeartRate(samples.sortedBy { it.time }, start, end)
    }
}

internal fun downsampleHeartRate(
    samples: List<HeartRateSample>,
    start: Instant,
    end: Instant,
): List<HeartRateSample> {
    if (samples.size <= MAX_POINTS) return samples
    val bucketSeconds = max(1L, Duration.between(start, end).seconds / MAX_POINTS)
    return samples
        .groupBy { Duration.between(start, it.time).seconds / bucketSeconds }
        .map { (_, group) ->
            HeartRateSample(
                time = Instant.ofEpochSecond(group.map { it.time.epochSecond }.average().roundToLong()),
                bpm = group.map { it.bpm }.average().roundToLong(),
            )
        }
        .sortedBy { it.time }
}

private const val MAX_POINTS = 500

private val MAX_GAP: Duration = Duration.ofMinutes(5)
private const val MIN_BPM_DEVIATION = 15f
fun buildHeartRatePolylines(
    samples: List<HeartRateSample>,
    startTime: Instant,
    widthInSeconds: Float,
): List<List<Vec2>> {
    if (samples.size < 2 || widthInSeconds <= 0f) return emptyList()
    val sortedBpms = samples.map { it.bpm }.sorted()
    val median = sortedBpms[sortedBpms.size / 2].toFloat()
    val maxDeviation = max(
        max(sortedBpms.last() - median, median - sortedBpms.first()),
        MIN_BPM_DEVIATION,
    )
    val gapSeconds = maxGapSeconds(samples)
    val polylines = mutableListOf<MutableList<Vec2>>()
    var previousTime: Instant? = null
    for (sample in samples) {
        if (previousTime == null || Duration.between(previousTime, sample.time).seconds > gapSeconds) {
            polylines.add(mutableListOf())
        }
        polylines.last().add(
            Vec2(
                x = Duration.between(startTime, sample.time).seconds / widthInSeconds,
                y = 0.5f - (sample.bpm - median) / maxDeviation * MAX_HEIGHT_FRACTION,
            )
        )
        previousTime = sample.time
    }
    return polylines
}

private const val MAX_HEIGHT_FRACTION = 0.35f

fun nearestBpm(samples: List<HeartRateSample>, at: Instant): Long? {
    val first = samples.firstOrNull() ?: return null
    if (at.isBefore(first.time) || at.isAfter(samples.last().time)) return null
    return samples
        .minByOrNull { abs(Duration.between(it.time, at).seconds) }
        ?.takeIf { abs(Duration.between(it.time, at).seconds) <= maxGapSeconds(samples) }
        ?.bpm
}

private fun maxGapSeconds(samples: List<HeartRateSample>): Long {
    val intervals = samples
        .zipWithNext { a, b -> Duration.between(a.time, b.time).seconds }
        .sorted()
    val medianInterval = intervals.getOrElse(intervals.size / 2) { 0L }
    return max(MAX_GAP.seconds, medianInterval * 3)
}
