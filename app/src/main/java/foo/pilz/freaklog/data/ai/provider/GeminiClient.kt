package foo.pilz.freaklog.data.ai.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Google Gemini `generateContent` client (`v1beta`), streamed through `streamGenerateContent?alt=sse`.
 *
 * Gemini 2.5 and 3 models attach a `thoughtSignature` to `functionCall` parts and reject the next
 * request when it is missing ("Function call is missing a thought_signature"). The archived
 * `generative-ai-android` SDK dropped it. This client stores the model's raw parts in
 * [NativeContent] and sends them back unchanged.
 */
class GeminiClient(private val endpoint: AiEndpoint) : AiChatClient {
    override val standard = AiApiStandard.GEMINI

    override fun stream(request: AiChatRequest): Flow<AiStreamEvent> = flow {
        val model = request.model.removePrefix("models/")
        val url = AiHttp.join(endpoint.baseUrl, "models/$model:streamGenerateContent?alt=sse")
        val parts = JSONArray()
        var finishReason: String? = null
        AiHttp.postSse(url, headers(), buildBody(request)).collect { event ->
            val chunk = JSONObject(event.data)
            chunk.optJSONObject("error")?.let { throw apiError(it) }
            chunk.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotBlank() }?.let {
                throw AiException("Gemini blocked the prompt ($it).")
            }
            val candidate = chunk.optJSONArray("candidates")?.optJSONObject(0) ?: return@collect
            candidate.optString("finishReason").takeIf { it.isNotBlank() }?.let { finishReason = it }
            AiJson.objects(candidate.optJSONObject("content")?.optJSONArray("parts")).forEach { part ->
                parts.put(part)
                val text = part.optString("text")
                if (text.isNotEmpty() && !part.optBoolean("thought")) emit(AiStreamEvent.TextDelta(text))
            }
        }
        if (finishReason == "SAFETY" || finishReason == "PROHIBITED_CONTENT") {
            throw AiException("Gemini blocked the response ($finishReason).")
        }
        emit(AiStreamEvent.Completed(parseAssistant(parts), finishReason))
    }

    override suspend fun listModels(): List<String> {
        val json = AiHttp.getJson(AiHttp.join(endpoint.baseUrl, "models?pageSize=1000"), headers())
        return AiJson.objects(json.optJSONArray("models"))
            .filter { model ->
                val methods = model.optJSONArray("supportedGenerationMethods") ?: return@filter true
                (0 until methods.length()).any { methods.optString(it) == "generateContent" }
            }
            .map { it.optString("name").removePrefix("models/") }
            .filter { it.isNotBlank() }
            .sorted()
    }

    private fun headers(): Map<String, String> =
        if (endpoint.apiKey.isBlank()) emptyMap() else mapOf("x-goog-api-key" to endpoint.apiKey)

    internal fun buildBody(request: AiChatRequest): JSONObject = JSONObject().apply {
        put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", request.system))))
        put("contents", JSONArray().apply { request.messages.forEach { put(toContent(it)) } })
        if (request.tools.isNotEmpty()) {
            val decls = JSONArray()
            request.tools.forEach { tool ->
                decls.put(
                    JSONObject()
                        .put("name", tool.name)
                        .put("description", tool.description)
                        .put("parameters", AiJson.upperCaseSchemaTypes(tool.parameters)),
                )
            }
            put("tools", JSONArray().put(JSONObject().put("functionDeclarations", decls)))
        }
    }

    private fun toContent(message: AiMessage): JSONObject = when (message) {
        is AiMessage.User -> JSONObject()
            .put("role", "user")
            .put("parts", JSONArray().put(JSONObject().put("text", message.text)))

        is AiMessage.Assistant -> message.native?.takeIf { it.standard == standard }?.json
            ?: JSONObject().put("role", "model").put(
                "parts",
                JSONArray().apply {
                    if (message.text.isNotBlank()) put(JSONObject().put("text", message.text))
                    message.toolCalls.forEach { call ->
                        val fc = JSONObject().put("name", call.name).put("args", AiJson.fromMap(call.args))
                        call.id?.let { fc.put("id", it) }
                        put(JSONObject().put("functionCall", fc))
                    }
                },
            )

        is AiMessage.ToolResults -> JSONObject().put("role", "user").put(
            "parts",
            JSONArray().apply {
                message.results.forEach { result ->
                    val response = JSONObject().put("name", result.name).put("response", result.result)
                    result.callId?.let { response.put("id", it) }
                    put(JSONObject().put("functionResponse", response))
                }
            },
        )
    }

    private fun parseAssistant(parts: JSONArray): AiMessage.Assistant {
        val text = StringBuilder()
        val calls = mutableListOf<AiToolCall>()
        AiJson.objects(parts).forEach { part ->
            val fc = part.optJSONObject("functionCall")
            when {
                fc != null -> calls += AiToolCall(
                    id = fc.optString("id").ifBlank { null },
                    name = fc.optString("name"),
                    args = AiJson.toMap(fc.optJSONObject("args") ?: JSONObject()),
                )
                !part.optBoolean("thought") -> text.append(part.optString("text"))
            }
        }
        val native = JSONObject().put("role", "model").put("parts", parts)
        return AiMessage.Assistant(text.toString(), calls, NativeContent(standard, native))
    }

    private fun apiError(error: JSONObject): AiException {
        val code = error.optInt("code", -1)
        return if (code > 0) {
            AiHttp.httpError(code, JSONObject().put("error", error).toString())
        } else {
            AiException(error.optString("message", "Gemini returned an error."))
        }
    }
}
