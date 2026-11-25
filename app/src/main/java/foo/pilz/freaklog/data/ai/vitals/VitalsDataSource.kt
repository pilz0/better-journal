package foo.pilz.freaklog.data.ai.vitals

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import dagger.hilt.android.qualifiers.ApplicationContext
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.BloodPressureHealthConnect
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.BloodPressureReading
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.HeartRateHealthConnect
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.HeartRateSample
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.SleepHealthConnect
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.SleepSessionSample
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.VitalKind
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.VitalSeries
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.VitalsHealthConnect
import foo.pilz.freaklog.ui.utils.isHealthConnectAvailable
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/** Canonical metric keys accepted by `get_vitals` and returned in tool metadata. */
object VitalsTypes {
  const val HEART_RATE = "heart_rate"
  const val BLOOD_PRESSURE = "blood_pressure"
  const val SLEEP = "sleep"
  const val OXYGEN_SATURATION = "oxygen_saturation"
  const val RESPIRATORY_RATE = "respiratory_rate"
  const val BODY_TEMPERATURE = "body_temperature"
  const val SKIN_TEMPERATURE = "skin_temperature"
  const val HEART_RATE_VARIABILITY = "heart_rate_variability"

  val ALL: List<String> =
      listOf(
          HEART_RATE,
          BLOOD_PRESSURE,
          SLEEP,
          OXYGEN_SATURATION,
          RESPIRATORY_RATE,
          BODY_TEMPERATURE,
          SKIN_TEMPERATURE,
          HEART_RATE_VARIABILITY,
      )

  private val ALIASES =
      mapOf(
          "hr" to HEART_RATE,
          "heart_rate" to HEART_RATE,
          "heartrate" to HEART_RATE,
          "bpm" to HEART_RATE,
          "pulse" to HEART_RATE,
          "bp" to BLOOD_PRESSURE,
          "blood_pressure" to BLOOD_PRESSURE,
          "bloodpressure" to BLOOD_PRESSURE,
          "sleep" to SLEEP,
          "sleep_sessions" to SLEEP,
          "spo2" to OXYGEN_SATURATION,
          "oxygen" to OXYGEN_SATURATION,
          "blood_oxygen" to OXYGEN_SATURATION,
          "oxygen_saturation" to OXYGEN_SATURATION,
          "rr" to RESPIRATORY_RATE,
          "resp" to RESPIRATORY_RATE,
          "respiration" to RESPIRATORY_RATE,
          "respiratory_rate" to RESPIRATORY_RATE,
          "breathing" to RESPIRATORY_RATE,
          "temp" to BODY_TEMPERATURE,
          "temperature" to BODY_TEMPERATURE,
          "body_temp" to BODY_TEMPERATURE,
          "body_temperature" to BODY_TEMPERATURE,
          "skin_temp" to SKIN_TEMPERATURE,
          "skin_temperature" to SKIN_TEMPERATURE,
          "hrv" to HEART_RATE_VARIABILITY,
          "rmssd" to HEART_RATE_VARIABILITY,
          "heart_rate_variability" to HEART_RATE_VARIABILITY,
      )

  fun normalize(raw: String): String? {
    val key = raw.trim().lowercase().replace('-', '_').replace(' ', '_')
    return ALIASES[key]
  }

  fun keyFor(kind: VitalKind): String =
      when (kind) {
        VitalKind.OXYGEN_SATURATION -> OXYGEN_SATURATION
        VitalKind.RESPIRATORY_RATE -> RESPIRATORY_RATE
        VitalKind.BODY_TEMPERATURE -> BODY_TEMPERATURE
        VitalKind.SKIN_TEMPERATURE -> SKIN_TEMPERATURE
        VitalKind.HEART_RATE_VARIABILITY -> HEART_RATE_VARIABILITY
      }

  fun unitFor(kind: VitalKind): String =
      when (kind) {
        VitalKind.OXYGEN_SATURATION -> "%"
        VitalKind.RESPIRATORY_RATE -> "breaths/min"
        VitalKind.BODY_TEMPERATURE -> "°C"
        VitalKind.SKIN_TEMPERATURE -> "°C delta"
        VitalKind.HEART_RATE_VARIABILITY -> "ms RMSSD"
      }

