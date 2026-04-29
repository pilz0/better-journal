package com.ndm4.freakquery

import org.json.JSONArray
import org.json.JSONObject
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
        val timeMode = plan.display["time"] ?: ctx.config.renderTime

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
        val c = item["count"].toString()
        parts += if (count) c else wrapCount(c, parens)
        return parts.filter { it.isNotBlank() }.joinToString(" ").trim()
    }

    private fun renderTopSubstance(item: Row, labels: Boolean, count: Boolean, parens: Boolean, config: FreakQueryConfig): String {
        val left = if (labels) Aliases.displayValue(config, "substance", item["substance"]) else ""
        val right = if (count) item["count"].toString() else wrapCount(item["count"].toString(), parens)
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
    fun render(data: Any?, plan: QueryPlan, ctx: Context): String =
        when (val normalized = normalizeData(data, ctx.config)) {
            is List<*> -> JSONArray(normalized).toString(2)
            is Map<*, *> -> JSONObject(normalized).toString(2)
            is String -> JSONObject.quote(normalized)
            null -> "null"
            else -> normalized.toString()
        }

    private fun normalizeData(data: Any?, config: FreakQueryConfig): Any? = when (data) {
        is List<*> -> data.map { normalizeData(it, config) }
        is Map<*, *> -> normalizeObj(data, config)
        else -> data
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
