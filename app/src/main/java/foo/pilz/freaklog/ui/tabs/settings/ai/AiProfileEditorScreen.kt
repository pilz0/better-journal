package foo.pilz.freaklog.ui.tabs.settings.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import foo.pilz.freaklog.data.ai.profiles.AiProviderPreset
import foo.pilz.freaklog.data.ai.profiles.isInsecureRemoteUrl
import foo.pilz.freaklog.data.ai.provider.AiApiStandard
import foo.pilz.freaklog.ui.theme.horizontalPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProfileEditorScreen(
    navigateBack: () -> Unit,
    viewModel: AiProfileEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val draft = state.draft
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Add provider" else "Edit provider") },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete provider")
                        }
                    }
                    TextButton(onClick = { viewModel.save(navigateBack) }, enabled = state.canSave) {
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoaded) {
            EditorForm(state, viewModel, Modifier.padding(padding))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${draft.name}?") },
            text = { Text("The profile and its saved API key are removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(navigateBack)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EditorForm(state: AiProfileEditorState, viewModel: AiProfileEditorViewModel, modifier: Modifier) {
    val draft = state.draft
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ChoiceField(
            label = "Provider",
            selected = draft.preset,
            options = AiProviderPreset.entries,
            optionLabel = { it.label },
            onSelect = viewModel::selectPreset,
        )
        Text(
            text = draft.preset.hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = draft.name,
            onValueChange = { value -> viewModel.edit { it.copy(name = value) } },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        ChoiceField(
            label = "API standard",
            selected = draft.standard,
            options = AiApiStandard.entries,
            optionLabel = { it.label },
            onSelect = { standard -> viewModel.edit { it.copy(standard = standard) } },
        )
        BaseUrlField(draft.baseUrl) { value -> viewModel.edit { it.copy(baseUrl = value) } }
        ApiKeyField(
            draft = draft,
            onChange = { value -> viewModel.edit { it.copy(apiKey = value) } },
            onRemoveSaved = viewModel::removeSavedKey
        )
        ModelField(state = state, viewModel = viewModel)
        if (draft.standard == AiApiStandard.ANTHROPIC_MESSAGES) {
            OutlinedTextField(
                value = draft.maxOutputTokens,
                onValueChange = { value ->
                    viewModel.edit { it.copy(maxOutputTokens = value.filter(Char::isDigit).take(6)) }
                },
                label = { Text("Max output tokens") },
                supportingText = { Text("Upper limit for one answer. The Anthropic standard requires it.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
        TestConnectionRow(state = state, onTest = viewModel::testConnection)
        Spacer(Modifier.size(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceField(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun BaseUrlField(value: String, onChange: (String) -> Unit) {
    val insecure = isInsecureRemoteUrl(value)
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("Base URL") },
        placeholder = { Text("https://example.com/v1") },
        singleLine = true,
        isError = insecure,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        supportingText = {
            if (insecure) {
                Text(
                    "Unencrypted http:// – your messages and journal data can be read on the way. " +
                        "Only use this on a network you trust, or put the server behind https."
                )
            } else {
                Text("The address the API paths are appended to, usually ending in /v1.")
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ApiKeyField(draft: AiProfileDraft, onChange: (String) -> Unit, onRemoveSaved: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val keepsSaved = draft.hasSavedKey && !draft.removeSavedKey
    OutlinedTextField(
        value = draft.apiKey,
        onValueChange = onChange,
        label = { Text(if (draft.preset.requiresApiKey) "API key" else "API key (optional)") },
        placeholder = { if (keepsSaved) Text("Saved – leave empty to keep it") },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Hide key" else "Show key"
                )
            }
        },
        supportingText = { Text("Stored encrypted on this device and only sent to this provider.") },
        modifier = Modifier.fillMaxWidth()
    )
    if (keepsSaved) {
        TextButton(onClick = onRemoveSaved) { Text("Remove saved key") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelField(state: AiProfileEditorState, viewModel: AiProfileEditorViewModel) {
    val draft = state.draft
    var expanded by remember { mutableStateOf(false) }
    val matches = state.models.filter { it.contains(draft.model.trim(), ignoreCase = true) }
        .ifEmpty { state.models }
    ExposedDropdownMenuBox(
        expanded = expanded && state.models.isNotEmpty(),
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = draft.model,
            onValueChange = { value ->
                viewModel.edit { it.copy(model = value) }
                expanded = true
            },
            label = { Text("Model") },
            placeholder = { Text("Fetch the list or type a model id") },
            singleLine = true,
            trailingIcon = {
                if (state.models.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            supportingText = state.modelsError?.let { error -> { Text(error) } },
            isError = state.modelsError != null,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded && state.models.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            matches.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        expanded = false
                        viewModel.edit { it.copy(model = model) }
                    }
                )
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = {
                viewModel.fetchModels()
                expanded = true
            },
            enabled = draft.baseUrl.isNotBlank() && !state.isFetchingModels && !state.missingKey
        ) {
            Text("Fetch models")
        }
        if (state.isFetchingModels) {
            Spacer(Modifier.size(12.dp))
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else if (state.models.isNotEmpty()) {
            Spacer(Modifier.size(12.dp))
            Text(
                "${state.models.size} models available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (state.missingKey) {
        Text(
            "Enter an API key to fetch models and test the connection.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TestConnectionRow(state: AiProfileEditorState, onTest: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onTest,
            enabled = state.canSave && !state.missingKey && state.test != ConnectionTest.Running
        ) {
            Text("Test connection")
        }
        when (val test = state.test) {
            ConnectionTest.Idle -> Unit
            ConnectionTest.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text("Sending a test message…", style = MaterialTheme.typography.bodySmall)
            }
            is ConnectionTest.Passed -> StatusLine(Icons.Outlined.CheckCircle, test.message, isError = false)
            is ConnectionTest.Failed -> StatusLine(Icons.Outlined.ErrorOutline, test.message, isError = true)
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, text: String, isError: Boolean) {
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
