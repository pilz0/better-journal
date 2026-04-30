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
    private val freakQueryRepository: FreakQueryRepository?,
    private val userPreferences: UserPreferences?,
    private val logger: WebhookLogger,
    private val delayMillis: suspend (Long) -> Unit,
) {

    @Inject constructor(
        freakQueryRepository: FreakQueryRepository,
        userPreferences: UserPreferences
    ) : this(freakQueryRepository, userPreferences, AndroidWebhookLogger, ::delay)

    private val sortedSubstances = AnodyneAliases.map.keys.sortedByDescending { it.length }

    companion object {
        const val DEFAULT_TEMPLATE = "{user}: [{dose} {units} ]{substance} via {route}[ at {site}][\n> {note}]"
        private const val MAX_RETRIES = 3
        private const val SUBSTANCE_INFO_URL = "https://anodynw.wiki/substance/"
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
        substanceInfoUrl: String = SUBSTANCE_INFO_URL
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
            messageId = null
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
        substanceInfoUrl: String = SUBSTANCE_INFO_URL
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
            messageId = messageId
        )
    }

    suspend fun deleteWebhookMessage(url: String, messageId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val cleanUrl = if (url.endsWith("/")) url.dropLast(1) else url
                val webhookUrl = URL(cleanUrl)
                val requestUrl = URL("${webhookUrl}/messages/$messageId")

                val connection = requestUrl.openConnection() as HttpURLConnection
                connection.requestMethod = "DELETE"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                connection.disconnect()

                responseCode in 200..299
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
        messageId: String?
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

        val values = mapOf(
            "user" to user,
            "substance" to substance,
            "dose" to doseString,
            "units" to unitString,
            "route" to route,
            "site" to (site ?: ""),
            "note" to (note ?: "")
        )

        val content = processTemplate(template, values)
        
        val freakQueryContent = if (userPreferences != null && userPreferences.webhookUseFreakQueryFlow.first()) {
            val config = FreakQueryConfig(
                renderSeparator = userPreferences.webhookFreakQuerySeparatorFlow.first()
            )
            freakQueryRepository?.renderFlow(content, config)?.first() ?: content
        } else {
            content
        }

        val hyperlinkEnabled = userPreferences?.webhookHyperlinkSubstancesFlow?.first() ?: isHyperlinked
        val finalContent = if (hyperlinkEnabled) {
            hyperlinkSubstancesInText(freakQueryContent, substanceInfoUrl)
        } else {
            freakQueryContent
        }

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
        val webhookUrl = URL(cleanUrl)

        val requestUrl = if (isEdit && messageId != null) {
            URL("${webhookUrl}/messages/$messageId")
        } else {
            val separator = if (cleanUrl.contains("?")) "&" else "?"
            URL("$cleanUrl${separator}wait=true")
        }

        val connection = requestUrl.openConnection() as HttpURLConnection
        connection.requestMethod = if (isEdit) "PATCH" else "POST"
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
            connection.disconnect()
            logger.warn("WebhookService", "HTTP error code:$responseCode")
            throw IOException("HTTP error code: $responseCode")
        }

        if (!isEdit) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = Json.parseToJsonElement(response).jsonObject
            json["id"]?.jsonPrimitive?.content
        } else {
            connection.disconnect()
            null
        }
    }

    fun processTemplate(template: String, values: Map<String, String>): String {
        var processed = template
        
        val pattern = "\\[([^\\[\\]]+)\\]".toRegex()
        val matches = pattern.findAll(processed).toList().reversed()

        for (match in matches) {
            val fullMatch = match.value
            val content = match.groupValues[1]
            var keepBlock = true
            for ((key, value) in values) {
                if (content.contains("{$key}") && value.isEmpty()) {
                    keepBlock = false
                    break
                }
            }
            if (keepBlock) {
                processed = processed.replace(fullMatch, content)
            } else {
                processed = processed.replace(fullMatch, "")
            }
        }
        for ((key, value) in values) {
            processed = processed.replace("{$key}", value)
        }
        return processed
    }

    private fun hyperlinkSubstancesInText(text: String, substanceInfoUrl: String): String {
        var result = text
        for (name in sortedSubstances) {
            if (name.length < 3) continue // Avoid hyperlinking very short names
            
            val escapedName = java.util.regex.Pattern.quote(name)
            // Match whole word, not preceded by [, /, or = (to avoid FreakQuery parameters)
            // and not followed by ]( or > (already linked or inside URL)
            val regex = "(?<!\\[|/|=)\\b$escapedName\\b(?!\\s*]\\(|>)".toRegex()
            
            result = regex.replace(result) { match ->
                val canonicalName = AnodyneAliases.map[match.value] ?: match.value
                val encodedSub = java.net.URLEncoder.encode(canonicalName, "UTF-8")
                    .replace("+", "%20") // Use %20 instead of + for better compatibility in some Markdown parsers
                "[${match.value}]($substanceInfoUrl$encodedSub)"
            }
        }
        return result
    }
}
