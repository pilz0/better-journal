package foo.pilz.freaklog.ui.tabs.settings.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import foo.pilz.freaklog.data.ai.profiles.AiProfile
import foo.pilz.freaklog.data.ai.web.AiWebSearchEngine
import foo.pilz.freaklog.data.ai.web.WebUrlValidator
import foo.pilz.freaklog.ui.tabs.settings.health.coreHealthConnectPermissions
import foo.pilz.freaklog.ui.tabs.settings.health.readGrantedVitalLabels
import foo.pilz.freaklog.ui.theme.horizontalPadding
import foo.pilz.freaklog.ui.utils.isHealthConnectAvailable
import java.net.URI
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantSettingsScreen(
    navigateBack: () -> Unit,
    navigateToProfileEditor: (profileId: String?) -> Unit,
    navigateToAnodyneSettings: () -> Unit = {},
    navigateToHealthSettings: () -> Unit = {},
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
  val isEnabled by viewModel.isEnabled.collectAsStateWithLifecycle()
  val profiles by viewModel.profiles.collectAsStateWithLifecycle()
  val activeId by viewModel.activeProfileId.collectAsStateWithLifecycle()
  val withKey by viewModel.profilesWithKey.collectAsStateWithLifecycle()

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text("AI assistant") },
            navigationIcon = {
              IconButton(onClick = navigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
              }
            },
        )
      }
  ) { padding ->
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(padding)
                .padding(horizontal = horizontalPadding)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      EnableRow(isEnabled, viewModel::setEnabled)
      AiLocalNetworkAccess()
      ProvidersSection(
          profiles = profiles,
          activeId = activeId,
          withKey = withKey,
          onSelect = viewModel::setActive,
          onEdit = navigateToProfileEditor,
      )
      AnodyneSection(
          enabled = viewModel.isAnodyneEnabled.collectAsStateWithLifecycle().value,
          onEnabledChange = viewModel::setAnodyneEnabled,
          navigateToAnodyneSettings = navigateToAnodyneSettings,
      )
      WebToolsSection(
          enabled = viewModel.isWebEnabled.collectAsStateWithLifecycle().value,
          engine = viewModel.webSearchEngine.collectAsStateWithLifecycle().value,
          searxngUrl = viewModel.searxngUrl.collectAsStateWithLifecycle().value,
          hasBraveApiKey = viewModel.hasBraveApiKey.collectAsStateWithLifecycle().value,
          onEnabledChange = viewModel::setWebEnabled,
          onEngineChange = viewModel::setWebSearchEngine,
          onSearxngUrlChange = viewModel::setSearxngUrl,
          onSaveBraveKey = viewModel::saveBraveApiKey,
          onRemoveBraveKey = viewModel::removeBraveApiKey,
      )
      VitalsToolsSection(
          enabled = viewModel.isVitalsEnabled.collectAsStateWithLifecycle().value,
          onEnabledChange = viewModel::setVitalsEnabled,
          navigateToHealthSettings = navigateToHealthSettings,
      )
      Spacer(Modifier.size(24.dp))
    }
  }
}

@Composable
private fun ProvidersSection(
    profiles: List<AiProfile>,
    activeId: String?,
    withKey: Set<String>,
    onSelect: (String) -> Unit,
    onEdit: (String?) -> Unit,
) {
  Text("Providers", style = MaterialTheme.typography.titleMedium)
  Text(
      text =
          "Save one profile per provider or model and pick the one to use. You can also switch " +
              "in the chat. Hosted services need an API key; your own server (Ollama, LM Studio, " +
              "llama.cpp, vLLM, ...) often doesn't.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  if (profiles.isEmpty()) {
    Text(
        text = "No providers yet.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 8.dp),
    )
  }
  profiles.forEach { profile ->
    ProfileCard(
        profile = profile,
        isActive = profile.id == activeId,
        hasKey = profile.id in withKey,
        onSelect = { onSelect(profile.id) },
        onEdit = { onEdit(profile.id) },
    )
  }
  FilledTonalButton(onClick = { onEdit(null) }) {
    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.size(8.dp))
    Text("Add provider")
  }
}

