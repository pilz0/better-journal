package foo.pilz.freaklog.scheduled

import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class ReminderScheduleInvariantTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val from = Instant.parse("2026-03-28T23:00:00Z").toEpochMilli()
    private val daily = Reminder(
        title = "Daily", hour = 8, minute = 0, isEnabled = true, timesOfDay = "08:00",
    )

    @Test
    fun everyWeeklyMaskFindsOnlyAllowedDaysAndAdvancesStrictly() {
        for (mask in 0..127) {
            var previous = from
            repeat(14) {
                val next = requireNotNull(
                    ReminderSchedule.nextFireAt(daily.copy(daysOfWeekMask = mask), previous, zone)
                )
                val local = Instant.ofEpochMilli(next).atZone(zone)
                assertTrue("mask=$mask previous=$previous next=$next", next > previous)
                assertTrue(ReminderSchedule.isDayAllowed(local.dayOfWeek, if (mask == 0) 127 else mask))
                assertEquals(8, local.hour)
                assertEquals(0, local.minute)
                previous = next
            }
        }
    }

    @Test
    fun togglingAnyDayTwiceRestoresEveryMaskWithoutChangingOtherDays() {
        for (mask in 0..127) {
            for (day in DayOfWeek.entries) {
                val toggled = ReminderSchedule.toggleDay(mask, day)
                assertEquals(mask, ReminderSchedule.toggleDay(toggled, day))
                for (other in DayOfWeek.entries.filter { it != day }) {
                    assertEquals(ReminderSchedule.isDayAllowed(other, mask), ReminderSchedule.isDayAllowed(other, toggled))
                }
            }
        }
    }

    @Test
    fun intervalMeasuresElapsedTimeAcrossBothClockChanges() {
        for (anchor in listOf("2026-03-28T08:00:00Z", "2026-10-24T07:00:00Z")) {
            val start = Instant.parse(anchor).toEpochMilli()
            val reminder = daily.copy(
                scheduleType = ReminderSchedule.SCHEDULE_TYPE_INTERVAL,
                startEpochMillis = start, intervalValue = 1, intervalUnit = "DAYS",
            )
            val next = requireNotNull(ReminderSchedule.nextFireAt(reminder, start, zone))
            assertEquals(Duration.ofHours(24).toMillis(), next - start)
        }
    }

    @Test
    fun endBoundaryIncludesTheExactFireInstantButNotOneMillisecondLater() {
        val next = requireNotNull(ReminderSchedule.nextFireAt(daily, from, zone))
        assertEquals(next, ReminderSchedule.nextFireAt(daily.copy(endEpochMillis = next), from, zone))
        assertNull(ReminderSchedule.nextFireAt(daily.copy(endEpochMillis = next - 1), from, zone))
        assertNull(ReminderSchedule.nextFireAt(daily.copy(endEpochMillis = next), next, zone))
    }

    @Test
    fun invalidIntervalConfigurationsNeverSchedule() {
        for (value in listOf(0, -1, Int.MIN_VALUE)) {
            val reminder = daily.copy(
                scheduleType = ReminderSchedule.SCHEDULE_TYPE_INTERVAL,
                intervalValue = value, intervalUnit = "HOURS", startEpochMillis = from,
            )
            assertNull(ReminderSchedule.nextFireAt(reminder, from, zone))
        }
        assertNull(
            ReminderSchedule.nextFireAt(
                daily.copy(scheduleType = ReminderSchedule.SCHEDULE_TYPE_INTERVAL, intervalUnit = "UNKNOWN"),
                from, zone,
            )
        )
    }

    @Test
    fun everyMinuteSurvivesFormattingAndParsingWithoutDuplicates() {
        val times = (0 until 1440).map { java.time.LocalTime.of(it / 60, it % 60) }
        assertEquals(times, ReminderSchedule.parseTimesOfDay(ReminderSchedule.formatTimesOfDay(times.reversed() + times)))
    }
}
