@file:OptIn(
    androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package foo.pilz.freaklog.ui.tabs.settings.health

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import foo.pilz.freaklog.data.health.MedicationHealthConnect

/**
 * All sharing is initiated here by the user, with a visible preview and a separate permission
 * grant.
 */
@Suppress("LongMethod")
@Composable
fun HealthSharingScreen(
    navigateBack: () -> Unit,
    viewModel: HealthSharingViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val uriHandler = LocalUriHandler.current
  val state by viewModel.state.collectAsState()
  val busy by viewModel.busy.collectAsState()
  val report by viewModel.preview.collectAsState()
  val message by viewModel.message.collectAsState()
  // Rechecked on click too: a permission request can return after the activity has been recreated.
  val available = viewModel.medicalAvailable()
  val vitalsAvailable =
      androidx.health.connect.client.HealthConnectClient.getSdkStatus(context) ==
          androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE
  var showRemoveConfirmation by remember { mutableStateOf(false) }
  val permissionLauncher =
      rememberLauncherForActivityResult(
          PermissionController.createRequestPermissionResultContract()
      ) { granted ->
        viewModel.message.value =
            if (granted.containsAll(MedicationHealthConnect.permissions)) {
              "Permission granted. Review the preview, then tap Refresh Health Connect."
            } else {
              "Medical record permission was not granted."
            }
      }
  val vitalsPermissions = remember {
    setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
    )
  }
  val vitalsLauncher =
      rememberLauncherForActivityResult(
          PermissionController.createRequestPermissionResultContract()
      ) { granted ->
        if (granted.any { it in vitalsPermissions }) {
          viewModel.allowAi(true)
        } else {
          viewModel.message.value = "No vitals access was granted."
        }
      }
  val fileLauncher =
      rememberLauncherForActivityResult(
          ActivityResultContracts.CreateDocument("application/fhir+json")
      ) { uri ->
        uri?.let(viewModel::exportFile)
      }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text("Health Connect & Google Health") },
            navigationIcon = {
              IconButton(onClick = navigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
              }
            },
        )
      }
  ) { padding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Text("Share medications you choose")
        Text(
            "Only your own logged doses from the last 30 days are included. Select the substances you want " +
                "to share. Journal notes and other people's entries are excluded."
        )
        Text(
            "Sharing is manual. Refresh after adding, editing or deleting doses, or changing this " +
                "selection. Refresh removes previously exported doses outside this selection or time window."
        )
        Text(
            "These records describe logged intake, not prescriptions, current treatment, or proof of adherence."
        )
      }
      if (!state.loaded) item { Text("Loading medications…") }
      if (state.loaded && state.choices.isEmpty()) {
        item { Text("Log a medication in the journal to select it here.") }
      }
      items(state.choices, key = { it.name }) { choice ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(
              checked = choice.name in state.selected,
              onCheckedChange = { viewModel.select(choice.name, it) },
              enabled = !busy,
          )
          Text("${choice.name} — ${choice.doses} logged doses")
        }
      }
      item {
        Button(onClick = viewModel::preparePreview, enabled = state.loaded && !busy) {
          Text("Preview selected medication records")
        }
      }
      report?.let { preview ->
        item {
          Text("${preview.statements.size} doses · snapshot ${preview.generatedAt}")
          Text(preview.summary())
          TextButton(
              onClick = {
                viewModel.summary()?.let { text ->
                  val clip = ClipData.newPlainText("Medication summary", text)
                  clip.description.extras =
                      android.os.PersistableBundle().apply {
                        putBoolean("android.content.extra.IS_SENSITIVE", true)
                      }
                  (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                      .setPrimaryClip(clip)
                  viewModel.message.value = "Copied. Paste this summary into Google Health Coach."
                }
              },
              enabled = !busy,
          ) {
            Text("Copy summary for Coach")
          }
          TextButton(
              onClick = { fileLauncher.launch("freaklog-medications.fhir.json") },
              enabled = !busy,
          ) {
            Text("Save FHIR medication file")
          }
        }
      }
      item {
        HorizontalDivider()
        if (available) {
          TextButton(
              onClick = { permissionLauncher.launch(MedicationHealthConnect.permissions) },
              enabled = !busy,
          ) {
            Text("Allow Health Connect medication export")
          }
          Button(onClick = viewModel::sync, enabled = report != null && !busy) {
            Text("Refresh Health Connect")
          }
          TextButton(onClick = { showRemoveConfirmation = true }, enabled = !busy) {
            Text("Remove medication export from Health Connect")
          }
        } else {
          Text(
              "Health Connect medical records are unavailable on this device. You can still copy the summary " +
                  "or save a FHIR file."
          )
        }
        Text(
            "In Google Health: Connections → Apps and services → Partner apps → Health Connect. Enable " +
                "Personal health records and allow medications from Freaklog. Enable Coach and its " +
                "personalization to use those records. Availability depends on your Google account, device, " +
                "country and subscription."
        )
        TextButton(
            onClick = {
              uriHandler.openUri("https://support.google.com/googlehealth/answer/16998660?hl=en")
            }
        ) {
          Text("Google Health setup guide")
        }
        Text(
            "Removing the Health Connect export does not delete summaries or records already copied to other apps."
        )
        HorizontalDivider()
      }
      item {
        Text("Vitals in Freaklog's AI assistant")
        Text(
            "Allow the configured AI provider to receive a summary of heart rate, sleep sessions and blood " +
                "pressure when you ask. This uses separate Health Connect read permissions. Off by default."
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text("Share vitals with Freaklog AI", Modifier.weight(1f))
          Switch(
              checked = state.aiVitals,
              enabled = !busy && (state.aiVitals || vitalsAvailable),
              onCheckedChange = {
                if (it) vitalsLauncher.launch(vitalsPermissions) else viewModel.allowAi(false)
              },
          )
        }
        Text(
            "You can also show heart rate, sleep and blood pressure alongside your doses using Settings → " +
                "Vitals on timeline, and log blood pressure from an experience."
        )
      }
      message?.let { value -> item { Text(value) } }
    }
  }
  if (showRemoveConfirmation) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { showRemoveConfirmation = false },
        title = { Text("Remove exported medications?") },
        text = {
          Text(
              "This deletes only Freaklog's medication data source in Health Connect. Your journal stays available."
          )
        },
        confirmButton = {
          TextButton(
              onClick = {
                showRemoveConfirmation = false
                viewModel.removeExport()
              }
          ) {
            Text("Remove")
          }
        },
        dismissButton = {
          TextButton(onClick = { showRemoveConfirmation = false }) { Text("Cancel") }
        },
    )
  }
}
