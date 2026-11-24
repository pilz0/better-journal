package foo.pilz.freaklog.ui.tabs.journal.experience.timeline

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import foo.pilz.freaklog.ui.graph.scene.Vec2
import foo.pilz.freaklog.ui.utils.isHealthConnectAvailable
import foo.pilz.freaklog.ui.utils.readHealthRecords
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.reflect.KClass

/**
 * Optional Health Connect vitals drawn on the timeline next to heart rate. Each one is off until
 * the user turns it on in Settings, which also asks for its read permission.
 */
enum class VitalKind(
    val title: String,
    val description: String,
    val recordType: KClass<out Record>,
    /** Smallest value spread that fills the line's vertical range, so noise stays flat. */
    val minDeviation: Float,
    val color: Color,
) {
  OXYGEN_SATURATION(
      "Blood oxygen (SpO₂)",
      "Can drop with opioids and other depressants",
      OxygenSaturationRecord::class,
      3f,
      Color(0xFF42A5F5),
  ),
  RESPIRATORY_RATE(
      "Respiratory rate",
      "Slowed breathing with depressants",
      RespiratoryRateRecord::class,
      4f,
      Color(0xFF26A69A),
  ),
  BODY_TEMPERATURE(
      "Body temperature",
      "Overheating with stimulants and MDMA",
      BodyTemperatureRecord::class,
      0.5f,
      Color(0xFFFF7043),
  ),
  SKIN_TEMPERATURE(
      "Skin temperature change",
      "Change from your baseline, from wearables that measure it",
      SkinTemperatureRecord::class,
      0.5f,
      Color(0xFFFFB300),
  ),
  HEART_RATE_VARIABILITY(
      "Heart rate variability",
      "RMSSD, a rough marker of stress and recovery",
      HeartRateVariabilityRmssdRecord::class,
      20f,
      Color(0xFFAB47BC),
  );

  val permissions: Set<String>
    get() =
        setOf(
            HealthPermission.getReadPermission(recordType),
            HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
        )

  fun format(value: Double): String =
      when (this) {
        OXYGEN_SATURATION -> "${value.roundToInt()}% SpO₂"
        RESPIRATORY_RATE -> "${value.roundToInt()} breaths/min"
        BODY_TEMPERATURE -> "%.1f °C".format(value)
        SKIN_TEMPERATURE -> "%+.1f °C skin".format(value)
        HEART_RATE_VARIABILITY -> "${value.roundToInt()} ms HRV"
      }

  /** Skin temperature needs a recent Health Connect; everything else is always there. */
  fun isSupported(context: Context): Boolean =
      isHealthConnectAvailable(context) &&
          (this != SKIN_TEMPERATURE ||
              HealthConnectClient.getOrCreate(context)
                  .features
                  .getFeatureStatus(HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE) ==
                  HealthConnectFeatures.FEATURE_STATUS_AVAILABLE)
}

/** How often vitals are re-read while a timeline still reaches past now (experience ongoing). */
val VITALS_REFRESH_INTERVAL: Duration = Duration.ofMinutes(5)

fun AllTimelinesModel.isOngoingAt(now: Instant): Boolean =
    startTime.plusSeconds(widthInSeconds.toLong()).isAfter(now)

data class VitalSample(val time: Instant, val value: Double)

data class VitalSeries(val kind: VitalKind, val samples: List<VitalSample>)

object VitalsHealthConnect {
  /** Reads every kind in [kinds] whose permission is granted; kinds without data are left out. */
  @Suppress("TooGenericExceptionCaught")
  suspend fun read(
      context: Context,
      kinds: Set<VitalKind>,
      start: Instant,
      end: Instant,
  ): List<VitalSeries> {
    if (kinds.isEmpty()) return emptyList()
    val granted =
        HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
    return kinds
        .filter { granted.containsAll(it.permissions) && it.isSupported(context) }
        .mapNotNull { kind ->
          // One unreadable type (e.g. revoked meanwhile) shouldn't hide the others.
          val samples =
              try {
                readHealthRecords(context, kind.recordType, start, end)
                    .flatMap { it.toVitalSamples() }
                    .filter { !it.time.isBefore(start) && !it.time.isAfter(end) }
                    .sortedBy { it.time }
              } catch (e: CancellationException) {
                throw e
              } catch (_: Exception) {
                emptyList()
              }
          samples
              .takeIf { it.isNotEmpty() }
              ?.let { VitalSeries(kind, downsampleVitals(it, start, end)) }
        }
  }
}

