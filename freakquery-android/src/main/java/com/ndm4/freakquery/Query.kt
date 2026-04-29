package com.ndm4.freakquery

import java.util.Locale
import kotlin.math.min

internal object Tags {
    private data class Meta(val accept: Set<String>, val returns: String, val stage: String)

    private val registry = mapOf(
        "first" to Meta(setOf(Types.LOG_ROWS, Types.GROUP_ROWS, Types.TEXT_ROWS), Types.LOG_ROWS, "selector"),
        "last" to Meta(setOf(Types.LOG_ROWS, Types.GROUP_ROWS, Types.TEXT_ROWS), Types.LOG_ROWS, "selector"),
        "random" to Meta(setOf(Types.LOG_ROWS, Types.GROUP_ROWS, Types.TEXT_ROWS), Types.LOG_ROWS, "selector"),
        "largest" to Meta(setOf(Types.GROUP_ROWS), Types.LOG_ROWS, "selector"),
        "longest" to Meta(setOf(Types.GROUP_ROWS), Types.LOG_ROWS, "selector"),
        "binges" to Meta(setOf(Types.LOG_ROWS), Types.GROUP_ROWS, "group"),
        "streaks" to Meta(setOf(Types.LOG_ROWS), Types.GROUP_ROWS, "group"),
        "count" to Meta(setOf(Types.LOG_ROWS, Types.GROUP_ROWS, Types.TEXT_ROWS, Types.TOTAL_ROWS, Types.TREND_ROWS), Types.SCALAR, "metric"),
        "dose" to Meta(setOf(Types.LOG_ROWS), Types.SCALAR, "metric"),
        "substance" to Meta(setOf(Types.LOG_ROWS), Types.SCALAR, "metric"),
        "since" to Meta(setOf(Types.LOG_ROWS), Types.SCALAR, "metric"),
        "sum_dose" to Meta(setOf(Types.LOG_ROWS), Types.SCALAR, "metric"),
        "top_substances" to Meta(setOf(Types.LOG_ROWS), Types.TEXT_ROWS, "metric"),
        "top_routes" to Meta(setOf(Types.LOG_ROWS), Types.TEXT_ROWS, "metric"),
        "sites" to Meta(setOf(Types.LOG_ROWS), Types.TEXT_ROWS, "metric"),
        "substance_totals" to Meta(setOf(Types.LOG_ROWS), Types.TOTAL_ROWS, "metric"),
        "timeline" to Meta(setOf(Types.LOG_ROWS), Types.TEXT_ROWS, "metric"),
        "sequence" to Meta(setOf(Types.LOG_ROWS), Types.TEXT_ROWS, "metric"),
        "group_sum" to Meta(setOf(Types.LOG_ROWS, Types.GROUP_ROWS), Types.SCALAR, "metric"),
        "group_duration" to Meta(setOf(Types.LOG_ROWS, Types.GROUP_ROWS), Types.SCALAR, "metric"),
        "group_count" to Meta(setOf(Types.LOG_ROWS, Types.GROUP_ROWS), Types.SCALAR, "metric"),
        "main_substance" to Meta(setOf(Types.LOG_ROWS, Types.GROUP_ROWS), Types.SCALAR, "metric"),
        "trend_month" to Meta(setOf(Types.LOG_ROWS), Types.TREND_ROWS, "metric"),
        "trend_year" to Meta(setOf(Types.LOG_ROWS), Types.TREND_ROWS, "metric")
    )

    val keys: Set<String> get() = registry.keys

    fun stage(tag: String): String? = registry[tag]?.stage

    fun returns(tag: String): String? = registry[tag]?.returns

    fun accepts(tag: String): Set<String>? = registry[tag]?.accept

    fun contains(tag: String): Boolean = tag in registry
}

internal object Planner {
    private val terminalMetrics = setOf("count", "dose", "substance", "since", "group_sum", "group_duration", "main_substance")
    private val formatPriority = listOf("json", "pretty", "numbered", "bullets", "lines")
    private val displayOnly = setOf("parens", "time", "labels", "percent", "compact", "sep", "dose", "unit", "route", "site")
    private val fieldExtractors = setOf("dose", "substance", "route", "unit", "site")

    fun parseTag(rawTag: String): List<String> =
        rawTag.trim().split("|").map { it.trim() }.filter { it.isNotEmpty() }

