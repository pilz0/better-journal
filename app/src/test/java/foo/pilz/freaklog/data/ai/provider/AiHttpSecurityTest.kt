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
            }.messageContains("HTTPS is required")
            assertFailure {
                AiHttp.postSse(url, emptyMap(), JSONObject().put("message", "private journal")).toList()
            }.messageContains("HTTPS is required")
        }
    }
}
