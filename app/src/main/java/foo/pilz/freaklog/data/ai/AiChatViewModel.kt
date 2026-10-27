package foo.pilz.freaklog.data.ai

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.ai.provider.AiConversation
import foo.pilz.freaklog.data.ai.provider.AiTurnEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A single bubble shown in the chat transcript. */
sealed interface ChatItem {
    val id: String

    data class Message(
        override val id: String = java.util.UUID.randomUUID().toString(),
        val text: String,
        val isUser: Boolean,
        /** Waiting for the first token. */
        val isLoading: Boolean = false,
        /** Tokens are still arriving. */
        val isStreaming: Boolean = false,
        val isError: Boolean = false,
        /** The user pressed Stop before the reply finished. */
        val isStopped: Boolean = false,
    ) : ChatItem

    /** Inline status chip describing a tool the assistant just executed. */
    data class ToolEvent(
        override val id: String = java.util.UUID.randomUUID().toString(),
        val toolName: String,
        val args: Map<String, Any?>,
        val status: Status = Status.Running,
        val resultSummary: String? = null
    ) : ChatItem {
        enum class Status { Running, Success, Error }
    }
}

data class AiChatUiState(
    val items: List<ChatItem> = emptyList(),
    val isApiKeyMissing: Boolean = false,
    val isAssistantDisabled: Boolean = false,
    val isContextLoading: Boolean = true,
    val isAssistantBusy: Boolean = false,
    val statusMessage: String? = null,
    val modelName: String? = null,
    /** The last turn can be sent again (it finished, failed or was stopped). */
    val canRetry: Boolean = false,
    val suggestionPrompts: List<String> = DEFAULT_SUGGESTIONS
) {
    companion object {
        val DEFAULT_SUGGESTIONS = listOf(
            "Summarise my current session",
            "When did I last take this substance?",
            "What did I take in the past 30 days?",
            "Find my favourite past experiences"
        )
    }
}

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val repository: AiChatbotRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "AiChatViewModel"

        /** Minimum time between transcript updates while text streams in. */
        private const val STREAM_UPDATE_INTERVAL_MS = 50L
    }

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    private var experienceId: Int? = null
    private var initialized = false
    private var conversation: AiConversation? = null
    private var activeJob: Job? = null

    /**
     * Monotonically increasing token incremented every time we start a new chat. UI updates from
     * an in-flight turn are gated on this so a turn that's cancelled by [clearChat] cannot race
     * with the fresh state set up by [startNewChat].
     */
    private var sessionToken: Int = 0

    fun initialize(expId: Int?) {
        if (initialized && experienceId == expId && conversation != null) return
        initialized = true
        experienceId = expId
        startNewChat()
    }

    fun clearChat() {
        activeJob?.cancel()
        startNewChat()
    }

    private fun startNewChat() {
        sessionToken++
        val token = sessionToken
        conversation = null
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    items = emptyList(),
                    isContextLoading = true,
                    isAssistantBusy = false,
                    statusMessage = null,
                    canRetry = false,
                )
            }
            val result = repository.createChatSession(experienceId)
            if (token != sessionToken) return@launch
            when (result) {
                is AiChatSessionResult.Disabled -> _uiState.update {
                    it.copy(
                        isContextLoading = false,
                        isAssistantDisabled = true,
                        isApiKeyMissing = false,
                        modelName = null,
                    )
                }
                is AiChatSessionResult.MissingApiKey -> _uiState.update {
                    it.copy(
                        isContextLoading = false,
                        isApiKeyMissing = true,
                        isAssistantDisabled = false,
                        modelName = null
                    )
                }
                is AiChatSessionResult.Failed -> _uiState.update {
                    it.copy(
                        isContextLoading = false,
                        isApiKeyMissing = false,
                        isAssistantDisabled = false,
                        modelName = null,
                        items = listOf(
                            ChatItem.Message(
                                text = "Failed to initialise assistant: ${result.message}",
                                isUser = false,
                                isError = true
                            )
                        )
                    )
                }
                is AiChatSessionResult.Ready -> {
                    conversation = result.conversation
                    _uiState.update {
                        it.copy(
                            isContextLoading = false,
                            isApiKeyMissing = false,
                            isAssistantDisabled = false,
                            modelName = result.modelName,
                            items = listOf(
                                ChatItem.Message(
                                    text = "Hi! I'm your in-app journal assistant. I can search your past " +
                                        "experiences, summarise patterns, and answer harm-reduction questions. " +
                                        "Try a suggestion below or ask me anything.",
                                    isUser = false
                                )
                            ),
                        )
                    }
                }
            }
        }
    }

    fun sendMessage(messageText: String) {
        val text = messageText.trim()
        if (text.isBlank() || _uiState.value.isAssistantBusy) return
        val chat = conversation ?: return
        val token = sessionToken

        val userMsg = ChatItem.Message(text = text, isUser = true)
        val placeholder = ChatItem.Message(text = "", isUser = false, isLoading = true)
        _uiState.update {
            it.copy(
                items = it.items + userMsg + placeholder,
                isAssistantBusy = true,
                canRetry = false,
                statusMessage = "Thinking…"
            )
        }
        val turn = TurnRenderer(placeholder.id, token)
        activeJob = viewModelScope.launch {
            try {
                chat.send(text).collect { event -> turn.handle(event) }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.e(TAG, "Chat turn failed", t)
                turn.fail(t.message ?: t::class.java.simpleName)
            }
        }
    }

    /** Stops the reply that is streaming. Text received so far stays in the transcript. */
    fun stop() {
        val job = activeJob ?: return
        val token = sessionToken
        viewModelScope.launch {
            // Wait until the conversation has repaired its history before allowing another send.
            job.cancelAndJoin()
            if (token != sessionToken) return@launch
            _uiState.update { state ->
                val items = state.items.mapNotNull { item ->
                    when {
                        item is ChatItem.Message && (item.isLoading || item.isStreaming) ->
                            if (item.text.isBlank()) {
                                item.copy(text = "Stopped.", isLoading = false, isStreaming = false, isStopped = true)
                            } else {
                                item.copy(isLoading = false, isStreaming = false, isStopped = true)
                            }
                        item is ChatItem.ToolEvent && item.status == ChatItem.ToolEvent.Status.Running ->
                            item.copy(status = ChatItem.ToolEvent.Status.Error, resultSummary = "stopped")
                        else -> item
                    }
                }
                state.copy(items = items, isAssistantBusy = false, statusMessage = null, canRetry = true)
            }
        }
    }

    /** Sends the last user message again, replacing the reply it got. */
    fun retryLastTurn() {
        if (_uiState.value.isAssistantBusy) return
        val chat = conversation ?: return
        val text = chat.rollbackLastTurn() ?: return
        _uiState.update { state ->
            val lastUser = state.items.indexOfLast { it is ChatItem.Message && it.isUser }
            state.copy(items = if (lastUser < 0) state.items else state.items.take(lastUser))
        }
        sendMessage(text)
    }

    /**
     * Turns the [AiTurnEvent]s of one turn into transcript items. Text that arrives before a
     * tool call stays in its own bubble; the answer after the tools streams into a new one.
     */
    private inner class TurnRenderer(private var bubbleId: String, private val token: Int) {
        private val text = StringBuilder()
        private var lastFlush = 0L
        private val toolItemIds = mutableMapOf<String, String>()

        fun handle(event: AiTurnEvent) {
            if (token != sessionToken) return
            when (event) {
                is AiTurnEvent.TextDelta -> {
                    text.append(event.text)
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastFlush >= STREAM_UPDATE_INTERVAL_MS) {
                        lastFlush = now
                        flush(streaming = true)
                    }
                }
                is AiTurnEvent.ToolStarted -> {
                    val chip = ChatItem.ToolEvent(toolName = event.call.name, args = event.call.args)
                    toolItemIds[event.key] = chip.id
                    if (text.isBlank()) {
                        insertBefore(bubbleId, chip)
                    } else {
                        // Keep the text written before the tool call and open a fresh bubble.
                        flush(streaming = false)
                        val next = ChatItem.Message(text = "", isUser = false, isLoading = true)
                        _uiState.update { it.copy(items = it.items + chip + next) }
                        bubbleId = next.id
                        text.setLength(0)
                    }
                    _uiState.update { it.copy(statusMessage = "Running ${event.call.name}…") }
                }
                is AiTurnEvent.ToolFinished -> {
                    val isError = event.result.optString("status") == "error"
                    val status = if (isError) ChatItem.ToolEvent.Status.Error else ChatItem.ToolEvent.Status.Success
                    toolItemIds[event.key]?.let { id ->
                        updateToolEvent(id) {
                            it.copy(
                                status = status,
                                resultSummary = summariseToolResult(event.call.name, event.result)
                            )
                        }
                    }
                    _uiState.update { it.copy(statusMessage = "Thinking…") }
                }
                is AiTurnEvent.Completed -> {
                    if (text.isBlank() && event.text.isNotBlank()) text.append(event.text)
                    if (text.isBlank()) {
                        val hadEarlierText = _uiState.value.items
                            .dropWhile { !(it is ChatItem.Message && it.isUser) }
                            .any { it is ChatItem.Message && !it.isUser && it.text.isNotBlank() && it.id != bubbleId }
                        if (hadEarlierText) {
                            _uiState.update { s -> s.copy(items = s.items.filterNot { it.id == bubbleId }) }
                        } else {
                            text.append("The assistant returned an empty response.")
                            flush(streaming = false)
                        }
                    } else {
                        flush(streaming = false)
                    }
                    _uiState.update { it.copy(isAssistantBusy = false, statusMessage = null, canRetry = true) }
                }
            }
        }

        fun fail(message: String) {
            if (token != sessionToken) return
            if (text.isBlank()) {
                replaceMessage(bubbleId) {
                    it.copy(text = "Error: $message", isLoading = false, isStreaming = false, isError = true)
                }
            } else {
                flush(streaming = false)
                val error = ChatItem.Message(text = "Error: $message", isUser = false, isError = true)
                _uiState.update { it.copy(items = it.items + error) }
            }
            _uiState.update { it.copy(isAssistantBusy = false, statusMessage = null, canRetry = true) }
        }

        private fun flush(streaming: Boolean) {
            val current = text.toString()
            replaceMessage(bubbleId) {
                it.copy(text = current, isLoading = false, isStreaming = streaming)
            }
        }
    }

    private fun summariseToolResult(name: String, result: org.json.JSONObject): String {
        if (result.optString("status") == "error") {
            return result.optString("message", "tool failed")
        }
        return when (name) {
            "list_recent_experiences" -> "${result.optJSONArray("experiences")?.length() ?: 0} experiences"
            "search_experiences", "search_experiences_by_substance" ->
                "${result.optJSONArray("matches")?.length() ?: 0} matches"
            "get_experience_details" -> result.optString("title", "experience loaded")
            "get_recent_ingestions" -> "${result.optInt("count")} ingestions in ${result.optInt("days_back")}d"
            "get_substance_usage_stats" ->
                "${result.optJSONArray("substances")?.length() ?: 0} substances, " +
                    "${result.optInt("total_ingestions")} ingestions"
            else -> "ok"
        }
    }

    private fun replaceMessage(id: String, transform: (ChatItem.Message) -> ChatItem.Message) {
        _uiState.update { state ->
            val newItems = state.items.map { item ->
                if (item is ChatItem.Message && item.id == id) transform(item) else item
            }
            state.copy(items = newItems)
        }
    }

    private fun updateToolEvent(id: String, transform: (ChatItem.ToolEvent) -> ChatItem.ToolEvent) {
        _uiState.update { state ->
            val newItems = state.items.map { item ->
                if (item is ChatItem.ToolEvent && item.id == id) transform(item) else item
            }
            state.copy(items = newItems)
        }
    }

    private fun insertBefore(targetId: String, item: ChatItem) {
        _uiState.update { state ->
            val idx = state.items.indexOfFirst { it.id == targetId }
            val newItems = if (idx == -1) state.items + item
            else state.items.toMutableList().apply { add(idx, item) }
            state.copy(items = newItems)
        }
    }
}
