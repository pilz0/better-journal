package foo.pilz.freaklog.scheduled

import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Instant
import java.time.ZoneId

/** Expected instants are explicit so a shared scheduling error cannot make the oracle pass. */
@RunWith(Parameterized::class)
class ReminderScheduleTimeZoneTest(
    private val zoneName: String,
    private val times: String,
    private val from: String,
    private val expected: String,
) {
    @Test
    fun dailyReminderResolvesClockChangesAndMidnight() {
        val reminder = Reminder(
            title = "Clock change", hour = 0, minute = 0, isEnabled = true,
            timesOfDay = times,
        )
        assertEquals(
            Instant.parse(expected).toEpochMilli(),
            ReminderSchedule.nextFireAt(
                reminder, Instant.parse(from).toEpochMilli(), ZoneId.of(zoneName)
            ),
        )
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}: {1} after {2}")
        fun cases(): List<Array<String>> = listOf(
            // Nonexistent local times move forward by the daylight saving gap.
            arrayOf("Europe/Berlin", "02:30", "2026-03-29T00:00:00Z", "2026-03-29T01:30:00Z"),
            arrayOf("America/New_York", "02:30", "2026-03-08T05:00:00Z", "2026-03-08T07:30:00Z"),
            // A repeated local time uses its earlier offset and is not fired twice.
            arrayOf("Europe/Berlin", "02:30", "2026-10-25T00:00:00Z", "2026-10-25T00:30:00Z"),
            arrayOf("Europe/Berlin", "02:30", "2026-10-25T00:30:00Z", "2026-10-26T01:30:00Z"),
            arrayOf("America/New_York", "01:30", "2026-11-01T05:30:00Z", "2026-11-02T06:30:00Z"),
            // Half-hour offsets and the international date line must use the local date.
            arrayOf("Asia/Kolkata", "00:15", "2026-01-07T18:30:00Z", "2026-01-07T18:45:00Z"),
            arrayOf("Pacific/Kiritimati", "00:15", "2026-01-07T10:00:00Z", "2026-01-07T10:15:00Z"),
            arrayOf("UTC", "00:00", "2026-12-31T23:59:59Z", "2027-01-01T00:00:00Z"),
        )
    }
}
