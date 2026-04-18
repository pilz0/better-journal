/*
 * Copyright (c) 2026. FreakLog contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package foo.pilz.freaklog.scheduled

import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pure functions for reminder scheduling math, kept free of Android dependencies so
 * they can be exercised by JVM unit tests.
 */
object ReminderSchedule {

    const val SCHEDULE_TYPE_DAILY_AT_TIMES = "DAILY_AT_TIMES"
    const val SCHEDULE_TYPE_INTERVAL = "INTERVAL"

    private const val MASK_ALL_DAYS = 127

    /**
     * Returns the next epoch-millis instant at which [reminder] should fire, strictly after
     * [fromMillis], or null if the reminder will never fire again (disabled, past end date,
     * or has no schedule data).
     */
    fun nextFireAt(
        reminder: Reminder,
        fromMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long? {
        if (!reminder.isEnabled) return null
        val end = reminder.endEpochMillis
        if (end != null && fromMillis > end) return null

        val candidate = when (reminder.scheduleType) {
            SCHEDULE_TYPE_INTERVAL -> nextIntervalFire(reminder, fromMillis, zone)
            else -> nextDailyAtTimesFire(reminder, fromMillis, zone)
        }
        return candidate?.takeIf { end == null || it <= end }
    }

    private fun nextDailyAtTimesFire(reminder: Reminder, fromMillis: Long, zone: ZoneId): Long? {
        val times = parseTimesOfDay(reminder.timesOfDay)
        if (times.isEmpty()) return null
        val mask = effectiveDaysMask(reminder.daysOfWeekMask)
        if (mask == 0) return null

        val from = Instant.ofEpochMilli(fromMillis).atZone(zone)
        // Search up to 8 days forward to guarantee we hit at least one allowed day.
        for (offset in 0..7) {
            val date = from.toLocalDate().plusDays(offset.toLong())
            if (!isDayAllowedForFiring(date.dayOfWeek, mask)) continue
            for (time in times) {
                val instant = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                if (instant > fromMillis) return instant
            }
        }
        return null
    }

    private fun nextIntervalFire(reminder: Reminder, fromMillis: Long, zone: ZoneId): Long? {
        val intervalMs = intervalMillis(reminder.intervalValue, reminder.intervalUnit) ?: return null
        if (intervalMs <= 0L) return null
        val mask = effectiveDaysMask(reminder.daysOfWeekMask)
        if (mask == 0) return null

        val anchor = if (reminder.startEpochMillis > 0L) {
            reminder.startEpochMillis
        } else {
            // Legacy reminders: anchor at hour:minute today (in zone).
            val today = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
            today.atTime(LocalTime.of(reminder.hour.coerceIn(0, 23), reminder.minute.coerceIn(0, 59)))
                .atZone(zone).toInstant().toEpochMilli()
        }

        var next = if (anchor > fromMillis) {
            anchor
        } else {
            val diff = fromMillis - anchor
            // (diff / intervalMs) + 1 intervals past the anchor.
            anchor + ((diff / intervalMs) + 1) * intervalMs
        }
        // Skip forward until the day is allowed. Use a time-based horizon (8 days) instead
        // of a fixed step count so short intervals across a weekend mask still find the
        // next allowed slot. Hard cap the iteration count so a degenerate mask can never
        // loop forever.
        val horizon = next + 8L * 24L * 60L * 60_000L
        val maxSteps = (8L * 24L * 60L * 60_000L / intervalMs + 8L).coerceAtMost(100_000L).toInt()
        repeat(maxSteps) {
            if (next > horizon) return null
            val day = Instant.ofEpochMilli(next).atZone(zone).dayOfWeek
            if (isDayAllowedForFiring(day, mask)) return next
            next += intervalMs
        }
        return null
    }

    /** Parses `"08:00,20:30"` (whitespace-tolerant) into a sorted list of [LocalTime]. */
    fun parseTimesOfDay(csv: String): List<LocalTime> {
        if (csv.isBlank()) return emptyList()
        return csv.split(',')
            .mapNotNull { token ->
                val trimmed = token.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val parts = trimmed.split(':')
                if (parts.size != 2) return@mapNotNull null
                val hh = parts[0].toIntOrNull() ?: return@mapNotNull null
                val mm = parts[1].toIntOrNull() ?: return@mapNotNull null
                if (hh !in 0..23 || mm !in 0..59) return@mapNotNull null
                LocalTime.of(hh, mm)
            }
            .distinct()
            .sorted()
    }

    /** Inverse of [parseTimesOfDay]. */
    fun formatTimesOfDay(times: List<LocalTime>): String =
        times.distinct().sorted().joinToString(",") {
            "%02d:%02d".format(it.hour, it.minute)
        }

    /** Treats a mask of 0 as "every day" so legacy rows without an explicit mask still fire. */
    private fun effectiveDaysMask(mask: Int): Int = if (mask == 0) MASK_ALL_DAYS else mask and MASK_ALL_DAYS

    fun isDayAllowed(day: DayOfWeek, mask: Int): Boolean {
        // Bit 0 = Monday … bit 6 = Sunday. java.time.DayOfWeek.value is 1..7 (Mon..Sun).
        val bit = 1 shl (day.value - 1)
        return (mask and MASK_ALL_DAYS) and bit != 0
    }

    /** Internal: legacy rows might have a 0 mask; treat that as "every day" when scheduling. */
    private fun isDayAllowedForFiring(day: DayOfWeek, mask: Int): Boolean =
        isDayAllowed(day, effectiveDaysMask(mask))

    fun toggleDay(mask: Int, day: DayOfWeek): Int {
        val bit = 1 shl (day.value - 1)
        return (mask and MASK_ALL_DAYS) xor bit
    }

    fun intervalMillis(value: Int, unit: String): Long? {
        val v = value.toLong()
        return when (unit.uppercase()) {
            "MINUTES" -> v * 60_000L
            "HOURS" -> v * 60L * 60_000L
            "DAYS" -> v * 24L * 60L * 60_000L
            else -> null
        }
    }

    /**
     * Human-readable summary of a reminder's schedule, e.g.
     *   "Daily at 08:00, 20:00"
     *   "Mon, Wed, Fri at 09:30"
     *   "Every 6 hours · Mon–Fri"
     */
    fun summarize(reminder: Reminder): String {
        val days = daysSummary(reminder.daysOfWeekMask)
        return when (reminder.scheduleType) {
            SCHEDULE_TYPE_INTERVAL -> {
                val unit = reminder.intervalUnit.lowercase().trimEnd('s')
                val plural = if (reminder.intervalValue == 1) unit else "${unit}s"
                val base = "Every ${reminder.intervalValue} $plural"
                if (days == "Daily") base else "$base · $days"
            }

            else -> {
                val times = parseTimesOfDay(reminder.timesOfDay)
                val timesStr = if (times.isEmpty()) "(no times set)"
                else times.joinToString(", ") { "%02d:%02d".format(it.hour, it.minute) }
                if (days == "Daily") "Daily at $timesStr" else "$days at $timesStr"
            }
        }
    }

    /** Compact day-of-week summary: `"Daily"`, `"Mon–Fri"`, `"Sat, Sun"`, etc. */
    fun daysSummary(mask: Int): String {
        val effective = effectiveDaysMask(mask)
        if (effective == MASK_ALL_DAYS) return "Daily"
        val weekdayMask = 0b0011111
        val weekendMask = 0b1100000
        if (effective == weekdayMask) return "Mon–Fri"
        if (effective == weekendMask) return "Sat, Sun"

        val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        return (0..6)
            .filter { (effective shr it) and 1 == 1 }
            .joinToString(", ") { labels[it] }
    }
}
