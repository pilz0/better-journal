/*
 * Copyright (c) 2026. FreakLog contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package foo.pilz.freaklog.scheduled

import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderScheduleTest {

    private val zone = ZoneId.of("UTC")

    private fun reminder(
        scheduleType: String = ReminderSchedule.SCHEDULE_TYPE_DAILY_AT_TIMES,
        times: String = "08:00,20:00",
        daysMask: Int = 127,
        intervalValue: Int = 1,
        intervalUnit: String = "Days",
        startEpochMillis: Long = 0L,
        endEpochMillis: Long? = null,
        hour: Int = 0,
        minute: Int = 0,
        enabled: Boolean = true,
    ) = Reminder(
        id = 1,
        title = "test",
        hour = hour,
        minute = minute,
        isEnabled = enabled,
        intervalValue = intervalValue,
        intervalUnit = intervalUnit,
        scheduleType = scheduleType,
        timesOfDay = times,
        daysOfWeekMask = daysMask,
        startEpochMillis = startEpochMillis,
        endEpochMillis = endEpochMillis,
    )

    private fun millis(date: LocalDate, time: LocalTime): Long =
        LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun parseTimesOfDay_handlesWhitespaceAndOrders() {
        val result = ReminderSchedule.parseTimesOfDay(" 20:00, 08:00 ,12:30 ")
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(12, 30), LocalTime.of(20, 0)), result)
    }

    @Test
    fun parseTimesOfDay_skipsInvalid() {
        assertEquals(emptyList<LocalTime>(), ReminderSchedule.parseTimesOfDay(""))
        assertEquals(emptyList<LocalTime>(), ReminderSchedule.parseTimesOfDay("garbage"))
        assertEquals(listOf(LocalTime.of(9, 5)), ReminderSchedule.parseTimesOfDay("9:5,25:99"))
    }

    @Test
    fun formatTimesOfDay_isInverseOfParse() {
        val csv = "08:00,12:30,20:00"
        assertEquals(csv, ReminderSchedule.formatTimesOfDay(ReminderSchedule.parseTimesOfDay(csv)))
    }

    @Test
    fun isDayAllowed_bitsAreMonToSun() {
        val monOnly = 0b0000001
        assertTrue(ReminderSchedule.isDayAllowed(DayOfWeek.MONDAY, monOnly))
        assertFalse(ReminderSchedule.isDayAllowed(DayOfWeek.TUESDAY, monOnly))
        val sunOnly = 0b1000000
        assertTrue(ReminderSchedule.isDayAllowed(DayOfWeek.SUNDAY, sunOnly))
        assertFalse(ReminderSchedule.isDayAllowed(DayOfWeek.SATURDAY, sunOnly))
    }

    @Test
    fun toggleDay_flipsBit() {
        val empty = 0
        val withMon = ReminderSchedule.toggleDay(empty, DayOfWeek.MONDAY)
        assertTrue(ReminderSchedule.isDayAllowed(DayOfWeek.MONDAY, withMon))
        val withoutMon = ReminderSchedule.toggleDay(withMon, DayOfWeek.MONDAY)
        assertFalse(ReminderSchedule.isDayAllowed(DayOfWeek.MONDAY, withoutMon))
    }

    @Test
    fun nextFireAt_dailyAtTimes_picksLaterTimeToday() {
        // Wednesday 2026-01-07
        val now = millis(LocalDate.of(2026, 1, 7), LocalTime.of(10, 0))
        val expected = millis(LocalDate.of(2026, 1, 7), LocalTime.of(20, 0))
        val next = ReminderSchedule.nextFireAt(reminder(), now, zone)
        assertEquals(expected, next)
    }

    @Test
    fun nextFireAt_dailyAtTimes_rollsToNextDay() {
        val now = millis(LocalDate.of(2026, 1, 7), LocalTime.of(21, 0))
        val expected = millis(LocalDate.of(2026, 1, 8), LocalTime.of(8, 0))
        assertEquals(expected, ReminderSchedule.nextFireAt(reminder(), now, zone))
    }

    @Test
    fun nextFireAt_dailyAtTimes_skipsDisallowedDay() {
        // Mon-Fri only (bits 0..4)
        val r = reminder(daysMask = 0b0011111, times = "09:00")
        // Friday 2026-01-09 evening → Saturday & Sunday skipped → Monday 2026-01-12 09:00
        val now = millis(LocalDate.of(2026, 1, 9), LocalTime.of(20, 0))
        val expected = millis(LocalDate.of(2026, 1, 12), LocalTime.of(9, 0))
        assertEquals(expected, ReminderSchedule.nextFireAt(r, now, zone))
    }

    @Test
    fun nextFireAt_disabled_returnsNull() {
        val r = reminder(enabled = false)
        val now = millis(LocalDate.of(2026, 1, 7), LocalTime.of(0, 0))
        assertNull(ReminderSchedule.nextFireAt(r, now, zone))
    }

    @Test
    fun nextFireAt_pastEndDate_returnsNull() {
        val end = millis(LocalDate.of(2026, 1, 7), LocalTime.of(0, 0))
        val r = reminder(endEpochMillis = end)
        val now = end + 1
        assertNull(ReminderSchedule.nextFireAt(r, now, zone))
    }

    @Test
    fun nextFireAt_dailyAtTimes_emptyTimes_returnsNull() {
        val r = reminder(times = "")
        val now = millis(LocalDate.of(2026, 1, 7), LocalTime.of(0, 0))
        assertNull(ReminderSchedule.nextFireAt(r, now, zone))
    }

    @Test
    fun nextFireAt_interval_steppedFromAnchor() {
        val anchor = millis(LocalDate.of(2026, 1, 1), LocalTime.of(0, 0))
        val r = reminder(
            scheduleType = ReminderSchedule.SCHEDULE_TYPE_INTERVAL,
            intervalValue = 6,
            intervalUnit = "Hours",
            startEpochMillis = anchor,
        )
        val now = millis(LocalDate.of(2026, 1, 1), LocalTime.of(7, 0))
        // 6h-aligned slots from anchor: 0, 6, 12 → next after 7h = 12:00
        val expected = millis(LocalDate.of(2026, 1, 1), LocalTime.of(12, 0))
        assertEquals(expected, ReminderSchedule.nextFireAt(r, now, zone))
    }

    @Test
    fun nextFireAt_interval_legacyAnchorUsesHourMinute() {
        val r = reminder(
            scheduleType = ReminderSchedule.SCHEDULE_TYPE_INTERVAL,
            intervalValue = 1,
            intervalUnit = "Days",
            hour = 9,
            minute = 0,
            startEpochMillis = 0L,
        )
        val now = millis(LocalDate.of(2026, 1, 7), LocalTime.of(10, 0))
        val expected = millis(LocalDate.of(2026, 1, 8), LocalTime.of(9, 0))
        assertEquals(expected, ReminderSchedule.nextFireAt(r, now, zone))
    }

    @Test
    fun nextFireAt_interval_skipsDisallowedDays() {
        val anchor = millis(LocalDate.of(2026, 1, 9), LocalTime.of(9, 0)) // Friday
        val r = reminder(
            scheduleType = ReminderSchedule.SCHEDULE_TYPE_INTERVAL,
            intervalValue = 1,
            intervalUnit = "Days",
            startEpochMillis = anchor,
            daysMask = 0b0011111, // Mon..Fri
        )
        val now = millis(LocalDate.of(2026, 1, 9), LocalTime.of(10, 0))
        // Next slot = Sat 09:00 (skip), Sun 09:00 (skip), Mon 09:00
        val expected = millis(LocalDate.of(2026, 1, 12), LocalTime.of(9, 0))
        assertEquals(expected, ReminderSchedule.nextFireAt(r, now, zone))
    }

    @Test
    fun summarize_dailyAtTimes_allDays() {
        val r = reminder(times = "08:00,20:00", daysMask = 127)
        assertEquals("Daily at 08:00, 20:00", ReminderSchedule.summarize(r))
    }

    @Test
    fun summarize_dailyAtTimes_weekdays() {
        val r = reminder(times = "09:30", daysMask = 0b0011111)
        assertEquals("Mon–Fri at 09:30", ReminderSchedule.summarize(r))
    }

    @Test
    fun summarize_interval() {
        val r = reminder(
            scheduleType = ReminderSchedule.SCHEDULE_TYPE_INTERVAL,
            intervalValue = 6,
            intervalUnit = "Hours",
        )
        assertEquals("Every 6 hours", ReminderSchedule.summarize(r))
    }

    @Test
    fun summarize_interval_singular() {
        val r = reminder(
            scheduleType = ReminderSchedule.SCHEDULE_TYPE_INTERVAL,
            intervalValue = 1,
            intervalUnit = "Days",
        )
        assertEquals("Every 1 day", ReminderSchedule.summarize(r))
    }

    @Test
    fun daysSummary_specialCases() {
        assertEquals("Daily", ReminderSchedule.daysSummary(127))
        assertEquals("Mon–Fri", ReminderSchedule.daysSummary(0b0011111))
        assertEquals("Sat, Sun", ReminderSchedule.daysSummary(0b1100000))
        assertEquals("Mon, Wed, Fri", ReminderSchedule.daysSummary(0b0010101))
    }

    @Test
    fun intervalMillis_supportedUnits() {
        assertEquals(60_000L, ReminderSchedule.intervalMillis(1, "Minutes"))
        assertEquals(3_600_000L, ReminderSchedule.intervalMillis(1, "Hours"))
        assertEquals(86_400_000L, ReminderSchedule.intervalMillis(1, "Days"))
        assertNull(ReminderSchedule.intervalMillis(1, "Lightyears"))
    }

    @Test
    fun nextFireAt_returnsValueForLegacyDailyReminder() {
        // A row with no times configured: should return null rather than crash.
        val r = reminder(times = "", daysMask = 127)
        val now = millis(LocalDate.of(2026, 1, 7), LocalTime.of(10, 0))
        assertNull(ReminderSchedule.nextFireAt(r, now, zone))

        // The same row migrated to interval mode with legacy hour:minute should still
        // produce a valid next-fire time.
        val migrated = r.copy(
            scheduleType = ReminderSchedule.SCHEDULE_TYPE_INTERVAL,
            hour = 14,
            minute = 0,
        )
        val expected = millis(LocalDate.of(2026, 1, 7), LocalTime.of(14, 0))
        assertEquals(expected, ReminderSchedule.nextFireAt(migrated, now, zone))
    }

    @Test
    fun nextFireAt_dailyAtTimes_strictlyAfterFromMillis() {
        val now = millis(LocalDate.of(2026, 1, 7), LocalTime.of(8, 0))
        val r = reminder(times = "08:00,20:00")
        val next = ReminderSchedule.nextFireAt(r, now, zone)
        assertNotNull(next)
        assertTrue("expected $next to be > $now", next!! > now)
    }

    @Test
    fun nextFireAt_intervalEveryHour_skipsAcrossWeekendToWeekday() {
        // Saturday 2026-01-10 18:00, mask = Mon-Fri only.
        val now = millis(LocalDate.of(2026, 1, 10), LocalTime.of(18, 0))
        val anchor = millis(LocalDate.of(2026, 1, 5), LocalTime.of(0, 0)) // Mon midnight anchor
        val r = reminder(
            scheduleType = ReminderSchedule.SCHEDULE_TYPE_INTERVAL,
            intervalValue = 1,
            intervalUnit = "Hours",
            startEpochMillis = anchor,
            daysMask = 0b0011111, // Mon-Fri
        )
        val expected = millis(LocalDate.of(2026, 1, 12), LocalTime.of(0, 0)) // Monday 00:00
        val next = ReminderSchedule.nextFireAt(r, now, zone)
        assertEquals(expected, next)
    }

    @Test
    fun nextFireAt_endDateInclusiveAtCutoff() {
        // End-date stored as start-of-next-day (00:00 of day+1) so the entire end day is
        // included. A reminder that fires at 23:59 on the end day must still fire.
        val end = millis(LocalDate.of(2026, 1, 11), LocalTime.of(0, 0)) // start of Jan 11 = end of Jan 10
        val now = millis(LocalDate.of(2026, 1, 10), LocalTime.of(23, 0))
        val r = reminder(
            times = "23:59",
            endEpochMillis = end,
        )
        val expected = millis(LocalDate.of(2026, 1, 10), LocalTime.of(23, 59))
        assertEquals(expected, ReminderSchedule.nextFireAt(r, now, zone))
    }
}
