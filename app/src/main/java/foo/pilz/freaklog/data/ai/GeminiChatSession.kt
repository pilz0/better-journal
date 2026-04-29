package foo.pilz.freaklog.data.ai

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/** A single function call that the model wants the app to execute. */
data class FunctionCallResult(
    val name: String,
    /** Typed arguments extracted from the JSON — values are String, Number, Boolean, or null. */
    val args: Map<String, Any?>
)

/** The parsed result of one generateContent round-trip. */
sealed class ChatResponse {
    /** The model produced a final text answer; the turn is complete. */
    data class TextResponse(val text: String) : ChatResponse()

    /** The model wants the app to call one or more tools before it can finish. */
    data class FunctionCallsResponse(val calls: List<FunctionCallResult>) : ChatResponse()

    /** A network or API-level error occurred. */
    data class ErrorResponse(val message: String) : ChatResponse()
}

/**
 * Manages a multi-turn Gemini conversation with correct `thoughtSignature` handling.
 *
 * ### Why this class exists
 * Gemini 2.5 and 3 series models ("thinking models") embed a `thoughtSignature` in every
 * `functionCall` part of their responses.  The signature must be echoed back verbatim in
 * the conversation history on the next turn; omitting it causes the API to return HTTP 400.
 *
 * The deprecated `generative-ai-android` SDK (v0.9.0) deserialises responses into typed
 * Kotlin objects that have no `thoughtSignature` field, so the signature is silently
 * dropped and the next request fails.  This class bypasses the SDK, stores each content
 * block as a raw [JSONObject], and therefore preserves every field — including
 * `thoughtSignature` — automatically.
 *
 * @param apiKey         Gemini Developer API key.
 * @param modelName      Model identifier, e.g. `"gemini-2.5-flash"`.
 * @param systemInstruction  Raw JSON object: `{"parts": [{"text": "…"}]}`.
 * @param tools          Raw JSON array: `[{"functionDeclarations": […]}]`.
 */
class GeminiChatSession(
    private val apiKey: String,
    private val modelName: String,
    private val systemInstruction: JSONObject,
    private val tools: JSONArray
) {
    companion object {
        private const val TAG = "GeminiChatSession"
    }

    /** Mutable conversation history stored as raw JSON content objects. */
    private val history = mutableListOf<JSONObject>()

    /**
     * Send a user text message and return the model's response.
     *
     * Returns [ChatResponse.FunctionCallsResponse] if the model wants to invoke tools, or
     * [ChatResponse.TextResponse] once it has produced a final answer.
     */
    suspend fun sendUserMessage(text: String): ChatResponse {
        val userContent = JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().apply {
                put(JSONObject().put("text", text))
            })
        }
        history.add(userContent)
        return doGenerateContent()
    }

    /**
     * Send tool results back to the model after executing the functions it requested.
     *
     * @param results  List of `(toolName, resultJson)` pairs in the same order as the
     *                 calls from the previous [ChatResponse.FunctionCallsResponse].
     */
    suspend fun sendFunctionResults(results: List<Pair<String, JSONObject>>): ChatResponse {
        val parts = JSONArray()
        results.forEach { (name, resultJson) ->
            parts.put(
                JSONObject().apply {
                    put("functionResponse", JSONObject().apply {
                        put("name", name)
                        put("response", resultJson)
                    })
                }
            )
        }
        val functionResultContent = JSONObject().apply {
            put("role", "user")
            put("parts", parts)
        }
        history.add(functionResultContent)
        return doGenerateContent()
    }

    // -----------------------------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------------------------

    private suspend fun doGenerateContent(): ChatResponse {
        val request = buildRequest()

        val response = try {
            GeminiRestClient.generateContent(apiKey, modelName, request)
        } catch (t: Throwable) {
            Log.e(TAG, "Network error calling Gemini API", t)
            return ChatResponse.ErrorResponse(t.message ?: t::class.java.simpleName)
        }

        // API-level errors (4xx / 5xx) come back as {"error": {…}} in the response body
        val apiError = response.optJSONObject("error")
        if (apiError != null) {
            val code = apiError.optInt("code", -1)
            val msg = apiError.optString("message", "Unknown API error")
            val status = apiError.optString("status", "")
            Log.e(TAG, "Gemini API error $code $status: $msg")
            return ChatResponse.ErrorResponse("[$code $status] $msg")
        }

        val candidates = response.optJSONArray("candidates")
        val firstCandidate = candidates?.optJSONObject(0)
            ?: return ChatResponse.ErrorResponse("No candidates in response.")

        val finishReason = firstCandidate.optString("finishReason", "")
        if (finishReason == "SAFETY") {
            return ChatResponse.ErrorResponse("Response blocked by safety filters.")
        }

        val modelContent = firstCandidate.optJSONObject("content")
            ?: return ChatResponse.ErrorResponse("Empty content in response candidate.")

        // Store the model's raw content object in history — this preserves `thoughtSignature`
        // on any functionCall part so it is echoed back on the next request.
        history.add(modelContent)

        // Parse parts: collect both function calls and text fragments
        val parts = modelContent.optJSONArray("parts") ?: JSONArray()
        val functionCalls = mutableListOf<FunctionCallResult>()
        val textParts = mutableListOf<String>()

        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            val fcJson = part.optJSONObject("functionCall")
            if (fcJson != null) {
                val name = fcJson.optString("name")
                val argsJson = fcJson.optJSONObject("args") ?: JSONObject()
                functionCalls.add(FunctionCallResult(name, jsonObjectToMap(argsJson)))
            } else {
                val text = part.optString("text", "")
                if (text.isNotEmpty()) textParts.add(text)
            }
        }

        return when {
            functionCalls.isNotEmpty() -> ChatResponse.FunctionCallsResponse(functionCalls)
            textParts.isNotEmpty() -> ChatResponse.TextResponse(textParts.joinToString("\n"))
            else -> ChatResponse.ErrorResponse("The model returned an empty response.")
        }
    }

    private fun buildRequest(): JSONObject = JSONObject().apply {
        put("system_instruction", systemInstruction)
        // JSONArray(Collection) iterates and puts each JSONObject by reference; the
        // thoughtSignature fields are preserved because we never modify stored content objects.
        put("contents", JSONArray(history))
        put("tools", tools)
    }

    /**
     * Converts a [JSONObject] into a shallow [Map] with null-safe values.
     * Integers in JSON become [Int] or [Long], floats become [Double],
     * booleans become [Boolean], strings become [String], nested objects
     * stay as [JSONObject], arrays as [JSONArray], and `null` becomes `null`.
     *
     * [AiTools] already casts all values safely via `as? Number`, `as? String`,
     * etc., so nested types that the model doesn't actually send are harmless.
     */
    private fun jsonObjectToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        json.keys().forEach { key ->
            map[key] = when (val v = json.get(key)) {
                JSONObject.NULL -> null
                else -> v
            }
        }
        return map
    }
}
