package foo.pilz.freaklog.data.webhook

import java.net.URI

fun isSecureWebhookUrl(url: String): Boolean {
    val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
    if (uri.host == null || uri.userInfo != null) return false
    return uri.scheme.equals("https", true) || (
        uri.scheme.equals("http", true) &&
            uri.host.lowercase() in setOf("localhost", "127.0.0.1", "::1", "[::1]")
        )
}
