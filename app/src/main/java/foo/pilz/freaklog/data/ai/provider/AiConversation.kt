package foo.pilz.freaklog.data.ai.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject

/** What happens during one user turn, in order. */
sealed interface AiTurnEvent {
    data class TextDelta(val text: String) : AiTurnEvent

    /** [key] is unique within the turn and pairs a start with its finish. */
    data class ToolStarted(val key: String, val call: AiToolCall) : AiTurnEvent
    data class ToolFinished(val key: String, val call: AiToolCall, val result: JSONObject) : AiTurnEvent

    /** The model answered without asking for more tools. */
    data class Completed(val text: String) : AiTurnEvent
}

/**
 * A provider-neutral multi-turn chat with tool calling. Each [send] streams the model's reply,
 * runs the tools it asks for, sends the results back and repeats until the model answers, at
 * most [maxToolRounds] times.
 *
 * If a turn is cancelled or fails, the history is repaired so the next request is still valid:
 * an assistant message whose tool calls never got results is removed, partial text is kept as a
 * normal assistant reply, and a turn that produced nothing is rolled back entirely.
 */
class AiConversation(
    private val client: AiChatClient,
    private val model: String,
    private val system: String,
    private val tools: List<AiToolSpec>,
    private val maxOutputTokens: Int,
    private val executeTool: suspend (AiToolCall) -> JSONObject,
    private val maxToolRounds: Int = DEFAULT_MAX_TOOL_ROUNDS,
) {
    companion object {
        const val DEFAULT_MAX_TOOL_ROUNDS = 8

        /** Real turns use a handful of tools; a provider asking for more is refused, not run. */
        const val MAX_TOOL_CALLS_PER_TURN = 32
    }

    private val history = mutableListOf<AiMessage>()

    val messages: List<AiMessage> get() = history.toList()

    fun send(text: String): Flow<AiTurnEvent> = flow {
        val checkpoint = history.size
        history += AiMessage.User(text)
        var finished = false
        val partial = StringBuilder()
        var toolCallsRun = 0
        try {
            for (round in 0..maxToolRounds) {
                partial.setLength(0)
                var reply: AiMessage.Assistant? = null
                client.stream(request()).collect { event ->
                    when (event) {
                        is AiStreamEvent.TextDelta -> {
                            partial.append(event.text)
                            emit(AiTurnEvent.TextDelta(event.text))
                        }
                        is AiStreamEvent.Completed -> reply = event.message
                    }
                }
                val assistant = reply ?: throw AiException("The response ended unexpectedly.")
                history += assistant
                if (assistant.toolCalls.isEmpty()) {
                    finished = true
                    emit(AiTurnEvent.Completed(assistant.text))
                    return@flow
                }
                if (round == maxToolRounds) {
                    throw AiException(
                        "The assistant kept calling tools without answering. Try a more specific question.",
                    )
                }
                toolCallsRun += assistant.toolCalls.size
                if (toolCallsRun > MAX_TOOL_CALLS_PER_TURN) {
                    throw AiException("The assistant asked for too many tools at once. Try a more specific question.")
                }
                val results = assistant.toolCalls.mapIndexed { index, call ->
                    val key = "$round-$index"
                    emit(AiTurnEvent.ToolStarted(key, call))
                    val result = executeTool(call)
                    emit(AiTurnEvent.ToolFinished(key, call, result))
                    AiToolResult(call.id, call.name, result)
                }
                history += AiMessage.ToolResults(results)
                partial.setLength(0)
            }
        } catch (e: AiException) {
            if (e.discardPartial) partial.setLength(0)
            throw e
        } finally {
            if (!finished) repair(checkpoint, partial.toString())
        }
    }

    /**
     * Removes the last user message and everything after it, and returns its text so the turn
     * can be sent again.
     */
    fun rollbackLastTurn(): String? {
        val index = history.indexOfLast { it is AiMessage.User }
        if (index < 0) return null
        val text = (history[index] as AiMessage.User).text
        while (history.size > index) history.removeAt(history.lastIndex)
        return text
    }

    private fun request() = AiChatRequest(model, system, history.toList(), tools, maxOutputTokens)

    private fun repair(checkpoint: Int, partialText: String) {
        var text = partialText
        val last = history.lastOrNull()
        if (history.size > checkpoint && last is AiMessage.Assistant && last.toolCalls.isNotEmpty()) {
            history.removeAt(history.lastIndex)
            if (text.isBlank()) text = last.text
        }
        if (text.isNotBlank()) {
            history += AiMessage.Assistant(text)
        } else {
            while (history.size > checkpoint) history.removeAt(history.lastIndex)
        }
    }
}
