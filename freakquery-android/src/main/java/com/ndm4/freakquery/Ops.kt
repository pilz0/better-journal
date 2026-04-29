package com.ndm4.freakquery

import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

internal object Filters {
    fun apply(rows: Rows, plan: QueryPlan, ctx: Context): Rows {
        if (plan.filters.isEmpty()) return rows
        val now = localDate(ctx.nowMs)

        return rows.filter { row ->
            val dt = rowTime(row, ctx.config).takeIf { it > 0L }?.let { dayFields(it) }
            plan.filters.all { filter ->
                val low = filter.trim().lowercase(Locale.ROOT)
                when {
                    low in setOf("day", "today") -> dt != null && dt == now
                    low == "week" -> dt != null && dt.year == now.year && dt.week == now.week
                    low == "month" -> dt != null && dt.year == now.year && dt.month == now.month
                    low == "year" -> dt != null && dt.year == now.year
                    "=" in low -> {
                        val key = low.substringBefore("=")
                        val value = low.substringAfter("=")
                        val rowValue = Aliases.rowGet(ctx.config, row, key)
                        rowValue != null && Aliases.sameValue(ctx.config, key, rowValue, value)
                    }
                    else -> true
                }
            }
        }
    }

    private fun localDate(ms: Long): DayFields = dayFields(ms)
}

internal object Grouping {
    fun groupDuration(group: Rows, config: FreakQueryConfig): Long {
        if (group.isEmpty()) return 0L
        return rowTime(group.last(), config) - rowTime(group.first(), config)
    }

    fun groupSum(group: Rows, config: FreakQueryConfig): Double =
        group.sumOf { Units.toMg(Aliases.rowGet(config, it, "dose"), Aliases.rowGet(config, it, "unit")) }

    fun mainSubstance(group: Rows, config: FreakQueryConfig): String =
        group.groupingBy { Aliases.canonicalValue(config, "substance", Aliases.rowGet(config, it, "substance") ?: "") }
            .eachCount()
            .filterKeys { it.isNotEmpty() }
            .maxByOrNull { it.value }
            ?.key
            .orEmpty()

    fun apply(rows: Rows, plan: QueryPlan, ctx: Context): Any {
        return when (plan.group) {
            "binges" -> buildBinges(rows, ctx.config)
            "streaks" -> buildStreaks(rows, ctx.config)
            else -> rows
        }
    }

    private fun buildBinges(rows: Rows, config: FreakQueryConfig): List<Rows> {
        if (rows.isEmpty()) return emptyList()
        val ordered = rows.sortedBy { rowTime(it, config) }
        val maxGap = config.bingeGapHours * 60L * 60L * 1000L
        val groups = mutableListOf<Rows>()
        var current = mutableListOf<Row>()

        for (i in 0 until ordered.lastIndex) {
            val a = ordered[i]
            val b = ordered[i + 1]
            val diff = rowTime(b, config) - rowTime(a, config)
            if (diff <= maxGap) {
                if (current.isEmpty()) current += a
                current += b
            } else if (current.isNotEmpty()) {
                groups += current.toList()
                current = mutableListOf()
            }
        }

        if (current.isNotEmpty()) groups += current.toList()
        return groups
    }

    private fun buildStreaks(rows: Rows, config: FreakQueryConfig): List<Rows> {
        if (rows.isEmpty()) return emptyList()
        val byDay = rows.sortedBy { rowTime(it, config) }.groupBy { dayStart(rowTime(it, config)) }.toSortedMap()
        if (byDay.isEmpty()) return emptyList()

        val groups = mutableListOf<Rows>()
        val iterator = byDay.entries.iterator()
        val first = iterator.next()
        var previous = first.key
        var current = first.value.toMutableList()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key == nextDayStart(previous)) {
                current += entry.value
            } else {
                groups += current.toList()
                current = entry.value.toMutableList()
            }
            previous = entry.key
        }

        groups += current.toList()
        return groups
    }
}

internal object Selectors {
    fun apply(data: Any, plan: QueryPlan, ctx: Context): Any {
        val mode = plan.selector?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (mode.isEmpty()) return data

        if (data is List<*> && data.isEmpty()) return data
        val grouped = data is List<*> && data.firstOrNull() is List<*>

        if (grouped) {
            @Suppress("UNCHECKED_CAST")
            val groups = data as List<Rows>
            return when (mode) {
                "first" -> groups.first()
                "last" -> groups.last()
                "random" -> groups.random()
                "largest" -> groups.maxBy { Grouping.groupSum(it, ctx.config) }
                "longest" -> groups.maxBy { Grouping.groupDuration(it, ctx.config) }
                else -> groups
            }
        }

        @Suppress("UNCHECKED_CAST")
        val rows = data as? Rows ?: return data
        if (rows.isEmpty()) return rows

        return when (mode) {
            "first" -> listOf(rows.minBy { rowTime(it, ctx.config) })
            "last" -> listOf(rows.maxBy { rowTime(it, ctx.config) })
            "random" -> listOf(rows[Random.nextInt(rows.size)])
            else -> rows
        }
    }
}

internal object Transforms {
    fun apply(data: Any?, plan: QueryPlan, ctx: Context): Any? {
        val rows = data as? List<*> ?: return data
        var out = rows
        val reverse = (plan.params["reverse"] as? Boolean) ?: ctx.config.queryReverse
        if (reverse) out = out.reversed()
        val limit = plan.params["limit"] as? Int
        if (limit != null && limit >= 0) out = out.take(limit)
        return out
    }
}

internal fun rowTime(row: Row, config: FreakQueryConfig): Long =
    Aliases.rowGet(config, row, "time")?.toString()?.toLongOrNull() ?: 0L

private data class DayFields(
    val year: Int,
    val month: Int,
    val dayOfYear: Int,
    val week: Int
)

private fun dayFields(ms: Long): DayFields {
    val calendar = Calendar.getInstance()
    calendar.firstDayOfWeek = Calendar.MONDAY
    calendar.minimalDaysInFirstWeek = 4
    calendar.timeInMillis = ms
    return DayFields(
        year = calendar.get(Calendar.YEAR),
        month = calendar.get(Calendar.MONTH),
        dayOfYear = calendar.get(Calendar.DAY_OF_YEAR),
        week = calendar.get(Calendar.WEEK_OF_YEAR)
    )
}

private fun dayStart(ms: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = ms
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

private fun nextDayStart(ms: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = ms
    calendar.add(Calendar.DAY_OF_YEAR, 1)
    return calendar.timeInMillis
}
