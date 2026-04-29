package foo.pilz.freaklog.data.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal Gemini REST API client that calls the v1beta generateContent endpoint directly.
 *
 * The deprecated `generative-ai-android` SDK (v0.9.0) strips the `thoughtSignature` field
 * that Gemini 2.5 and 3 series models attach to every `functionCall` part.  When that
 * signature is absent from the conversation history the API returns HTTP 400
 * ("Function call is missing a thought_signature").  By parsing the raw JSON ourselves
 * we preserve every field — including `thoughtSignature` — and can echo it back verbatim
 * in subsequent turns.
 */
object GeminiRestClient {

    private const val TAG = "GeminiRestClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    suspend fun generateContent(
        apiKey: String,
        modelName: String,
        requestBody: JSONObject
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = URL("$BASE_URL/${modelName}:generateContent")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            // Pass the API key as a request header rather than a URL query parameter to avoid
            // accidental exposure in HTTP access logs or proxy traces.
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 180_000

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody.toString())
            }

            val responseCode = conn.responseCode
            val responseText = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            } else {
                Log.w(TAG, "HTTP $responseCode from Gemini API")
                conn.errorStream?.bufferedReader(Charsets.UTF_8)?.readText()
                    ?: """{"error":{"code":$responseCode,"message":"HTTP $responseCode","status":"HTTP_ERROR"}}"""
            }

            JSONObject(responseText)
        } finally {
            conn.disconnect()
        }
    }
}
