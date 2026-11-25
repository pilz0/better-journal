package foo.pilz.freaklog.data.ai.web

import foo.pilz.freaklog.data.ai.profiles.isInsecureRemoteUrl
import foo.pilz.freaklog.data.ai.provider.AiException
import java.net.InetAddress
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.net.UnknownHostException

fun interface HostResolver {
  fun resolveAll(host: String): List<InetAddress>
}

object SystemHostResolver : HostResolver {
  override fun resolveAll(host: String): List<InetAddress> = InetAddress.getAllByName(host).toList()
}

/**
 * Validates URLs before the app connects on the model's behalf.
 *
 * Model-supplied web fetches require public `https://` URLs and reject loopback, link-local,
 * carrier-grade NAT, multicast, and private LAN addresses so a prompt or redirect cannot probe
 * local or internal services.
 */
object WebUrlValidator {

  fun validatePublicHttpsUrl(rawUrl: String, resolver: HostResolver = SystemHostResolver): URL {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) throw AiException("Parameter 'url' is required.")
    val (uri, parsed) = parseUrlAndUri(trimmed)
    val host = validateHttpsSchemeAndHost(uri, parsed)
    resolveAndVerifyPublicHost(host.removePrefix("[").removeSuffix("]"), resolver)
    return parsed
  }

  private fun parseUrlAndUri(trimmed: String): Pair<URI, URL> {
    val uri =
        try {
          URI(trimmed)
        } catch (e: URISyntaxException) {
          throw AiException("Invalid URL: $trimmed", cause = e)
        }
    val parsed =
        try {
          URL(trimmed)
        } catch (e: MalformedURLException) {
          throw AiException("Invalid URL: $trimmed", cause = e)
        }
    return uri to parsed
  }

  private fun validateHttpsSchemeAndHost(uri: URI, parsed: URL): String {
    val invalidScheme =
        !uri.scheme.equals("https", ignoreCase = true) ||
            !parsed.protocol.equals("https", ignoreCase = true)
    val hasCredentials = uri.rawUserInfo != null || parsed.userInfo != null
    val host = parsed.host?.trim()?.takeIf { it.isNotEmpty() }
    val error =
        when {
          invalidScheme -> "Only public https:// URLs are allowed."
          hasCredentials -> "URLs with embedded credentials are not allowed."
          host == null -> "URL must include a host."
          isLocalHostname(host.removePrefix("[").removeSuffix("]")) ->
              "Refusing to connect to local or private host '${host.removePrefix("[").removeSuffix("]")}'."
          else -> null
        }
    if (error != null) throw AiException(error)
    return host!!
  }

  private fun resolveAndVerifyPublicHost(bareHost: String, resolver: HostResolver) {
    val addresses =
        try {
          resolver.resolveAll(bareHost)
        } catch (e: UnknownHostException) {
          throw AiException("Can't resolve $bareHost.", cause = e)
        }
    val error =
        when {
          addresses.isEmpty() -> "Can't resolve $bareHost."
          addresses.any(::isRestrictedAddress) ->
              "Refusing to connect to a local or private network address."
          else -> null
        }
    if (error != null) throw AiException(error)
  }

  /**
   * Normalizes a user-configured SearXNG base URL. Allows `https://` anywhere and `http://` only on
   * localhost (`127.0.0.1`, `::1`, `localhost`), matching the rest of the app.
   */
  fun normalizeSearxngUrl(raw: String): String? {
    var text = raw.trim()
    if (text.isBlank()) return null
    if (!text.contains("://")) {
      text = "https://$text"
    }
    while (text.endsWith("/")) {
      text = text.dropLast(1)
    }
    return try {
      val uri = URI(text)
      val parsed = URL(text)
      val proto = parsed.protocol?.lowercase()
      val invalid =
          (proto != "http" && proto != "https") ||
              parsed.host.isNullOrBlank() ||
              parsed.query != null ||
              uri.fragment != null ||
              uri.rawUserInfo != null ||
              parsed.userInfo != null ||
              isInsecureRemoteUrl(text)
      if (invalid) null else text
    } catch (_: Exception) {
      null
    }
  }

  internal fun isLocalHostname(host: String): Boolean {
    val lower = host.lowercase()
    return lower == "localhost" ||
        lower.endsWith(".localhost") ||
        lower.endsWith(".local") ||
        lower.endsWith(".internal")
  }

  internal fun isRestrictedAddress(addr: InetAddress): Boolean {
    val basicRestricted =
        addr.isAnyLocalAddress ||
            addr.isLoopbackAddress ||
            addr.isLinkLocalAddress ||
            addr.isSiteLocalAddress ||
            addr.isMulticastAddress
    if (basicRestricted) return true
    val bytes = addr.address
    return when (bytes.size) {
      4 -> isRestrictedIpv4(bytes)
      16 -> isRestrictedIpv6(bytes)
      else -> true
    }
  }

  private fun isRestrictedIpv4(bytes: ByteArray): Boolean {
    val b0 = bytes[0].toInt() and 0xFF
    val b1 = bytes[1].toInt() and 0xFF
    val b2 = bytes[2].toInt() and 0xFF
    return b0 == 0 ||
        b0 == 10 ||
        (b0 == 100 && b1 in 64..127) ||
        b0 == 127 ||
        (b0 == 169 && b1 == 254) ||
        (b0 == 172 && b1 in 16..31) ||
        (b0 == 192 && b1 == 0 && b2 == 0) ||
        (b0 == 192 && b1 == 168) ||
        (b0 == 198 && (b1 == 18 || b1 == 19)) ||
        b0 >= 224
  }

  private fun isRestrictedIpv6(bytes: ByteArray): Boolean {
    val b0 = bytes[0].toInt() and 0xFF
    val b1 = bytes[1].toInt() and 0xFF
    val isUla = (b0 and 0xFE) == 0xFC
    val isLinkLocal = b0 == 0xFE && (b1 and 0xC0) == 0x80
    val isSiteLocal = b0 == 0xFE && (b1 and 0xC0) == 0xC0
    val isMulticast = b0 == 0xFF
    val isIpv4Mapped =
        (0..9).all { bytes[it] == 0.toByte() } &&
            bytes[10] == 0xFF.toByte() &&
            bytes[11] == 0xFF.toByte()
    return isUla ||
        isLinkLocal ||
        isSiteLocal ||
        isMulticast ||
        (isIpv4Mapped && isRestrictedIpv4(bytes.copyOfRange(12, 16)))
  }
}
