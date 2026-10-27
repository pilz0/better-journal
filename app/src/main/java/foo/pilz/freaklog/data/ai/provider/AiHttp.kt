package foo.pilz.freaklog.data.ai.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStreamWriter
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Small HTTP helper shared by the provider clients. It uses [HttpURLConnection] like the rest of
 * the app, so no HTTP library is needed. Cancelling the calling coroutine disconnects the
 * connection, which unblocks a pending read.
 */
object AiHttp {
    private const val CONNECT_TIMEOUT_MS = 30_000

    // Local models can take minutes before the first token.
    private const val READ_TIMEOUT_MS = 300_000
    private const val MAX_JSON_BYTES = 8 * 1024 * 1024
    private const val MAX_ERROR_CHARS = 600

    /** Joins a base URL and a path without doubling or dropping the slash. */
    fun join(baseUrl: String, path: String): String =
        baseUrl.trim().trimEnd('/') + "/" + path.trimStart('/')

    /**
     * POSTs [body] and streams the response as server-sent events. A plain JSON response (a
     * server that ignored `stream: true`) arrives as a single event with a null name.
     */
    fun postSse(url: String, headers: Map<String, String>, body: JSONObject): Flow<SseEvent> = flow {
        val conn = open(url, "POST", headers + ("Accept" to "text/event-stream, application/json"))
        withDisconnectOnCancel(conn) {
            send(conn, body)
            val stream = responseStream(conn)
            if (conn.contentType.orEmpty().contains("json", ignoreCase = true)) {
                emit(SseEvent(null, readCapped(stream, conn.url.host)))
            } else {
                val parser = SseParser()
                stream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        parser.feed(line)?.let { emit(it) }
                    }
                }
                parser.finish()?.let { emit(it) }
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getJson(url: String, headers: Map<String, String>): JSONObject =
        get(url, headers) { stream, host ->
            val text = readCapped(stream, host)
            try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw AiException("$host returned something that isn't JSON.", cause = e)
            }
        }

    /** GETs [url] and hands the successful response body to [read]. */
    suspend fun <T> get(
        url: String,
        headers: Map<String, String>,
        read: (InputStream, String) -> T,
    ): T = withContext(Dispatchers.IO) {
        val conn = open(url, "GET", headers + ("Accept" to "application/json"))
        withDisconnectOnCancel(conn) {
            responseStream(conn).use { read(it, conn.url.host) }
        }
    }

    private fun open(url: String, method: String, headers: Map<String, String>): HttpURLConnection {
        val parsed = try {
            URL(url)
        } catch (e: java.net.MalformedURLException) {
            throw AiException("Invalid URL: $url", cause = e)
        }
        if (parsed.protocol != "http" && parsed.protocol != "https") {
            throw AiException("Only http:// and https:// URLs are supported.")
        }
        return (parsed.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
    }

    private fun send(conn: HttpURLConnection, body: JSONObject) {
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        mapNetworkErrors(conn) {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
        }
    }

    /** Returns the body of a 2xx response or throws a readable [AiException]. */
    private fun responseStream(conn: HttpURLConnection): InputStream {
        val code = mapNetworkErrors(conn) { conn.responseCode }
        if (code in 200..299) return conn.inputStream
        val body = try {
            conn.errorStream?.let { readCapped(it, conn.url.host) }.orEmpty()
        } catch (_: IOException) {
            ""
        }
        throw httpError(code, body)
    }

    /**
     * Builds the message for a failed request from the status code and whatever error text the
     * server sent. It understands OpenAI, Anthropic, Gemini and Ollama error bodies.
     */
    fun httpError(code: Int, body: String): AiException {
        val headline = when (code) {
            401, 403 -> "The API key was rejected (HTTP $code)"
            404 -> "Not found (HTTP 404). Check the base URL and the model name"
            408 -> "The server timed out (HTTP 408)"
            413 -> "The conversation is too long for this server (HTTP 413)"
            429 -> "Rate limited or out of quota (HTTP 429). Try again later"
            in 500..599 -> "The provider had an error (HTTP $code)"
            else -> "Request failed (HTTP $code)"
        }
        val detail = extractErrorMessage(body)
        val message = if (detail.isNullOrBlank()) "$headline." else "$headline: $detail"
        return AiException(message.take(MAX_ERROR_CHARS), httpCode = code)
    }

    /** Pulls the human-readable message out of the common provider error shapes. */
    fun extractErrorMessage(body: String): String? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null
        val json = try {
            if (trimmed.startsWith("[")) {
                org.json.JSONArray(trimmed).optJSONObject(0)
            } else {
                JSONObject(trimmed)
            }
        } catch (_: JSONException) {
            null
        } ?: return trimmed.takeUnless { it.startsWith("<") }?.take(MAX_ERROR_CHARS)
        val error = json.opt("error")
        return when {
            error is JSONObject -> error.optString("message").ifBlank { error.toString() }
            error is String -> error
            json.has("message") -> json.optString("message")
            json.has("detail") -> json.opt("detail").toString()
            else -> null
        }
    }

    private inline fun <T> mapNetworkErrors(conn: HttpURLConnection, block: () -> T): T {
        try {
            return block()
        } catch (e: IOException) {
            throw describeNetworkError(e, conn.url.host)
        }
    }

    private fun describeNetworkError(e: IOException, host: String): IOException = when (e) {
        is AiException -> e
        is UnknownHostException -> AiException("Can't resolve $host. Check the base URL and your connection.", cause = e)
        is ConnectException -> AiException("Can't connect to $host. Is the server running and reachable?", cause = e)
        is SocketTimeoutException -> AiException("$host didn't answer in time.", cause = e)
        is javax.net.ssl.SSLException -> AiException("Secure connection to $host failed: ${e.message}", cause = e)
        else -> e
    }

    private fun readCapped(stream: InputStream, host: String): String {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_JSON_BYTES) throw AiException("$host sent a response that is too large.")
        }
        return out.toString(Charsets.UTF_8.name())
    }

    /**
     * Runs a blocking [block] on [conn] and disconnects the connection as soon as the calling
     * coroutine is cancelled, so a read that is stuck waiting for tokens returns immediately.
     */
    private suspend fun <T> withDisconnectOnCancel(conn: HttpURLConnection, block: suspend () -> T): T =
        coroutineScope {
            val watcher = launch(Dispatchers.IO) {
                try {
                    awaitCancellation()
                } finally {
                    conn.disconnect()
                }
            }
            try {
                block()
            } catch (e: IOException) {
                // A read aborted by disconnect() surfaces as an IOException; report the cancellation instead.
                coroutineContext.ensureActive()
                throw e
            } finally {
                watcher.cancel()
                conn.disconnect()
            }
        }
}
