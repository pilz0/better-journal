package foo.pilz.freaklog.ui.tabs.settings.health

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import foo.pilz.freaklog.ui.components.NavigateBackButton
import foo.pilz.freaklog.ui.tabs.journal.experience.components.CardWithTitle
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.VitalKind
import foo.pilz.freaklog.ui.tabs.settings.SettingsSwitchRow
import foo.pilz.freaklog.ui.tabs.settings.combinations.BloodPressureDisplay
import foo.pilz.freaklog.ui.theme.horizontalPadding
import foo.pilz.freaklog.ui.utils.HapticType
import foo.pilz.freaklog.ui.utils.healthConnectInstallIntent
import foo.pilz.freaklog.ui.utils.isHealthConnectAvailable
import foo.pilz.freaklog.ui.utils.isHealthConnectInstallable
import foo.pilz.freaklog.ui.utils.rememberHaptic
import kotlinx.coroutines.launch

@Immutable
data class HealthSettingsUiState(
    val isVitalsAvailable: Boolean,
    val isVitalsEnabled: Boolean,
    val sleepStagesEnabled: Boolean,
    val bloodPressureDisplay: BloodPressureDisplay,
    val supportedExtraVitals: List<VitalKind>,
    val extraVitals: Set<VitalKind>,
    val aiAssistantEnabled: Boolean,
    val aiVitalsEnabled: Boolean,
    val grantedVitalLabels: List<String>,
    val canInstallHealthConnect: Boolean,
)

@Immutable
data class HealthSettingsActions(
    val saveIsVitalsEnabled: (Boolean) -> Unit,
    val saveSleepStagesEnabled: (Boolean) -> Unit,
    val saveBloodPressureDisplay: (BloodPressureDisplay) -> Unit,
    val saveExtraVitalEnabled: (VitalKind, Boolean) -> Unit,
    val saveAiVitalsEnabled: (Boolean) -> Unit,
    val installHealthConnect: () -> Unit,
    val requestAiVitalsPermissions: () -> Unit,
)

