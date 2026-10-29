package foo.pilz.freaklog.ui.tabs.journal.experience.recommendations

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.jeziellago.compose.markdowntext.MarkdownText
import foo.pilz.freaklog.data.ai.AiChatUiState
import foo.pilz.freaklog.data.ai.AiChatViewModel
import foo.pilz.freaklog.data.ai.ChatItem
import foo.pilz.freaklog.data.ai.profiles.AiProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatBottomSheet(
    experienceId: Int?,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
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
                uiState = uiState,
                onClearChat = { viewModel.clearChat() },
                onSwitchProfile = { viewModel.switchProfile(it) },
                onOpenSettings = onOpenSettings
            )

            when {
                uiState.setupMessage != null -> SetupNeededState(uiState.setupMessage!!, onOpenSettings)
                uiState.isAssistantDisabled -> AssistantDisabledState(onOpenSettings)
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
                        canRetry = uiState.canRetry && !uiState.isAssistantBusy,
                        onRetry = { viewModel.retryLastTurn() },
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
                        isBusy = uiState.isAssistantBusy,
                        onSend = { viewModel.sendMessage(it) },
                        onStop = { viewModel.stop() }
                    )
                    Text(
                        text = "AI answers can be wrong. Not medical advice.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(
    uiState: AiChatUiState,
    onClearChat: () -> Unit,
    onSwitchProfile: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    var pendingSwitch by remember { mutableStateOf<AiProfile?>(null) }
    val hasConversation = uiState.items.any { it is ChatItem.Message && it.isUser }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "AI assistant",
                style = MaterialTheme.typography.titleLarge
            )
            ProfileSwitcher(
                profiles = uiState.profiles,
                activeProfileId = uiState.activeProfileId,
                modelName = uiState.modelName,
                enabled = !uiState.isAssistantBusy,
                onPick = { profile ->
                    if (hasConversation) pendingSwitch = profile else onSwitchProfile(profile.id)
                },
                onManage = onOpenSettings
            )
            val subtitle = uiState.statusMessage ?: uiState.contextLabel
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        TextButton(onClick = onClearChat, enabled = uiState.setupMessage == null) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("New chat")
        }
    }
    pendingSwitch?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingSwitch = null },
            title = { Text("Switch to ${profile.name}?") },
            text = { Text("This starts a new chat. The current conversation will be cleared.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingSwitch = null
                    onSwitchProfile(profile.id)
                }) { Text("Switch") }
            },
            dismissButton = {
                TextButton(onClick = { pendingSwitch = null }) { Text("Cancel") }
            }
        )
    }
}

/** Shows the active profile and model; tapping it lists the other saved profiles. */
@Composable
private fun ProfileSwitcher(
    profiles: List<AiProfile>,
    activeProfileId: String?,
    modelName: String?,
    enabled: Boolean,
    onPick: (AiProfile) -> Unit,
    onManage: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val active = profiles.firstOrNull { it.id == activeProfileId }
    val label = when {
        active == null -> "No provider"
        modelName != null -> "${active.name} · $modelName"
        else -> "${active.name} · ${active.model.ifBlank { "no model" }}"
    }
    Box {
        Row(
            modifier = Modifier
                .clickable(enabled = enabled) { expanded = true }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = "Switch AI provider",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(profile.name)
                            Text(
                                text = profile.model.ifBlank { "no model chosen" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    leadingIcon = {
                        if (profile.id == activeProfileId) {
                            Icon(Icons.Filled.Check, contentDescription = "Active")
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    onClick = {
                        expanded = false
                        if (profile.id != activeProfileId) onPick(profile)
                    }
                )
            }
            if (profiles.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Manage providers…") },
                leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                onClick = {
                    expanded = false
                    onManage()
                }
            )
        }
    }
}

@Composable
private fun AssistantDisabledState(onOpenSettings: () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            text = "AI assistant is disabled.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Turn it on in Settings → AI assistant to start a chat.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
        Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 12.dp)) {
            Text("Open AI settings")
        }
    }
}

@Composable
private fun SetupNeededState(message: String, onOpenSettings: () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "The assistant works with Gemini, OpenAI, Anthropic, OpenRouter and other providers, " +
                "or with your own Ollama, LM Studio or other compatible server.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 12.dp)) {
            Text("Open AI settings")
        }
    }
}

@Composable
private fun Transcript(
    items: List<ChatItem>,
    canRetry: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val lastText = (items.lastOrNull() as? ChatItem.Message)?.text?.length ?: 0
    LaunchedEffect(items.size, lastText) {
        if (items.isNotEmpty()) {
            listState.animateScrollToItem(items.lastIndex)
        }
    }
    // Only the reply to the latest user message can be retried.
    val firstUserIndex = items.indexOfFirst { it is ChatItem.Message && it.isUser }
    val lastUserIndex = items.indexOfLast { it is ChatItem.Message && it.isUser }
    val lastReplyId = items.lastOrNull { it is ChatItem.Message && !it.isUser }
        ?.takeIf { lastUserIndex >= 0 && items.indexOf(it) > lastUserIndex }?.id
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            when (item) {
                is ChatItem.Message -> MessageBubble(
                    message = item,
                    // The welcome text before the first question gets no actions.
                    showActions = firstUserIndex in 0 until index,
                    onRetry = if (canRetry && item.id == lastReplyId) onRetry else null
                )
                is ChatItem.ToolEvent -> ToolEventChip(item)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatItem.Message, showActions: Boolean, onRetry: (() -> Unit)?) {
    val context = LocalContext.current
    val images = remember(context) { assistantImageLoader(context) }
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
            } else if (message.isStopped && message.text.isBlank()) {
                Text(
                    "Stopped before an answer arrived.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.7f)
                )
            } else {
                MarkdownText(
                    markdown = message.text,
                    imageLoader = images,
                    style = MaterialTheme.typography.bodyMedium.copy(color = contentColor)
                )
            }
        }
        val showCopy = showActions && !message.isUser && !message.isError && !message.isLoading &&
            !message.isStreaming && message.text.isNotBlank()
        if (showCopy || onRetry != null) {
            MessageActions(
                text = message.text.takeIf { showCopy },
                isStopped = message.isStopped && message.text.isNotBlank(),
                onRetry = onRetry
            )
        }
    }
}

@Composable
private fun MessageActions(text: String?, isStopped: Boolean, onRetry: (() -> Unit)?) {
    val clipboardManager = LocalClipboardManager.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isStopped) {
            Text(
                "Stopped",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        if (text != null) {
            IconButton(
                onClick = { clipboardManager.setText(AnnotatedString(text)) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = "Copy answer",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onRetry != null) {
            TextButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Retry", style = MaterialTheme.typography.labelMedium)
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
    var showDetails by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDetails = !showDetails }
            .padding(vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            val summary = event.resultSummary?.let { " → $it" } ?: ""
            Text(
                text = "${event.label}$summary",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showDetails) {
            val argText = event.args.entries
                .filter { (_, v) -> v != null && v.toString().isNotBlank() }
                .joinToString(", ") { (k, v) -> "$k=$v" }
            Text(
                text = "${event.toolName}($argText)",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 22.dp, top = 2.dp)
            )
        }
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
    isBusy: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit
) {
    var textState by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val send: () -> Unit = {
        val text = textState.text.trim()
        if (text.isNotBlank() && !isBusy) {
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
        if (isBusy) {
            IconButton(onClick = onStop) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 2.dp)
                    Icon(Icons.Filled.Stop, contentDescription = "Stop", modifier = Modifier.size(18.dp))
                }
            }
        } else {
            IconButton(onClick = send, enabled = textState.text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}


