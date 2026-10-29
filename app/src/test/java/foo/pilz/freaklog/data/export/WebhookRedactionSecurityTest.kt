package foo.pilz.freaklog.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WebhookRedactionSecurityTest {
    @Test
    fun queryAndFragmentCannotHideTheCredentialFromRedaction() {
        listOf("?thread=anything/later", "#anything/later", "?token=extra-secret#path/later", "").forEach { suffix ->
            val redacted = WebhookSerializable.redactUrl("https://user:pass@discord.com/api/v10/webhooks/123/private-token$suffix")
            assertEquals("https://discord.com/api/v10/webhooks/123/REDACTED", redacted)
            assertFalse(redacted.contains("private-token"))
            assertFalse(redacted.contains("extra-secret"))
        }
    }

    @Test
    fun unrecognizedCredentialLayoutsAreOmittedAndCannotBeEnabledOnImport() {
        listOf("https://example.com/private-secret", "not a URL/private-secret", "https://example.com/hook?key=private-secret").forEach {
            val redacted = WebhookSerializable.redactUrl(it)
            assertEquals("", redacted)
            assertFalse(WebhookSerializable(name = "test", url = redacted, isEnabled = true).toEntity().isEnabled)
        }
    }
}
