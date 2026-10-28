package foo.pilz.freaklog.data.ai.provider

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test

class GeminiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: GeminiClient

    private val tool = AiToolSpec(
        "search",
        "Search",
        JSONObject("""{"type":"object","properties":{"q":{"type":"string"}},"required":["q"]}"""),
    )

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = GeminiClient(AiEndpoint(server.url("/v1beta").toString(), "gem-key"))
    }

    @After
    fun tearDown() = server.shutdown()

    private fun sse(vararg chunks: String) = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(chunks.joinToString("") { "data: $it\r\n\r\n" })

    private fun request(vararg messages: AiMessage) =
        AiChatRequest("gemini-test", "be safe", messages.toList(), listOf(tool), 1000)

    @Test
    fun streamsTextAndSendsKeyHeaderAndUpperCaseSchema() = runBlocking {
        server.enqueue(
            sse(
                """{"candidates":[{"content":{"role":"model","parts":[{"text":"Hel"}]}}]}""",
                """{"candidates":[{"content":{"role":"model","parts":[{"text":"lo"}]},"finishReason":"STOP"}]}""",
            ),
        )
        val events = client.stream(request(AiMessage.User("hi"))).toList()

        assertThat(events.filterIsInstance<AiStreamEvent.TextDelta>().map { it.text }).containsExactly("Hel", "lo")
        val done = events.last() as AiStreamEvent.Completed
        assertThat(done.message.text).isEqualTo("Hello")

        val recorded = server.takeRequest()
        assertThat(recorded.path).isEqualTo("/v1beta/models/gemini-test:streamGenerateContent?alt=sse")
        assertThat(recorded.getHeader("x-goog-api-key")).isEqualTo("gem-key")
        val body = JSONObject(recorded.body.readUtf8())
        val params = body.getJSONArray("tools").getJSONObject(0)
            .getJSONArray("functionDeclarations").getJSONObject(0).getJSONObject("parameters")
        assertThat(params.getString("type")).isEqualTo("OBJECT")
        assertThat(params.getJSONObject("properties").getJSONObject("q").getString("type")).isEqualTo("STRING")
        assertThat(body.getJSONObject("system_instruction").getJSONArray("parts").getJSONObject(0).getString("text"))
            .isEqualTo("be safe")
    }

    @Test
    fun thoughtSignatureIsEchoedBackOnTheNextRequest() = runBlocking {
        server.enqueue(
            sse(
                """{"candidates":[{"content":{"role":"model","parts":[""" +
                    """{"functionCall":{"name":"search","args":{"q":"lsd"}},"thoughtSignature":"SIG123"}]},""" +
                    """"finishReason":"STOP"}]}""",
            ),
        )
        server.enqueue(sse("""{"candidates":[{"content":{"role":"model","parts":[{"text":"ok"}]}}]}"""))

        val first = client.stream(request(AiMessage.User("hi"))).toList().last() as AiStreamEvent.Completed
        val call = first.message.toolCalls.single()
        assertThat(call.name).isEqualTo("search")
        assertThat(call.args["q"]).isEqualTo("lsd")

        val results = AiMessage.ToolResults(listOf(AiToolResult(call.id, call.name, JSONObject().put("n", 1))))
        client.stream(request(AiMessage.User("hi"), first.message, results)).toList()

        server.takeRequest()
        val contents = JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("contents")
        val modelPart = contents.getJSONObject(1).getJSONArray("parts").getJSONObject(0)
        assertThat(modelPart.getString("thoughtSignature")).isEqualTo("SIG123")
        val response = contents.getJSONObject(2).getJSONArray("parts").getJSONObject(0).getJSONObject("functionResponse")
        assertThat(response.getString("name")).isEqualTo("search")
        assertThat(response.getJSONObject("response").getInt("n")).isEqualTo(1)
    }

    @Test
    fun httpErrorsBecomeReadableExceptions() {
        server.enqueue(
            MockResponse().setResponseCode(400).setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"code":400,"message":"API key not valid.","status":"INVALID_ARGUMENT"}}"""),
        )
        assertFailure { runBlocking { client.stream(request(AiMessage.User("hi"))).toList() } }
            .isInstanceOf(AiException::class)
            .messageContains("API key not valid.")
    }

    @Test
    fun listModelsKeepsOnlyGenerateContentModels() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"models":[
                  {"name":"models/gemini-b","supportedGenerationMethods":["generateContent"]},
                  {"name":"models/embedding","supportedGenerationMethods":["embedContent"]},
                  {"name":"models/gemini-a","supportedGenerationMethods":["countTokens","generateContent"]}]}""",
            ),
        )
        assertThat(client.listModels()).containsExactly("gemini-a", "gemini-b")
    }
}