    fun normalizeParts(parts: List<String>): List<String> {
        val filters = mutableListOf<String>()
        val params = linkedMapOf<String, String>()
        var group: String? = null
        var selector: String? = null
        val metrics = mutableListOf<String>()
        val formats = mutableListOf<String>()

        for (raw in parts) {
            val part = raw.trim()
            val low = part.lowercase(Locale.ROOT)
            if ("=" in part) {
                val pieces = part.split("=", limit = 2)
                params[pieces[0].trim().lowercase(Locale.ROOT)] = pieces[1].trim()
                continue
            }
            when {
                low in setOf("today", "week", "month", "year") -> filters += low
                low in setOf("binges", "streaks") -> group = low
                low in setOf("last", "first", "random", "largest", "longest") -> selector = low
                low in formatPriority -> formats += low
                else -> metrics += low
            }
        }

        val finalMetrics = mutableListOf<String>()
        for (metric in metrics) {
            finalMetrics += metric
            if (metric in terminalMetrics) break
        }

        val out = mutableListOf<String>()
        out += filters
        params.forEach { (key, value) -> out += "$key=$value" }
        group?.let { out += it }
        selector?.let { out += it }
        out += finalMetrics
        formatPriority.firstOrNull { it in formats }?.let { out += it }
        return out
    }

    fun validateParts(parts: List<String>): Pair<Boolean, String?> {
        var current = Types.LOG_ROWS
        for (part in parts) {
            if ("=" in part) {
                val key = part.substringBefore("=")
                if (key in setOf("substance", "route", "limit", "top", "ratio")) {
                    if (current != Types.LOG_ROWS) return false to "$key= requires log_rows, got $current"
                }
                continue
            }
            val allowed = Tags.accepts(part) ?: continue
            if (Types.ANY !in allowed && current !in allowed) {
                return false to "$part requires ${allowed.sorted().joinToString("/")}, got $current"
            }
            current = Tags.returns(part) ?: current
        }
        return true to null
    }

    fun buildPlan(parts: List<String>, config: FreakQueryConfig): QueryPlan {
        val plan = QueryPlan()
        for (raw in parts) {
            val part = raw.trim()
            if (part.isEmpty()) continue
            val low = part.lowercase(Locale.ROOT)

            if ("=" in part) {
                val key = part.substringBefore("=").trim().lowercase(Locale.ROOT)
                val value = part.substringAfter("=").trim()
                when {
                    key == "ratio" -> plan.metrics += "ratio=${value.lowercase(Locale.ROOT)}"
                    key == "sequence" -> plan.metrics += "sequence=$value"
                    key == "field" -> plan.params["field"] = value
                    key in setOf("limit", "top") -> plan.params["limit"] = parseValue(value)
                    key == "reverse" -> plan.params["reverse"] = parseValue(value)
                    key in displayOnly -> plan.display[key] = parseValue(value)
                    else -> plan.filters += "$key=$value"
                }
                continue
            }

            when {
                low in setOf("today", "week", "month", "year") -> plan.filters += low
                low == "reverse" -> plan.params["reverse"] = true
                low in fieldExtractors -> {
                    if (plan.selector != null) plan.metrics += low else plan.display[low] = true
                }
                low in displayOnly -> plan.display[low] = true
                low == "json" -> plan.formats += "json"
                Tags.contains(low) -> when (Tags.stage(low)) {
                    "group" -> plan.group = low
                    "selector" -> plan.selector = low
                    "metric" -> plan.metrics += low
                }
            }
        }
        applyDefaultLimits(plan, config)
        return plan
    }

    private fun parseValue(value: String): Any {
        val low = value.trim().lowercase(Locale.ROOT)
        if (low in setOf("true", "1", "yes", "on")) return true
        if (low in setOf("false", "0", "no", "off")) return false
        value.toIntOrNull()?.let { return it }
        value.toDoubleOrNull()?.let { return it }
        return value
    }

    private fun applyDefaultLimits(plan: QueryPlan, config: FreakQueryConfig) {
        if ("limit" in plan.params) return
        if (plan.group != null && plan.selector != null) return

        for (metric in plan.metrics) {
            val limit = config.limit(metric)
            if (limit != null) {
                plan.params["limit"] = limit
                return
            }
        }

        plan.group?.let { group ->
            config.limit(group)?.let {
                plan.params["limit"] = it
                return
            }
        }

        config.limit("rows")?.let { plan.params["limit"] = it }
            ?: config.limit("default")?.let { plan.params["limit"] = it }
    }
}

internal fun closestTag(raw: String): String? {
    val target = raw.lowercase(Locale.ROOT)
    return Tags.keys
        .map { it to levenshtein(target, it) }
        .minByOrNull { it.second }
        ?.takeIf { (_, distance) -> distance <= min(4, target.length / 2 + 1) }
        ?.first
}

private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var prev = IntArray(b.length + 1) { it }
    var cur = IntArray(b.length + 1)
    for (i in a.indices) {
        cur[0] = i + 1
        for (j in b.indices) {
            val cost = if (a[i] == b[j]) 0 else 1
            cur[j + 1] = minOf(cur[j] + 1, prev[j + 1] + 1, prev[j] + cost)
        }
        val tmp = prev
        prev = cur
        cur = tmp
    }
    return prev[b.length]
}
