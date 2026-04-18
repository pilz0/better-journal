package foo.pilz.freaklog.data.ai

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.FunctionResponsePart
import com.google.ai.client.generativeai.type.Part
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
        val isLoading: Boolean = false,
        val isError: Boolean = false
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
    val isContextLoading: Boolean = true,
    val isAssistantBusy: Boolean = false,
    val statusMessage: String? = null,
    val modelName: String? = null,
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
    private val tools: AiTools
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    private var experienceId: Int? = null
    private var chatSession: Chat? = null
    private var activeJob: Job? = null

    /**
     * Monotonically increasing token incremented every time we start a new chat. UI updates from
     * an in-flight turn are gated on this so a turn that's cancelled by [clearChat] cannot race
     * with the fresh state set up by [startNewChat].
     */
    private var sessionToken: Int = 0

    fun initialize(expId: Int) {
        if (experienceId == expId && chatSession != null) return
        experienceId = expId
        startNewChat(showWelcome = true)
    }

    fun clearChat() {
        activeJob?.cancel()
        startNewChat(showWelcome = true)
    }

    private fun startNewChat(showWelcome: Boolean) {
        sessionToken++
        val token = sessionToken
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    items = emptyList(),
                    isContextLoading = true,
                    isAssistantBusy = false,
                    statusMessage = null
                )
            }
            val ready = repository.getGenerativeModelReady(experienceId)
            if (token != sessionToken) return@launch
            if (ready == null) {
                _uiState.update {
                    it.copy(
                        isContextLoading = false,
                        isApiKeyMissing = true,
                        modelName = null
                    )
                }
                return@launch
            }
            chatSession = ready.model.startChat()
            val initial = if (showWelcome) {
                listOf(
                    ChatItem.Message(
                        text = "Hi! I'm your in-app journal assistant. I can search your past " +
                            "experiences, summarise patterns, and answer harm-reduction questions about " +
                            "your current session. Try a suggestion below or ask me anything.",
                        isUser = false
                    )
                )
            } else emptyList()
            _uiState.update {
                it.copy(
                    isContextLoading = false,
                    isApiKeyMissing = false,
                    modelName = ready.modelName,
                    items = initial
                )
            }
        }
    }

    fun sendMessage(messageText: String) {
        val text = messageText.trim()
        if (text.isBlank() || _uiState.value.isAssistantBusy) return
        val chat = chatSession ?: return

        val userMsg = ChatItem.Message(text = text, isUser = true)
        val assistantPlaceholder = ChatItem.Message(text = "", isUser = false, isLoading = true)
        _uiState.update {
            it.copy(
                items = it.items + userMsg + assistantPlaceholder,
                isAssistantBusy = true,
                statusMessage = "Thinking…"
            )
        }
        val placeholderId = assistantPlaceholder.id
        val token = sessionToken

        activeJob = viewModelScope.launch {
            try {
                var response = chat.sendMessage(text)
                while (true) {
                    if (token != sessionToken) return@launch
                    val functionCalls = response.functionCalls
                    if (functionCalls.isEmpty()) {
                        val finalText = response.text?.takeIf { it.isNotBlank() }
                            ?: "The assistant returned an empty response."
                        replaceMessage(placeholderId) {
                            it.copy(text = finalText, isLoading = false)
                        }
                        _uiState.update { it.copy(isAssistantBusy = false, statusMessage = null) }
                        return@launch
                    }

                    // The Gemini SDK can return multiple function calls per turn; execute every
                    // call and reply with one FunctionResponsePart per call before asking for the
                    // next assistant message, otherwise the model keeps waiting on the missing
                    // tool responses.
                    val responseParts = mutableListOf<Part>()
                    for (functionCall in functionCalls) {
                        if (token != sessionToken) return@launch
                        val toolEvent = ChatItem.ToolEvent(
                            toolName = functionCall.name,
                            args = functionCall.args
                        )
                        insertBefore(placeholderId, toolEvent)
                        _uiState.update { it.copy(statusMessage = "Running ${functionCall.name}…") }

                        val toolResult = tools.execute(functionCall.name, functionCall.args)
                        val isError = toolResult.optString("status") == "error"
                        updateToolEvent(toolEvent.id) {
                            it.copy(
                                status = if (isError) ChatItem.ToolEvent.Status.Error else ChatItem.ToolEvent.Status.Success,
                                resultSummary = summariseToolResult(functionCall.name, toolResult)
                            )
                        }
                        responseParts += FunctionResponsePart(functionCall.name, toolResult)
                    }

                    if (token != sessionToken) return@launch
                    _uiState.update { it.copy(statusMessage = "Thinking…") }
                    response = chat.sendMessage(
                        Content(role = "function", parts = responseParts)
                    )
                }
            } catch (ce: CancellationException) {
                // Cancellation is a control-flow signal, not an error: do not surface it as one.
                throw ce
            } catch (t: Throwable) {
                Log.e("AiChatViewModel", "Chat turn failed", t)
                if (token != sessionToken) return@launch
                replaceMessage(placeholderId) {
                    it.copy(
                        text = "Error: ${t.message ?: t::class.java.simpleName}",
                        isLoading = false,
                        isError = true
                    )
                }
                _uiState.update { it.copy(isAssistantBusy = false, statusMessage = null) }
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
