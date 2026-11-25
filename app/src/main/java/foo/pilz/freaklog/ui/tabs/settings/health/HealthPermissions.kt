package foo.pilz.freaklog.ui.tabs.settings.health

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import foo.pilz.freaklog.data.ai.vitals.VitalsTypes
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.BloodPressureHealthConnect
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.HeartRateHealthConnect
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.SleepHealthConnect
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.VitalKind
import kotlinx.coroutines.CancellationException

internal val coreHealthConnectPermissions: Set<String> =
    HeartRateHealthConnect.permissions +
        SleepHealthConnect.permissions +
        BloodPressureHealthConnect.permissions

internal suspend fun readGrantedVitalLabels(context: Context): List<String> =
    try {
      val granted =
          HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
      buildList {
        if (granted.containsAll(HeartRateHealthConnect.permissions)) {
          add(VitalsTypes.labelFor(VitalsTypes.HEART_RATE))
        }
        if (granted.containsAll(BloodPressureHealthConnect.permissions)) {
          add(VitalsTypes.labelFor(VitalsTypes.BLOOD_PRESSURE))
        }
        if (granted.containsAll(SleepHealthConnect.permissions)) {
          add(VitalsTypes.labelFor(VitalsTypes.SLEEP))
        }
        VitalKind.entries.forEach { kind ->
          if (kind.isSupported(context) && granted.containsAll(kind.permissions)) {
            add(VitalsTypes.labelFor(VitalsTypes.keyFor(kind)))
          }
        }
      }
    } catch (ce: CancellationException) {
      throw ce
    } catch (_: Exception) {
      emptyList()
    }

@Composable
internal fun rememberVitalsToggle(
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onDenied: () -> Unit,
    onPermissionsChanged: () -> Unit,
): (Boolean) -> Unit {
  val launcher =
      rememberLauncherForActivityResult(
          PermissionController.createRequestPermissionResultContract()
      ) { granted ->
        onPermissionsChanged()
        if (
            granted.containsAll(HeartRateHealthConnect.permissions) ||
                granted.containsAll(SleepHealthConnect.permissions) ||
                granted.containsAll(BloodPressureHealthConnect.permissions)
        ) {
          onEnable()
        } else {
          onDenied()
        }
      }
  return { enable ->
    if (enable) launcher.launch(coreHealthConnectPermissions) else onDisable()
  }
}

/** Turning an extra vital on first asks Health Connect for its read permission. */
@Composable
internal fun rememberExtraVitalToggle(
    onSave: (VitalKind, Boolean) -> Unit,
    onDenied: () -> Unit,
    onPermissionsChanged: () -> Unit,
): (VitalKind, Boolean) -> Unit {
  var pendingKind by rememberSaveable { mutableStateOf<VitalKind?>(null) }
  val launcher =
      rememberLauncherForActivityResult(
          PermissionController.createRequestPermissionResultContract()
      ) { granted ->
        val kind = pendingKind ?: return@rememberLauncherForActivityResult
        pendingKind = null
        onPermissionsChanged()
        if (granted.containsAll(kind.permissions)) {
          onSave(kind, true)
        } else {
          onDenied()
        }
      }
  return { kind, enable ->
    if (enable) {
      pendingKind = kind
      launcher.launch(kind.permissions)
    } else {
      onSave(kind, false)
    }
  }
}
