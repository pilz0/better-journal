/*
 * Copyright (c) 2026.
 * This file is part of FreakLog.
 *
 * FreakLog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.data.webhook

import com.ndm4.freakquery.FreakQueryConfig
import foo.pilz.freaklog.data.freakquery.FreakQueryRepository
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.flowOf
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Tests the HTTP send / delete behaviour of [WebhookService].
 *
 * The "persist generated ingestion id" fix in `Ingestion`/`ExperienceDao`/
 * `FinishIngestionScreenViewModel` only matters if [WebhookService] actually
 * extracts the message id from Discord's `wait=true` response and reuses it
 * verbatim for delete (and edit). These tests pin that behaviour:
 *
 *  * [sendWebhook_returnsMessageIdFromWaitTrueResponse] — the value the fix
 *    now stores in `Ingestion.webhookMessageId`.
 *  * [deleteWebhookMessage_issuesDeleteOnMessagesIdEndpointAndReturnsTrueOn204]
 *    — what runs once a message id is stored.
 *
 * `editWebhook` is intentionally NOT covered here: it issues a `PATCH`, which
 * the JDK's `HttpURLConnection.setRequestMethod` rejects with a
 * `ProtocolException`. Android's okhttp-backed `HttpURLConnection` does accept
 * `PATCH`, so the production code is correct on-device but cannot be exercised
 * from a plain JVM unit test without an HTTP-client refactor.
 */
class WebhookServiceHttpTest {

    private lateinit var server: MockWebServer
    private lateinit var baseUrl: String

