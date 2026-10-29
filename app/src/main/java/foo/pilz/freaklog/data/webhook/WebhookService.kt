/*
 * Copyright (c) 2022-2023. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.data.webhook

import com.ndm4.freakquery.Aliases
import com.ndm4.freakquery.AnodyneAliases
import com.ndm4.freakquery.DefaultAliases
import com.ndm4.freakquery.FreakQueryConfig
import foo.pilz.freaklog.data.freakquery.FreakQueryRepository
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class WebhookResult(
    val success: Boolean,
    val messageId: String?,
    val error: Exception?
)

/** Minimal logger seam so unit tests can avoid the Android framework. */
internal fun interface WebhookLogger {
    fun warn(tag: String, message: String)
}

private object AndroidWebhookLogger : WebhookLogger {
    override fun warn(tag: String, message: String) {
        android.util.Log.w(tag, message)
    }
}

@Singleton
class WebhookService internal constructor(
    private val freakQueryRepository: FreakQueryRepository? = null,
    private val userPreferences: UserPreferences? = null,
    private val logger: WebhookLogger = AndroidWebhookLogger,
    private val delayMillis: suspend (Long) -> Unit = ::delay,
) {

    @Inject constructor(
        freakQueryRepository: FreakQueryRepository,
        userPreferences: UserPreferences
    ) : this(freakQueryRepository, userPreferences, AndroidWebhookLogger, ::delay)

    private val sortedSubstances = AnodyneAliases.map.keys.sortedByDescending { it.length }

    companion object {
        const val DEFAULT_TEMPLATE =
            "{user}: [{dose} {units} ]{substance}[{formulation}] via {route}[ at {site}][\n> {note}]"
        private const val MAX_RETRIES = 3
        private const val SUBSTANCE_INFO_URL = "https://anodyne.wiki/substance/"
        private val TEMPLATE_BRACKET_REGEX = Regex("\\[([^\\[\\]]+)\\]")
        private val PLACEHOLDER_REGEX = Regex("\\{([a-zA-Z]+)\\}")
    }

    suspend fun sendWebhook(
        url: String,
        user: String,
        substance: String,
        dose: Double?,
        units: String?,
        isEstimate: Boolean,
        route: String,
        site: String?,
        note: String?,
        template: String,
        isHyperlinked: Boolean,
        substanceInfoUrl: String = SUBSTANCE_INFO_URL,
        formulation: String? = null,
        saltForm: String? = null
    ): WebhookResult {
        return sendWebhookWithRetry(
            url = url,
            user = user,
            substance = substance,
            dose = dose,
            units = units,
            isEstimate = isEstimate,
            route = route,
            site = site,
            note = note,
            template = template,
            isHyperlinked = isHyperlinked,
            substanceInfoUrl = substanceInfoUrl,
            isEdit = false,
            messageId = null,
            formulation = formulation,
            saltForm = saltForm
        )
    }

    suspend fun editWebhook(
        url: String,
        messageId: String,
        user: String,
        substance: String,
        dose: Double?,
        units: String?,
        isEstimate: Boolean,
        route: String,
        site: String?,
        note: String?,
        template: String,
        isHyperlinked: Boolean,
        substanceInfoUrl: String = SUBSTANCE_INFO_URL,
        formulation: String? = null,
        saltForm: String? = null
    ): WebhookResult {
        return sendWebhookWithRetry(
            url = url,
            user = user,
            substance = substance,
            dose = dose,
            units = units,
            isEstimate = isEstimate,
            route = route,
            site = site,
            note = note,
            template = template,
            isHyperlinked = isHyperlinked,
            substanceInfoUrl = substanceInfoUrl,
            isEdit = true,
            messageId = messageId,
            formulation = formulation,
            saltForm = saltForm
        )
    }

    suspend fun deleteWebhookMessage(url: String, messageId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = if (url.endsWith("/")) url.dropLast(1) else url
                val webhookUrl = checkedWebhookUrl(cleanUrl)
                val requestUrl = URL("${webhookUrl}/messages/$messageId")

                val connection = requestUrl.openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "DELETE"
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000

                    val responseCode = connection.responseCode
                    try {
                        if (responseCode in 200..299) {
                            connection.inputStream?.close()
                        } else {
                            connection.errorStream?.close()
                        }
                    } catch (_: IOException) {}
                    responseCode in 200..299
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                false
            }
        }

    private suspend fun sendWebhookWithRetry(
        url: String,
        user: String,
        substance: String,
        dose: Double?,
        units: String?,
        isEstimate: Boolean,
        route: String,
        site: String?,
        note: String?,
        template: String,
        isHyperlinked: Boolean,
        substanceInfoUrl: String,
        isEdit: Boolean,
        messageId: String?,
        formulation: String? = null,
        saltForm: String? = null
    ): WebhookResult {
        val doseString: String
        val unitString: String
        if (dose != null && dose >= 0) {
            val formattedDose = if (dose % 1.0 == 0.0) {
                dose.toInt().toString()
            } else {
                dose.toString()
            }
            doseString = if (isEstimate) "~$formattedDose" else formattedDose
            unitString = units ?: ""
        } else {
            doseString = ""
            unitString = ""
        }

        val displaySubstance = if (!saltForm.isNullOrBlank()) "$substance[$saltForm]" else substance
        val displayFormulation = if (!formulation.isNullOrBlank()) "[$formulation]" else ""

        val values = mapOf(
            "user" to user,
            "substance" to displaySubstance,
            "dose" to doseString,
            "units" to unitString,
            "route" to route,
            "site" to (site ?: ""),
            "note" to (note ?: ""),
            "formulation" to displayFormulation
        )

        require(template.length <= 16_384) { "Webhook template is too large" }
        val content = template
        val freakQueryContent = if (userPreferences?.webhookUseFreakQueryFlow?.first() != false) {
            val separator = userPreferences?.webhookFreakQuerySeparatorFlow?.first() ?: ", "
            val config = FreakQueryConfig(
                renderSeparator = separator,
                aliases = DefaultAliases.all + ("substance" to anodyneAliasMap())
            )
            freakQueryRepository?.render(content, config) ?: content
        } else {
            content
        }

        val literalContent = processTemplate(freakQueryContent, values)
        val finalContent = if (isHyperlinked) {
            hyperlinkSubstancesInText(literalContent, substanceInfoUrl, substance)
        } else {
            literalContent
        }
        require(finalContent.length <= 32_768) { "Webhook message is too large" }

        val payload = buildJsonObject {
            put("content", finalContent)
        }

        var lastError: Exception? = null

        for (attempt in 0 until MAX_RETRIES) {
            try {
                logger.warn("WebhookService", "Attempting to send webhook")
                val result = performWebhookRequest(
                    url = url,
                    payload = payload,
                    isEdit = isEdit,
                    messageId = messageId
                )
                return WebhookResult(success = true, messageId = result, error = null)
            } catch (e: Exception) {
                logger.warn("WebhookService", "Got error: $e")
                lastError = e
                if (attempt < MAX_RETRIES - 1) {
                    val delayMs = (1L shl attempt) * 1000L // Exponential backoff: 1s, 2s, 4s
                    delayMillis(delayMs)
                }
            }
        }

        return WebhookResult(success = false, messageId = null, error = lastError)
    }

    private suspend fun performWebhookRequest(
        url: String,
        payload: JsonObject,
        isEdit: Boolean,
        messageId: String?
    ): String? = withContext(Dispatchers.IO) {
        val cleanUrl = if (url.endsWith("/")) url.dropLast(1) else url
        val webhookUrl = checkedWebhookUrl(cleanUrl)

        val requestUrl = if (isEdit && messageId != null) {
            URL("${webhookUrl}/messages/$messageId")
        } else {
            val separator = if (cleanUrl.contains("?")) "&" else "?"
            URL("$cleanUrl${separator}wait=true")
        }

        val connection = requestUrl.openConnection() as HttpURLConnection
        try {
            if (isEdit) {
                setPatchMethod(connection)
            } else {
                connection.requestMethod = "POST"
            }
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("Content-Type", "application/json")

            val jsonString = payload.toString()
            connection.outputStream.use { os ->
                os.write(jsonString.toByteArray())
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.use { readBoundedResponse(it, connection.contentEncoding) }
                val detail = if (!errorBody.isNullOrBlank()) ": $errorBody" else ""
                logger.warn("WebhookService", "HTTP error code:$responseCode$detail")
                throw IOException("HTTP error code $responseCode$detail")
            }

            if (!isEdit) {
                val response = connection.inputStream.use { readBoundedResponse(it, connection.contentEncoding) }
                val json = Json.parseToJsonElement(response).jsonObject
                json["id"]?.jsonPrimitive?.content
            } else {
                try {
                    connection.inputStream?.close()
                } catch (_: IOException) {}
                null
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Android's okhttp-backed [HttpURLConnection] accepts `PATCH`, but the
     * JDK's implementation (used by JVM unit tests) rejects it with a
     * [ProtocolException]. There, write the protected `method` field declared
     * on [HttpURLConnection] directly; if reflection is not permitted, fall
     * back to `POST` with an `X-HTTP-Method-Override` header.
     */
    private fun setPatchMethod(connection: HttpURLConnection) {
        try {
            connection.requestMethod = "PATCH"
        } catch (_: ProtocolException) {
            try {
                val field = generateSequence<Class<*>>(connection.javaClass) { it.superclass }
                    .firstNotNullOf { cls -> cls.declaredFields.firstOrNull { it.name == "method" } }
                field.isAccessible = true
                field.set(connection, "PATCH")
            } catch (_: Exception) {
                connection.requestMethod = "POST"
                connection.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            }
        }
    }

    fun processTemplate(template: String, values: Map<String, String>): String {
        require(template.length <= 16_384) { "Webhook template is too large" }
        val blocks = TEMPLATE_BRACKET_REGEX.replace(template) { match ->
            val content = match.groupValues[1]
            if (PLACEHOLDER_REGEX.findAll(content).any { values[it.groupValues[1]] == "" }) "" else content
        }
        return buildString {
            var start = 0
            for (match in PLACEHOLDER_REGEX.findAll(blocks)) {
                val replacement = values[match.groupValues[1]] ?: match.value
                require(length.toLong() + match.range.first - start + replacement.length <= 32_768) {
                    "Webhook message is too large"
                }
                append(blocks, start, match.range.first)
                append(replacement)
                start = match.range.last + 1
            }
            require(length + blocks.length - start <= 32_768) { "Webhook message is too large" }
            append(blocks, start, blocks.length)
        }
    }

    private fun checkedWebhookUrl(url: String): URL {
        require(isSecureWebhookUrl(url)) { "HTTPS is required for remote webhook destinations" }
        return URL(url)
    }

    private fun readBoundedResponse(stream: java.io.InputStream, encoding: String?): String {
        val decoded = if (encoding.equals("gzip", ignoreCase = true)) java.util.zip.GZIPInputStream(stream) else stream
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val read = decoded.read(buffer)
            if (read < 0) break
            if (out.size() + read > 65_536) throw IOException("Webhook response is too large")
            out.write(buffer, 0, read)
        }
        return out.toString("UTF-8")
    }

    private val cachedAnodyneAliasMap: Map<String, String> by lazy {
        AnodyneAliases.map.mapKeys { Aliases.norm(it.key) }
    }

    private fun anodyneAliasMap(): Map<String, String> = cachedAnodyneAliasMap

    private fun buildHyperlinkRegex(names: List<String>): Regex? {
        val escapedAlternatives = names
            .asSequence()
            .filter { it.length >= 3 }
            .distinct()
            .sortedByDescending { it.length }
            .map { java.util.regex.Pattern.quote(it) }
            .toList()

        if (escapedAlternatives.isEmpty()) return null

        val combinedPattern = escapedAlternatives.joinToString("|")
        return "(?<!\\[|/|=)\\b(?:$combinedPattern)\\b(?!\\s*]\\(|>)".toRegex()
    }

    private fun markdownLinkRanges(text: String): List<IntRange> = buildList {
        var opening = -1
        var index = 0
        while (index < text.length) {
            when (text[index]) {
                '[' -> opening = index
                ']' -> if (opening >= 0 && text.startsWith("(<", index + 1)) {
                    val end = text.indexOf(">)", index + 3)
                    if (end < 0) break
                    add(opening..end + 1)
                    opening = -1
                    index = end + 1
                }
            }
            index++
        }
    }

    private fun IntRange.isInsideMarkdownLink(linkRanges: List<IntRange>): Boolean =
        linkRanges.any { linkRange ->
            first >= linkRange.first && last <= linkRange.last
        }

    private fun hyperlinkSubstancesInText(
        text: String,
        substanceInfoUrl: String,
        primarySubstance: String
    ): String {
        val names = (listOf(primarySubstance) + sortedSubstances)
            .filter { it.length >= 3 }
            .distinct()
            .sortedByDescending { it.length }
        val regex = buildHyperlinkRegex(names) ?: return text
        val existingLinkRanges = markdownLinkRanges(text)
        val aliasMap = anodyneAliasMap()

        return regex.replace(text) { match ->
            if (match.range.isInsideMarkdownLink(existingLinkRanges)) {
                match.value
            } else {
                val canonicalName = AnodyneAliases.map[match.value]
                    ?: aliasMap[Aliases.norm(match.value)]
                    ?: match.value
                val encodedSubstance = java.net.URLEncoder.encode(canonicalName, "UTF-8")
                    .replace("+", "%20")
                "[${match.value}](<$substanceInfoUrl$encodedSubstance>)"
            }
        }
    }
}
