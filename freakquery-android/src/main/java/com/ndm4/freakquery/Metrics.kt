package com.ndm4.freakquery

import java.util.Locale
import kotlin.math.roundToInt

internal object Metrics {
    fun apply(data: Any, plan: QueryPlan, ctx: Context): Any {
        if (plan.metrics.isEmpty()) return data
        val metric = resolveMetric(plan) ?: return data

        @Suppress("UNCHECKED_CAST")
        val rows = data as? Rows ?: return data
        val total = rows.size

        return when {
            metric == "count" -> total
            metric == "first" -> rows.firstOrNull() ?: emptyMap<String, Any?>()
            metric == "last" -> rows.lastOrNull() ?: emptyMap<String, Any?>()
            metric == "since" -> rows.lastOrNull()?.let {
                humanSince((ctx.nowMs - rowTime(it, ctx.config)).coerceAtLeast(0L))
            }.orEmpty()
            metric == "dose" -> rows.lastOrNull()?.let { rowDoseText(it, ctx.config) }.orEmpty()
            metric == "substance" -> rows.lastOrNull()?.let { rowSubstance(it, ctx.config) }.orEmpty()
            metric == "sum_dose" -> cleanNumericValue(rows.sumOf {
                Units.toMg(Aliases.rowGet(ctx.config, it, "dose"), Aliases.rowGet(ctx.config, it, "unit"))
            })
            metric == "top_substances" -> topN(
                counter(rows.mapNotNull { rowSubstance(it, ctx.config).takeIf(String::isNotEmpty) })
                    .map { mapOf("substance" to it.key, "count" to it.value) },
                plan.params["limit"]
            )
            metric == "top_routes" -> topN(
                counter(rows.mapNotNull {
                    Aliases.rowGet(ctx.config, it, "route")?.let { value ->
                        Aliases.canonicalValue(ctx.config, "route", value).takeIf(String::isNotEmpty)
                    }
                }).map { mapOf("value" to it.key, "count" to it.value) },
                plan.params["limit"]
            )
            metric == "sites" -> topN(
                counter(rows.mapNotNull {
                    Aliases.rowGet(ctx.config, it, "site")?.let { value ->
                        Aliases.canonicalValue(ctx.config, "site", value).takeIf(String::isNotEmpty)
                    }
                }).map { mapOf("value" to it.key, "count" to it.value) },
                plan.params["limit"]
            )
            metric == "substance_totals" -> substanceTotals(rows, plan, ctx.config)
            metric.startsWith("ratio=") -> ratio(rows, metric.substringAfter("="), plan, ctx)
            metric == "group_sum" -> cleanNumericValue(Grouping.groupSum(rows, ctx.config))
            metric == "group_duration" -> humanSince(Grouping.groupDuration(rows, ctx.config))
            metric == "group_count" -> rows.size
            metric == "main_substance" -> Grouping.mainSubstance(rows, ctx.config)
            metric == "substances_count" -> orderedSubstances(rows, ctx.config).toSet().size
            metric == "avg_gap" -> avgGap(rows, ctx.config)
            metric == "timeline" -> rows
            metric == "sequence" -> compressSequence(orderedSubstances(rows, ctx.config)).joinToString(" -> ")
            metric == "sequence=dose" -> sequenceDose(rows, ctx.config)
            metric == "sequence=time" -> sequenceTime(rows, ctx.config)
            metric == "sequence=patterns" -> counterRows(sequencePatterns(rows, ctx.config))
            metric.startsWith("sequence=after:") -> sequenceAfter(rows, metric.substringAfter(":"), ctx.config)
            metric.startsWith("sequence=before:") -> sequenceBefore(rows, metric.substringAfter(":"), ctx.config)
            else -> rows
        }
    }

    private fun resolveMetric(plan: QueryPlan): String? {
        for (metric in plan.metrics) {
            val low = metric.trim().lowercase(Locale.ROOT)
            if (low.startsWith("ratio=") || low.startsWith("sequence=")) return metric
        }
        return plan.metrics.firstOrNull()?.trim()?.lowercase(Locale.ROOT)
    }

    private fun ratio(rows: Rows, field: String, plan: QueryPlan, ctx: Context): List<Map<String, Any?>> {
        val counts = counter(rows.mapNotNull {
            Aliases.rowGet(ctx.config, it, field)?.let { value ->
                Aliases.canonicalValue(ctx.config, field, value).takeIf(String::isNotEmpty)
            }
        })
        val denom = counts.sumOf { it.value }
        return topN(
            counts.map {
                mapOf("value" to it.key, "count" to it.value, "label" to pct(it.value, denom, ctx.config))
            },
            plan.params["limit"]
        )
    }

    private fun substanceTotals(rows: Rows, plan: QueryPlan, config: FreakQueryConfig): List<Map<String, Any?>> {
        val totals = linkedMapOf<String, Double>()
        val counts = linkedMapOf<String, Int>()
        for (row in rows) {
            val sub = rowSubstance(row, config)
            if (sub.isEmpty()) continue
            totals[sub] = (totals[sub] ?: 0.0) + Units.toMg(Aliases.rowGet(config, row, "dose"), Aliases.rowGet(config, row, "unit"))
            counts[sub] = (counts[sub] ?: 0) + 1
        }
        return topN(
            totals.map {
                mapOf("substance" to it.key, "dose" to cleanNumericValue(it.value), "unit" to "mg", "count" to counts[it.key])
            }.sortedByDescending { (it["dose"] as? Number)?.toDouble() ?: 0.0 },
            plan.params["limit"]
        )
    }

