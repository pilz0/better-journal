package foo.pilz.freaklog.ui.tabs.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import foo.pilz.freaklog.data.ai.AiChatbotRepository
import foo.pilz.freaklog.ui.theme.horizontalPadding

@Composable
fun AiAssistantSettingsScreen(
    navigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    AiAssistantSettingsScreen(
        navigateBack = navigateBack,
        aiApiKey = viewModel.aiApiKeyFlow.collectAsState().value,
        saveAiApiKey = viewModel::saveAiApiKey,
        aiModelName = viewModel.aiModelNameFlow.collectAsState().value,
        saveAiModelName = viewModel::saveAiModelName,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantSettingsScreen(
    navigateBack: () -> Unit,
    aiApiKey: String,
    saveAiApiKey: (String) -> Unit,
    aiModelName: String,
    saveAiModelName: (String) -> Unit,
) {
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
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "The assistant uses your Gemini API key to send relevant journal context " +
                    "to Google's Gemini API when you open or message the assistant.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            OutlinedTextField(
                value = aiApiKey,
                onValueChange = saveAiApiKey,
                label = { Text("Gemini API key") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = aiModelName.ifBlank { AiChatbotRepository.DEFAULT_MODEL_NAME },
                onValueChange = saveAiModelName,
                label = { Text("Gemini model name") },
                supportingText = {
                    Text("Recommended: ${AiChatbotRepository.DEFAULT_MODEL_NAME}. Settings apply to new chats.")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
    }
}
