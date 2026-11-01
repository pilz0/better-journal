package com.ndm4.freakquery

import java.io.File
import java.io.InputStream
import java.util.Locale

object FreakQuery {
    const val VERSION = "3.2.2"

    @JvmStatic
    fun loadLogs(json: String, config: FreakQueryConfig = FreakQueryConfig()): List<Map<String, Any?>> =
        FreakQueryLoader.loadLogs(json, config)

    @JvmStatic
    fun loadLogs(file: File, config: FreakQueryConfig = FreakQueryConfig()): List<Map<String, Any?>> =
        FreakQueryLoader.loadLogs(file, config)

    @JvmStatic
    fun loadLogs(inputStream: InputStream, config: FreakQueryConfig = FreakQueryConfig()): List<Map<String, Any?>> =
        FreakQueryLoader.loadLogs(inputStream, config)

    @JvmStatic
    fun query(tag: String, source: String, config: FreakQueryConfig = FreakQueryConfig()): String =
        query(tag, loadLogs(source, config), config)

    @JvmStatic
    fun query(tag: String, file: File, config: FreakQueryConfig = FreakQueryConfig()): String =
        query(tag, loadLogs(file, config), config)

    @JvmStatic
    fun query(tag: String, inputStream: InputStream, config: FreakQueryConfig = FreakQueryConfig()): String =
        query(tag, loadLogs(inputStream, config), config)

    @JvmStatic
    fun query(tag: String, data: List<Map<String, Any?>>, config: FreakQueryConfig = FreakQueryConfig()): String {
        var cleanTag = tag.trim()
        if (cleanTag.startsWith("{{") && cleanTag.endsWith("}}")) {
            cleanTag = cleanTag.removePrefix("{{").removeSuffix("}}").trim()
        }
        return executeTag(cleanTag, data, Context(data, config)).trimEnd()
    }

    @JvmStatic
    fun render(template: String, source: String, config: FreakQueryConfig = FreakQueryConfig()): String =
        render(template, loadLogs(source, config), config)

    @JvmStatic
    fun render(template: String, file: File, config: FreakQueryConfig = FreakQueryConfig()): String =
        render(template, loadLogs(file, config), config)

    @JvmStatic
    fun render(template: String, inputStream: InputStream, config: FreakQueryConfig = FreakQueryConfig()): String =
        render(template, loadLogs(inputStream, config), config)

    @JvmStatic
    fun render(template: String, data: List<Map<String, Any?>>, config: FreakQueryConfig = FreakQueryConfig()): String {
        val ctx = Context(data, config)
        val maxLen = config.maxOutputLength
        // Forward-only scan: once a `{{` has no closing `}}`, no later one can either, so stop
        // there instead of rescanning the rest of the template for every opening.
        return buildString {
            var start = 0
            while (true) {
                if (maxLen != null && length >= maxLen) break
                val open = template.indexOf("{{", start)
                val close = if (open >= 0) template.indexOf("}}", open + 2) else -1
                if (close < 0) {
                    val remaining = template.substring(start)
                    val toAppend = if (maxLen != null && length + remaining.length > maxLen) {
                        remaining.take(maxLen - length)
                    } else remaining
                    append(toAppend)
                    break
                }
                val prefix = template.substring(start, open)
                if (maxLen != null && length + prefix.length > maxLen) {
                    append(prefix.take(maxLen - length))
                    break
                }
                append(prefix)
                val tag = template.substring(open + 2, close).trim()
                val evaluated = runCatching { executeTag(tag, data, ctx) }.getOrElse { "[error:$tag]" }
                if (maxLen != null && length + evaluated.length > maxLen) {
                    append(evaluated.take(maxLen - length))
                    break
                }
                append(evaluated)
                start = close + 2
            }
        }
    }

    internal fun executeTag(tag: String, data: Rows, ctx: Context): String {
        val raw = tag.trim()
        if (raw.isEmpty()) return ""

        val low = raw.lowercase(Locale.ROOT)
        if (low == "version") return ctx.config.version
        if (low == "version|json" || low == "json|version") return """{"version":"${ctx.config.version}"}"""

        val parts = Planner.normalizeParts(Planner.parseTag(raw))
        val (ok, error) = Planner.validateParts(parts)
        if (!ok) return "[error:$error]"

        val plan = Planner.buildPlan(parts, ctx.config)
        if (emptyPlan(plan)) return unknownError(raw)

        val wantedField = parts.firstOrNull { it.lowercase(Locale.ROOT).startsWith("field=") }?.substringAfter("=")?.trim()

        val filtered: Rows = Filters.apply(data.toList(), plan, ctx)
        val grouped: Any = Grouping.apply(filtered, plan, ctx)
        var rows: Any = Selectors.apply(grouped, plan, ctx)

        if (wantedField != null) {
            val flatRows: Rows = when {
                rows is List<*> && rows.firstOrNull() is List<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    (rows as List<Rows>).firstOrNull().orEmpty()
                }
                rows is List<*> -> {
                    rows.filterIsInstance<Row>()
                }
                else -> emptyList()
            }
            val row = flatRows.firstOrNull() ?: return ""
            return Aliases.rowGet(ctx.config, row, wantedField)?.let { cleanNumber(it) }.orEmpty()
        }

        rows = Metrics.apply(rows, plan, ctx)
        val transformed = Transforms.apply(rows, plan, ctx)
        return if ("json" in plan.formats) {
            JsonRenderer.render(transformed, plan, ctx)
        } else {
            TextRenderer.render(transformed, plan, ctx)
        }
    }

    private fun emptyPlan(plan: QueryPlan): Boolean =
        plan.filters.isEmpty() && plan.group == null && plan.selector == null && plan.metrics.isEmpty() && plan.formats.isEmpty()

    private fun unknownError(raw: String): String {
        val guess = closestTag(raw)
        return if (guess != null) {
            "[error: unknown query '$raw' (did you mean '$guess'?)]"
        } else {
            "[error: unknown query '$raw']"
        }
    }
}
