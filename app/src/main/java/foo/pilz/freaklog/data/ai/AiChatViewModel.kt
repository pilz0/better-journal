package foo.pilz.freaklog.data.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.util.Log
import javax.inject.Inject
import com.google.ai.client.generativeai.GenerativeModel

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val isLoading: Boolean = false
)

data class AiChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isApiKeyMissing: Boolean = false,
    val isContextLoading: Boolean = true,
    val includeHistorySummary: Boolean = false
)

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val repository: AiChatbotRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    private var experienceId: Int? = null
    private var systemPromptContext: String = ""
    private var conversationHistory: String = ""

    fun initialize(expId: Int) {
        Log.d("AiChatViewModel", "initialize called with expId: $expId")
        experienceId = expId
        reloadContext()
    }

    fun toggleHistorySummary(include: Boolean) {
        _uiState.update { it.copy(includeHistorySummary = include) }
        reloadContext()
    }

    private fun reloadContext() {
        Log.d("AiChatViewModel", "reloadContext started")
        viewModelScope.launch {
            _uiState.update { it.copy(isContextLoading = true) }
            val model = repository.getGenerativeModelReady()
            if (model == null) {
                Log.e("AiChatViewModel", "reloadContext: model returned null, setting isApiKeyMissing = true")
                _uiState.update { it.copy(isContextLoading = false, isApiKeyMissing = true) }
                return@launch
            } else {
                Log.d("AiChatViewModel", "reloadContext: model is ready")
                _uiState.update { it.copy(isApiKeyMissing = false) }
            }

            val expId = experienceId ?: return@launch
            systemPromptContext = repository.buildContextPrompt(expId, _uiState.value.includeHistorySummary)
            _uiState.update {
                val currentMessages = it.messages
                val welcomeMessage = ChatMessage(text = "Hello! I'm your cloud AI assistant. How can I help you regarding your current session?", isUser = false)
                it.copy(
                    isContextLoading = false,
                    messages = if (currentMessages.isEmpty()) listOf(welcomeMessage) else currentMessages
                )
            }
        }
    }

    fun sendMessage(messageText: String) {
        if (messageText.isBlank()) return

        val userMessage = ChatMessage(text = messageText, isUser = true)
        val loadingMessage = ChatMessage(text = "...", isUser = false, isLoading = true)

        _uiState.update { state ->
            state.copy(messages = state.messages + userMessage + loadingMessage)
        }

        viewModelScope.launch {
            try {
                Log.d("AiChatViewModel", "Sending message: $messageText")
                val model = repository.getGenerativeModelReady()
                if (model == null) {
                    Log.e("AiChatViewModel", "Model is unavailable during sendMessage")
                    replaceLoadingMessageWith("AI API Key is missing. Please set it in Settings.")
                    return@launch
                }
                
                val fullPrompt = "$systemPromptContext\n$conversationHistory\nUser: $messageText\nAssistant:"
                Log.d("AiChatViewModel", "Prompt ready, calling generateContentStream...")
                
                var accumulatedResponse = ""
                model.generateContentStream(fullPrompt).collect { chunk ->
                    accumulatedResponse += chunk.text ?: ""
                    replaceLoadingMessageWith(accumulatedResponse, isFinal = false)
                }
                
                Log.d("AiChatViewModel", "Stream completed. Response length: ${accumulatedResponse.length}")
                
                conversationHistory += "\nUser: $messageText\nAssistant: $accumulatedResponse"

                replaceLoadingMessageWith(accumulatedResponse, isFinal = true)
            } catch (e: Exception) {
                Log.e("AiChatViewModel", "Exception during generateContent", e)
                val strBuilder = java.lang.StringBuilder("Error: ${e.message}\n")
                e.stackTrace.take(5).forEach { strBuilder.append("\n  at $it") }
                replaceLoadingMessageWith(strBuilder.toString())
            }
        }
    }

    private fun replaceLoadingMessageWith(text: String, isFinal: Boolean = true) {
        _uiState.update { state ->
            val newMessages = state.messages.toMutableList()
            val index = newMessages.indexOfLast { it.isLoading }
            if (index != -1) {
                newMessages[index] = newMessages[index].copy(text = text, isLoading = !isFinal)
            } else {
                newMessages.add(ChatMessage(text = text, isUser = false, isLoading = !isFinal))
            }
            state.copy(messages = newMessages)
        }
    }
}
