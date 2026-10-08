package foo.pilz.freaklog

import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.HeartRateSample
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.buildHeartRatePolylines
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.downsampleHeartRate
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.nearestBpm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class TestHeartRate {
    private val start: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private fun sample(minutes: Long, bpm: Long) =
        HeartRateSample(start.plusSeconds(minutes * 60), bpm)

    @Test
    fun medianSitsAtVerticalCenter() {
        val samples = listOf(sample(0, 60), sample(1, 80), sample(2, 100))
        val polylines = buildHeartRatePolylines(samples, start, widthInSeconds = 3600f)
        assertEquals(1, polylines.size)
        assertEquals(0.5f, polylines.single()[1].y, 0.0001f)
        assertEquals(0.85f, polylines.single()[0].y, 0.0001f)
        assertEquals(0.15f, polylines.single()[2].y, 0.0001f)
    }

    @Test
    fun syncGapSplitsPolyline() {
        val samples = listOf(sample(0, 60), sample(1, 70), sample(30, 80), sample(31, 90))
        val polylines = buildHeartRatePolylines(samples, start, widthInSeconds = 3600f)
        assertEquals(2, polylines.size)
    }

    @Test
    fun isolatedSampleKeptAsSinglePoint() {
        val samples = listOf(sample(0, 60), sample(1, 70), sample(2, 80), sample(60, 90))
        val polylines = buildHeartRatePolylines(samples, start, widthInSeconds = 7200f)
        assertEquals(2, polylines.size)
        assertEquals(3, polylines.first().size)
        assertEquals(1, polylines.last().size)
    }

    @Test
    fun downsampleAveragesIntoBuckets() {
        val samples = (0 until 2000).map {
            HeartRateSample(start.plusSeconds(it.toLong()), 60L + (it % 4))
        }
        val result = downsampleHeartRate(samples, start, start.plusSeconds(2000))
        assertEquals(500, result.size)
        assertEquals(62L, result.first().bpm)
    }

    @Test
    fun downsampleKeepsSmallListsUntouched() {
        val samples = listOf(sample(0, 60), sample(1, 70))
        assertEquals(samples, downsampleHeartRate(samples, start, start.plusSeconds(120)))
    }

    @Test
    fun nearestBpmOnlyWithinWindow() {
        val samples = listOf(sample(0, 60), sample(10, 90))
        assertEquals(60L, nearestBpm(samples, start.plusSeconds(60)))
        assertEquals(90L, nearestBpm(samples, start.plusSeconds(9 * 60)))
        assertNull(nearestBpm(samples, start.plusSeconds(60 * 60)))
    }

    @Test
    fun nearestBpmNeverExtrapolatesPastDataEdges() {
        val samples = listOf(sample(0, 60), sample(10, 90))
        assertNull(nearestBpm(samples, start.minusSeconds(60)))
        assertNull(nearestBpm(samples, start.plusSeconds(11 * 60)))
    }

    @Test
    fun downsampleTimestampsStayWithinRealSamples() {
        val samples = (0 until 2000).map {
            HeartRateSample(start.plusSeconds(600 + it.toLong()), 70L)
        }
        val result = downsampleHeartRate(samples, start, start.plusSeconds(4000))
        assertEquals(false, result.first().time.isBefore(samples.first().time))
        assertEquals(false, result.last().time.isAfter(samples.last().time))
    }
}
