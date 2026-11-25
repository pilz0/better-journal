package com.ndm4.freakquery

import java.io.File
import java.io.InputStream
import java.util.Locale

object FreakQuery {
  const val VERSION = "3.2.2"

  @JvmStatic
  fun loadLogs(
      json: String,
      config: FreakQueryConfig = FreakQueryConfig(),
  ): List<Map<String, Any?>> = FreakQueryLoader.loadLogs(json, config)

  @JvmStatic
  fun loadLogs(file: File, config: FreakQueryConfig = FreakQueryConfig()): List<Map<String, Any?>> =
      FreakQueryLoader.loadLogs(file, config)

  @JvmStatic
  fun loadLogs(
      inputStream: InputStream,
      config: FreakQueryConfig = FreakQueryConfig(),
  ): List<Map<String, Any?>> = FreakQueryLoader.loadLogs(inputStream, config)

  @JvmStatic
  fun query(tag: String, source: String, config: FreakQueryConfig = FreakQueryConfig()): String =
      query(tag, loadLogs(source, config), config)

  @JvmStatic
  fun query(tag: String, file: File, config: FreakQueryConfig = FreakQueryConfig()): String =
      query(tag, loadLogs(file, config), config)

  @JvmStatic
  fun query(
      tag: String,
      inputStream: InputStream,
      config: FreakQueryConfig = FreakQueryConfig(),
  ): String = query(tag, loadLogs(inputStream, config), config)

  @JvmStatic
  fun query(
      tag: String,
      data: List<Map<String, Any?>>,
      config: FreakQueryConfig = FreakQueryConfig(),
  ): String {
    var cleanTag = tag.trim()
    if (cleanTag.startsWith("{{") && cleanTag.endsWith("}}")) {
      cleanTag = cleanTag.removePrefix("{{").removeSuffix("}}").trim()
    }
    return executeTag(cleanTag, data, Context(data, config)).trimEnd()
  }

  @JvmStatic
  fun render(
      template: String,
      source: String,
      config: FreakQueryConfig = FreakQueryConfig(),
  ): String = render(template, loadLogs(source, config), config)

  @JvmStatic
  fun render(template: String, file: File, config: FreakQueryConfig = FreakQueryConfig()): String =
      render(template, loadLogs(file, config), config)

  @JvmStatic
  fun render(
      template: String,
      inputStream: InputStream,
      config: FreakQueryConfig = FreakQueryConfig(),
  ): String = render(template, loadLogs(inputStream, config), config)

  @JvmStatic
  fun render(
      template: String,
      data: List<Map<String, Any?>>,
      config: FreakQueryConfig = FreakQueryConfig(),
  ): String {
    val ctx = Context(data, config)
    val maxLen = config.maxOutputLength
    // Forward-only scan: once a `{{` has no closing `}}`, no later one can either, so stop
    // there instead of rescanning the rest of the template for every opening.
    return buildString {
      var start = 0
      var done = false
      while (!done && (maxLen == null || length < maxLen)) {
        val open = template.indexOf("{{", start)
        val close = if (open >= 0) template.indexOf("}}", open + 2) else -1
        if (close < 0) {
          val remaining = template.substring(start)
          append(if (maxLen != null) remaining.take(maxLen - length) else remaining)
          done = true
        } else {
          val prefix = template.substring(start, open)
          if (maxLen != null && length + prefix.length > maxLen) {
            append(prefix.take(maxLen - length))
            done = true
          } else {
            append(prefix)
            val tag = template.substring(open + 2, close).trim()
            val evaluated = runCatching { executeTag(tag, data, ctx) }.getOrElse { "[error:$tag]" }
            if (maxLen != null && length + evaluated.length > maxLen) {
              append(evaluated.take(maxLen - length))
              done = true
            } else {
              append(evaluated)
              start = close + 2
            }
          }
        }
      }
    }
  }

  internal fun executeTag(tag: String, data: Rows, ctx: Context): String {
    val raw = tag.trim()
    val low = raw.lowercase(Locale.ROOT)
    val builtIn =
        when {
          raw.isEmpty() -> ""
          low == "version" -> ctx.config.version
          low == "version|json" || low == "json|version" ->
              """{"version":"${ctx.config.version}"}"""
          else -> null
        }
    if (builtIn != null) return builtIn

    val parts = Planner.normalizeParts(Planner.parseTag(raw))
    val (ok, error) = Planner.validateParts(parts)
    if (!ok) return "[error:$error]"

    val plan = Planner.buildPlan(parts, ctx.config)
    if (emptyPlan(plan)) return unknownError(raw)

    val wantedField =
        parts
            .firstOrNull { it.lowercase(Locale.ROOT).startsWith("field=") }
            ?.substringAfter("=")
            ?.trim()

    val filtered: Rows = Filters.apply(data.toList(), plan, ctx)
    val grouped: Any = Grouping.apply(filtered, plan, ctx)
    var rows: Any = Selectors.apply(grouped, plan, ctx)

    return if (wantedField != null) {
      extractWantedField(rows, wantedField, ctx.config)
    } else {
      rows = Metrics.apply(rows, plan, ctx)
      val transformed = Transforms.apply(rows, plan, ctx)
      if ("json" in plan.formats) {
        JsonRenderer.render(transformed, ctx)
      } else {
        TextRenderer.render(transformed, plan, ctx)
      }
    }
  }

  private fun extractWantedField(rows: Any, wantedField: String, config: FreakQueryConfig): String {
    val flatRows: Rows =
        when {
          rows is List<*> && rows.firstOrNull() is List<*> -> {
            @Suppress("UNCHECKED_CAST") (rows as List<Rows>).firstOrNull().orEmpty()
          }
          rows is List<*> -> rows.filterIsInstance<Row>()
          else -> emptyList()
        }
    val row = flatRows.firstOrNull() ?: return ""
    return Aliases.rowGet(config, row, wantedField)?.let { cleanNumber(it) }.orEmpty()
  }

  private fun emptyPlan(plan: QueryPlan): Boolean =
      plan.filters.isEmpty() &&
          plan.group == null &&
          plan.selector == null &&
          plan.metrics.isEmpty() &&
          plan.formats.isEmpty()

  private fun unknownError(raw: String): String {
    val guess = closestTag(raw)
    return if (guess != null) {
      "[error: unknown query '$raw' (did you mean '$guess'?)]"
    } else {
      "[error: unknown query '$raw']"
    }
  }
}
