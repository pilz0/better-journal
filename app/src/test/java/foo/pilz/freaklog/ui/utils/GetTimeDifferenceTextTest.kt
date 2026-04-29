package foo.pilz.freaklog.ui.utils

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class GetTimeDifferenceTextTest {

    @Test
    fun testMinutesDifference() {
        val from = Instant.parse("2024-01-01T12:00:00Z")
        val to = Instant.parse("2024-01-01T12:30:00Z")
        val result = getTimeDifferenceText(from, to)
        assertTrue(result.contains("minutes"))
        assertTrue(result.contains("30"))
    }

    @Test
    fun testHoursDifference() {
        val from = Instant.parse("2024-01-01T12:00:00Z")
        val to = Instant.parse("2024-01-01T16:00:00Z")
        val result = getTimeDifferenceText(from, to)
        assertTrue(result.contains("hours"))
    }

    @Test
    fun testDaysDifference() {
        val from = Instant.parse("2024-01-01T12:00:00Z")
        val to = Instant.parse("2024-01-04T12:00:00Z")
        val result = getTimeDifferenceText(from, to)
        assertTrue(result.contains("days"))
    }

    @Test
    fun testWeeksDifference() {
        val from = Instant.parse("2024-01-01T12:00:00Z")
        val to = Instant.parse("2024-02-15T12:00:00Z")
        val result = getTimeDifferenceText(from, to)
        assertTrue(result.contains("weeks"))
    }

    @Test
    fun testMonthsDifference() {
        val from = Instant.parse("2024-01-01T12:00:00Z")
        val to = Instant.parse("2024-05-01T12:00:00Z")
        val result = getTimeDifferenceText(from, to)
        assertTrue(result.contains("months"))
    }

    @Test
    fun testYearsDifference() {
        val from = Instant.parse("2020-01-01T12:00:00Z")
        val to = Instant.parse("2024-01-01T12:00:00Z")
        val result = getTimeDifferenceText(from, to)
        assertTrue(result.contains("years"))
    }

    @Test
    fun testSmallMinutesDifference() {
        val from = Instant.parse("2024-01-01T12:00:00Z")
        val to = Instant.parse("2024-01-01T12:05:00Z")
        val result = getTimeDifferenceText(from, to)
        assertTrue(result.contains("minutes"))
        assertTrue(result.contains("5"))
    }

    @Test
    fun testExactlyThreeHours() {
        // hours > 3 check uses strict >, so exactly 3.0h is NOT > 3 → falls into the minutes branch
        val from = Instant.parse("2024-01-01T12:00:00Z")
        val to = Instant.parse("2024-01-01T15:00:00Z")
        val result = getTimeDifferenceText(from, to)
        assertTrue(result.contains("minutes"))
        assertTrue(result.contains("180"))
    }
}
