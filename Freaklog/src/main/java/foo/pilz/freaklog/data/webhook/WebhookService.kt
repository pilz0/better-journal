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

import kotlinx.coroutines.delay
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

@Singleton
class WebhookService @Inject constructor() {

    companion object {
        const val DEFAULT_TEMPLATE = "{user}: [{dose} {unit} ]{substance} via {route}[ ({site})][\n> {note}]"
        private const val MAX_RETRIES = 3
        private const val SUBSTANCE_INFO_URL = "https://psychonautwiki.org/wiki/"
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

    suspend fun deleteWebhookMessage(url: String, messageId: String): Boolean {
        return try {
            val webhookUrl = URL(url)
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
        if (dose != null && dose > 0) {
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

        var displaySubstance = substance
        if (isHyperlinked) {
            val encodedSub = java.net.URLEncoder.encode(substance, "UTF-8")
            displaySubstance = "[$substance](<$substanceInfoUrl$encodedSub>)"
        }

        val values = mapOf(
            "user" to user,
            "substance" to displaySubstance,
            "dose" to doseString,
            "unit" to unitString,
            "route" to route,
            "site" to (site ?: ""),
            "note" to (note ?: "")
        )

        val content = processTemplate(template, values)
        val payload = buildJsonObject {
            put("content", content)
        }

        var lastError: Exception? = null

        for (attempt in 0 until MAX_RETRIES) {
            try {
                val result = performWebhookRequest(
                    url = url,
                    payload = payload,
                    isEdit = isEdit,
                    messageId = messageId
                )
                return WebhookResult(success = true, messageId = result, error = null)
            } catch (e: Exception) {
                lastError = e
                if (attempt < MAX_RETRIES - 1) {
                    val delayMs = (1L shl attempt) * 1000L // Exponential backoff: 1s, 2s, 4s
                    delay(delayMs)
                }
            }
        }

        return WebhookResult(success = false, messageId = null, error = lastError)
    }

    private fun performWebhookRequest(
        url: String,
        payload: JsonObject,
        isEdit: Boolean,
        messageId: String?
    ): String? {
        val webhookUrl = URL(url)
        
        val requestUrl = if (isEdit && messageId != null) {
            URL("${webhookUrl}/messages/$messageId")
        } else {
            val separator = if (url.contains("?")) "&" else "?"
            URL("$url${separator}wait=true")
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
            throw IOException("HTTP error code: $responseCode")
        }

        if (!isEdit) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            
            val json = Json.parseToJsonElement(response).jsonObject
            return json["id"]?.jsonPrimitive?.content
        }

        connection.disconnect()
        return null
    }

    fun processTemplate(template: String, values: Map<String, String>): String {
        var processed = template
        
        // Pattern to match optional blocks: [...] 
        val pattern = "\\[([^\\[\\]]+)\\]".toRegex()
        
        // Process all matches in reverse order to avoid index issues
        val matches = pattern.findAll(processed).toList().reversed()
        
        for (match in matches) {
            val fullMatch = match.value
            val content = match.groupValues[1]
            
            // Check if any placeholder in the content has an empty value
            var keepBlock = true
            for ((key, value) in values) {
                if (content.contains("{$key}") && value.isEmpty()) {
                    keepBlock = false
                    break
                }
            }
            
            if (keepBlock) {
                // Keep the content, remove the brackets
                processed = processed.replace(fullMatch, content)
            } else {
                // Remove the entire block
                processed = processed.replace(fullMatch, "")
            }
        }
        
        // Replace all placeholders with their values
        for ((key, value) in values) {
            processed = processed.replace("{$key}", value)
        }
        
        return processed
    }
}
