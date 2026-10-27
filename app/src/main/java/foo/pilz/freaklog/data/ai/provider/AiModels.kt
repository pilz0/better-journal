package foo.pilz.freaklog.data.ai.provider

import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.io.IOException

/** The wire protocol a provider speaks. Self-hosted servers usually implement one of these. */
enum class AiApiStandard(val label: String) {
    OPENAI_CHAT("OpenAI Chat Completions"),
    ANTHROPIC_MESSAGES("Anthropic Messages"),
    GEMINI("Google Gemini"),
}

/** A function call the model wants the app to execute. */
data class AiToolCall(
    /** Provider call id. Gemini may omit it; OpenAI and Anthropic always need one. */
    val id: String?,
    val name: String,
    /** Primitive values are String, Number or Boolean; nested values stay JSONObject/JSONArray. */
    val args: Map<String, Any?>,
)

data class AiToolResult(val callId: String?, val name: String, val result: JSONObject) {
    val isError: Boolean get() = result.optString("status") == "error"
}

/** A provider's own representation of an assistant turn, echoed back verbatim to that provider. */
data class NativeContent(val standard: AiApiStandard, val json: JSONObject)

/** Provider-neutral conversation history. */
sealed interface AiMessage {
    data class User(val text: String) : AiMessage

    data class Assistant(
        val text: String,
        val toolCalls: List<AiToolCall> = emptyList(),
        /**
         * Raw provider content. It keeps fields that must survive the round-trip, such as
         * Gemini's `thoughtSignature` and Anthropic's signed `thinking` blocks.
         */
        val native: NativeContent? = null,
    ) : AiMessage

    data class ToolResults(val results: List<AiToolResult>) : AiMessage
}

/** A tool the model may call. [parameters] is a JSON Schema object with lowercase types. */
data class AiToolSpec(val name: String, val description: String, val parameters: JSONObject)

data class AiChatRequest(
    val model: String,
    val system: String,
    val messages: List<AiMessage>,
    val tools: List<AiToolSpec>,
    val maxOutputTokens: Int,
)

sealed interface AiStreamEvent {
    data class TextDelta(val text: String) : AiStreamEvent

    /** Always the last event of a successful stream. */
    data class Completed(val message: AiMessage.Assistant, val stopReason: String?) : AiStreamEvent
}

/** Where and how to reach a provider. */
data class AiEndpoint(val baseUrl: String, val apiKey: String)

/** One provider's implementation of an [AiApiStandard]. */
interface AiChatClient {
    val standard: AiApiStandard

    /** Streams one model turn. Errors are thrown as [AiException]. */
    fun stream(request: AiChatRequest): Flow<AiStreamEvent>

    /** Model ids the endpoint offers, sorted. */
    suspend fun listModels(): List<String>
}

/** A readable failure talking to a provider. */
class AiException(message: String, val httpCode: Int? = null, cause: Throwable? = null) :
    IOException(message, cause)