@Composable
fun HealthSettingsScreen(viewModel: HealthSettingsViewModel = hiltViewModel()) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val isVitalsAvailable = remember { isHealthConnectAvailable(context) }
  val canInstallHealthConnect = remember { isHealthConnectInstallable(context) }
  val supportedExtraVitals = remember { VitalKind.entries.filter { it.isSupported(context) } }
  var grantedVitalLabels by remember { mutableStateOf<List<String>>(emptyList()) }
  val refreshGrantedLabels: () -> Unit = {
    scope.launch {
      grantedVitalLabels = if (isVitalsAvailable) readGrantedVitalLabels(context) else emptyList()
    }
  }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshGrantedLabels() }

  val saveIsVitalsEnabled =
      rememberVitalsToggle(
          onEnable = { viewModel.saveIsVitalsEnabled(true) },
          onDisable = { viewModel.saveIsVitalsEnabled(false) },
          onDenied = { viewModel.showMessage("Health Connect permission denied") },
          onPermissionsChanged = refreshGrantedLabels,
      )
  val saveExtraVitalEnabled =
      rememberExtraVitalToggle(
          onSave = viewModel::saveExtraVitalEnabled,
          onDenied = { viewModel.showMessage("Health Connect permission denied") },
          onPermissionsChanged = refreshGrantedLabels,
      )
  val aiVitalsPermissionLauncher =
      rememberLauncherForActivityResult(
          PermissionController.createRequestPermissionResultContract()
      ) { _ ->
        refreshGrantedLabels()
      }
  val installHealthConnect: () -> Unit = {
    try {
      context.startActivity(healthConnectInstallIntent())
    } catch (_: ActivityNotFoundException) {
      viewModel.showMessage("Play Store not found")
    }
  }

  val state =
      HealthSettingsUiState(
          isVitalsAvailable = isVitalsAvailable,
          isVitalsEnabled = viewModel.isVitalsEnabledFlow.collectAsStateWithLifecycle().value,
          sleepStagesEnabled = viewModel.sleepStagesEnabledFlow.collectAsStateWithLifecycle().value,
          bloodPressureDisplay =
              viewModel.bloodPressureDisplayFlow.collectAsStateWithLifecycle().value,
          supportedExtraVitals = supportedExtraVitals,
          extraVitals = viewModel.extraVitalsFlow.collectAsStateWithLifecycle().value,
          aiAssistantEnabled = viewModel.aiAssistantEnabledFlow.collectAsStateWithLifecycle().value,
          aiVitalsEnabled = viewModel.aiVitalsEnabledFlow.collectAsStateWithLifecycle().value,
          grantedVitalLabels = grantedVitalLabels,
          canInstallHealthConnect = canInstallHealthConnect,
      )
  val actions =
      HealthSettingsActions(
          saveIsVitalsEnabled = saveIsVitalsEnabled,
          saveSleepStagesEnabled = viewModel::saveSleepStagesEnabled,
          saveBloodPressureDisplay = viewModel::saveBloodPressureDisplay,
          saveExtraVitalEnabled = saveExtraVitalEnabled,
          saveAiVitalsEnabled = viewModel::saveAiVitalsEnabled,
          installHealthConnect = installHealthConnect,
          requestAiVitalsPermissions = {
            aiVitalsPermissionLauncher.launch(coreHealthConnectPermissions)
          },
      )
  HealthSettingsContent(
      state = state,
      actions = actions,
      snackbarHostState = viewModel.snackbarHostState,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthSettingsContent(
    state: HealthSettingsUiState,
    actions: HealthSettingsActions,
    snackbarHostState: SnackbarHostState,
) {
  Scaffold(
      topBar = {
        TopAppBar(
            navigationIcon = { NavigateBackButton() },
            title = { Text("Health & vitals") },
        )
      },
      snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { padding ->
    Column(
        modifier =
            Modifier.padding(padding)
                .padding(horizontal = horizontalPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
    ) {
      TimelineVitalsCard(state = state, actions = actions)
      if (
          state.isVitalsEnabled &&
              state.isVitalsAvailable &&
              state.supportedExtraVitals.isNotEmpty()
      ) {
        ExtraVitalsCard(state = state, actions = actions)
      }
      AiVitalsCard(state = state, actions = actions)
    }
  }
}

@Composable
private fun TimelineVitalsCard(
    state: HealthSettingsUiState,
    actions: HealthSettingsActions,
) {
  val performHaptic = rememberHaptic()
  CardWithTitle(title = "Timeline vitals", innerPaddingHorizontal = 0.dp) {
    SettingsSwitchRow(
        text = "Vitals on timeline",
        checked = state.isVitalsEnabled && state.isVitalsAvailable,
        description =
            when {
              state.isVitalsAvailable -> "Health Connect: heart rate, sleep, blood pressure"
              state.canInstallHealthConnect -> "Tap to install or update Health Connect"
              else -> "Health Connect is not available on this device"
            },
        onCheckedChange = {
          performHaptic(HapticType.TOGGLE)
          if (state.isVitalsAvailable) {
            actions.saveIsVitalsEnabled(it)
          } else if (state.canInstallHealthConnect) {
            actions.installHealthConnect()
          }
        },
    )
    if (state.isVitalsEnabled && state.isVitalsAvailable) {
      HorizontalDivider()
      SettingsSwitchRow(
          text = "Show detailed sleep stages",
          checked = state.sleepStagesEnabled,
          onCheckedChange = {
            performHaptic(HapticType.TOGGLE)
            actions.saveSleepStagesEnabled(it)
          },
      )
      HorizontalDivider()
      BloodPressureDisplayRow(
          value = state.bloodPressureDisplay,
          onValueChange = actions.saveBloodPressureDisplay,
      )
    }
  }
}

@Composable
private fun ExtraVitalsCard(
    state: HealthSettingsUiState,
    actions: HealthSettingsActions,
) {
  val performHaptic = rememberHaptic()
  CardWithTitle(title = "Extra vitals", innerPaddingHorizontal = 0.dp) {
    state.supportedExtraVitals.forEachIndexed { index, kind ->
      if (index > 0) HorizontalDivider()
      SettingsSwitchRow(
          text = kind.title,
          checked = kind in state.extraVitals,
          description = kind.description,
          onCheckedChange = {
            performHaptic(HapticType.TOGGLE)
            actions.saveExtraVitalEnabled(kind, it)
          },
      )
    }
  }
}

@Composable
private fun AiVitalsCard(
    state: HealthSettingsUiState,
    actions: HealthSettingsActions,
) {
  val performHaptic = rememberHaptic()
  CardWithTitle(title = "AI assistant", innerPaddingHorizontal = 0.dp) {
    SettingsSwitchRow(
        text = "Share Health Connect vitals",
        checked = state.aiVitalsEnabled,
        description =
            if (state.aiAssistantEnabled) {
              "Lets the assistant read your Health Connect vitals and logged blood pressure " +
                  "measurements and send them to the active AI provider."
            } else {
              "Lets the assistant read your Health Connect vitals and logged blood pressure " +
                  "measurements. Enable AI assistant in Settings to use this."
            },
        onCheckedChange = {
          performHaptic(HapticType.TOGGLE)
          actions.saveAiVitalsEnabled(it)
        },
    )
    if (state.aiVitalsEnabled) {
      HorizontalDivider()
      AiVitalsStatusContent(state = state, actions = actions)
    }
  }
}

@Composable
private fun AiVitalsStatusContent(
    state: HealthSettingsUiState,
    actions: HealthSettingsActions,
) {
  Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 8.dp)
  ) {
    if (!state.isVitalsAvailable) {
      Text(
          text =
              "Health Connect is not available on this device. Only legacy journal blood " +
                  "pressure logs can be read.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (state.canInstallHealthConnect) {
        TextButton(onClick = actions.installHealthConnect) {
          Text("Install or update Health Connect")
        }
      }
    } else if (state.grantedVitalLabels.isEmpty()) {
      TextButton(onClick = actions.requestAiVitalsPermissions) {
        Text("Grant Health Connect permissions")
      }
    } else {
      Text(
          text = "Granted: ${state.grantedVitalLabels.joinToString(", ")}.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun BloodPressureDisplayRow(
    value: BloodPressureDisplay,
    onValueChange: (BloodPressureDisplay) -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }
  Row(
      modifier = Modifier.fillMaxWidth().padding(start = horizontalPadding),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("Blood pressure", style = MaterialTheme.typography.bodyLarge)
    Box {
      TextButton(onClick = { menuOpen = true }) {
        Text(value.displayName)
        Icon(if (menuOpen) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, null)
      }
      DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        BloodPressureDisplay.entries.forEach { display ->
          DropdownMenuItem(
              text = { Text(display.displayName) },
              onClick = {
                onValueChange(display)
                menuOpen = false
              },
              leadingIcon = {
                if (display == value) Icon(Icons.Default.Check, "Selected")
              },
          )
        }
      }
    }
  }
}

@Preview
@Composable
fun HealthSettingsPreview() {
  HealthSettingsContent(
      state =
          HealthSettingsUiState(
              isVitalsAvailable = true,
              isVitalsEnabled = true,
              sleepStagesEnabled = true,
              bloodPressureDisplay = BloodPressureDisplay.COMBINED,
              supportedExtraVitals = VitalKind.entries,
              extraVitals = setOf(VitalKind.OXYGEN_SATURATION),
              aiAssistantEnabled = true,
              aiVitalsEnabled = true,
              grantedVitalLabels = listOf("heart rate", "blood pressure"),
              canInstallHealthConnect = false,
          ),
      actions =
          HealthSettingsActions(
              saveIsVitalsEnabled = {},
              saveSleepStagesEnabled = {},
              saveBloodPressureDisplay = {},
              saveExtraVitalEnabled = { _, _ -> },
              saveAiVitalsEnabled = {},
              installHealthConnect = {},
              requestAiVitalsPermissions = {},
          ),
      snackbarHostState = remember { SnackbarHostState() },
  )
}
