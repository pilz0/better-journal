package com.ndm4.freakquery

import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

internal object Filters {
  private sealed interface ParsedFilter {
    val needsDate: Boolean

    data object Day : ParsedFilter {
      override val needsDate = true
    }

    data object Week : ParsedFilter {
      override val needsDate = true
    }

    data object Month : ParsedFilter {
      override val needsDate = true
    }

    data object Year : ParsedFilter {
      override val needsDate = true
    }

    data class KeyValue(val key: String, val value: String) : ParsedFilter {
      override val needsDate = false
    }

    data object PassThrough : ParsedFilter {
      override val needsDate = false
    }
  }

  private fun parseFilter(raw: String): ParsedFilter {
    val low = raw.trim().lowercase(Locale.ROOT)
    return when {
      low == "day" || low == "today" -> ParsedFilter.Day
      low == "week" -> ParsedFilter.Week
      low == "month" -> ParsedFilter.Month
      low == "year" -> ParsedFilter.Year
      "=" in low -> ParsedFilter.KeyValue(low.substringBefore("="), low.substringAfter("="))
      else -> ParsedFilter.PassThrough
    }
  }

  fun apply(rows: Rows, plan: QueryPlan, ctx: Context): Rows {
    if (plan.filters.isEmpty()) return rows
    val parsedFilters = plan.filters.map(::parseFilter)
    val needsDate = parsedFilters.any { it.needsDate }
    val calendar = if (needsDate) newIsoCalendar() else null
    val now = if (calendar != null) dayFields(ctx.nowMs, calendar) else null

    return rows.filter { row ->
      val dt =
          if (calendar != null) {
            rowTime(row, ctx.config).takeIf { it > 0L }?.let { dayFields(it, calendar) }
          } else {
            null
          }
      parsedFilters.all { filter ->
        when (filter) {
          ParsedFilter.Day -> dt != null && dt == now
          ParsedFilter.Week ->
              dt != null && now != null && dt.weekYear == now.weekYear && dt.week == now.week
          ParsedFilter.Month ->
              dt != null && now != null && dt.year == now.year && dt.month == now.month
          ParsedFilter.Year -> dt != null && now != null && dt.year == now.year
          is ParsedFilter.KeyValue -> {
            val rowValue = Aliases.rowGet(ctx.config, row, filter.key)
            rowValue != null && Aliases.sameValue(ctx.config, filter.key, rowValue, filter.value)
          }
          ParsedFilter.PassThrough -> true
        }
      }
    }
  }
}

internal object Grouping {
  fun groupDuration(group: Rows, config: FreakQueryConfig): Long {
    if (group.isEmpty()) return 0L
    val times = group.mapNotNull { rowTime(it, config).takeIf { t -> t > 0L } }
    if (times.isEmpty()) return 0L
    return (times.max() - times.min()).coerceAtLeast(0L)
  }

  fun groupSum(group: Rows, config: FreakQueryConfig): Double = group.sumOf {
    Units.toMgOrNull(Aliases.rowGet(config, it, "dose"), Aliases.rowGet(config, it, "unit")) ?: 0.0
  }

  fun mainSubstance(group: Rows, config: FreakQueryConfig): String =
      group
          .groupingBy {
            Aliases.canonicalValue(
                config,
                "substance",
                Aliases.rowGet(config, it, "substance") ?: "",
            )
          }
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
    val ordered = rows.map { it to rowTime(it, config) }.sortedBy { it.second }
    val maxGap = config.bingeGapHours * 60L * 60L * 1000L
    val groups = mutableListOf<Rows>()
    var current = mutableListOf<Row>()

    for (i in 0 until ordered.lastIndex) {
      val (a, timeA) = ordered[i]
      val (b, timeB) = ordered[i + 1]
      val diff = timeB - timeA
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
    val calendar = Calendar.getInstance()
    val byDay =
        rows
            .map { it to rowTime(it, config) }
            .sortedBy { it.second }
            .groupBy(
                keySelector = { dayStart(it.second, calendar) },
                valueTransform = { it.first },
            )
            .toSortedMap()
    if (byDay.isEmpty()) return emptyList()

    val groups = mutableListOf<Rows>()
    val iterator = byDay.entries.iterator()
    val first = iterator.next()
    var previous = first.key
    var current = first.value.toMutableList()

    while (iterator.hasNext()) {
      val entry = iterator.next()
      if (entry.key == nextDayStart(previous, calendar)) {
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
      @Suppress("UNCHECKED_CAST") val groups = data as List<Rows>
      return when (mode) {
        "first" -> groups.first()
        "last" -> groups.last()
        "random" -> groups.random()
        "largest" -> groups.maxBy { Grouping.groupSum(it, ctx.config) }
        "longest" -> groups.maxBy { Grouping.groupDuration(it, ctx.config) }
        else -> groups
      }
    }

    @Suppress("UNCHECKED_CAST") val rows = data as? Rows ?: return data
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

internal data class DayFields(
    val year: Int,
    val month: Int,
    val dayOfYear: Int,
    val week: Int,
    val weekYear: Int,
)

internal fun dayFields(ms: Long): DayFields = dayFields(ms, newIsoCalendar())

internal fun newIsoCalendar(): Calendar =
    Calendar.getInstance().apply {
      firstDayOfWeek = Calendar.MONDAY
      minimalDaysInFirstWeek = 4
    }

internal fun dayFields(ms: Long, calendar: Calendar): DayFields {
  calendar.timeInMillis = ms
  val year = calendar.get(Calendar.YEAR)
  val month = calendar.get(Calendar.MONTH)
  val week = calendar.get(Calendar.WEEK_OF_YEAR)
  val weekYear =
      when {
        month == Calendar.DECEMBER && week == 1 -> year + 1
        month == Calendar.JANUARY && week >= 52 -> year - 1
        else -> year
      }
  return DayFields(
      year = year,
      month = month,
      dayOfYear = calendar.get(Calendar.DAY_OF_YEAR),
      week = week,
      weekYear = weekYear,
  )
}

internal fun dayStart(ms: Long): Long = dayStart(ms, Calendar.getInstance())

internal fun dayStart(ms: Long, calendar: Calendar): Long {
  calendar.timeInMillis = ms
  calendar.set(Calendar.HOUR_OF_DAY, 0)
  calendar.set(Calendar.MINUTE, 0)
  calendar.set(Calendar.SECOND, 0)
  calendar.set(Calendar.MILLISECOND, 0)
  return calendar.timeInMillis
}

internal fun nextDayStart(ms: Long): Long = nextDayStart(ms, Calendar.getInstance())

internal fun nextDayStart(ms: Long, calendar: Calendar): Long {
  calendar.timeInMillis = ms
  calendar.add(Calendar.DAY_OF_YEAR, 1)
  return calendar.timeInMillis
}
