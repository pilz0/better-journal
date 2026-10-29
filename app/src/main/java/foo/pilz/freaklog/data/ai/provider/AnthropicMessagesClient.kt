package foo.pilz.freaklog.data.ai.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * Anthropic Messages client (`POST {base}/messages` with `stream: true`). Works with Anthropic's
 * API and with servers that implement the same protocol (Ollama, llama.cpp, LiteLLM, ...).
 *
 * The assistant's content blocks are kept in [NativeContent] and sent back unchanged, because
 * `thinking` blocks carry signatures that must be echoed verbatim on the next turn.
 */
class AnthropicMessagesClient(private val endpoint: AiEndpoint) : AiChatClient {
    override val standard = AiApiStandard.ANTHROPIC_MESSAGES

    companion object {
        const val API_VERSION = "2023-06-01"
        private const val OFFICIAL_HOST = "api.anthropic.com"
        private const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

        /** Models that accept server-side refusal fallbacks (`fallbacks: "default"`). */
        private val FALLBACK_MODELS =
            listOf("claude-fable-5-1", "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5")

        /** Block types we know how to send back. Anything else is dropped from the history. */
        private val ECHO_TYPES = setOf("text", "thinking", "redacted_thinking", "tool_use")
    }

    override fun stream(request: AiChatRequest): Flow<AiStreamEvent> = flow {
        val message = MessageAccumulator()
        AiHttp.postSse(AiHttp.join(endpoint.baseUrl, "messages"), headers(useFallbacks(request.model)), buildBody(request))
            .collect { event ->
                message.handle(JSONObject(event.data), event.event).forEach { emit(AiStreamEvent.TextDelta(it)) }
            }
        if (message.stopReason == "refusal") {
            val category = message.stopDetails?.optString("category")?.takeIf { it.isNotBlank() && it != "null" }
            val suffix = if (category != null) " ($category)" else ""
            throw AiException("The model declined to answer this request$suffix.", discardPartial = true)
        }
        message.finish()
        emit(AiStreamEvent.Completed(toAssistant(message.blocks.values.toList()), message.stopReason))
    }.flowOn(Dispatchers.Default)

    /** Rebuilds the message's content blocks from the stream events. */
    private inner class MessageAccumulator {
        val blocks = sortedMapOf<Int, JSONObject>()
        private val toolInput = mutableMapOf<Int, StringBuilder>()
        private val text = mutableMapOf<Int, StringBuilder>()
        private val thinking = mutableMapOf<Int, StringBuilder>()
        var stopReason: String? = null
        var stopDetails: JSONObject? = null

        /** Applies one event and returns the text it adds to the visible answer. */
        fun handle(data: JSONObject, eventName: String?): List<String> {
            val index = data.optInt("index")
            return when (data.optString("type", eventName.orEmpty())) {
                "error" -> throw errorFrom(data.optJSONObject("error"))
                // A server that ignored `stream: true` returns the whole message at once.
                "message" -> {
                    AiJson.objects(data.optJSONArray("content")).forEachIndexed { i, block -> blocks[i] = block }
                    stopReason = data.optString("stop_reason").ifBlank { null }
                    stopDetails = data.optJSONObject("stop_details")
                    blocks.values.filter { it.optString("type") == "text" }.map { it.optString("text") }
                }
                "content_block_start" -> {
                    val block = JSONObject(data.getJSONObject("content_block").toString())
                    blocks[index] = block
                    if (block.has("text")) text[index] = StringBuilder(block.optString("text"))
                    if (block.has("thinking")) thinking[index] = StringBuilder(block.optString("thinking"))
                    if (block.optString("type") == "tool_use") toolInput[index] = StringBuilder()
                    listOfNotNull(block.optString("text").takeIf { block.optString("type") == "text" && it.isNotEmpty() })
                }
                "content_block_delta" -> applyDelta(index, data.optJSONObject("delta"))
                "content_block_stop" -> {
                    finishBlock(index)
                    emptyList()
                }
                "message_delta" -> {
                    data.optJSONObject("delta")?.let { delta ->
                        delta.optString("stop_reason").takeIf { it.isNotBlank() && it != "null" }?.let { stopReason = it }
                        delta.optJSONObject("stop_details")?.let { stopDetails = it }
                    }
                    emptyList()
                }
                else -> emptyList()
            }
        }

        private fun finishBlock(index: Int) {
            text.remove(index)?.let { blocks[index]?.put("text", it.toString()) }
            thinking.remove(index)?.let { blocks[index]?.put("thinking", it.toString()) }
            toolInput.remove(index)?.let { blocks[index]?.put("input", AiJson.parseArguments(it.toString())) }
        }

        fun finish() = blocks.keys.forEach(::finishBlock)

        private fun applyDelta(index: Int, delta: JSONObject?): List<String> {
            val block = blocks[index] ?: return emptyList()
            when (delta?.optString("type")) {
                "text_delta" -> {
                    val text = delta.optString("text")
                    this.text.getOrPut(index) { StringBuilder(block.optString("text")) }.append(text)
                    return listOfNotNull(text.takeIf { it.isNotEmpty() })
                }
                "input_json_delta" -> toolInput[index]?.append(delta.optString("partial_json"))
                "thinking_delta" -> thinking.getOrPut(index) {
                    StringBuilder(block.optString("thinking"))
                }.append(delta.optString("thinking"))
                "signature_delta" -> block.put("signature", delta.optString("signature"))
            }
            return emptyList()
        }
    }

