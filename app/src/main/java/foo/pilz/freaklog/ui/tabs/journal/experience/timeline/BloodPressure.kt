package foo.pilz.freaklog.ui.tabs.journal.experience.timeline

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import foo.pilz.freaklog.ui.graph.scene.Vec2
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
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
    )

    val writePermissions: Set<String> = setOf(
        HealthPermission.getWritePermission(BloodPressureRecord::class),
    )

    fun isAvailable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun isPermissionGranted(context: Context): Boolean =
        HealthConnectClient.getOrCreate(context)
            .permissionController
            .getGrantedPermissions()
            .containsAll(permissions)

    suspend fun read(context: Context, start: Instant, end: Instant): List<BloodPressureReading> {
        val client = HealthConnectClient.getOrCreate(context)
        val readings = mutableListOf<BloodPressureReading>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = BloodPressureRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        start.minus(Duration.ofHours(24)),
                        end,
                    ),
                    pageSize = 5000,
                    pageToken = pageToken,
                )
            )
            response.records.forEach { record ->
                if (!record.time.isBefore(start) && !record.time.isAfter(end)) {
                    readings.add(
                        BloodPressureReading(
                            id = record.metadata.id,
                            time = record.time,
                            systolic = record.systolic.inMillimetersOfMercury,
                            diastolic = record.diastolic.inMillimetersOfMercury,
                        )
                    )
                }
            }
            pageToken = response.pageToken
        } while (pageToken != null)
        return readings.sortedBy { it.time }
    }
}

private const val MAX_HEIGHT_FRACTION = 0.35f

data class BloodPressurePolyLines(
    val systolic: List<Vec2>,
    val diastolic: List<Vec2>,
    val minPressure: Double = 0.0,
    val maxPressure: Double = 0.0,
) {
    val hasScale: Boolean get() = maxPressure > minPressure
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
    if (readings.size < 2 || widthInSeconds <= 0f) return BloodPressurePolyLines(emptyList(), emptyList())

    val allValues = readings.flatMap { listOf(it.systolic, it.diastolic) }
    val minPressure = floor(allValues.min() / 25.0) * 25.0
    val maxPressure = ceil(allValues.max() / 25.0) * 25.0

    val polylinesSystolic = mutableListOf<Vec2>()
    val polylinesDiastolic = mutableListOf<Vec2>()
    for (reading in readings) {
        val x = Duration.between(startTime, reading.time).seconds / widthInSeconds
        polylinesSystolic.add(Vec2(x, bloodPressureYFraction(reading.systolic, minPressure, maxPressure)))
        polylinesDiastolic.add(Vec2(x, bloodPressureYFraction(reading.diastolic, minPressure, maxPressure)))
    }
    return BloodPressurePolyLines(polylinesSystolic, polylinesDiastolic, minPressure, maxPressure)
}

