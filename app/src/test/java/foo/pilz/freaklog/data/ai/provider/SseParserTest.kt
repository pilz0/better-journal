package foo.pilz.freaklog.data.ai.provider

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test

class SseParserTest {

    private fun parse(vararg lines: String): List<SseEvent> {
        val parser = SseParser()
        val events = lines.mapNotNull { parser.feed(it) }
        return events + listOfNotNull(parser.finish())
    }

    @Test
    fun parsesNamedEventsSeparatedByBlankLines() {
        val events = parse(
            "event: message_start",
            "data: {\"a\":1}",
            "",
            "event: ping",
            "data: {}",
            "",
        )
        assertThat(events).containsExactly(SseEvent("message_start", "{\"a\":1}"), SseEvent("ping", "{}"))
    }

    @Test
    fun joinsMultiLineDataAndIgnoresComments() {
        val events = parse(": keep-alive", "data: one", "data:two", "", "")
        assertThat(events).containsExactly(SseEvent(null, "one\ntwo"))
    }

    @Test
    fun flushesUnterminatedFinalEvent() {
        assertThat(parse("data: [DONE]")).containsExactly(SseEvent(null, "[DONE]"))
    }

    @Test
    fun eventNameDoesNotLeakIntoNextEvent() {
        val parser = SseParser()
        parser.feed("event: error")
        parser.feed("data: x")
        assertThat(parser.feed("")?.event).isEqualTo("error")
        parser.feed("data: y")
        assertThat(parser.feed("")?.event).isNull()
    }
}
