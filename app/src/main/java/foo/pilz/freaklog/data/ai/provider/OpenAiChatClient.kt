package foo.pilz.freaklog.data.ai.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * OpenAI Chat Completions client (`POST {base}/chat/completions` with `stream: true`). It also
 * works with the many servers that copy this API: OpenRouter, Mistral, Groq, DeepSeek, Ollama,
 * LM Studio, llama.cpp, vLLM and LiteLLM.
 */
class OpenAiChatClient(private val endpoint: AiEndpoint) : AiChatClient {
    override val standard = AiApiStandard.OPENAI_CHAT

    /** A tool call being assembled from streamed fragments. */
    private class PendingCall {
        var id: String? = null
        var name: String = ""
        val arguments = StringBuilder()
    }

    override fun stream(request: AiChatRequest): Flow<AiStreamEvent> = flow {
        val text = StringBuilder()
        val calls = sortedMapOf<Int, PendingCall>()
        var finishReason: String? = null
        AiHttp.postSse(AiHttp.join(endpoint.baseUrl, "chat/completions"), headers(), buildBody(request))
            .collect { event ->
                val data = event.data.trim()
                if (data == "[DONE]" || data.isEmpty()) return@collect
                val chunk = JSONObject(data)
                chunk.opt("error")?.let { throw AiException(errorMessage(it)) }
                val choice = chunk.optJSONArray("choices")?.optJSONObject(0) ?: return@collect
                // `delta` while streaming; `message` when the server ignored `stream: true`.
                val delta = choice.optJSONObject("delta") ?: choice.optJSONObject("message") ?: JSONObject()
                val content = delta.opt("content")
                if (content is String && content.isNotEmpty()) {
                    text.append(content)
                    emit(AiStreamEvent.TextDelta(content))
                }
                AiJson.objects(delta.optJSONArray("tool_calls")).forEachIndexed { position, fragment ->
                    val pending = calls.getOrPut(fragment.optInt("index", position)) { PendingCall() }
                    fragment.optString("id").takeIf { it.isNotBlank() }?.let { pending.id = it }
                    val function = fragment.optJSONObject("function") ?: return@forEachIndexed
                    function.optString("name").takeIf { it.isNotBlank() }?.let { pending.name = it }
                    when (val arguments = function.opt("arguments")) {
                        is String -> pending.arguments.append(arguments)
                        is JSONObject -> pending.arguments.append(arguments.toString())
                    }
                }
                choice.optString("finish_reason").takeIf { it.isNotBlank() && it != "null" }?.let { finishReason = it }
            }
        val toolCalls = calls.values.filter { it.name.isNotBlank() }.map { pending ->
            AiToolCall(
                id = pending.id ?: "call_${UUID.randomUUID().toString().replace("-", "").take(24)}",
                name = pending.name,
                args = AiJson.toMap(AiJson.parseArguments(pending.arguments.toString())),
            )
        }
        if (finishReason == "content_filter" && text.isEmpty() && toolCalls.isEmpty()) {
            throw AiException("The provider's content filter blocked the response.")
        }
        emit(AiStreamEvent.Completed(AiMessage.Assistant(text.toString(), toolCalls), finishReason))
    }

    override suspend fun listModels(): List<String> {
        val json = AiHttp.getJson(AiHttp.join(endpoint.baseUrl, "models"), headers())
        return AiJson.objects(json.optJSONArray("data") ?: json.optJSONArray("models"))
            .map { it.optString("id").ifBlank { it.optString("name") } }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    /** Local servers (Ollama, LM Studio) need no key, so the header is only sent when there is one. */
    private fun headers(): Map<String, String> =
        if (endpoint.apiKey.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer ${endpoint.apiKey}")

    internal fun buildBody(request: AiChatRequest): JSONObject = JSONObject().apply {
        put("model", request.model)
        put("stream", true)
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", request.system))
        request.messages.forEach { message -> toMessages(message).forEach { messages.put(it) } }
        put("messages", messages)
        if (request.tools.isNotEmpty()) {
            put(
                "tools",
                JSONArray().apply {
                    request.tools.forEach { tool ->
                        put(
                            JSONObject().put("type", "function").put(
                                "function",
                                JSONObject()
                                    .put("name", tool.name)
                                    .put("description", tool.description)
                                    .put("parameters", tool.parameters),
                            ),
                        )
                    }
                },
            )
        }
    }

    private fun toMessages(message: AiMessage): List<JSONObject> = when (message) {
        is AiMessage.User -> listOf(JSONObject().put("role", "user").put("content", message.text))

        is AiMessage.Assistant -> {
            val json = JSONObject().put("role", "assistant")
            if (message.toolCalls.isEmpty()) {
                json.put("content", message.text)
            } else {
                json.put("content", message.text.ifBlank { null } ?: JSONObject.NULL)
                json.put(
                    "tool_calls",
                    JSONArray().apply {
                        message.toolCalls.forEach { call ->
                            put(
                                JSONObject()
                                    .put("id", call.id)
                                    .put("type", "function")
                                    .put(
                                        "function",
                                        JSONObject()
                                            .put("name", call.name)
                                            .put("arguments", AiJson.fromMap(call.args).toString()),
                                    ),
                            )
                        }
                    },
                )
            }
            listOf(json)
        }

        is AiMessage.ToolResults -> message.results.map { result ->
            JSONObject()
                .put("role", "tool")
                .put("tool_call_id", result.callId)
                .put("content", result.result.toString())
        }
    }

    private fun errorMessage(error: Any): String = when (error) {
        is JSONObject -> error.optString("message").ifBlank { error.toString() }
        else -> error.toString()
    }
}
