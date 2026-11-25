package foo.pilz.freaklog.data.ai

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale

internal class AiDateFormatter {
  private val baseFormatter: DateTimeFormatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)

  fun format(temporal: TemporalAccessor): String =
      baseFormatter.withZone(ZoneId.systemDefault()).format(temporal)
}

/** Shared display-format for instants used in the AI chatbot context and tool responses. */
internal val AI_DATE_FORMATTER: AiDateFormatter = AiDateFormatter()
