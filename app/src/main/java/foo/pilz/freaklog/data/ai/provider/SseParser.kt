package foo.pilz.freaklog.data.ai.provider

/** One server-sent event. [event] is null when the server sent no `event:` line. */
data class SseEvent(val event: String?, val data: String)

/**
 * Incremental Server-Sent Events parser (WHATWG `text/event-stream`). Feed it one line at a time;
 * it returns an event whenever a blank line completes one.
 */
class SseParser {
  // ponytail: readLine buffers one line whole before these checks; a bounded line reader is the
  // upgrade if a server ever streams a single endless line.
  private companion object {
    const val MAX_EVENT_CHARS = AiHttp.MAX_JSON_BYTES
    const val MAX_LINES_PER_EVENT = 100_000
  }

  private var event: String? = null
  private val data = StringBuilder()
  private var hasData = false
  private var lines = 0

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
        if (++lines > MAX_LINES_PER_EVENT || data.length > MAX_EVENT_CHARS) {
          throw AiException("The provider sent a stream event that is too large.")
        }
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
    lines = 0
    return result
  }
}