    /**
     * Built with a no-op logger (so the real `android.util.Log` calls don't
     * blow up under JVM tests without needing the global
     * `unitTests.isReturnDefaultValues` Gradle flag) and a no-op delay (so the
     * exponential-backoff retry tests don't sleep in real time).
     */
    private val service = WebhookService(
        logger = WebhookLogger { _, _ -> },
        delayMillis = { /* no-op: skip exponential backoff in tests */ },
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        // Use a Discord-shaped path so we can also assert the path the service
        // POSTs to is preserved verbatim (e.g. trailing-slash handling).
        baseUrl = server.url("/webhooks/abc/token").toString().trimEnd('/')
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueue(status: Int, body: String = "") {
        server.enqueue(MockResponse().setResponseCode(status).setBody(body))
    }

    private fun serviceWithFreakQuery(
        separator: String = ", ",
        render: suspend (String, FreakQueryConfig) -> String
    ): WebhookService {
        val repo = mockk<FreakQueryRepository>()
        val prefs = mockk<UserPreferences>()
        every { prefs.webhookUseFreakQueryFlow } returns flowOf(true)
        every { prefs.webhookFreakQuerySeparatorFlow } returns flowOf(separator)
        coEvery { repo.render(any(), any()) } coAnswers {
            render(firstArg(), secondArg())
        }
        return WebhookService(
            freakQueryRepository = repo,
            userPreferences = prefs,
            logger = WebhookLogger { _, _ -> },
            delayMillis = { },
        )
    }

    /**
     * Bounded `takeRequest` so a regression that prevents the HTTP request
     * from being sent fails fast instead of hanging the whole test run.
     */
    private fun takeRequest(): RecordedRequest {
        val req = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull("expected an HTTP request to MockWebServer within 5s", req)
        return req!!
    }

    // ---------------------------------------------------------------------
    // sendWebhook
    // ---------------------------------------------------------------------

    @Test
    fun sendWebhook_returnsMessageIdFromWaitTrueResponse() = runBlocking {
        enqueue(200, """{"id":"1234567890","content":"hi"}""")

        val result = service.sendWebhook(
            url = baseUrl,
            user = "Alice",
            substance = "Caffeine",
            dose = 100.0,
            units = "mg",
            isEstimate = false,
            route = "oral",
            site = null,
            note = null,
            template = "{user}: {dose} {units} {substance} via {route}",
            isHyperlinked = false,
        )

        assertTrue("expected success, got $result", result.success)
        assertNull(result.error)
        assertEquals("1234567890", result.messageId)

        val req = takeRequest()
        assertEquals("POST", req.method)
        // The path includes `wait=true` so Discord returns the created
        // message body (which is where we get the id from).
        assertEquals("/webhooks/abc/token?wait=true", req.path)
        assertEquals("application/json", req.getHeader("Content-Type"))
        val payload = JSONObject(req.body.readUtf8())
        assertEquals("Alice: 100 mg Caffeine via oral", payload.getString("content"))
    }

    @Test
    fun sendWebhook_stripsSingleTrailingSlashBeforeAppendingWaitTrue() = runBlocking {
        enqueue(200, """{"id":"42"}""")

        val result = service.sendWebhook(
            url = "$baseUrl/",
            user = "u", substance = "s", dose = null, units = null,
            isEstimate = false, route = "r", site = null, note = null,
            template = "{user}", isHyperlinked = false,
        )

        assertTrue(result.success)
        // Path must not contain a doubled slash; if the trailing slash had not
        // been stripped, the separator-choice would also have been wrong.
        assertEquals("/webhooks/abc/token?wait=true", takeRequest().path)
    }

    @Test
    fun sendWebhook_usesAmpersandSeparatorWhenUrlAlreadyHasQueryString() = runBlocking {
        enqueue(200, """{"id":"1"}""")

        val result = service.sendWebhook(
            url = "$baseUrl?thread_id=999",
            user = "u", substance = "s", dose = null, units = null,
            isEstimate = false, route = "r", site = null, note = null,
            template = "{user}", isHyperlinked = false,
        )

        assertTrue(result.success)
        // Both the existing `thread_id` and the appended `wait=true` must be
        // present; if `?` had been used a second time the request would have
        // been malformed.
        assertEquals(
            "/webhooks/abc/token?thread_id=999&wait=true",
            takeRequest().path,
        )
    }

    @Test
    fun sendWebhook_succeedsButReturnsNullMessageIdWhenResponseHasNoIdField() =
        runBlocking {
            enqueue(200, """{"content":"hi"}""")

            val result = service.sendWebhook(
                url = baseUrl,
                user = "u", substance = "s", dose = null, units = null,
                isEstimate = false, route = "r", site = null, note = null,
                template = "{user}", isHyperlinked = false,
            )

            assertTrue(result.success)
            assertNull(result.messageId)
        }

    @Test
    fun sendWebhook_retriesOn500AndReturnsIdFromSuccessfulAttempt() = runBlocking {
        enqueue(500, "boom")
        enqueue(200, """{"id":"second-try"}""")

        val result = service.sendWebhook(
            url = baseUrl,
            user = "u", substance = "s", dose = null, units = null,
            isEstimate = false, route = "r", site = null, note = null,
            template = "{user}", isHyperlinked = false,
        )

        assertTrue(result.success)
        assertEquals("second-try", result.messageId)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun sendWebhook_returnsFailureAfterAllRetriesExhausted() = runBlocking {
        // WebhookService retries up to MAX_RETRIES = 3 attempts.
        repeat(3) { enqueue(500, "still broken") }

        val result = service.sendWebhook(
            url = baseUrl,
            user = "u", substance = "s", dose = null, units = null,
            isEstimate = false, route = "r", site = null, note = null,
            template = "{user}", isHyperlinked = false,
        )

        assertFalse(result.success)
        assertNull(result.messageId)
        assertNotNull(result.error)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun sendWebhook_withIsHyperlinkedTrue_wrapsSubstanceInMarkdownLink() = runBlocking {
        enqueue(200, """{"id":"x"}""")

        service.sendWebhook(
            url = baseUrl,
            user = "u", substance = "Caffeine", dose = null, units = null,
            isEstimate = false, route = "oral", site = null, note = null,
            template = "{substance}", isHyperlinked = true,
            substanceInfoUrl = "https://example.org/substance/",
        )

        val payload = JSONObject(takeRequest().body.readUtf8())
        assertEquals(
            "[Caffeine](<https://example.org/substance/Caffeine>)",
            payload.getString("content"),
        )
    }

    @Test
    fun sendWebhook_urlEncodesSubstanceNamesContainingSpacesAndSpecialChars() = runBlocking {
        enqueue(200, """{"id":"x"}""")

        service.sendWebhook(
            url = baseUrl,
            user = "u", substance = "5-MeO-DMT freebase", dose = null, units = null,
            isEstimate = false, route = "oral", site = null, note = null,
            template = "{substance}", isHyperlinked = true,
            substanceInfoUrl = "https://example.org/s/",
        )

        val content = JSONObject(takeRequest().body.readUtf8()).getString("content")
        // The display text keeps the original substance name.
        assertTrue("expected display name preserved in: $content",
            content.startsWith("[5-MeO-DMT freebase]"))
        // The URL inside the link must be percent- or plus-encoded.
        assertTrue("expected encoded URL in: $content",
            content.contains("https://example.org/s/5-MeO-DMT+freebase") ||
            content.contains("https://example.org/s/5-MeO-DMT%20freebase"))
    }

    @Test
    fun sendWebhook_rendersFreakQueryTemplateBeforePosting() = runBlocking {
        enqueue(200, """{"id":"x"}""")
        val service = serviceWithFreakQuery { template, _ ->
            template.replace("{{today|count}}", "2")
        }

        service.sendWebhook(
            url = baseUrl,
            user = "u", substance = "Caffeine", dose = null, units = null,
            isEstimate = false, route = "oral", site = null, note = null,
            template = "Today: {{today|count}}", isHyperlinked = false,
        )

        val payload = JSONObject(takeRequest().body.readUtf8())
        assertEquals("Today: 2", payload.getString("content"))
    }

    @Test
    fun sendWebhook_passesConfiguredFreakQueryCompactSeparator() = runBlocking {
        enqueue(200, """{"id":"x"}""")
        val service = serviceWithFreakQuery(separator = " | ") { _, config ->
            "separator=${config.renderSeparator}"
        }

        service.sendWebhook(
            url = baseUrl,
            user = "u", substance = "Caffeine", dose = null, units = null,
            isEstimate = false, route = "oral", site = null, note = null,
            template = "{{today|compact=true}}", isHyperlinked = false,
        )

        val payload = JSONObject(takeRequest().body.readUtf8())
        assertEquals("separator= | ", payload.getString("content"))
    }

    @Test
    fun sendWebhook_formatsIntegerDoseWithoutTrailingDecimals() = runBlocking {
        enqueue(200, """{"id":"x"}""")

        service.sendWebhook(
            url = baseUrl,
            user = "u", substance = "s", dose = 100.0, units = "mg",
            isEstimate = false, route = "oral", site = null, note = null,
            template = "[{dose} {units} ]done", isHyperlinked = false,
        )

        val payload = JSONObject(takeRequest().body.readUtf8())
        assertEquals("100 mg done", payload.getString("content"))
    }

    @Test
    fun sendWebhook_preservesNonIntegerDoses() = runBlocking {
        enqueue(200, """{"id":"x"}""")

        service.sendWebhook(
            url = baseUrl,
            user = "u", substance = "s", dose = 12.5, units = "mg",
            isEstimate = false, route = "oral", site = null, note = null,
            template = "[{dose} {units} ]done", isHyperlinked = false,
        )

        val payload = JSONObject(takeRequest().body.readUtf8())
        assertEquals("12.5 mg done", payload.getString("content"))
    }

    @Test
    fun sendWebhook_prefixesEstimatedDoseWithTilde() = runBlocking {
        enqueue(200, """{"id":"x"}""")

        service.sendWebhook(
            url = baseUrl,
            user = "u", substance = "s", dose = 25.0, units = "mg",
            isEstimate = true, route = "oral", site = null, note = null,
            template = "[{dose} {units} ]done", isHyperlinked = false,
        )

        val payload = JSONObject(takeRequest().body.readUtf8())
        assertEquals("~25 mg done", payload.getString("content"))
    }

    @Test
    fun sendWebhook_dropsDoseBlockWhenDoseIsNull() = runBlocking {
        enqueue(200, """{"id":"x"}""")

        service.sendWebhook(
            url = baseUrl,
            user = "u", substance = "s", dose = null, units = "mg",
            isEstimate = false, route = "oral", site = null, note = null,
            template = "[{dose} {units} ]done", isHyperlinked = false,
        )

        val payload = JSONObject(takeRequest().body.readUtf8())
        // The optional `[…]` block must be removed entirely because both
        // `dose` and `units` resolve to empty strings.
        assertEquals("done", payload.getString("content"))
    }

    @Test
    fun sendWebhook_dropsDoseBlockWhenDoseIsNegative() = runBlocking {
        enqueue(200, """{"id":"x"}""")

        service.sendWebhook(
            url = baseUrl,
            user = "u", substance = "s", dose = -1.0, units = "mg",
            isEstimate = false, route = "oral", site = null, note = null,
            template = "[{dose} {units} ]done", isHyperlinked = false,
        )

        val payload = JSONObject(takeRequest().body.readUtf8())
        assertEquals("done", payload.getString("content"))
    }

    // ---------------------------------------------------------------------
    // deleteWebhookMessage
    // ---------------------------------------------------------------------

    @Test
    fun deleteWebhookMessage_issuesDeleteOnMessagesIdEndpointAndReturnsTrueOn204() =
        runBlocking {
            enqueue(204)

            val ok = service.deleteWebhookMessage(baseUrl, "777")

            assertTrue(ok)
            val req = takeRequest()
            assertEquals("DELETE", req.method)
            assertEquals("/webhooks/abc/token/messages/777", req.path)
        }

    @Test
    fun deleteWebhookMessage_stripsSingleTrailingSlashBeforeAppendingPath() = runBlocking {
        enqueue(200)

        val ok = service.deleteWebhookMessage("$baseUrl/", "777")

        assertTrue(ok)
        // Path must not contain a doubled slash before `messages`.
        assertEquals("/webhooks/abc/token/messages/777", takeRequest().path)
    }

    @Test
    fun deleteWebhookMessage_returnsFalseWhenServerResponds404() = runBlocking {
        enqueue(404, "not found")

        val ok = service.deleteWebhookMessage(baseUrl, "missing")

        assertFalse(ok)
    }

    @Test
    fun deleteWebhookMessage_returnsFalseWhenConnectionCannotBeEstablished() = runBlocking {
        // Port 1 on the loopback address has no listener: the kernel
        // immediately replies with RST (connection refused) without any DNS
        // lookup. This deterministically exercises the `catch` branch in
        // `deleteWebhookMessage`, which must return `false` on any IO error.
        val ok = service.deleteWebhookMessage(
            "http://127.0.0.1:1/webhooks/abc/token",
            "anything",
        )

        assertFalse(ok)
    }
}
