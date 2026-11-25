package foo.pilz.freaklog.data.ai.web

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AiWebToolsTest {
  private val client = mockk<AiWebClient>()
  private val settings = mockk<AiWebSettingsStore>()
  private val tools = AiWebTools(client, settings)

  @Test
  fun rejectsCallsWhenDisabled() = runTest {
    coEvery { settings.isEnabled() } returns false
    val search = tools.execute("web_search", mapOf("query" to "mdma"))!!
    val fetch = tools.execute("fetch_webpage", mapOf("url" to "https://example.com"))!!
    assertThat(search.getString("status")).isEqualTo("error")
    assertThat(fetch.getString("status")).isEqualTo("error")
  }

  @Test
  fun returnsHelpfulErrorsWhenSelectedBackendNotConfigured() = runTest {
    coEvery { settings.isEnabled() } returns true
    coEvery { settings.searchEngine() } returns AiWebSearchEngine.SEARXNG
    coEvery { settings.searxngUrl() } returns ""
    val searxErr = tools.execute("web_search", mapOf("query" to "lsd"))!!
    assertThat(searxErr.getString("status")).isEqualTo("error")
    assertThat(searxErr.getString("message")).contains("SearXNG URL")

    coEvery { settings.searchEngine() } returns AiWebSearchEngine.BRAVE
    coEvery { settings.braveApiKey() } returns ""
    val braveErr = tools.execute("web_search", mapOf("query" to "lsd"))!!
    assertThat(braveErr.getString("status")).isEqualTo("error")
    assertThat(braveErr.getString("message")).contains("Brave API key")
  }

  @Test
  fun webSearchClampsLimitAndAttachesUntrustedNotice() = runTest {
    coEvery { settings.isEnabled() } returns true
    coEvery { settings.searchEngine() } returns AiWebSearchEngine.DUCKDUCKGO
    coEvery { client.searchDuckDuckGo("ketamine", AiWebTools.MAX_SEARCH_LIMIT) } returns
        listOf(WebSearchResult("Title", "https://example.org", "Snippet"))

    val res = tools.execute("web_search", mapOf("query" to " ketamine ", "limit" to 99))!!
    assertThat(res.getString("status")).isEqualTo("success")
    assertThat(res.getString("engine")).isEqualTo("duckduckgo")
    assertThat(res.getJSONArray("results").length()).isEqualTo(1)
    assertThat(res.getString("untrusted_content_notice")).contains("untrusted data")
    coVerify { client.searchDuckDuckGo("ketamine", AiWebTools.MAX_SEARCH_LIMIT) }
  }

  @Test
  fun fetchWebpageTruncatesToMaxCharsAndReportsTotalChars() = runTest {
    coEvery { settings.isEnabled() } returns true
    coEvery { client.fetchPage("https://example.org/a") } returns
        WebPageContent(
            finalUrl = "https://example.org/a",
            title = "Example",
            contentType = "text/html",
            text = "x".repeat(500),
            byteCapped = false,
        )

    val res =
        tools.execute(
            "fetch_webpage",
            mapOf("url" to "https://example.org/a", "max_chars" to 250),
        )!!
    assertThat(res.getString("status")).isEqualTo("success")
    assertThat(res.getString("content").length).isEqualTo(250)
    assertThat(res.getBoolean("truncated")).isTrue()
    assertThat(res.getInt("total_chars")).isEqualTo(500)
    assertThat(res.getString("untrusted_content_notice")).contains("untrusted data")
  }
}
