package foo.pilz.freaklog.data.ai.provider

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.json.JSONObject
import org.junit.Test

class AiConversationTest {

    /** Replays one scripted reply per request and records what it was asked. */
    private class ScriptedClient(vararg replies: List<AiStreamEvent>) : AiChatClient {
        override val standard = AiApiStandard.OPENAI_CHAT
        private val queue = ArrayDeque(replies.toList())
        val requests = mutableListOf<AiChatRequest>()
        var hangAfterText: String? = null

        override fun stream(request: AiChatRequest): Flow<AiStreamEvent> = flow {
            requests += request
            hangAfterText?.let {
                emit(AiStreamEvent.TextDelta(it))
                awaitCancellation()
            }
            queue.removeFirst().forEach { emit(it) }
        }

        override suspend fun listModels() = emptyList<String>()
    }

    private fun text(value: String) = listOf(
        AiStreamEvent.TextDelta(value),
        AiStreamEvent.Completed(AiMessage.Assistant(value), "stop"),
    )

    private fun toolCall(vararg calls: AiToolCall, text: String = "") = listOf(
        AiStreamEvent.Completed(AiMessage.Assistant(text, calls.toList()), "tool_calls"),
    )

    private fun conversation(
        client: AiChatClient,
        maxRounds: Int = 8,
        tool: suspend (AiToolCall) -> JSONObject = { JSONObject().put("status", "success").put("echo", it.name) },
    ) = AiConversation(client, "m", "sys", emptyList(), 1000, tool, maxRounds)

    @Test
    fun plainAnswerStreamsAndIsStored() = runTest {
        val client = ScriptedClient(text("Hello"))
        val chat = conversation(client)
        val events = chat.send("hi").toList()
        assertThat(events).containsExactly(AiTurnEvent.TextDelta("Hello"), AiTurnEvent.Completed("Hello"))
        assertThat(chat.messages).containsExactly(AiMessage.User("hi"), AiMessage.Assistant("Hello"))
    }

    @Test
    fun toolCallsRunAndResultsGoBackToTheModel() = runTest {
        val call = AiToolCall("c1", "list_recent_experiences", mapOf("limit" to 3))
        val client = ScriptedClient(toolCall(call), text("Done"))
        val chat = conversation(client)
        val events = chat.send("what did I do").toList()

        assertThat(events.map { it::class.simpleName }).containsExactly(
            "ToolStarted", "ToolFinished", "TextDelta", "Completed",
        )
        val second = client.requests[1].messages
        assertThat(second.size).isEqualTo(3)
        val results = second[2] as AiMessage.ToolResults
        assertThat(results.results.single().callId).isEqualTo("c1")
        assertThat(results.results.single().result.getString("echo")).isEqualTo("list_recent_experiences")
    }

    @Test
    fun endlessToolCallsStopAtTheRoundCapAndLeaveValidHistory() = runTest {
        val call = AiToolCall("c", "t", emptyMap())
        val client = ScriptedClient(toolCall(call), toolCall(call), toolCall(call))
        val chat = conversation(client, maxRounds = 2)
        assertFailure { chat.send("loop").toList() }.isInstanceOf(AiException::class)
        // Nothing was answered, so the whole turn is rolled back.
        assertThat(chat.messages.isEmpty()).isTrue()
    }

    @Test
    fun cancelKeepsPartialTextAsAnswer() = runTest {
        val client = ScriptedClient().apply { hangAfterText = "Partial" }
        val chat = conversation(client)
        val started = CompletableDeferred<Unit>()
        val job = launch {
            chat.send("hi").collect { started.complete(Unit) }
        }
        started.await()
        job.cancelAndJoin()
        assertThat(chat.messages).containsExactly(AiMessage.User("hi"), AiMessage.Assistant("Partial"))
    }

    @Test
    fun cancelDuringToolDropsUnansweredToolCall() = runTest {
        val call = AiToolCall("c", "slow", emptyMap())
        val client = ScriptedClient(toolCall(call))
        val toolStarted = CompletableDeferred<Unit>()
        val chat = conversation(client) {
            toolStarted.complete(Unit)
            awaitCancellation()
        }
        val job = async { chat.send("hi").toList() }
        toolStarted.await()
        job.cancelAndJoin()
        yield()
        assertThat(chat.messages.isEmpty()).isTrue()
    }

    @Test
    fun streamErrorWithoutTextRollsBackTheTurn() = runTest {
        val failing = object : AiChatClient {
            override val standard = AiApiStandard.GEMINI
            override fun stream(request: AiChatRequest): Flow<AiStreamEvent> = flow { throw AiException("boom") }
            override suspend fun listModels() = emptyList<String>()
        }
        val chat = conversation(failing)
        assertFailure { chat.send("hi").toList() }.hasMessage("boom")
        assertThat(chat.messages.isEmpty()).isTrue()
    }

    @Test
    fun rollbackLastTurnReturnsTheUserText() = runTest {
        val client = ScriptedClient(text("one"), text("two"))
        val chat = conversation(client)
        chat.send("first").toList()
        chat.send("second").toList()
        assertThat(chat.rollbackLastTurn()).isEqualTo("second")
        assertThat(chat.messages).containsExactly(AiMessage.User("first"), AiMessage.Assistant("one"))
    }
}
