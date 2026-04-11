package foo.pilz.freaklog.ui.tabs.journal.experience.recommendations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import foo.pilz.freaklog.data.ai.AiChatViewModel
import foo.pilz.freaklog.data.ai.ChatMessage
import dev.jeziellago.compose.markdowntext.MarkdownText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatBottomSheet(
    experienceId: Int,
    onDismiss: () -> Unit,
    viewModel: AiChatViewModel = hiltViewModel()
) {
    LaunchedEffect(experienceId) {
        viewModel.initialize(experienceId)
    }

    val uiState by viewModel.uiState.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            Text(
                text = "AI Session Assistant",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (uiState.isApiKeyMissing) {
                Text(
                    text = "AI API Key is missing. Please configure it in the Settings tab to use the Chatbot.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Checkbox(
                        checked = uiState.includeHistorySummary,
                        onCheckedChange = { viewModel.toggleHistorySummary(it) }
                    )
                    Text(text = "Include history summary", style = MaterialTheme.typography.bodyMedium)
                }

                if (uiState.isContextLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    reverseLayout = true
                ) {
                    items(uiState.messages.reversed(), key = { it.id }) { msg ->
                        MessageBubble(message = msg)
                    }
                }

                var textState by remember { mutableStateOf(TextFieldValue("")) }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textState,
                        onValueChange = { textState = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask about recommendations...") },
                        maxLines = 3
                    )
                    IconButton(
                        onClick = {
                            if (textState.text.isNotBlank()) {
                                viewModel.sendMessage(textState.text)
                                textState = TextFieldValue("")
                            }
                        },
                        enabled = !uiState.isContextLoading && uiState.messages.none { it.isLoading }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val backgroundColor = if (message.isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    
    val alignment = if (message.isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .background(backgroundColor, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            MarkdownText(
                markdown = message.text,
                color = if (message.isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
