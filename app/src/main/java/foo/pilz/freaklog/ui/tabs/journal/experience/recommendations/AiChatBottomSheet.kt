package foo.pilz.freaklog.ui.tabs.journal.experience.recommendations

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.jeziellago.compose.markdowntext.MarkdownText
import foo.pilz.freaklog.data.ai.AiChatViewModel
import foo.pilz.freaklog.data.ai.ChatItem

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
        modifier = Modifier.fillMaxHeight(0.95f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Header(
                modelName = uiState.modelName,
                statusMessage = uiState.statusMessage,
                onClearChat = { viewModel.clearChat() }
            )

            when {
                uiState.isApiKeyMissing -> ApiKeyMissingState()
                uiState.isContextLoading -> {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                }
                else -> {
                    Transcript(
                        items = uiState.items,
                        modifier = Modifier.weight(1f)
                    )

                    if (uiState.items.none { it is ChatItem.Message && it.isUser }) {
                        SuggestionRow(
                            prompts = uiState.suggestionPrompts,
                            enabled = !uiState.isAssistantBusy,
                            onPick = { viewModel.sendMessage(it) }
                        )
                    }

                    InputBar(
                        enabled = !uiState.isAssistantBusy,
                        onSend = { viewModel.sendMessage(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(
    modelName: String?,
    statusMessage: String?,
    onClearChat: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "AI Journal Assistant",
                style = MaterialTheme.typography.titleLarge
            )
            val subtitle = when {
                statusMessage != null -> statusMessage
                modelName != null -> "Model: $modelName"
                else -> null
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        TextButton(onClick = onClearChat) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("New chat")
        }
    }
}

@Composable
private fun ApiKeyMissingState() {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            text = "Gemini API key is missing.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = "Open Settings → AI Chatbot to paste your Google AI Studio API key. " +
                "The default model is gemini-2.5-flash; you can change it in the same place.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun Transcript(items: List<ChatItem>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) {
            listState.animateScrollToItem(items.lastIndex)
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(items, key = { it.id }) { item ->
            when (item) {
                is ChatItem.Message -> MessageBubble(item)
                is ChatItem.ToolEvent -> ToolEventChip(item)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatItem.Message) {
    val backgroundColor = when {
        message.isError -> MaterialTheme.colorScheme.errorContainer
        message.isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when {
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        message.isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val alignment = if (message.isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .background(backgroundColor, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (message.isLoading && message.text.isBlank()) {
                TypingIndicator(color = contentColor)
            } else {
                MarkdownText(
                    markdown = message.text,
                    color = contentColor,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun ToolEventChip(event: ChatItem.ToolEvent) {
    val (icon, tint) = when (event.status) {
        ChatItem.ToolEvent.Status.Running ->
            Icons.Filled.Build to MaterialTheme.colorScheme.onSurfaceVariant
        ChatItem.ToolEvent.Status.Success ->
            Icons.Filled.Check to MaterialTheme.colorScheme.primary
        ChatItem.ToolEvent.Status.Error ->
            Icons.Filled.Warning to MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        val argText = event.args.entries
            .filter { (_, v) -> v != null && v.toString().isNotBlank() }
            .joinToString(", ") { (k, v) -> "$k=$v" }
        val summary = event.resultSummary?.let { " → $it" } ?: ""
        Text(
            text = "${event.toolName}($argText)$summary",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TypingIndicator(color: Color) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = androidx.compose.animation.core.StartOffset(index * 150)
                ),
                label = "dot$index"
            )
            Text(
                text = "•",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = color.copy(alpha = alpha),
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

@Composable
private fun SuggestionRow(
    prompts: List<String>,
    enabled: Boolean,
    onPick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        prompts.forEach { prompt ->
            SuggestionChip(
                onClick = { if (enabled) onPick(prompt) },
                label = { Text(prompt) },
                enabled = enabled
            )
        }
    }
}

@Composable
private fun InputBar(
    enabled: Boolean,
    onSend: (String) -> Unit
) {
    var textState by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val send: () -> Unit = {
        val text = textState.text.trim()
        if (text.isNotBlank() && enabled) {
            onSend(text)
            textState = TextFieldValue("")
            keyboardController?.hide()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = textState,
            onValueChange = { textState = it },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            placeholder = { Text("Ask about your journal…") },
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() })
        )
        Spacer(Modifier.width(4.dp))
        if (!enabled) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(40.dp)
                    .padding(8.dp),
                strokeWidth = 2.dp
            )
        } else {
            IconButton(onClick = send, enabled = textState.text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}



