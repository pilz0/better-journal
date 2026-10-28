package foo.pilz.freaklog.data.ai.provider

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.messageContains
import assertk.assertions.prop
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test

class OpenAiChatClientTest {
    private lateinit var server: MockWebServer

    private val tool = AiToolSpec("search", "Search", JSONObject("""{"type":"object","properties":{}}"""))

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun client(key: String = "sk-test") = OpenAiChatClient(AiEndpoint(server.url("/v1/").toString(), key))

    private fun sse(vararg chunks: String) = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(chunks.joinToString("") { "data: $it\n\n" } + "data: [DONE]\n\n")

    private fun request(vararg messages: AiMessage) = AiChatRequest("gpt-test", "sys", messages.toList(), listOf(tool), 100)

    @Test
    fun streamsTextWithBearerKeyAndSystemMessage() = runBlocking {
        server.enqueue(
            sse(
                """{"choices":[{"index":0,"delta":{"role":"assistant","content":"Hi"}}]}""",
                """{"choices":[{"index":0,"delta":{"content":" there"},"finish_reason":"stop"}]}""",
            ),
        )
        val events = client().stream(request(AiMessage.User("hello"))).toList()
        assertThat(events.filterIsInstance<AiStreamEvent.TextDelta>().map { it.text }).containsExactly("Hi", " there")
        assertThat((events.last() as AiStreamEvent.Completed).message.text).isEqualTo("Hi there")

        val recorded = server.takeRequest()
        assertThat(recorded.path).isEqualTo("/v1/chat/completions")
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer sk-test")
        val body = JSONObject(recorded.body.readUtf8())
        assertThat(body.getBoolean("stream")).isEqualTo(true)
        assertThat(body.getJSONArray("messages").getJSONObject(0).getString("role")).isEqualTo("system")
        assertThat(body.getJSONArray("tools").getJSONObject(0).getJSONObject("function").getString("name"))
            .isEqualTo("search")
    }

    @Test
    fun blankKeySendsNoAuthorizationHeader() = runBlocking {
        server.enqueue(sse("""{"choices":[{"delta":{"content":"ok"}}]}"""))
        client(key = "").stream(request(AiMessage.User("hi"))).toList()
        assertThat(server.takeRequest().getHeader("Authorization")).isNull()
    }

    @Test
    fun reassemblesFragmentedParallelToolCalls() = runBlocking {
        server.enqueue(
            sse(
                """{"choices":[{"delta":{"tool_calls":[""" +
                    """{"index":0,"id":"call_a","type":"function","function":{"name":"search","arguments":""}}]}}]}""",
                """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"q\":"}}]}}]}""",
                """{"choices":[{"delta":{"tool_calls":[{"index":1,"id":"call_b","function":{"name":"stats","arguments":"{}"}}]}}]}""",
                """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"mdma\"}"}}]}}]}""",
                """{"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""",
            ),
        )
        val done = client().stream(request(AiMessage.User("hi"))).toList().last() as AiStreamEvent.Completed
        val calls = done.message.toolCalls
        assertThat(calls.map { it.id }).containsExactly("call_a", "call_b")
        assertThat(calls[0].args["q"]).isEqualTo("mdma")
        assertThat(calls[1].name).isEqualTo("stats")
        assertThat(done.stopReason).isEqualTo("tool_calls")
    }

    @Test
    fun toolHistoryUsesToolRoleWithCallId() {
        val call = AiToolCall("call_1", "search", mapOf("q" to "x"))
        val body = client().buildBody(
            request(
                AiMessage.User("hi"),
                AiMessage.Assistant("", listOf(call)),
                AiMessage.ToolResults(listOf(AiToolResult("call_1", "search", JSONObject().put("n", 2)))),
            ),
        )
        val messages = body.getJSONArray("messages")
        val assistant = messages.getJSONObject(2)
        assertThat(assistant.isNull("content")).isEqualTo(true)
        val fn = assistant.getJSONArray("tool_calls").getJSONObject(0).getJSONObject("function")
        assertThat(JSONObject(fn.getString("arguments")).getString("q")).isEqualTo("x")
        val tool = messages.getJSONObject(3)
        assertThat(tool.getString("role")).isEqualTo("tool")
        assertThat(tool.getString("tool_call_id")).isEqualTo("call_1")
        assertThat(JSONObject(tool.getString("content")).getInt("n")).isEqualTo(2)
    }

    @Test
    fun acceptsNonStreamingJsonReply() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"role":"assistant","content":"whole"},"finish_reason":"stop"}]}"""),
        )
        val done = client().stream(request(AiMessage.User("hi"))).toList().last() as AiStreamEvent.Completed
        assertThat(done.message.text).isEqualTo("whole")
    }

    @Test
    fun unauthorizedIsReported() {
        server.enqueue(
            MockResponse().setResponseCode(401).setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"message":"Incorrect API key provided","type":"invalid_request_error"}}"""),
        )
        assertFailure { runBlocking { client().stream(request(AiMessage.User("hi"))).toList() } }
            .isInstanceOf(AiException::class)
            .all {
                messageContains("API key was rejected")
                messageContains("Incorrect API key provided")
                prop(AiException::httpCode).isEqualTo(401)
            }
    }

    @Test
    fun rateLimitIsReported() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"too many requests"}"""))
        assertFailure { runBlocking { client().stream(request(AiMessage.User("hi"))).toList() } }
            .messageContains("Rate limited")
    }

    @Test
    fun listModelsReadsDataIds() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"object":"list","data":[{"id":"zeta"},{"id":"alpha"}]}"""),
        )
        assertThat(client().listModels()).containsExactly("alpha", "zeta")
        assertThat(server.takeRequest().path).isEqualTo("/v1/models")
    }

    @Test
    fun brokenArgumentsBecomeEmptyMap() = runBlocking {
        server.enqueue(
            sse("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c","function":{"name":"t","arguments":"{nope"}}]}}]}"""),
        )
        val done = client().stream(request(AiMessage.User("hi"))).toList().last() as AiStreamEvent.Completed
        assertThat(done.message.toolCalls.single().args.isEmpty()).isEqualTo(true)
        assertThat(done.message.text.isEmpty()).isEqualTo(true)
        assertThat(done.message.toolCalls.single().id == null).isFalse()
    }
}