    override suspend fun listModels(): List<String> {
        val json = AiHttp.getJson(AiHttp.join(endpoint.baseUrl, "models?limit=1000"), headers(fallbacks = false))
        return AiJson.objects(json.optJSONArray("data"))
            .map { it.optString("id") }
            .filter { it.isNotBlank() }
            .sorted()
    }

    private fun isOfficialEndpoint(): Boolean =
        runCatching { URI(endpoint.baseUrl.trim()).host == OFFICIAL_HOST }.getOrDefault(false)

    /**
     * On Anthropic's own API, let a refused request be retried on a fallback model server-side.
     * Self-hosted servers don't know the beta, so it is only sent to api.anthropic.com.
     */
    private fun useFallbacks(model: String): Boolean = isOfficialEndpoint() && model in FALLBACK_MODELS

    private fun headers(fallbacks: Boolean): Map<String, String> = buildMap {
        put("anthropic-version", API_VERSION)
        if (endpoint.apiKey.isNotBlank()) put("x-api-key", endpoint.apiKey)
        if (fallbacks) put("anthropic-beta", FALLBACK_BETA)
    }

    internal fun buildBody(request: AiChatRequest): JSONObject = JSONObject().apply {
        put("model", request.model)
        put("max_tokens", request.maxOutputTokens)
        put("stream", true)
        put("system", request.system)
        put("messages", JSONArray().apply { request.messages.forEach { put(toMessage(it)) } })
        if (request.tools.isNotEmpty()) {
            put(
                "tools",
                JSONArray().apply {
                    request.tools.forEach { tool ->
                        put(
                            JSONObject()
                                .put("name", tool.name)
                                .put("description", tool.description)
                                .put("input_schema", tool.parameters),
                        )
                    }
                },
            )
        }
        if (useFallbacks(request.model)) put("fallbacks", "default")
    }

    private fun toMessage(message: AiMessage): JSONObject = when (message) {
        is AiMessage.User -> JSONObject()
            .put("role", "user")
            .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", message.text)))

        is AiMessage.Assistant -> {
            val content = message.native?.takeIf { it.standard == standard }?.json?.optJSONArray("content")
                ?: JSONArray().apply {
                    if (message.text.isNotBlank()) put(JSONObject().put("type", "text").put("text", message.text))
                    message.toolCalls.forEach { call ->
                        put(
                            JSONObject()
                                .put("type", "tool_use")
                                .put("id", call.id)
                                .put("name", call.name)
                                .put("input", AiJson.fromMap(call.args)),
                        )
                    }
                }
            JSONObject().put("role", "assistant").put("content", content)
        }

        is AiMessage.ToolResults -> JSONObject().put("role", "user").put(
            "content",
            JSONArray().apply {
                message.results.forEach { result ->
                    val block = JSONObject()
                        .put("type", "tool_result")
                        .put("tool_use_id", result.callId)
                        .put("content", result.result.toString())
                    if (result.isError) block.put("is_error", true)
                    put(block)
                }
            },
        )
    }

    /**
     * Builds the assistant message from the streamed blocks. After a server-side fallback, the
     * declined attempt's thinking and tool_use blocks before the last `fallback` marker are not
     * echoed back; its text is kept.
     */
    private fun toAssistant(all: List<JSONObject>): AiMessage.Assistant {
        val boundary = all.indexOfLast { it.optString("type") == "fallback" }
        val kept = all.filterIndexed { index, block ->
            val type = block.optString("type")
            type in ECHO_TYPES && (index > boundary || type == "text")
        }
        val text = kept.filter { it.optString("type") == "text" }.joinToString("") { it.optString("text") }
        val calls = kept.filter { it.optString("type") == "tool_use" }.map { block ->
            AiToolCall(
                id = block.optString("id"),
                name = block.optString("name"),
                args = AiJson.toMap(block.optJSONObject("input") ?: JSONObject()),
            )
        }
        val native = NativeContent(standard, JSONObject().put("content", JSONArray(kept)))
        return AiMessage.Assistant(text, calls, native)
    }

    private fun errorFrom(error: JSONObject?): AiException {
        val type = error?.optString("type").orEmpty()
        val message = error?.optString("message").orEmpty().ifBlank { "Unknown error" }
        return when (type) {
            "overloaded_error" -> AiException("The provider is overloaded. Try again in a moment: $message")
            "rate_limit_error" -> AiException("Rate limited. Try again later: $message")
            else -> AiException(if (type.isBlank()) message else "$type: $message")
        }
    }
}