internal fun Record.toVitalSamples(): List<VitalSample> =
    when (this) {
      is OxygenSaturationRecord -> listOf(VitalSample(time, percentage.value))
      is RespiratoryRateRecord -> listOf(VitalSample(time, rate))
      is BodyTemperatureRecord -> listOf(VitalSample(time, temperature.inCelsius))
      is SkinTemperatureRecord -> deltas.map { VitalSample(it.time, it.delta.inCelsius) }
      is HeartRateVariabilityRmssdRecord -> listOf(VitalSample(time, heartRateVariabilityMillis))
      else -> emptyList()
    }

private const val MAX_POINTS = 500

internal fun downsampleVitals(
    samples: List<VitalSample>,
    start: Instant,
    end: Instant,
): List<VitalSample> {
  if (samples.size <= MAX_POINTS) return samples
  val bucketSeconds = max(1L, Duration.between(start, end).seconds / MAX_POINTS)
  return samples
      .groupBy { Duration.between(start, it.time).seconds / bucketSeconds }
      .map { (_, group) ->
        VitalSample(
            time = Instant.ofEpochSecond(group.map { it.time.epochSecond }.average().roundToLong()),
            value = group.map { it.value }.average(),
        )
      }
      .sortedBy { it.time }
}

private val MAX_GAP: Duration = Duration.ofMinutes(5)
private const val MAX_HEIGHT_FRACTION = 0.35f

/**
 * Polylines for one vital, centred on its median and scaled to the larger deviation (at least
 * [minDeviation]). A gap much longer than the usual sampling interval starts a new polyline.
 */
fun buildVitalPolylines(
    samples: List<VitalSample>,
    startTime: Instant,
    widthInSeconds: Float,
    minDeviation: Float,
): List<List<Vec2>> {
  if (samples.isEmpty() || widthInSeconds <= 0f) return emptyList()
  val sortedValues = samples.map { it.value.toFloat() }.sorted()
  val median = sortedValues[sortedValues.size / 2]
  val maxDeviation =
      max(max(sortedValues.last() - median, median - sortedValues.first()), minDeviation)
  val gapSeconds = maxGapSeconds(samples)
  val polylines = mutableListOf<MutableList<Vec2>>()
  var previousTime: Instant? = null
  for (sample in samples) {
    if (previousTime == null || Duration.between(previousTime, sample.time).seconds > gapSeconds) {
      polylines.add(mutableListOf())
    }
    polylines
        .last()
        .add(
            Vec2(
                x = Duration.between(startTime, sample.time).seconds / widthInSeconds,
                y = 0.5f - (sample.value.toFloat() - median) / maxDeviation * MAX_HEIGHT_FRACTION,
            )
        )
    previousTime = sample.time
  }
  return polylines
}

/** The sample closest to [at], unless [at] falls in a gap or outside the samples. */
fun nearestVitalValue(samples: List<VitalSample>, at: Instant): Double? {
  val first = samples.firstOrNull() ?: return null
  if (at.isBefore(first.time) || at.isAfter(samples.last().time)) return null
  return samples
      .minByOrNull { abs(Duration.between(it.time, at).seconds) }
      ?.takeIf { abs(Duration.between(it.time, at).seconds) <= maxGapSeconds(samples) }
      ?.value
}

private fun maxGapSeconds(samples: List<VitalSample>): Long {
  val intervals = samples.zipWithNext { a, b -> Duration.between(a.time, b.time).seconds }.sorted()
  val medianInterval = intervals.getOrElse(intervals.size / 2) { 0L }
  return max(MAX_GAP.seconds, medianInterval * 3)
}
