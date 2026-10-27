package foo.pilz.freaklog.data.ai.provider

/** One server-sent event. [event] is null when the server sent no `event:` line. */
data class SseEvent(val event: String?, val data: String)

/**
 * Incremental Server-Sent Events parser (WHATWG `text/event-stream`). Feed it one line at a
 * time; it returns an event whenever a blank line completes one.
 */
class SseParser {
    private var event: String? = null
    private val data = StringBuilder()
    private var hasData = false

    fun feed(line: String): SseEvent? {
        if (line.isEmpty()) return dispatch()
        if (line.startsWith(":")) return null // comment / keep-alive
        val colon = line.indexOf(':')
        val field = if (colon == -1) line else line.substring(0, colon)
        val value = if (colon == -1) "" else line.substring(colon + 1).removePrefix(" ")
        when (field) {
            "event" -> event = value
            "data" -> {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
        }
        return null
    }

    /** Flushes an event the server didn't terminate with a blank line. */
    fun finish(): SseEvent? = dispatch()

    private fun dispatch(): SseEvent? {
        val result = if (hasData) SseEvent(event, data.toString()) else null
        event = null
        data.setLength(0)
        hasData = false
        return result
    }
}
