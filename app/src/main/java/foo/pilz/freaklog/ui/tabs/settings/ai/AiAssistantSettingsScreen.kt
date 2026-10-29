package foo.pilz.freaklog.ui.tabs.settings.ai

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import foo.pilz.freaklog.data.ai.anodyne.AnodyneWikiClient
import foo.pilz.freaklog.data.ai.profiles.AiProfile
import foo.pilz.freaklog.data.ai.profiles.isInsecureRemoteUrl
import foo.pilz.freaklog.ui.theme.horizontalPadding
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantSettingsScreen(
    navigateBack: () -> Unit,
    navigateToProfileEditor: (profileId: String?) -> Unit,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    val isEnabled by viewModel.isEnabled.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val activeId by viewModel.activeProfileId.collectAsState()
    val withKey by viewModel.profilesWithKey.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI assistant") },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = horizontalPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EnableRow(isEnabled, viewModel::setEnabled)
            AiLocalNetworkAccess()
            Text("Providers", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Save one profile per provider or model and pick the one to use. You can also switch " +
                    "in the chat. Hosted services need an API key; your own server (Ollama, LM Studio, " +
                    "llama.cpp, vLLM, ...) often doesn't.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (profiles.isEmpty()) {
                Text(
                    text = "No providers yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            profiles.forEach { profile ->
                ProfileCard(
                    profile = profile,
                    isActive = profile.id == activeId,
                    hasKey = profile.id in withKey,
                    onSelect = { viewModel.setActive(profile.id) },
                    onEdit = { navigateToProfileEditor(profile.id) }
                )
            }
            FilledTonalButton(onClick = { navigateToProfileEditor(null) }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Add provider")
            }
            AnodyneSection(
                enabled = viewModel.isAnodyneEnabled.collectAsState().value,
                baseUrl = viewModel.anodyneBaseUrl.collectAsState().value,
                onEnabledChange = viewModel::setAnodyneEnabled,
                onBaseUrlChange = viewModel::setAnodyneBaseUrl,
            )
            Spacer(Modifier.size(24.dp))
        }
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
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = isActive, onClick = onSelect)
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${profile.model.ifBlank { "no model chosen" }} · ${profile.standard.label}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = hostOf(profile.baseUrl),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val warning = when {
                    profile.preset.requiresApiKey && !hasKey -> "No API key saved"
                    profile.model.isBlank() -> "No model chosen"
                    profile.isInsecureRemote -> "HTTPS required: edit this provider URL"
                    else -> null
                }
                if (warning != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(warning, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!isEnabled) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Enable AI assistant", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "When you chat, your messages and the journal data the assistant looks up " +
                    "are sent to the provider you choose below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = isEnabled, onCheckedChange = onChange)
    }
}

@Composable
private fun AnodyneSection(
    enabled: Boolean,
    baseUrl: String,
    onEnabledChange: (Boolean) -> Unit,
    onBaseUrlChange: (String) -> Unit,
) {
    var draftUrl by remember(baseUrl) { mutableStateOf(baseUrl) }
    Text("Substance lookups", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEnabledChange(!enabled) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Look substances up on AnodyneWiki", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Lets the assistant read dose tiers, interactions and warnings from anodyne.wiki when the " +
                    "bundled data isn't enough. Only the substance name is sent there.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
    if (enabled) {
        OutlinedTextField(
            value = draftUrl,
            onValueChange = { value ->
                draftUrl = value
                onBaseUrlChange(value)
            },
            label = { Text("AnodyneWiki API URL") },
            placeholder = { Text(AnodyneWikiClient.DEFAULT_BASE_URL) },
            isError = isInsecureRemoteUrl(draftUrl),
            supportingText = {
                Text(
                    if (isInsecureRemoteUrl(draftUrl)) {
                        "HTTPS is required for LAN and remote mirrors."
                    } else {
                        "Leave empty for ${AnodyneWikiClient.DEFAULT_BASE_URL}, or use a compatible mirror."
                    }
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            trailingIcon = {
                if (draftUrl.isNotEmpty()) {
                    TextButton(onClick = {
                        draftUrl = ""
                        onBaseUrlChange("")
                    }) { Text("Reset") }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun hostOf(url: String): String =
    runCatching { URI(url.trim()).let { uri -> uri.host?.let { h -> if (uri.port > 0) "$h:${uri.port}" else h } } }
        .getOrNull() ?: url