@Composable
private fun ProfileCard(
    profile: AiProfile,
    isActive: Boolean,
    hasKey: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
) {
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      RadioButton(selected = isActive, onClick = onSelect)
      Column(modifier = Modifier.weight(1f)) {
        Text(profile.name, style = MaterialTheme.typography.titleSmall)
        Text(
            text = "${profile.model.ifBlank { "no model chosen" }} · ${profile.standard.label}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = hostOf(profile.baseUrl),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val warning =
            when {
              profile.preset.requiresApiKey && !hasKey -> "No API key saved"
              profile.model.isBlank() -> "No model chosen"
              profile.isInsecureRemote -> "HTTPS required: edit this provider URL"
              else -> null
            }
        if (warning != null) {
          Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(top = 2.dp),
          ) {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text(
                warning,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
      IconButton(onClick = onEdit) {
        Icon(Icons.Outlined.Edit, contentDescription = "Edit ${profile.name}")
      }
    }
  }
}

@Composable
private fun EnableRow(isEnabled: Boolean, onChange: (Boolean) -> Unit) {
  Row(
      modifier =
          Modifier.fillMaxWidth().clickable { onChange(!isEnabled) }.padding(vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text("Enable AI assistant", style = MaterialTheme.typography.titleMedium)
      Text(
          text =
              "When you chat, your messages and the journal data the assistant looks up " +
                  "are sent to the provider you choose below.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(checked = isEnabled, onCheckedChange = onChange)
  }
}

@Composable
private fun AnodyneSection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    navigateToAnodyneSettings: () -> Unit,
) {
  Text(
      "Tools",
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(top = 12.dp),
  )
  ToolToggleRow(
      title = "Look substances up on AnodyneWiki",
      description =
          "Lets the assistant read dose tiers, interactions and warnings from AnodyneWiki when the " +
              "bundled data isn't enough. Only the substance name is sent there.",
      enabled = enabled,
      onEnabledChange = onEnabledChange,
  )
  if (enabled) {
    TextButton(onClick = navigateToAnodyneSettings) { Text("Choose the AnodyneWiki instance") }
  }
}

@Composable
private fun ToolToggleRow(
    title: String,
    description: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
  Row(
      modifier = Modifier.fillMaxWidth().clickable { onEnabledChange(!enabled) },
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(title, style = MaterialTheme.typography.bodyLarge)
      Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(checked = enabled, onCheckedChange = onEnabledChange)
  }
}

@Suppress("LongParameterList")
@Composable
private fun WebToolsSection(
    enabled: Boolean,
    engine: AiWebSearchEngine,
    searxngUrl: String,
    hasBraveApiKey: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onEngineChange: (AiWebSearchEngine) -> Unit,
    onSearxngUrlChange: (String) -> Unit,
    onSaveBraveKey: (String) -> Unit,
    onRemoveBraveKey: () -> Unit,
) {
  ToolToggleRow(
      title = "Web search & page reading",
      description =
          "Lets the assistant search the web and read public https:// pages. Search queries and " +
              "visited URLs are sent to the search engine and websites.",
      enabled = enabled,
      onEnabledChange = onEnabledChange,
  )
  if (!enabled) return
  WebEngineSelector(selected = engine, onSelect = onEngineChange)
  when (engine) {
    AiWebSearchEngine.DUCKDUCKGO ->
        Text(
            text = "Uses DuckDuckGo with no API key required.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    AiWebSearchEngine.SEARXNG ->
        SearxngUrlField(savedUrl = searxngUrl, onValidOrCleared = onSearxngUrlChange)
    AiWebSearchEngine.BRAVE ->
        BraveApiKeyField(
            hasSavedKey = hasBraveApiKey,
            onSaveKey = onSaveBraveKey,
            onRemoveKey = onRemoveBraveKey,
        )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebEngineSelector(
    selected: AiWebSearchEngine,
    onSelect: (AiWebSearchEngine) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
    OutlinedTextField(
        value = selected.label,
        onValueChange = {},
        readOnly = true,
        label = { Text("Search engine") },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        modifier =
            Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
    )
    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      AiWebSearchEngine.entries.forEach { option ->
        DropdownMenuItem(
            text = { Text(option.label) },
            onClick = {
              expanded = false
              onSelect(option)
            },
        )
      }
    }
  }
}

@Composable
private fun SearxngUrlField(savedUrl: String, onValidOrCleared: (String) -> Unit) {
  var draftUrl by remember { mutableStateOf(savedUrl) }
  var hasEdited by remember { mutableStateOf(false) }
  LaunchedEffect(savedUrl) {
    if (!hasEdited) draftUrl = savedUrl
  }
  val isValid = draftUrl.isBlank() || WebUrlValidator.normalizeSearxngUrl(draftUrl) != null
  OutlinedTextField(
      value = draftUrl,
      onValueChange = { value ->
        hasEdited = true
        draftUrl = value
        if (value.isBlank() || WebUrlValidator.normalizeSearxngUrl(value) != null) {
          onValidOrCleared(value)
        }
      },
      label = { Text("SearXNG instance URL") },
      placeholder = { Text("https://searx.example.org") },
      singleLine = true,
      isError = !isValid,
      keyboardOptions =
          KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
      supportingText = {
        Text(
            if (isValid) {
              "Self-hosted SearXNG instance with JSON output enabled (formats: [html, json])."
            } else {
              "Enter an HTTPS address (plain http is allowed only for localhost)."
            }
        )
      },
      modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
private fun BraveApiKeyField(
    hasSavedKey: Boolean,
    onSaveKey: (String) -> Unit,
    onRemoveKey: () -> Unit,
) {
  val keyState = rememberTextFieldState()
  var visible by remember { mutableStateOf(false) }
  OutlinedSecureTextField(
      state = keyState,
      label = { Text("Brave Search API key") },
      placeholder = { if (hasSavedKey) Text("Saved – leave empty to keep it") },
      textObfuscationMode =
          if (visible) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
      trailingIcon = {
        IconButton(onClick = { visible = !visible }) {
          Icon(
              if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
              contentDescription = if (visible) "Hide key" else "Show key",
          )
        }
      },
      supportingText = {
        Text("Stored encrypted on this device and only sent to api.search.brave.com.")
      },
      modifier = Modifier.fillMaxWidth(),
  )
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(
        onClick = {
          onSaveKey(keyState.text.toString())
          keyState.clearText()
        },
        enabled = keyState.text.isNotBlank(),
    ) {
      Text("Save key")
    }
    if (hasSavedKey) {
      TextButton(onClick = onRemoveKey) { Text("Remove saved key") }
    }
  }
}

@Composable
private fun VitalsToolsSection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    navigateToHealthSettings: () -> Unit,
) {
  ToolToggleRow(
      title = "Share Health Connect vitals",
      description =
          "Lets the assistant read your Health Connect vitals and logged blood pressure " +
              "measurements and send them to the active AI provider.",
      enabled = enabled,
      onEnabledChange = onEnabledChange,
  )
  if (!enabled) return
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val hcAvailable = remember { isHealthConnectAvailable(context) }
  var grantedLabels by remember { mutableStateOf<List<String>>(emptyList()) }

  suspend fun refreshPermissions() {
    grantedLabels =
        if (!hcAvailable) {
          emptyList()
        } else {
          readGrantedVitalLabels(context)
        }
  }

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    scope.launch { refreshPermissions() }
  }

  val launcher =
      rememberLauncherForActivityResult(
          PermissionController.createRequestPermissionResultContract()
      ) { _ ->
        scope.launch { refreshPermissions() }
      }

  if (!hcAvailable) {
    Text(
        text =
            "Health Connect is not available on this device. Only legacy journal blood pressure " +
                "logs can be read.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    return
  }
  if (grantedLabels.isEmpty()) {
    TextButton(onClick = { launcher.launch(coreHealthConnectPermissions) }) {
      Text("Grant Health Connect permissions")
    }
  } else {
    Text(
        text = "Granted: ${grantedLabels.joinToString(", ")}.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
  TextButton(onClick = navigateToHealthSettings) { Text("Health & vitals settings") }
}

private fun hostOf(url: String): String =
    runCatching {
          URI(url.trim()).let { uri ->
            uri.host?.let { h -> if (uri.port > 0) "$h:${uri.port}" else h }
          }
        }
        .getOrNull() ?: url
