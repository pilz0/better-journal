package foo.pilz.freaklog.ui.tabs.journal.experience.vitals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class VitalsLogicTest {

    private val start: Instant = Instant.parse("2026-01-01T12:00:00Z")

    // --- blood pressure validation ---

    @Test
    fun `a plausible reading is valid with or without a pulse`() {
        assertTrue(isBloodPressureInputValid("120", "80", ""))
        assertTrue(isBloodPressureInputValid(" 120 ", "80", "64"))
    }

    @Test
    fun `systolic and diastolic are required`() {
        assertFalse(isBloodPressureInputValid("", "80", ""))
        assertFalse(isBloodPressureInputValid("120", "", "70"))
    }

    @Test
    fun `empty fields show no error while the form is still being filled in`() {
        assertEquals(BloodPressureFieldErrors(null, null, null), bloodPressureFieldErrors("", "", ""))
    }

    @Test
    fun `non-numbers, decimals and out-of-range values are rejected per field`() {
        assertEquals("Enter a whole number", bloodPressureFieldErrors("12o", "80", "").systolic)
        assertEquals("Enter a whole number", bloodPressureFieldErrors("120", "80.5", "").diastolic)
        assertNotNull(bloodPressureFieldErrors("400", "80", "").systolic)
        assertNotNull(bloodPressureFieldErrors("120", "5", "").diastolic)
        assertNotNull(bloodPressureFieldErrors("120", "80", "900").pulse)
        assertFalse(isBloodPressureInputValid("120", "80", "900"))
    }

    @Test
    fun `systolic must be above diastolic`() {
        assertEquals("Must be above diastolic", bloodPressureFieldErrors("80", "80", "").systolic)
        assertFalse(isBloodPressureInputValid("70", "90", ""))
    }

    @Test
    fun `categories follow the thresholds at their boundaries`() {
        assertEquals("Low", bloodPressureCategory(85, 55))
        assertEquals("Normal", bloodPressureCategory(119, 79))
        assertEquals("Elevated", bloodPressureCategory(120, 79))
        assertEquals("Elevated (stage 1)", bloodPressureCategory(118, 80))
        assertEquals("High", bloodPressureCategory(140, 85))
        assertEquals("Very high", bloodPressureCategory(150, 120))
    }

    // --- heart rate ---

    private fun samples(count: Int, bpm: (Int) -> Long = { 60L + it % 10 }) =
        (0 until count).map { HeartRateSample(start.plusSeconds(it.toLong()), bpm(it)) }

    @Test
    fun `summary gives min, rounded average and max`() {
        val summary = summarizeHeartRate(listOf(60L, 70L, 95L).mapIndexed { i, bpm -> HeartRateSample(start.plusSeconds(i.toLong()), bpm) })
        assertEquals(HeartRateSummary(min = 60, average = 75, max = 95), summary)
        assertNull(summarizeHeartRate(emptyList()))
    }

    @Test
    fun `short series are returned unchanged`() {
        val input = samples(MAX_POINTS)
        assertEquals(input, downsampleHeartRate(input, start, start.plusSeconds(MAX_POINTS.toLong())))
    }

    @Test
    fun `long series are averaged down, stay ordered and keep their range`() {
        val input = samples(10_000) { 80L }
        val result = downsampleHeartRate(input, start, start.plusSeconds(10_000))

        assertTrue(result.size <= MAX_POINTS + 1)
        assertTrue(result.size > MAX_POINTS / 2)
        assertTrue(result.all { it.bpm == 80L })
        assertEquals(result.sortedBy { it.time }, result)
        assertFalse(result.first().time.isBefore(start))
        assertFalse(result.last().time.isAfter(start.plusSeconds(10_000)))
    }

    @Test
    fun `window runs from the first ingestion to twelve hours after the last`() {
        val times = listOf(start.plusSeconds(3600), start)
        val window = heartRateWindow(times, now = start.plus(Duration.ofDays(3)))
        assertEquals(start to start.plusSeconds(3600).plus(Duration.ofHours(12)), window)
    }

    @Test
    fun `window ends now while the experience is still running`() {
        val now = start.plus(Duration.ofHours(2))
        assertEquals(start to now, heartRateWindow(listOf(start), now))
    }

    @Test
    fun `there is no window without ingestions or for ones in the future`() {
        assertNull(heartRateWindow(emptyList(), start))
        assertNull(heartRateWindow(listOf(start.plusSeconds(60)), now = start))
    }
}
