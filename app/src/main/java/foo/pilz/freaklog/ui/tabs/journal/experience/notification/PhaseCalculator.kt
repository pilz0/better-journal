package foo.pilz.freaklog.ui.tabs.journal.experience.notification

import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.RoaDuration
import foo.pilz.freaklog.ui.tabs.journal.addingestion.time.hourLimitToSeparateIngestions
import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

enum class Phase(val displayName: String) {
    ONSET("onset"),
    COMEUP("comeup"),
    PEAK("peak"),
    OFFSET("offset"),
    FINISHED("finished"),
    UNKNOWN("active")
}

data class PhaseResult(
    val phase: Phase,
    val elapsedSeconds: Float,
    val remainingSeconds: Float?,
    val nextUpdateIntervalSeconds: Long
)

object PhaseCalculator {

    private const val MIN_INTERVAL_SECONDS = 60L
    private const val MAX_INTERVAL_SECONDS = 1800L
    private val UNKNOWN_DURATION_FALLBACK_SECONDS = hourLimitToSeparateIngestions * 3600f

    fun currentPhase(
        ingestionTime: Instant,
        roaDuration: RoaDuration?,
        ingestionEndTime: Instant? = null,
    ): PhaseResult {
        val elapsedSeconds = Duration.between(ingestionTime, Instant.now()).seconds.toFloat()
        val rangeSeconds = ingestionEndTime
            ?.let { Duration.between(ingestionTime, it).seconds.toFloat().coerceAtLeast(0f) }
            ?: 0f
        val effectElapsedSeconds = elapsedSeconds - rangeSeconds

        if (roaDuration == null) {
            return if (effectElapsedSeconds >= UNKNOWN_DURATION_FALLBACK_SECONDS) {
                PhaseResult(Phase.FINISHED, elapsedSeconds, null, MAX_INTERVAL_SECONDS)
            } else {
                PhaseResult(Phase.UNKNOWN, elapsedSeconds, null, 300)
            }
        }

        val onsetMid = midpointSec(roaDuration.onset)
        val comeupMid = midpointSec(roaDuration.comeup)
        val peakMid = midpointSec(roaDuration.peak)
        val offsetMid = midpointSec(roaDuration.offset)

        val onsetEnd = onsetMid
        val comeupEnd = if (onsetEnd != null || comeupMid != null) (onsetEnd ?: 0f) + (comeupMid
            ?: 0f) else null
        val peakEnd =
            if (comeupEnd != null || peakMid != null) (comeupEnd ?: onsetEnd ?: 0f) + (peakMid
                ?: 0f) else null
        val offsetEnd = if (peakEnd != null || offsetMid != null) (peakEnd ?: comeupEnd ?: onsetEnd
        ?: 0f) + (offsetMid ?: 0f) else null

        val totalEnd = midpointSec(roaDuration.total) ?: offsetEnd

        val (phase, timeToNextSec) = when {
            onsetEnd != null && effectElapsedSeconds < onsetEnd ->
                Phase.ONSET to (onsetEnd - effectElapsedSeconds)

            comeupEnd != null && effectElapsedSeconds < comeupEnd ->
                Phase.COMEUP to (comeupEnd - effectElapsedSeconds)

            peakEnd != null && effectElapsedSeconds < peakEnd ->
                Phase.PEAK to (peakEnd - effectElapsedSeconds)

            offsetEnd != null && effectElapsedSeconds < offsetEnd ->
                Phase.OFFSET to (offsetEnd - effectElapsedSeconds)

            totalEnd != null && effectElapsedSeconds < totalEnd ->
                Phase.OFFSET to (totalEnd - effectElapsedSeconds)

            else ->
                Phase.FINISHED to 1800f
        }

        val endOfEffect = listOfNotNull(offsetEnd, totalEnd).maxOrNull()
        val remaining = if (phase != Phase.FINISHED) {
            endOfEffect?.minus(effectElapsedSeconds)?.coerceAtLeast(0f)
        } else {
            null
        }
        val interval = smartInterval(timeToNextSec)
        return PhaseResult(phase, elapsedSeconds, remaining, interval)
    }

    fun smartInterval(timeToNextTransitionSec: Float): Long {
        val raw = (timeToNextTransitionSec / 3).toLong()
        return max(MIN_INTERVAL_SECONDS, min(MAX_INTERVAL_SECONDS, raw))
    }

    fun combinedInterval(phaseResults: List<PhaseResult>): Long {
        if (phaseResults.isEmpty()) return MAX_INTERVAL_SECONDS
        return phaseResults.minOf { it.nextUpdateIntervalSeconds }
    }

    private fun midpointSec(range: DurationRange?): Float? {
        if (range == null) return null
        val minSec = range.minInSec
        val maxSec = range.maxInSec
        if (minSec == null && maxSec == null) return null
        val a = minSec ?: maxSec!!
        val b = maxSec ?: minSec!!
        return (a + b) / 2f
    }
}
