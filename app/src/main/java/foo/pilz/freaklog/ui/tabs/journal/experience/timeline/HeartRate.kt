package foo.pilz.freaklog.ui.tabs.journal.experience.timeline

import android.content.Context
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import foo.pilz.freaklog.ui.graph.scene.Vec2
import foo.pilz.freaklog.ui.utils.hasHealthPermissions
import foo.pilz.freaklog.ui.utils.isHealthConnectAvailable
import foo.pilz.freaklog.ui.utils.readHealthRecords
import java.time.Instant
import kotlin.math.roundToLong

data class HeartRateSample(val time: Instant, val bpm: Long)

object HeartRateHealthConnect {
  val permissions: Set<String> =
      setOf(
          HealthPermission.getReadPermission(HeartRateRecord::class),
          HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
      )

  fun isAvailable(context: Context) = isHealthConnectAvailable(context)

  suspend fun isPermissionGranted(context: Context): Boolean =
      hasHealthPermissions(context, permissions)

  suspend fun read(context: Context, start: Instant, end: Instant): List<HeartRateSample> {
    val samples =
        readHealthRecords(context, HeartRateRecord::class, start, end).flatMap { record ->
          record.samples
              .filter { !it.time.isBefore(start) && !it.time.isAfter(end) }
              .map { HeartRateSample(it.time, it.beatsPerMinute) }
        }
    return downsampleHeartRate(samples.sortedBy { it.time }, start, end)
  }
}

internal fun downsampleHeartRate(
    samples: List<HeartRateSample>,
    start: Instant,
    end: Instant,
): List<HeartRateSample> =
    downsampleVitals(samples.map { it.asVital() }, start, end).map {
      HeartRateSample(it.time, it.value.roundToLong())
    }

private const val MIN_BPM_DEVIATION = 15f

fun buildHeartRatePolylines(
    samples: List<HeartRateSample>,
    startTime: Instant,
    widthInSeconds: Float,
): List<List<Vec2>> =
    buildVitalPolylines(samples.map { it.asVital() }, startTime, widthInSeconds, MIN_BPM_DEVIATION)

fun nearestBpm(samples: List<HeartRateSample>, at: Instant): Long? =
    nearestVitalValue(samples.map { it.asVital() }, at)?.roundToLong()

private fun HeartRateSample.asVital() = VitalSample(time, bpm.toDouble())
