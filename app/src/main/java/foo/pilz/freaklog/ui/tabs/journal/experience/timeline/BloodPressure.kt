package foo.pilz.freaklog.ui.tabs.journal.experience.timeline

import android.content.Context
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import foo.pilz.freaklog.ui.graph.scene.Vec2
import foo.pilz.freaklog.ui.utils.hasHealthPermissions
import foo.pilz.freaklog.ui.utils.isHealthConnectAvailable
import foo.pilz.freaklog.ui.utils.readHealthRecords
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

data class BloodPressureReading(
    val id: String,
    val time: Instant,
    val systolic: Double,
    val diastolic: Double,
)

object BloodPressureHealthConnect {
  val permissions: Set<String> =
      setOf(
          HealthPermission.getReadPermission(BloodPressureRecord::class),
          HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
      )

  val writePermissions: Set<String> =
      setOf(
          HealthPermission.getWritePermission(BloodPressureRecord::class),
      )

  fun isAvailable(context: Context): Boolean = isHealthConnectAvailable(context)

  suspend fun isPermissionGranted(context: Context): Boolean =
      hasHealthPermissions(context, permissions)

  suspend fun read(context: Context, start: Instant, end: Instant): List<BloodPressureReading> =
      readHealthRecords(context, BloodPressureRecord::class, start, end)
          .filter { !it.time.isBefore(start) && !it.time.isAfter(end) }
          .map { record ->
            BloodPressureReading(
                id = record.metadata.id,
                time = record.time,
                systolic = record.systolic.inMillimetersOfMercury,
                diastolic = record.diastolic.inMillimetersOfMercury,
            )
          }
          .sortedBy { it.time }
}

private const val MAX_HEIGHT_FRACTION = 0.35f

data class BloodPressurePolyLines(
    val systolic: List<Vec2>,
    val diastolic: List<Vec2>,
    val minPressure: Double = 0.0,
    val maxPressure: Double = 0.0,
) {
  val hasScale: Boolean
    get() = maxPressure > minPressure
}

fun bloodPressureYFraction(value: Double, minPressure: Double, maxPressure: Double): Float {
  val mid = (minPressure + maxPressure) / 2
  val halfSpan = max((maxPressure - minPressure) / 2, 1e-6)
  return (0.5 - (value - mid) / halfSpan * MAX_HEIGHT_FRACTION).toFloat()
}

fun buildBloodPressurePolylines(
    readings: List<BloodPressureReading>,
    startTime: Instant,
    widthInSeconds: Float,
): BloodPressurePolyLines {
  val validReadings = readings.filter { it.systolic in 1.0..400.0 && it.diastolic in 1.0..400.0 }
  if (validReadings.isEmpty() || widthInSeconds <= 0f)
      return BloodPressurePolyLines(emptyList(), emptyList())

  val allValues = validReadings.flatMap { listOf(it.systolic, it.diastolic) }
  val minPressure = floor(allValues.min() / 25.0) * 25.0
  val maxPressure = ceil(allValues.max() / 25.0) * 25.0

  val polylinesSystolic = mutableListOf<Vec2>()
  val polylinesDiastolic = mutableListOf<Vec2>()
  for (reading in validReadings) {
    val x = Duration.between(startTime, reading.time).seconds / widthInSeconds
    polylinesSystolic.add(
        Vec2(x, bloodPressureYFraction(reading.systolic, minPressure, maxPressure))
    )
    polylinesDiastolic.add(
        Vec2(x, bloodPressureYFraction(reading.diastolic, minPressure, maxPressure))
    )
  }
  return BloodPressurePolyLines(polylinesSystolic, polylinesDiastolic, minPressure, maxPressure)
}
