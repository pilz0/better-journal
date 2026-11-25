package foo.pilz.freaklog.data.ai.web

import android.annotation.SuppressLint
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import foo.pilz.freaklog.data.ai.provider.AiException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import org.junit.Assert.assertThrows
import org.junit.Test

class WebUrlValidatorTest {

  private fun resolverFor(vararg addresses: InetAddress): HostResolver = HostResolver {
    addresses.toList()
  }

  private val publicIpv4: InetAddress =
      InetAddress.getByAddress(byteArrayOf(93, 184.toByte(), 216.toByte(), 34))
  private val publicIpv6: InetAddress =
      Inet6Address.getByAddress(
          "public.example",
          byteArrayOf(0x26, 0x06, 0x47, 0x00, 0x47, 0x00, 0, 0, 0, 0, 0, 0, 0, 0, 0x11, 0x11),
          -1,
      )

  @Test
  fun acceptsPublicHttpsUrls() {
    val ipv4Url =
        WebUrlValidator.validatePublicHttpsUrl(
            "https://en.wikipedia.org/wiki/MDMA",
            resolverFor(publicIpv4),
        )
    val ipv6Url =
        WebUrlValidator.validatePublicHttpsUrl(
            "https://example.org/study",
            resolverFor(publicIpv6),
        )
    assertThat(ipv4Url.host).isEqualTo("en.wikipedia.org")
    assertThat(ipv6Url.host).isEqualTo("example.org")
  }

  @SuppressLint("AuthLeak")
  @Test
  fun rejectsNonHttpsLocalhostCredentialsAndMalformedUrls() {
    val resolver = resolverFor(publicIpv4)
    val badUrls =
        listOf(
            "",
            "   ",
            "http://example.com/page",
            "ftp://example.com/file",
            "file:///etc/passwd",
            "https://user:pass@example.com/secret",
            "https://localhost/api",
            "https://sub.localhost/api",
            "https://printer.local/status",
            "https://metadata.internal/latest",
            "https:///missing-host",
            "not a url",
        )
    badUrls.forEach { bad ->
      assertThrows("Expected rejection for '$bad'", AiException::class.java) {
        WebUrlValidator.validatePublicHttpsUrl(bad, resolver)
      }
    }
  }

  @Test
  fun rejectsPrivateLoopbackLinkLocalCgnatMulticastAndUlaAddresses() {
    val restricted =
        listOf(
            byteArrayOf(0, 0, 0, 1),
            byteArrayOf(10, 0, 0, 1),
            byteArrayOf(100, 64, 0, 1),
            byteArrayOf(100, 127, 255.toByte(), 254.toByte()),
            byteArrayOf(127, 0, 0, 1),
            byteArrayOf(169.toByte(), 254.toByte(), 169.toByte(), 254.toByte()),
            byteArrayOf(172.toByte(), 16, 0, 1),
            byteArrayOf(172.toByte(), 31, 255.toByte(), 254.toByte()),
            byteArrayOf(192.toByte(), 0, 0, 1),
            byteArrayOf(192.toByte(), 168.toByte(), 1, 10),
            byteArrayOf(198.toByte(), 18, 0, 1),
            byteArrayOf(224.toByte(), 0, 0, 1),
            byteArrayOf(240.toByte(), 0, 0, 1),
        )
    restricted.forEach { bytes ->
      val addr = InetAddress.getByAddress(bytes)
      assertThrows("Expected IPv4 ${addr.hostAddress} rejection", AiException::class.java) {
        WebUrlValidator.validatePublicHttpsUrl("https://example.com", resolverFor(addr))
      }
    }

    val restrictedV6 =
        listOf(
            byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
            byteArrayOf(0xfc.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
            byteArrayOf(0xfd.toByte(), 0x12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
            byteArrayOf(0xfe.toByte(), 0x80.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
            byteArrayOf(0xfe.toByte(), 0xc0.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
            byteArrayOf(0xff.toByte(), 0x02, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
            byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xff.toByte(), 0xff.toByte(), 127, 0, 0, 1),
            byteArrayOf(
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0xff.toByte(),
                0xff.toByte(),
                192.toByte(),
                168.toByte(),
                1,
                1,
            ),
        )
    restrictedV6.forEach { bytes ->
      val addr = Inet6Address.getByAddress("example.com", bytes, -1)
      assertThrows("Expected IPv6 ${addr.hostAddress} rejection", AiException::class.java) {
        WebUrlValidator.validatePublicHttpsUrl("https://example.com", resolverFor(addr))
      }
    }
  }

  @Test
  fun rejectsMixedPublicAndPrivateDnsResponsesAndUnknownHost() {
    val privateAddr = InetAddress.getByAddress(byteArrayOf(10, 0, 0, 5))
    assertThrows(AiException::class.java) {
      WebUrlValidator.validatePublicHttpsUrl(
          "https://example.com",
          resolverFor(publicIpv4, privateAddr),
      )
    }
    assertThrows(AiException::class.java) {
      WebUrlValidator.validatePublicHttpsUrl(
          "https://missing.example.invalid",
          HostResolver { throw UnknownHostException("nxdomain") },
      )
    }
  }

  @Test
  fun normalizesSearxngUrlsAndRejectsRemoteHttp() {
    assertThat(WebUrlValidator.normalizeSearxngUrl("https://searx.example.org/"))
        .isEqualTo("https://searx.example.org")
    assertThat(WebUrlValidator.normalizeSearxngUrl("searx.example.org/path/"))
        .isEqualTo("https://searx.example.org/path")
    assertThat(WebUrlValidator.normalizeSearxngUrl("http://localhost:8080/"))
        .isEqualTo("http://localhost:8080")
    assertThat(WebUrlValidator.normalizeSearxngUrl("http://127.0.0.1:8888"))
        .isEqualTo("http://127.0.0.1:8888")

    assertThat(WebUrlValidator.normalizeSearxngUrl("")).isNull()
    assertThat(WebUrlValidator.normalizeSearxngUrl("http://192.168.1.20:8080")).isNull()
    assertThat(WebUrlValidator.normalizeSearxngUrl("http://searx.example.org")).isNull()
    assertThat(WebUrlValidator.normalizeSearxngUrl("ftp://searx.example.org")).isNull()
    assertThat(WebUrlValidator.normalizeSearxngUrl("https://searx.example.org/?q=test")).isNull()
    assertThat(WebUrlValidator.normalizeSearxngUrl("https://user@searx.example.org")).isNull()
  }
}
