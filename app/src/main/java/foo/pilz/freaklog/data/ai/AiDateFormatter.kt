package foo.pilz.freaklog.data.ai

import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Shared display-format for instants used in the AI chatbot context and tool responses. */
internal val AI_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
