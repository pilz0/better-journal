package foo.pilz.freaklog.data.ai.web

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import foo.pilz.freaklog.data.ai.provider.AiException
import java.net.URL
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AiWebClientTest {
  private lateinit var server: MockWebServer
  private val validatedUrls = mutableListOf<String>()
  private lateinit var client: AiWebClient

  @Before
  fun setUp() {
    server = MockWebServer().apply { start() }
    validatedUrls.clear()
    client =
        AiWebClient(
            urlValidator = { raw ->
              validatedUrls += raw
              URL(raw)
            },
            duckDuckGoUrl = server.url("/ddg").toString(),
            braveApiUrl = server.url("/brave").toString(),
        )
  }

  @After fun tearDown() = server.shutdown()

  @Test
  fun parsesDuckDuckGoResultsUnwrapsRedirectAndSkipsAds() = runTest {
    val html =
        """
        <html><body>
          <div class="result results_links">
            <a rel="nofollow" class="result__a" href="https://duckduckgo.com/y.js?ad_provider=bing">Sponsored Ad</a>
            <a class="result__snippet">Buy stuff</a>
          </div>
          <div class="result results_links">
            <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fen.wikipedia.org%2Fwiki%2FMDMA&amp;rut=123">MDMA &amp; Facts</a>
            <a class="result__snippet">Overview of <b>MDMA</b> pharmacology.</a>
          </div>
          <div class="result results_links">
            <a href="https://psychonautwiki.org/wiki/MDMA" class="result__a">PsychonautWiki</a>
            <div class="result__snippet">Dose and duration chart.</div>
          </div>
        </body></html>
        """
            .trimIndent()
    server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody(html))

    val results = client.searchDuckDuckGo("MDMA pharmacology", 5)
    assertThat(results).hasSize(2)
    assertThat(results[0].title).isEqualTo("MDMA & Facts")
    assertThat(results[0].url).isEqualTo("https://en.wikipedia.org/wiki/MDMA")
    assertThat(results[0].snippet).isEqualTo("Overview of MDMA pharmacology.")
    assertThat(results[1].url).isEqualTo("https://psychonautwiki.org/wiki/MDMA")
    assertThat(results[1].snippet).isEqualTo("Dose and duration chart.")
  }

  @Test
  fun duckDuckGoChallengeThrowsReadableAiException() = runTest {
    server.enqueue(MockResponse().setResponseCode(202).setBody("<div id=\"anomaly-modal\"></div>"))
    try {
      client.searchDuckDuckGo("lsd", 5)
      throw AssertionError("Expected AiException")
    } catch (e: AiException) {
      assertThat(e.message.orEmpty()).contains("DuckDuckGo")
    }
  }

  @Test
  fun searxngParsesJsonAndRejectsNonJson() = runTest {
    server.enqueue(
        MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                """{"results":[{"title":"<b>Study</b>","url":"https://pubmed.ncbi.nlm.nih.gov/1/","content":"Abstract &amp; summary"}]}"""
            )
    )
    val baseUrl = server.url("").toString().trimEnd('/')
    val results = client.searchSearxng(baseUrl, "psilocybin", 5)
    val req = server.takeRequest()
    assertThat(req.path).isEqualTo("/search?q=psilocybin&format=json")
    assertThat(results).hasSize(1)
    assertThat(results[0].title).isEqualTo("Study")
    assertThat(results[0].snippet).isEqualTo("Abstract & summary")

    server.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))
    try {
      client.searchSearxng(baseUrl, "psilocybin", 5)
      throw AssertionError("Expected AiException")
    } catch (e: AiException) {
      assertThat(e.message.orEmpty()).contains("JSON")
    }
  }

  @Test
  fun braveSearchSendsSubscriptionTokenAndParsesResults() = runTest {
    server.enqueue(
        MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                """{"web":{"results":[{"title":"Erowid","url":"https://erowid.org/","description":"Vault &amp; guides"}]}}"""
            )
    )
    val results = client.searchBrave("brave-secret-key", "mescaline", 3)
    val req = server.takeRequest()
    assertThat(req.getHeader("X-Subscription-Token")).isEqualTo("brave-secret-key")
    assertThat(req.path).isEqualTo("/brave?q=mescaline&count=3")
    assertThat(results).hasSize(1)
    assertThat(results[0].title).isEqualTo("Erowid")
    assertThat(results[0].snippet).isEqualTo("Vault & guides")
  }

  @Test
  fun fetchPageFollowsRedirectsRevalidatesEachHopAndCachesResult() = runTest {
    val targetUrl = server.url("/article").toString()
    server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/article"))
    server.enqueue(
        MockResponse()
            .setHeader("Content-Type", "text/html; charset=utf-8")
            .setBody("<html><head><title>Paper</title></head><body><p>Body text</p></body></html>")
    )

    val initialUrl = server.url("/redirect").toString()
    val first = client.fetchPage(initialUrl)
    val second = client.fetchPage(initialUrl)

    assertThat(first.finalUrl).isEqualTo(targetUrl)
    assertThat(first.title).isEqualTo("Paper")
    assertThat(first.text).isEqualTo("Body text")
    assertThat(second).isEqualTo(first)
    assertThat(server.requestCount).isEqualTo(2)
    assertThat(validatedUrls).isEqualTo(listOf(initialUrl, targetUrl))
  }

  @Test
  fun fetchPageSupportsPlainTextRejectsBinaryCapsSizeAndLimitsRedirects() = runTest {
    server.enqueue(MockResponse().setHeader("Content-Type", "text/plain").setBody("Line 1\nLine 2"))
    val plain = client.fetchPage(server.url("/notes.txt").toString())
    assertThat(plain.title).isEqualTo("")
    assertThat(plain.text).isEqualTo("Line 1\nLine 2")

    server.enqueue(MockResponse().setHeader("Content-Type", "application/pdf").setBody("%PDF-1.7"))
    try {
      client.fetchPage(server.url("/doc.pdf").toString())
      throw AssertionError("Expected AiException")
    } catch (e: AiException) {
      assertThat(e.message.orEmpty()).contains("Unsupported content type")
    }

    val largeBody = "a".repeat(AiWebClient.MAX_FETCH_BYTES + 1000)
    server.enqueue(MockResponse().setHeader("Content-Type", "text/plain").setBody(largeBody))
    val capped = client.fetchPage(server.url("/huge.txt").toString())
    assertThat(capped.byteCapped).isTrue()
    assertThat(capped.text.length).isEqualTo(AiWebClient.MAX_FETCH_BYTES)

    repeat(6) {
      server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/loop"))
    }
    assertThrows(AiException::class.java) {
      kotlinx.coroutines.runBlocking { client.fetchPage(server.url("/loop").toString()) }
    }
  }
}
