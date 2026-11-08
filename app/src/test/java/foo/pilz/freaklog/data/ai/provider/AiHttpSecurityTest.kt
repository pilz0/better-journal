package foo.pilz.freaklog.data.ai.provider

import assertk.assertFailure
import assertk.assertions.messageContains
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test

class AiHttpSecurityTest {
  @Test
  fun rejectsRemoteHttpBeforeSendingCredentialsOrJournalData() = runBlocking {
    for (url in listOf("http://192.168.1.5:11434/v1", "http://example.com/v1")) {
      assertFailure {
            AiHttp.getJson(url, mapOf("Authorization" to "Bearer private-key"))
          }
          .messageContains("HTTPS is required")
      assertFailure {
            AiHttp.postSse(url, emptyMap(), JSONObject().put("message", "private journal")).toList()
          }
          .messageContains("HTTPS is required")
    }
  }

  @Test
  fun rejectsDeeplyNestedJson() {
    val validNested = "{\"a\":".repeat(64) + "1" + "}".repeat(64)
    AiHttp.validateJsonNesting(validNested, maxDepth = 64)

    val excessivelyNested = "{\"a\":".repeat(65) + "1" + "}".repeat(65)
    org.junit.Assert.assertThrows(AiException::class.java) {
      AiHttp.validateJsonNesting(excessivelyNested, maxDepth = 64)
    }
  }
}