  fun labelFor(type: String): String =
      when (type) {
        HEART_RATE -> "heart rate"
        BLOOD_PRESSURE -> "blood pressure"
        SLEEP -> "sleep"
        OXYGEN_SATURATION -> "SpO₂"
        RESPIRATORY_RATE -> "respiratory rate"
        BODY_TEMPERATURE -> "body temp"
        SKIN_TEMPERATURE -> "skin temp"
        HEART_RATE_VARIABILITY -> "HRV"
        else -> type
      }
}

data class VitalsSnapshot(
    val healthConnectAvailable: Boolean,
    val grantedTypes: Set<String>,
    val missingPermissionTypes: Set<String>,
    val heartRate: List<HeartRateSample>,
    val bloodPressure: List<BloodPressureReading>,
    val sleep: List<SleepSessionSample>,
    val extraVitals: List<VitalSeries>,
)

interface VitalsDataSource {
  suspend fun read(start: Instant, end: Instant, requestedTypes: Set<String>?): VitalsSnapshot
}

@Singleton
class HealthConnectVitalsDataSource
@Inject
constructor(
    @ApplicationContext private val context: Context,
) : VitalsDataSource {

  override suspend fun read(
      start: Instant,
      end: Instant,
      requestedTypes: Set<String>?,
  ): VitalsSnapshot {
    val targetTypes = requestedTypes?.takeIf { it.isNotEmpty() } ?: VitalsTypes.ALL.toSet()
    if (!isHealthConnectAvailable(context)) {
      return unavailableSnapshot(targetTypes)
    }
    val grantedPermissions =
        try {
          HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
        } catch (ce: CancellationException) {
          throw ce
        } catch (_: Exception) {
          return unavailableSnapshot(targetTypes)
        }

    val grantedTypes = linkedSetOf<String>()
    val missingTypes = linkedSetOf<String>()

    val heartRate =
        if (VitalsTypes.HEART_RATE in targetTypes) {
          if (grantedPermissions.containsAll(HeartRateHealthConnect.permissions)) {
            grantedTypes += VitalsTypes.HEART_RATE
            safeRead { HeartRateHealthConnect.read(context, start, end) }
          } else {
            missingTypes += VitalsTypes.HEART_RATE
            emptyList()
          }
        } else emptyList()

    val bloodPressure =
        if (VitalsTypes.BLOOD_PRESSURE in targetTypes) {
          if (grantedPermissions.containsAll(BloodPressureHealthConnect.permissions)) {
            grantedTypes += VitalsTypes.BLOOD_PRESSURE
            safeRead { BloodPressureHealthConnect.read(context, start, end) }
          } else {
            missingTypes += VitalsTypes.BLOOD_PRESSURE
            emptyList()
          }
        } else emptyList()

    val sleep =
        if (VitalsTypes.SLEEP in targetTypes) {
          if (grantedPermissions.containsAll(SleepHealthConnect.permissions)) {
            grantedTypes += VitalsTypes.SLEEP
            safeRead { SleepHealthConnect.read(context, start, end) }
          } else {
            missingTypes += VitalsTypes.SLEEP
            emptyList()
          }
        } else emptyList()

    val extraKinds = linkedSetOf<VitalKind>()
    VitalKind.entries.forEach { kind ->
      val key = VitalsTypes.keyFor(kind)
      if (key in targetTypes && kind.isSupported(context)) {
        if (grantedPermissions.containsAll(kind.permissions)) {
          grantedTypes += key
          extraKinds += kind
        } else {
          missingTypes += key
        }
      }
    }
    val extraVitals =
        if (extraKinds.isEmpty()) emptyList()
        else safeRead { VitalsHealthConnect.read(context, extraKinds, start, end) }

    return VitalsSnapshot(
        healthConnectAvailable = true,
        grantedTypes = grantedTypes,
        missingPermissionTypes = missingTypes,
        heartRate = heartRate,
        bloodPressure = bloodPressure,
        sleep = sleep,
        extraVitals = extraVitals,
    )
  }

  private fun unavailableSnapshot(targetTypes: Set<String>) =
      VitalsSnapshot(
          healthConnectAvailable = false,
          grantedTypes = emptySet(),
          missingPermissionTypes = targetTypes,
          heartRate = emptyList(),
          bloodPressure = emptyList(),
          sleep = emptyList(),
          extraVitals = emptyList(),
      )

  private suspend fun <T> safeRead(block: suspend () -> List<T>): List<T> =
      try {
        block()
      } catch (ce: CancellationException) {
        throw ce
      } catch (_: Exception) {
        emptyList()
      }
}
