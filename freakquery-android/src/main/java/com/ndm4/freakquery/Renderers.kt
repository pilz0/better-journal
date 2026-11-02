package com.ndm4.freakquery

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object TextRenderer {
    fun render(data: Any?, plan: QueryPlan, ctx: Context): String {
        if (data == null) return ""
        if (data is String || data is Number || data is Boolean) return data.toString()
        if (data is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            return render(listOf(data as Row), plan, ctx)
        }
        val list = data as? List<*> ?: return data.toString()
        if (list.isEmpty()) return ""

        val showDose = truthy(plan.display["dose"], ctx.config.renderDose)
        val showUnit = truthy(plan.display["unit"], ctx.config.renderUnit)
        val showRoute = truthy(plan.display["route"], false)
        val showSite = truthy(plan.display["site"], false)
        val useParens = truthy(plan.display["parens"], ctx.config.renderParens)
        val showLabels = truthy(plan.display["labels"], ctx.config.renderLabels)
        val showCount = truthy(plan.display["count"], ctx.config.renderCount)
        val showPercent = truthy(plan.display["percent"], ctx.config.renderPercent)
        val compact = truthy(plan.display["compact"], ctx.config.renderCompact)
        val sep = plan.display["sep"]?.toString() ?: ctx.config.renderSeparator
        val timeMode: Any? = plan.display["time"]
            ?: ctx.config.renderTimeFormat
            ?: ctx.config.renderTimeEnabled

        val lines = list.map { item ->
            @Suppress("UNCHECKED_CAST")
            val row = item as? Row ?: return@map item.toString()

            when {
                row.containsKey("value") && row.containsKey("label") ->
                    renderRatioRow(row, showLabels, showCount, showPercent)
                row.containsKey("value") && row.containsKey("count") && !row.containsKey("label") ->
                    renderValueCount(row, showLabels, showCount, useParens, ctx.config)
                row.containsKey("substance") && row.containsKey("count") && row.size <= 2 ->
                    renderTopSubstance(row, showLabels, showCount, useParens, ctx.config)
                else -> renderNormalRow(row, showLabels, showDose, showUnit, showRoute, showSite, useParens, compact, sep, timeMode, ctx.config)
            }
        }

        return lines.joinToString("\n")
    }

    private fun renderRatioRow(item: Row, labels: Boolean, count: Boolean, percent: Boolean): String =
        listOfNotNull(
            item["value"]?.toString()?.takeIf { labels },
            item["label"]?.toString()?.takeIf { percent },
            item["count"]?.toString()?.takeIf { count }
        ).joinToString(" ").trim()

    private fun renderValueCount(item: Row, labels: Boolean, count: Boolean, parens: Boolean, config: FreakQueryConfig): String {
        val parts = mutableListOf<String>()
        if (labels) parts += Aliases.displayValue(config, "value", item["value"])
        val c = item["count"]?.toString() ?: ""
        parts += if (count) c else wrapCount(c, parens)
        return parts.filter { it.isNotBlank() }.joinToString(" ").trim()
    }

    private fun renderTopSubstance(item: Row, labels: Boolean, count: Boolean, parens: Boolean, config: FreakQueryConfig): String {
        val left = if (labels) Aliases.displayValue(config, "substance", item["substance"]) else ""
        val right = if (count) item["count"]?.toString() ?: "" else wrapCount(item["count"]?.toString() ?: "", parens)
        return listOf(left, right).filter { it.isNotBlank() }.joinToString(" ").trim()
    }

    private fun renderNormalRow(
        row: Row,
        showLabels: Boolean,
        showDose: Boolean,
        showUnit: Boolean,
        showRoute: Boolean,
        showSite: Boolean,
        useParens: Boolean,
        compact: Boolean,
        sep: String,
        timeMode: Any?,
        config: FreakQueryConfig
    ): String {
        val parts = mutableListOf<String>()
        val time = Aliases.rowGet(config, row, "time")
        if (truthy(timeMode, false) && time != null) parts += formatTime(time, timeMode)

        val sub = Aliases.rowGet(config, row, "substance")
        if (showLabels && sub != null && sub.toString().isNotBlank()) {
            parts += Aliases.displayValue(config, "substance", sub)
        }

        val extras = mutableListOf<String>()
        val dose = Aliases.rowGet(config, row, "dose")
        if (showDose && dose != null) {
            var text = cleanNumber(dose)
            val unit = Aliases.rowGet(config, row, "unit")
            if (showUnit && unit != null && unit.toString().isNotBlank()) {
                text += " " + Aliases.displayValue(config, "unit", unit)
            }
            extras += text
        }

        val route = Aliases.rowGet(config, row, "route")
        if (showRoute && route != null && route.toString().isNotBlank()) {
            extras += Aliases.displayValue(config, "route", route)
        }

        val site = Aliases.rowGet(config, row, "site")
        if (showSite && site != null && site.toString().isNotBlank()) {
            extras += Aliases.displayValue(config, "site", site)
        }

        if (extras.isNotEmpty()) {
            val text = extras.joinToString(sep)
            parts += if (useParens) "($text)" else text
        }

        if (parts.isEmpty()) parts += row.toString()
        return if (compact) parts.joinToString(sep) else parts.joinToString(" ")
    }

    private fun wrapCount(value: String, parens: Boolean): String = if (parens) "($value)" else value

    private fun truthy(value: Any?, default: Boolean): Boolean {
        if (value == null) return default
        if (value is Boolean) return value
        return when (value.toString().trim().lowercase(Locale.ROOT)) {
            "false", "0", "no", "off" -> false
            "true", "1", "yes", "on" -> true
            else -> default
        }
    }

    private fun formatTime(ms: Any?, mode: Any?): String {
        val epoch = ms?.toString()?.toLongOrNull() ?: return ms.toString()
        val pattern = when (mode.toString().lowercase(Locale.ROOT)) {
            "iso" -> "yyyy-MM-dd'T'HH:mm:ss"
            "date" -> "yyyy-MM-dd"
            else -> "HH:mm"
        }
        return SimpleDateFormat(pattern, Locale.US).format(Date(epoch))
    }
}

