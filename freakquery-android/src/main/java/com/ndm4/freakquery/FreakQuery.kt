package com.ndm4.freakquery

import java.io.File
import java.io.InputStream

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
        return Regex("\\{\\{(.*?)\\}\\}", RegexOption.DOT_MATCHES_ALL).replace(template) { match ->
            val tag = match.groupValues[1].trim()
            runCatching { executeTag(tag, data, ctx) }.getOrElse { "[error:$tag]" }
        }
    }

    internal fun executeTag(tag: String, data: Rows, ctx: Context): String {
        val raw = tag.trim()
        if (raw.isEmpty()) return ""

        val low = raw.lowercase()
        if (low == "version") return ctx.config.version
        if (low == "version|json" || low == "json|version") return """{"version":"${ctx.config.version}"}"""

        val parts = Planner.normalizeParts(Planner.parseTag(raw))
        val (ok, error) = Planner.validateParts(parts)
        if (!ok) return "[error:$error]"

        val plan = Planner.buildPlan(parts, ctx.config)
        if (emptyPlan(plan)) return unknownError(raw)

        val wantedField = parts.firstOrNull { it.lowercase().startsWith("field=") }?.substringAfter("=")?.trim()

        val filtered: Rows = Filters.apply(data.toList(), plan, ctx)
        val grouped: Any = Grouping.apply(filtered, plan, ctx)
        var rows: Any = Selectors.apply(grouped, plan, ctx)

        if (wantedField != null) {
            @Suppress("UNCHECKED_CAST")
            val selectedRows = rows as? Rows ?: return ""
            val row = selectedRows.firstOrNull() ?: return ""
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
