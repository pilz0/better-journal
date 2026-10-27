package foo.pilz.freaklog.data.ai.provider

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
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

class AnthropicMessagesClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: AnthropicMessagesClient

    private val tool = AiToolSpec("search", "Search", JSONObject("""{"type":"object","properties":{}}"""))

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = AnthropicMessagesClient(AiEndpoint(server.url("/v1").toString(), "ant-key"))
    }

    @After
    fun tearDown() = server.shutdown()

    private fun sse(vararg events: Pair<String, String>) = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(events.joinToString("") { (name, data) -> "event: $name\ndata: $data\n\n" })

    private fun start(index: Int, block: String) =
        "content_block_start" to """{"type":"content_block_start","index":$index,"content_block":$block}"""

    private fun delta(index: Int, delta: String) =
        "content_block_delta" to """{"type":"content_block_delta","index":$index,"delta":$delta}"""

    private fun stop(index: Int) = "content_block_stop" to """{"type":"content_block_stop","index":$index}"""

    private fun stopReason(reason: String, details: String = "null") =
        "message_delta" to """{"type":"message_delta","delta":{"stop_reason":"$reason","stop_details":$details}}"""

    private fun request(vararg messages: AiMessage) =
        AiChatRequest("claude-test", "be safe", messages.toList(), listOf(tool), 2048)

    @Test
    fun streamsTextWithRequiredHeadersAndBody() = runBlocking {
        server.enqueue(
            sse(
                "message_start" to """{"type":"message_start","message":{"id":"m","type":"message","content":[]}}""",
                start(0, """{"type":"text","text":""}"""),
                "ping" to """{"type":"ping"}""",
                delta(0, """{"type":"text_delta","text":"Hel"}"""),
                delta(0, """{"type":"text_delta","text":"lo"}"""),
                stop(0),
                stopReason("end_turn"),
                "message_stop" to """{"type":"message_stop"}""",
            ),
        )
        val events = client.stream(request(AiMessage.User("hi"))).toList()
        assertThat(events.filterIsInstance<AiStreamEvent.TextDelta>().map { it.text }).containsExactly("Hel", "lo")
        val done = events.last() as AiStreamEvent.Completed
        assertThat(done.message.text).isEqualTo("Hello")
        assertThat(done.stopReason).isEqualTo("end_turn")

        val recorded = server.takeRequest()
        assertThat(recorded.path).isEqualTo("/v1/messages")
        assertThat(recorded.getHeader("x-api-key")).isEqualTo("ant-key")
        assertThat(recorded.getHeader("anthropic-version")).isEqualTo("2023-06-01")
        // Self-hosted endpoints never get the fallback beta.
        assertThat(recorded.getHeader("anthropic-beta")).isNull()
        val body = JSONObject(recorded.body.readUtf8())
        assertThat(body.getString("system")).isEqualTo("be safe")
        assertThat(body.getInt("max_tokens")).isEqualTo(2048)
        assertThat(body.has("fallbacks")).isFalse()
        assertThat(body.getJSONArray("tools").getJSONObject(0).has("input_schema")).isTrue()
    }

    @Test
    fun toolUseInputIsAssembledAndThinkingIsEchoedBack() = runBlocking {
        server.enqueue(
            sse(
                start(0, """{"type":"thinking","thinking":""}"""),
                delta(0, """{"type":"thinking_delta","thinking":"hmm"}"""),
                delta(0, """{"type":"signature_delta","signature":"SIG"}"""),
                stop(0),
                start(1, """{"type":"tool_use","id":"toolu_1","name":"search","input":{}}"""),
                delta(1, """{"type":"input_json_delta","partial_json":"{\"q\":"}"""),
                delta(1, """{"type":"input_json_delta","partial_json":"\"lsd\"}"}"""),
                stop(1),
                stopReason("tool_use"),
            ),
        )
        server.enqueue(sse(stopReason("end_turn")))

        val first = client.stream(request(AiMessage.User("hi"))).toList().last() as AiStreamEvent.Completed
        val call = first.message.toolCalls.single()
        assertThat(call.id).isEqualTo("toolu_1")
        assertThat(call.args["q"]).isEqualTo("lsd")

        val results = AiMessage.ToolResults(
            listOf(AiToolResult("toolu_1", "search", JSONObject().put("status", "error").put("message", "x"))),
        )
        client.stream(request(AiMessage.User("hi"), first.message, results)).toList()

        server.takeRequest()
        val messages = JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("messages")
        val content = messages.getJSONObject(1).getJSONArray("content")
        assertThat(content.getJSONObject(0).getString("type")).isEqualTo("thinking")
        assertThat(content.getJSONObject(0).getString("signature")).isEqualTo("SIG")
        assertThat(content.getJSONObject(1).getJSONObject("input").getString("q")).isEqualTo("lsd")
        val toolResult = messages.getJSONObject(2).getJSONArray("content").getJSONObject(0)
        assertThat(toolResult.getString("type")).isEqualTo("tool_result")
        assertThat(toolResult.getString("tool_use_id")).isEqualTo("toolu_1")
        assertThat(toolResult.getBoolean("is_error")).isTrue()
    }

    @Test
    fun refusalIsAnErrorThatDiscardsPartialText() {
        server.enqueue(
            sse(
                start(0, """{"type":"text","text":"Sure"}"""),
                stopReason("refusal", """{"type":"refusal","category":"bio"}"""),
            ),
        )
        assertFailure { runBlocking { client.stream(request(AiMessage.User("hi"))).toList() } }
            .isInstanceOf(AiException::class)
            .all {
                messageContains("declined")
                messageContains("bio")
                prop(AiException::discardPartial).isTrue()
            }
    }

    @Test
    fun blocksBeforeAFallbackBoundaryAreNotEchoed() = runBlocking {
        server.enqueue(
            sse(
                start(0, """{"type":"thinking","thinking":"","signature":"OLD"}"""),
                start(1, """{"type":"text","text":"Part "}"""),
                start(2, """{"type":"fallback","model":"claude-opus-4-8"}"""),
                start(3, """{"type":"text","text":"rest"}"""),
                stopReason("end_turn"),
            ),
        )
        val done = client.stream(request(AiMessage.User("hi"))).toList().last() as AiStreamEvent.Completed
        assertThat(done.message.text).isEqualTo("Part rest")
        val types = done.message.native!!.json.getJSONArray("content").let { arr ->
            (0 until arr.length()).map { arr.getJSONObject(it).getString("type") }
        }
        assertThat(types).containsExactly("text", "text")
    }

    @Test
    fun streamedErrorEventIsReported() {
        server.enqueue(
            sse("error" to """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}"""),
        )
        assertFailure { runBlocking { client.stream(request(AiMessage.User("hi"))).toList() } }
            .messageContains("overloaded")
    }

    @Test
    fun officialApiGetsServerSideFallbacksForSupportedModels() {
        val official = AnthropicMessagesClient(AiEndpoint("https://api.anthropic.com/v1", "k"))
        val body = official.buildBody(AiChatRequest("claude-opus-5-5", "s", emptyList(), emptyList(), 100))
        assertThat(body.getString("fallbacks")).isEqualTo("default")
        val other = official.buildBody(AiChatRequest("claude-haiku-4-5", "s", emptyList(), emptyList(), 100))
        assertThat(other.has("fallbacks")).isFalse()
    }

    @Test
    fun listModelsReadsIds() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"data":[{"id":"claude-b","type":"model"},{"id":"claude-a","type":"model"}],"has_more":false}"""),
        )
        assertThat(client.listModels()).containsExactly("claude-a", "claude-b")
        val recorded = server.takeRequest()
        assertThat(recorded.path).isEqualTo("/v1/models?limit=1000")
        assertThat(recorded.getHeader("anthropic-version")).isEqualTo("2023-06-01")
    }
}