internal object JsonRenderer {
    private const val MAX_DEPTH = 32

    fun render(data: Any?, plan: QueryPlan, ctx: Context): String {
        val maxLen = ctx.config.maxOutputLength ?: Int.MAX_VALUE
        val sb = StringBuilder()
        serialize(data, ctx.config, sb, 0, maxLen)
        return sb.toString()
    }

    private fun serialize(data: Any?, config: FreakQueryConfig, sb: StringBuilder, depth: Int, maxLen: Int) {
        if (sb.length >= maxLen) return
        if (depth > MAX_DEPTH) {
            sb.append("\"[depth-limit-exceeded]\"")
            return
        }
        when (data) {
            null -> sb.append("null")
            is Boolean -> sb.append(data)
            is Number -> sb.append(data)
            is String -> quoteString(data, sb, maxLen)
            is List<*> -> {
                sb.append('[')
                data.forEachIndexed { index, item ->
                    if (index > 0) sb.append(',')
                    if (sb.length >= maxLen) return
                    serialize(item, config, sb, depth + 1, maxLen)
                }
                sb.append(']')
            }
            is Map<*, *> -> {
                val normalized = normalizeObj(data, config)
                sb.append('{')
                var first = true
                for ((key, value) in normalized) {
                    if (!first) sb.append(',')
                    first = false
                    if (sb.length >= maxLen) return
                    quoteString(key, sb, maxLen)
                    sb.append(':')
                    serialize(value, config, sb, depth + 1, maxLen)
                }
                sb.append('}')
            }
            else -> quoteString(data.toString(), sb, maxLen)
        }
    }

    private fun quoteString(s: String, sb: StringBuilder, maxLen: Int) {
        sb.append('"')
        for (i in 0 until s.length) {
            if (sb.length >= maxLen - 1) break
            when (val c = s[i]) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c < ' ') {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        sb.append('"')
    }

    private fun normalizeObj(obj: Map<*, *>, config: FreakQueryConfig): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>()
        obj.forEach { (key, value) -> if (key != null) out[key.toString()] = value }
        for (field in listOf("substance", "route", "site", "unit")) {
            val value = Aliases.rowGet(config, out, field)
            if (value != null) out[field] = Aliases.displayValue(config, field, value)
        }
        return out
    }
}
