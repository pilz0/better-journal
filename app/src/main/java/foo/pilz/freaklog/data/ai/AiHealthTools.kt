package foo.pilz.freaklog.data.ai

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import foo.pilz.freaklog.data.health.HealthSharingPreferences
import foo.pilz.freaklog.ui.tabs.settings.lock.BiometricAuthManager
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class AiHealthTools
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: HealthSharingPreferences,
    private val auth: BiometricAuthManager,
) {
  suspend fun isEnabled() = preferences.aiVitals.first()

  val specs =
      listOf(
          toolSpec(
              name = TOOL_NAME,
              description =
                  "Summarize accessible Health Connect heart rate, sleep and blood pressure " +
                      "over the last 1–7 days. Only available after the user's separate sharing opt-in. " +
                      "Missing data does not mean normal readings. Associations with doses are not causal.",
              ToolParam.integer(
                  "days_back",
                  "Number of preceding days, default 1, clamped to 1–7.",
              ),
          )
      )

  suspend fun execute(name: String, args: Map<String, Any?>): JSONObject? {
    if (name != TOOL_NAME) return null
    if (!isEnabled() || auth.shouldLock.value) {
      return toolError("Health sharing is disabled or the journal is locked.")
    }
    if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
      return toolError("Health Connect is unavailable.")
    }
    return try {
      val client = HealthConnectClient.getOrCreate(context)
      val days = ((args["days_back"] as? Number)?.toInt() ?: 1).coerceIn(1, 7)
      val result = summarize(client, Instant.now(), days)
      if (!isEnabled() || auth.shouldLock.value) {
        toolError("Health sharing was disabled or the journal locked.")
      } else {
        result
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      toolError("Health Connect could not be read. Check permissions and try again.")
    }
  }

  internal suspend fun summarize(client: HealthConnectClient, now: Instant, days: Int): JSONObject {
    val granted = client.permissionController.getGrantedPermissions()
    val heart = HealthPermission.getReadPermission(HeartRateRecord::class) in granted
    val sleep = HealthPermission.getReadPermission(SleepSessionRecord::class) in granted
    val pressure = HealthPermission.getReadPermission(BloodPressureRecord::class) in granted
    val metrics = mutableSetOf<AggregateMetric<*>>()
    if (heart) {
      metrics.addAll(
          setOf(HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MIN, HeartRateRecord.BPM_MAX)
      )
    }
    if (sleep) metrics.add(SleepSessionRecord.SLEEP_DURATION_TOTAL)
    if (pressure) {
      metrics.addAll(setOf(BloodPressureRecord.SYSTOLIC_AVG, BloodPressureRecord.DIASTOLIC_AVG))
    }
    if (metrics.isEmpty()) {
      return toolError("No Health Connect vitals read permissions. Enable them in Health sharing.")
    }
    val start = now.minus(Duration.ofDays(days.coerceIn(1, 7).toLong()))
    val aggregate = client.aggregate(AggregateRequest(metrics, TimeRangeFilter.between(start, now)))
    val latestPermissions = client.permissionController.getGrantedPermissions()
    if (!latestPermissions.containsAll(granted.intersect(vitalsPermissions))) {
      return toolError("Health Connect permissions changed during the request.")
    }
    return JSONObject()
        .put("status", "success")
        .put("start", start.toString())
        .put("end", now.toString())
        .put("heart_rate_bpm_mean", aggregate[HeartRateRecord.BPM_AVG] ?: JSONObject.NULL)
        .put("heart_rate_bpm_min", aggregate[HeartRateRecord.BPM_MIN] ?: JSONObject.NULL)
        .put("heart_rate_bpm_max", aggregate[HeartRateRecord.BPM_MAX] ?: JSONObject.NULL)
        .put(
            "sleep_minutes",
            aggregate[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes() ?: JSONObject.NULL,
        )
        .put(
            "systolic_mmhg_mean",
            aggregate[BloodPressureRecord.SYSTOLIC_AVG]?.inMillimetersOfMercury ?: JSONObject.NULL,
        )
        .put(
            "diastolic_mmhg_mean",
            aggregate[BloodPressureRecord.DIASTOLIC_AVG]?.inMillimetersOfMercury ?: JSONObject.NULL,
        )
        .put(
            "missing_permissions",
            JSONArray(
                listOfNotNull(
                    "heart_rate".takeUnless { heart },
                    "sleep".takeUnless { sleep },
                    "blood_pressure".takeUnless { pressure },
                )
            ),
        )
        .put(
            "interpretation",
            "Accessible records only. Null means missing data or permission, never a normal value. " +
                "Health Connect applies its aggregation and source priority rules. " +
                "This summary cannot establish that a substance caused a change.",
        )
  }

  companion object {
    const val TOOL_NAME = "get_health_connect_summary"
    val vitalsPermissions =
        setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(BloodPressureRecord::class),
        )
  }
}