    private fun sequenceDose(rows: Rows, config: FreakQueryConfig): String =
        orderedRows(rows, config).mapNotNull { row ->
            val sub = rowSubstance(row, config).takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val dose = rowDoseText(row, config)
            if (dose.isNotEmpty()) "$sub ($dose)" else sub
        }.joinToString(" -> ")

    private fun sequenceTime(rows: Rows, config: FreakQueryConfig): String {
        val out = mutableListOf<String>()
        var previous: Long? = null
        for (row in orderedRows(rows, config)) {
            val sub = rowSubstance(row, config)
            if (sub.isEmpty()) continue
            val now = rowTime(row, config)
            previous?.let { out += "+${humanSince(now - it)}" }
            out += sub
            previous = now
        }
        return out.joinToString(" -> ")
    }

    private fun sequencePatterns(rows: Rows, config: FreakQueryConfig): List<String> {
        val seq = orderedSubstances(rows, config)
        return (0 until (seq.size - 1).coerceAtLeast(0)).map { "${seq[it]} -> ${seq[it + 1]}" }
    }

    private fun sequenceAfter(rows: Rows, target: String, config: FreakQueryConfig): List<Map<String, Any?>> {
        val seq = orderedSubstances(rows, config)
        val out = mutableListOf<String>()
        for (i in 0 until (seq.size - 1).coerceAtLeast(0)) {
            if (Aliases.sameValue(config, "substance", seq[i], target)) out += seq[i + 1]
        }
        return counterRows(out)
    }

    private fun sequenceBefore(rows: Rows, target: String, config: FreakQueryConfig): List<Map<String, Any?>> {
        val seq = orderedSubstances(rows, config)
        val out = mutableListOf<String>()
        for (i in 1 until seq.size) {
            if (Aliases.sameValue(config, "substance", seq[i], target)) out += seq[i - 1]
        }
        return counterRows(out)
    }

    private fun avgGap(rows: Rows, config: FreakQueryConfig): String {
        if (rows.size < 2) return "0s"
        val times = orderedRows(rows, config).map { rowTime(it, config) }.filter { it > 0L }
        if (times.size < 2) return "0s"
        val gaps = times.zipWithNext { a, b -> b - a }
        return humanSince(gaps.average().toLong())
    }

    private fun counterRows(items: List<String>): List<Map<String, Any?>> =
        counter(items).map { mapOf("value" to it.key, "count" to it.value) }

    private fun counter(items: List<String>): List<Map.Entry<String, Int>> =
        items.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }

    private fun pct(part: Int, total: Int, config: FreakQueryConfig): String {
        if (total <= 0) return "0%"
        val real = part.toDouble() / total.toDouble() * 100.0
        if (real < 1.0) return config.ratioUnderOne
        return "${real.roundToInt()}%"
    }

    private fun <T> topN(items: List<T>, n: Any?): List<T> =
        if (n is Int && n >= 0) items.take(n) else items
}

internal fun rowSubstance(row: Row, config: FreakQueryConfig): String =
    Aliases.rowGet(config, row, "substance")?.let { Aliases.canonicalValue(config, "substance", it) }.orEmpty()

internal fun rowDoseText(row: Row, config: FreakQueryConfig): String {
    val dose = Aliases.rowGet(config, row, "dose") ?: return ""
    val cleanDose = cleanNumber(dose)
    val unit = Aliases.rowGet(config, row, "unit")
    return if (unit != null && unit.toString().isNotBlank()) {
        "$cleanDose ${Aliases.canonicalValue(config, "unit", unit)}"
    } else {
        cleanDose
    }
}

internal fun orderedRows(rows: Rows, config: FreakQueryConfig): Rows = rows.sortedBy { rowTime(it, config) }

internal fun orderedSubstances(rows: Rows, config: FreakQueryConfig): List<String> =
    orderedRows(rows, config).mapNotNull { rowSubstance(it, config).takeIf(String::isNotEmpty) }

internal fun compressSequence(items: List<String>): List<String> {
    if (items.isEmpty()) return emptyList()
    val out = mutableListOf<String>()
    var current = items.first()
    var n = 1
    for (item in items.drop(1)) {
        if (item == current) {
            n += 1
        } else {
            out += if (n > 1) "$current x$n" else current
            current = item
            n = 1
        }
    }
    out += if (n > 1) "$current x$n" else current
    return out
}

internal fun humanSince(ms: Long): String {
    var seconds = ms / 1000L
    val sec = seconds % 60L
    seconds /= 60L
    val mins = seconds % 60L
    seconds /= 60L
    val hrs = seconds % 24L
    seconds /= 24L
    val years = seconds / 365L
    var days = seconds % 365L
    val months = days / 30L
    days %= 30L
    return when {
        years > 0 -> "${years}y ${months}mo"
        months > 0 -> "${months}mo ${days}d"
        days > 0 -> "${days}d ${hrs}h"
        hrs > 0 -> "${hrs}h ${mins}m"
        mins > 0 -> "${mins}m ${sec}s"
        else -> "${sec}s"
    }
}

internal fun cleanNumber(value: Any?): String {
    val n = value?.toString()?.toDoubleOrNull() ?: return value.toString()
    if (n % 1.0 == 0.0) return n.toLong().toString()
    return n.toString().trimEnd('0').trimEnd('.')
}

internal fun cleanNumericValue(value: Double): Any =
    if (value % 1.0 == 0.0) value.toLong() else "%.2f".format(Locale.US, value).trimEnd('0').trimEnd('.').toDouble()
