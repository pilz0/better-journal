package foo.pilz.freaklog.data.ai.web

import foo.pilz.freaklog.data.ai.provider.AiException
import foo.pilz.freaklog.data.ai.provider.AiHttp
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

data class WebSearchResult(val title: String, val url: String, val snippet: String)

data class WebPageContent(
    val finalUrl: String,
    val title: String,
    val contentType: String,
    val text: String,
    val byteCapped: Boolean,
)

/**
 * HTTP client for web search (DuckDuckGo, SearXNG, Brave Search) and public webpage fetching. Uses
 * [HttpURLConnection] with cancellation support, strict size caps, and redirect re-validation.
 */
@Singleton
class AiWebClient
internal constructor(
    private val urlValidator: (String) -> URL,
    private val duckDuckGoUrl: String,
    private val braveApiUrl: String,
) {
  @Inject
  constructor() : this(WebUrlValidator::validatePublicHttpsUrl, DEFAULT_DDG_URL, DEFAULT_BRAVE_URL)

  companion object {
    private const val DEFAULT_DDG_URL = "https://html.duckduckgo.com/html/"
    private const val DEFAULT_BRAVE_URL = "https://api.search.brave.com/res/v1/web/search"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val MAX_REDIRECTS = 5
    internal const val MAX_FETCH_BYTES = 2 * 1024 * 1024
    internal const val MAX_SEARCH_BYTES = 2 * 1024 * 1024
    private const val CACHE_TTL_MS = 10L * 60 * 1000
    private const val MAX_CACHED_PAGES = 16
    private const val USER_AGENT = "FreakLog-Android"
    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    private val BOT_MARKERS =
        listOf("anomaly-modal", "bots use DuckDuckGo", "challenge-form", "g-recaptcha")
  }

  private class CachedPage(val content: WebPageContent, val fetchedAt: Long)

  private val pageCache =
      object : LinkedHashMap<String, CachedPage>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedPage>) =
            size > MAX_CACHED_PAGES
      }

  suspend fun searchDuckDuckGo(query: String, limit: Int): List<WebSearchResult> {
    val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
    val url = "$duckDuckGoUrl?q=$encoded"
    val headers =
        mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "text/html,application/xhtml+xml",
            "Accept-Language" to "en-US,en;q=0.9",
        )
    val response = getBytes(url, headers, MAX_SEARCH_BYTES)
    val html = checkDuckDuckGoResponse(response)
    return parseDuckDuckGoHtml(html, limit)
  }

  private fun checkDuckDuckGoResponse(response: HttpBytesResponse): String {
    val html = response.text()
    val error =
        when {
          response.code == 202 || response.code == 403 || response.code == 429 ->
              AiException(duckDuckGoBlockedMessage())
          response.code !in 200..299 -> AiHttp.httpError(response.code, html)
          BOT_MARKERS.any { html.contains(it, ignoreCase = true) } ->
              AiException(duckDuckGoBlockedMessage())
          else -> null
        }
    if (error != null) throw error
    return html
  }

  suspend fun searchSearxng(baseUrl: String, query: String, limit: Int): List<WebSearchResult> {
    val normalized =
        WebUrlValidator.normalizeSearxngUrl(baseUrl)
            ?: throw AiException("SearXNG URL is missing or invalid.")
    val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
    val url = "$normalized/search?q=$encoded&format=json"
    val headers = mapOf("User-Agent" to USER_AGENT, "Accept" to "application/json")
    val response = getBytes(url, headers, MAX_SEARCH_BYTES)
    val json =
        parseJsonResponse(
            response = response,
            forbiddenMessage =
                "SearXNG returned HTTP 403. Make sure JSON format is enabled on that instance (formats: [html, json]).",
            invalidJsonMessage =
                "SearXNG did not return JSON. Make sure JSON format is enabled on that instance.",
        )
    return parseJsonSearchResults(json.optJSONArray("results"), limit, snippetKey = "content")
  }

  suspend fun searchBrave(apiKey: String, query: String, limit: Int): List<WebSearchResult> {
    val key = apiKey.trim()
    if (key.isEmpty()) throw AiException("Brave Search API key is missing.")
    val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
    val count = limit.coerceIn(1, 20)
    val url = "$braveApiUrl?q=$encoded&count=$count"
    val headers =
        mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "application/json",
            "X-Subscription-Token" to key,
        )
    val response = getBytes(url, headers, MAX_SEARCH_BYTES)
    val json =
        parseJsonResponse(
            response = response,
            unauthorizedMessage = "Brave Search rejected the API key (HTTP ${response.code}).",
            forbiddenMessage = "Brave Search rejected the API key (HTTP ${response.code}).",
            rateLimitMessage = "Brave Search rate limit reached (HTTP 429). Try again later.",
            invalidJsonMessage = "Brave Search returned an unexpected response.",
        )
    return parseJsonSearchResults(
        json.optJSONObject("web")?.optJSONArray("results"),
        limit,
        snippetKey = "description",
    )
  }

  private fun parseJsonResponse(
      response: HttpBytesResponse,
      unauthorizedMessage: String? = null,
      forbiddenMessage: String? = null,
      rateLimitMessage: String? = null,
      invalidJsonMessage: String,
  ): JSONObject {
    val httpFailure =
        when {
          response.code == 401 && unauthorizedMessage != null -> AiException(unauthorizedMessage)
          response.code == 403 && forbiddenMessage != null -> AiException(forbiddenMessage)
          response.code == 429 && rateLimitMessage != null -> AiException(rateLimitMessage)
          response.code !in 200..299 -> AiHttp.httpError(response.code, response.text())
          else -> null
        }
    if (httpFailure != null) throw httpFailure
    val body = response.text()
    AiHttp.validateJsonNesting(body)
    return try {
      JSONObject(body)
    } catch (e: JSONException) {
      throw AiException(invalidJsonMessage, cause = e)
    }
  }

  private fun parseJsonSearchResults(
      array: org.json.JSONArray?,
      limit: Int,
      snippetKey: String,
  ): List<WebSearchResult> {
    if (array == null) return emptyList()
    return (0 until array.length())
        .asSequence()
        .mapNotNull { array.optJSONObject(it) }
        .mapNotNull { item ->
          val resultUrl = item.optString("url").trim()
          if (resultUrl.isEmpty()) {
            null
          } else {
            val title = HtmlTextExtractor.stripTags(item.optString("title")).ifEmpty { resultUrl }
            val snippet = HtmlTextExtractor.stripTags(item.optString(snippetKey))
            WebSearchResult(title = title, url = resultUrl, snippet = snippet)
          }
        }
        .take(limit)
        .toList()
  }

  suspend fun fetchPage(rawUrl: String): WebPageContent {
    val key = rawUrl.trim()
    val now = System.currentTimeMillis()
    synchronized(pageCache) { pageCache[key]?.takeIf { now - it.fetchedAt < CACHE_TTL_MS } }
        ?.let {
          return it.content
        }

    var currentUrl = urlValidator(key)
    val fetched =
        withContext(Dispatchers.IO) {
          var result: WebPageContent? = null
          for (hop in 0..MAX_REDIRECTS) {
            val (nextUrl, page) = executeFetchHop(currentUrl, hop)
            if (page != null) {
              result = page
              break
            }
            if (nextUrl != null) {
              currentUrl = nextUrl
            }
          }
          result ?: throw AiException("Too many redirects.")
        }

    synchronized(pageCache) { pageCache[key] = CachedPage(fetched, now) }
    return fetched
  }

  private suspend fun executeFetchHop(
      currentUrl: URL,
      hop: Int,
  ): Pair<URL?, WebPageContent?> {
    val conn = openConnection(currentUrl, pageFetchHeaders())
    return AiHttp.withDisconnectOnCancel(conn) {
      val code = AiHttp.mapNetworkErrors(conn) { conn.responseCode }
      when {
        code in REDIRECT_CODES -> resolveRedirect(conn, currentUrl, hop) to null
        code !in 200..299 -> throw AiHttp.httpError(code, readErrorText(conn))
        else -> null to readPageContent(conn, currentUrl)
      }
    }
  }

  private fun resolveRedirect(conn: HttpURLConnection, currentUrl: URL, hop: Int): URL {
    if (hop == MAX_REDIRECTS) throw AiException("Too many redirects.")
    val location =
        conn.getHeaderField("Location")?.takeIf { it.isNotBlank() }
            ?: throw AiException("Redirect response is missing Location header.")
    return urlValidator(URL(currentUrl, location).toString())
  }

  private fun readErrorText(conn: HttpURLConnection): String =
      try {
        conn.errorStream
            ?.use { readCappedBytes(it, MAX_SEARCH_BYTES).first }
            ?.toString(Charsets.UTF_8)
            .orEmpty()
      } catch (_: IOException) {
        ""
      }

  private fun readPageContent(
      conn: HttpURLConnection,
      currentUrl: URL,
  ): WebPageContent {
    val rawContentType = conn.contentType.orEmpty()
    val mime = rawContentType.substringBefore(';').trim().lowercase(Locale.ROOT)
    if (!isSupportedTextMime(mime)) {
      throw AiException("Unsupported content type '$mime'. Only text and HTML pages can be read.")
    }
    val (bytes, capped) =
        AiHttp.mapNetworkErrors(conn) {
          conn.inputStream.use { readCappedBytes(it, MAX_FETCH_BYTES) }
        }
    val rawText = String(bytes, parseCharset(rawContentType))
    val isHtml =
        mime == "text/html" ||
            mime == "application/xhtml+xml" ||
            (mime.isEmpty() && rawText.trimStart().startsWith("<"))
    val (title, text) =
        if (isHtml) {
          HtmlTextExtractor.extract(rawText).let { it.title to it.text }
        } else {
          "" to rawText.trim()
        }
    return WebPageContent(
        finalUrl = currentUrl.toString(),
        title = title,
        contentType = mime.ifEmpty { if (isHtml) "text/html" else "text/plain" },
        text = text,
        byteCapped = capped,
    )
  }

  private fun pageFetchHeaders(): Map<String, String> =
      mapOf(
          "User-Agent" to USER_AGENT,
          "Accept" to
              "text/html,application/xhtml+xml,text/plain,text/markdown,application/json,application/xml;q=0.9",
          "Accept-Language" to "en-US,en;q=0.9",
      )

  private class HttpBytesResponse(
      val code: Int,
      val contentType: String,
      val bytes: ByteArray,
  ) {
    fun text(): String = String(bytes, parseCharset(contentType))
  }

  private suspend fun getBytes(
      url: String,
      headers: Map<String, String>,
      maxBytes: Int,
  ): HttpBytesResponse =
      withContext(Dispatchers.IO) {
        val conn = openConnection(URL(url), headers).apply { instanceFollowRedirects = true }
        AiHttp.withDisconnectOnCancel(conn) {
          val code = AiHttp.mapNetworkErrors(conn) { conn.responseCode }
          val stream =
              AiHttp.mapNetworkErrors(conn) {
                if (code in 200..299) conn.inputStream else conn.errorStream
              }
          val bytes = stream?.use { readCappedBytes(it, maxBytes).first } ?: ByteArray(0)
          HttpBytesResponse(code, conn.contentType.orEmpty(), bytes)
        }
      }

  private fun openConnection(url: URL, headers: Map<String, String>): HttpURLConnection =
      (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        instanceFollowRedirects = false
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
        headers.forEach { (k, v) -> setRequestProperty(k, v) }
      }

  private fun readCappedBytes(stream: InputStream, maxBytes: Int): Pair<ByteArray, Boolean> {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var capped = false
    var done = false
    while (!done) {
      val remaining = maxBytes - out.size()
      if (remaining <= 0) {
        if (stream.read() >= 0) capped = true
        done = true
      } else {
        val n = stream.read(buffer, 0, minOf(buffer.size, remaining))
        if (n < 0) done = true else out.write(buffer, 0, n)
      }
    }
    return out.toByteArray() to capped
  }

  internal fun parseDuckDuckGoHtml(html: String, limit: Int): List<WebSearchResult> {
    val results = mutableListOf<WebSearchResult>()
    var pos = 0
    while (pos < html.length && results.size < limit) {
      val openStart = findNextAnchorStart(html, pos)
      val openEnd = openStart?.let { html.indexOf('>', it).takeIf { end -> end >= 0 } }
      if (openStart == null || openEnd == null) {
        pos = html.length
      } else {
        val attrs = parseAttributes(html.substring(openStart + 2, openEnd))
        pos = openEnd + 1
        val classes = attrs["class"].orEmpty().split(' ')
        if ("result__a" in classes) {
          val (result, nextPos) = parseDuckDuckGoAnchorResult(html, pos, attrs["href"].orEmpty())
          pos = nextPos
          if (result != null) results += result
        }
      }
    }
    return results
  }

  private fun parseDuckDuckGoAnchorResult(
      html: String,
      contentStart: Int,
      hrefAttr: String,
  ): Pair<WebSearchResult?, Int> {
    val rawHref = HtmlTextExtractor.decodeEntities(hrefAttr).trim()
    val resolvedUrl = unwrapDuckDuckGoUrl(rawHref)
    if (resolvedUrl.isBlank() || isDuckDuckGoAd(rawHref, resolvedUrl)) {
      return null to contentStart
    }
    var pos = contentStart
    val closeAnchor = html.indexOf("</a>", pos, ignoreCase = true)
    val titleHtml = if (closeAnchor >= 0) html.substring(pos, closeAnchor) else ""
    if (closeAnchor >= 0) pos = closeAnchor + 4
    val title = HtmlTextExtractor.stripTags(titleHtml).ifEmpty { resolvedUrl }
    val nextResultPos = findNextAnchorWithClass(html, pos, "result__a") ?: html.length
    val snippet = extractSnippet(html, pos, nextResultPos)
    return WebSearchResult(title = title, url = resolvedUrl, snippet = snippet) to pos
  }

  private fun findNextAnchorStart(html: String, fromIndex: Int): Int? {
    var pos = fromIndex
    while (pos < html.length) {
      val idx = html.indexOf("<a", pos, ignoreCase = true)
      if (idx < 0) return null
      val after = html.getOrNull(idx + 2)
      if (after != null && after.isWhitespace()) return idx
      pos = idx + 2
    }
    return null
  }

  private fun findNextAnchorWithClass(html: String, fromIndex: Int, targetClass: String): Int? {
    var pos = fromIndex
    while (pos < html.length) {
      val openStart = findNextAnchorStart(html, pos) ?: return null
      val openEnd = html.indexOf('>', openStart)
      if (openEnd < 0) return null
      val attrs = parseAttributes(html.substring(openStart + 2, openEnd))
      val classes = attrs["class"].orEmpty().split(' ')
      if (targetClass in classes) return openStart
      pos = openEnd + 1
    }
    return null
  }

  private fun extractSnippet(html: String, fromIndex: Int, untilIndex: Int): String {
    val marker = html.indexOf("result__snippet", fromIndex, ignoreCase = false)
    if (marker < 0 || marker >= untilIndex) return ""
    val openEnd = html.indexOf('>', marker)
    if (openEnd < 0 || openEnd >= untilIndex) return ""
    val tagStart = html.lastIndexOf('<', marker)
    val tagName =
        if (tagStart >= fromIndex) {
          html
              .substring(tagStart + 1, marker)
              .trim()
              .takeWhile { !it.isWhitespace() }
              .lowercase(Locale.ROOT)
        } else {
          ""
        }
    val closeTag = if (tagName.isNotEmpty()) "</$tagName>" else "</a>"
    val closeIdx =
        html.indexOf(closeTag, openEnd + 1, ignoreCase = true).let {
          if (it in (openEnd + 1) until untilIndex) it else untilIndex
        }
    return HtmlTextExtractor.stripTags(html.substring(openEnd + 1, closeIdx))
  }

  private fun parseAttributes(tagBody: String): Map<String, String> {
    val map = linkedMapOf<String, String>()
    var i = skipAttrPadding(tagBody, 0)
    while (i < tagBody.length) {
      val nameStart = i
      while (i < tagBody.length && !tagBody[i].isWhitespace() && tagBody[i] != '=') i++
      val name = tagBody.substring(nameStart, i).lowercase(Locale.ROOT)
      while (i < tagBody.length && tagBody[i].isWhitespace()) i++
      if (i < tagBody.length && tagBody[i] == '=') {
        val (value, nextIndex) = readAttributeValue(tagBody, i + 1)
        i = nextIndex
        if (name.isNotEmpty()) map[name] = value
      }
      i = skipAttrPadding(tagBody, i)
    }
    return map
  }

  private fun skipAttrPadding(tagBody: String, start: Int): Int {
    var i = start
    while (i < tagBody.length && (tagBody[i].isWhitespace() || tagBody[i] == '/')) i++
    return i
  }

  private fun readAttributeValue(tagBody: String, start: Int): Pair<String, Int> {
    var i = start
    while (i < tagBody.length && tagBody[i].isWhitespace()) i++
    if (i >= tagBody.length) return "" to i
    return if (tagBody[i] == '"' || tagBody[i] == '\'') {
      val quote = tagBody[i++]
      val valStart = i
      while (i < tagBody.length && tagBody[i] != quote) i++
      val raw = tagBody.substring(valStart, i)
      raw to if (i < tagBody.length) i + 1 else i
    } else {
      val valStart = i
      while (i < tagBody.length && !tagBody[i].isWhitespace()) i++
      tagBody.substring(valStart, i) to i
    }
  }

  private fun unwrapDuckDuckGoUrl(rawHref: String): String {
    if (rawHref.isEmpty()) return ""
    val normalized =
        when {
          rawHref.startsWith("//") -> "https:$rawHref"
          rawHref.startsWith("/") -> "https://duckduckgo.com$rawHref"
          else -> rawHref
        }
    val uri = runCatching { URI(normalized) }.getOrNull() ?: return normalized
    if (uri.host.orEmpty().endsWith("duckduckgo.com", ignoreCase = true) && uri.path == "/l/") {
      val query = uri.rawQuery.orEmpty()
      for (part in query.split('&')) {
        val eq = part.indexOf('=')
        if (eq > 0 && part.substring(0, eq) == "uddg") {
          val target = part.substring(eq + 1)
          return runCatching { URLDecoder.decode(target, Charsets.UTF_8.name()) }
              .getOrDefault(normalized)
        }
      }
    }
    return normalized
  }

  private fun isDuckDuckGoAd(rawHref: String, resolvedUrl: String): Boolean =
      rawHref.contains("duckduckgo.com/y.js", ignoreCase = true) ||
          resolvedUrl.contains("duckduckgo.com/y.js", ignoreCase = true) ||
          rawHref.contains("ad_provider=", ignoreCase = true)

  private fun isSupportedTextMime(mime: String): Boolean =
      mime.isEmpty() ||
          mime.startsWith("text/") ||
          mime == "application/xhtml+xml" ||
          mime == "application/json" ||
          mime == "application/xml" ||
          mime == "application/ld+json" ||
          mime.endsWith("+json") ||
          mime.endsWith("+xml")

  private fun duckDuckGoBlockedMessage(): String =
      "DuckDuckGo temporarily blocked automated search requests. Try again later, " +
          "or switch to SearXNG or Brave Search in Settings → AI assistant."
}

private fun parseCharset(contentType: String): Charset {
  val parts = contentType.split(';')
  for (part in parts) {
    val trimmed = part.trim()
    if (trimmed.startsWith("charset=", ignoreCase = true)) {
      val name = trimmed.substringAfter('=').trim().trim('"', '\'')
      return runCatching { Charset.forName(name) }.getOrDefault(Charsets.UTF_8)
    }
  }
  return Charsets.UTF_8
}
