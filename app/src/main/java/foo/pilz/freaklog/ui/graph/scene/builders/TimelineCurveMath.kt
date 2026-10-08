package foo.pilz.freaklog.ui.graph.scene.builders

import foo.pilz.freaklog.data.substances.classes.roa.RoaDuration
import kotlin.math.max
import kotlin.math.min

data class RawPoint(
    val seconds: Float,
    val height: Float,
    val isIngestionDot: Boolean = false,
    val controlSeconds: Float? = null,
    val controlHeight: Float? = null,
)

data class CurveSegment(val points: List<RawPoint>, val dotted: Boolean)

data class RawTimelineCurve(
    val segments: List<CurveSegment>,
    val ingestionDots: List<RawPoint>,
    val nonNormalisedHeight: Float,
    val endSeconds: Float,
)

data class RawIngestion(
    val startSeconds: Float,
    val endSeconds: Float?,
    val horizontalWeight: Float,
    val height: Float,
)

data class NormalizedCurve(
    val segments: List<CurveSegment>,
    val ingestionDots: List<RawPoint>,
    val endFraction: Float,
)

private fun Float.finiteNonNegative(): Float = if (isFinite() && this >= 0f) this else 0f

fun normalizeCurve(raw: RawTimelineCurve, referenceHeight: Float, widthSeconds: Float): NormalizedCurve {
    val safeRef = if (!referenceHeight.isFinite() || referenceHeight <= 0f) 1f else referenceHeight
    val safeWidth = if (!widthSeconds.isFinite() || widthSeconds <= 0f) 1f else widthSeconds
    fun map(p: RawPoint) = RawPoint(
        seconds = p.seconds / safeWidth,
        height = p.height / safeRef,
        isIngestionDot = p.isIngestionDot,
        controlSeconds = p.controlSeconds?.div(safeWidth),
        controlHeight = p.controlHeight?.div(safeRef),
    )
    return NormalizedCurve(
        segments = raw.segments.map { CurveSegment(it.points.map(::map), it.dotted) },
        ingestionDots = raw.ingestionDots.map(::map),
        endFraction = raw.endSeconds / safeWidth,
    )
}

sealed interface TimelineShape {
    data class Full(
        val onset: FullDurationRange, val comeup: FullDurationRange,
        val peak: FullDurationRange, val offset: FullDurationRange,
    ) : TimelineShape
    data class OnsetComeupPeakTotal(
        val onset: FullDurationRange, val comeup: FullDurationRange,
        val peak: FullDurationRange, val total: FullDurationRange,
    ) : TimelineShape
    data class OnsetComeupTotal(val onset: FullDurationRange, val comeup: FullDurationRange, val total: FullDurationRange) : TimelineShape
    data class OnsetTotal(val onset: FullDurationRange, val total: FullDurationRange) : TimelineShape
    data class Total(val total: FullDurationRange) : TimelineShape
    data class OnsetComeupPeak(val onset: FullDurationRange, val comeup: FullDurationRange, val peak: FullDurationRange) : TimelineShape
    data class OnsetComeup(val onset: FullDurationRange, val comeup: FullDurationRange) : TimelineShape
    data class Onset(val onset: FullDurationRange) : TimelineShape
    data object None : TimelineShape
}

fun selectTimelineShape(roa: RoaDuration?): TimelineShape {
    if (roa == null) return TimelineShape.None
    val onset = roa.onset?.toFullDurationRange()
    val comeup = roa.comeup?.toFullDurationRange()
    val peak = roa.peak?.toFullDurationRange()
    val offset = roa.offset?.toFullDurationRange()
    val total = roa.total?.toFullDurationRange()

    if (onset != null && comeup != null && peak != null && offset != null) {
        return TimelineShape.Full(onset, comeup, peak, offset)
    }
    if (onset != null && comeup != null && peak != null && total != null) {
        return TimelineShape.OnsetComeupPeakTotal(onset, comeup, peak, total)
    }
    if (onset != null && comeup != null && total != null) {
        return TimelineShape.OnsetComeupTotal(onset, comeup, total)
    }
    if (onset != null && total != null) return TimelineShape.OnsetTotal(onset, total)
    if (total != null) return TimelineShape.Total(total)
    if (onset != null && comeup != null && peak != null) {
        return TimelineShape.OnsetComeupPeak(onset, comeup, peak)
    }
    if (onset != null && comeup != null) return TimelineShape.OnsetComeup(onset, comeup)
    if (onset != null) return TimelineShape.Onset(onset)
    return TimelineShape.None
}

fun sampleRangeCurve(
    startX: Float, endX: Float, hMax: Float,
    onset: Float, comeup: Float, peak: Float, offset: Float,
): List<RawPoint> {
    if (startX >= endX) return emptyList()
    val steps = 30
    val startSample = startX + onset
    val endSample = endX + onset + comeup + peak + offset
    val stepSize = (endSample - startSample) / steps
    val interior = (1 until steps).map { step ->
        val x = startSample + step * stepSize
        RawPoint(seconds = x, height = expressionAt(x, startX, endX, hMax, onset, comeup, peak, offset))
    }
    return listOf(RawPoint(startSample, 0f)) + interior + listOf(RawPoint(endSample, 0f))
}

private fun expressionAt(
    x: Float, startX: Float, endX: Float, hMax: Float,
    onset: Float, comeup: Float, peak: Float, offset: Float,
): Float {
    val denominator = comeup.toDouble() * offset.toDouble() * (endX - startX).toDouble()
    if (denominator == 0.0) return 0f

    val xd = x.toDouble(); val s = startX.toDouble(); val e = endX.toDouble()
    val on = onset.toDouble(); val cu = comeup.toDouble(); val pk = peak.toDouble(); val off = offset.toDouble()
    fun clamp(v: Double) = min(e, max(s, v))

    val term1 = 2 * cu * off * (clamp(-cu - on + xd) - clamp(-cu - on - pk + xd))
    val term2 = 2 * cu * (clamp(-cu - on - pk + xd) - clamp(-cu - off - on - pk + xd)) * (cu + off + on + pk - xd)
    val term3 = cu * (clamp(-cu - on - pk + xd).pow2() - clamp(-cu - off - on - pk + xd).pow2())
    val term4 = 2 * off * (on - xd) * (-clamp(-on + xd) + clamp(-cu - on + xd))
    val term5 = off * (-clamp(-on + xd).pow2() + clamp(-cu - on + xd).pow2())

    val numerator = 0.5 * hMax.toDouble() * (term1 + term2 + term3 + term4 + term5)
    val result = numerator / denominator
    return if (result.isFinite()) result.toFloat() else 0f
}

private fun Double.pow2(): Double = this * this

private data class Seg(val sx: Float, val sy: Float, val ex: Float, val ey: Float) {
    fun isInside(x: Float): Boolean = x in sx..<ex
    fun heightAt(x: Float): Float {
        val d = ex - sx
        if (d == 0f) return 0f
        val m = (ey - sy) / d
        val b = sy - m * sx
        return m * x + b
    }
}

private fun pointsToSegs(points: List<RawPoint>): List<Seg> =
    points.zipWithNext { a, b -> Seg(a.seconds, a.height, b.seconds, b.height) }

private fun sumHeightAt(segs: List<Seg>, x: Float): Float =
    segs.sumOf { if (it.isInside(x)) it.heightAt(x).toDouble() else 0.0 }.toFloat()

private fun superpose(segs: List<Seg>, ingestionXs: List<Float>): List<RawPoint> {
    val breakpointXs = segs.flatMap { listOf(it.sx, it.ex) }.distinct()
    val consideredXs = ingestionXs + breakpointXs
    return consideredXs.map { x -> RawPoint(seconds = x, height = sumHeightAt(segs, x)) }.sortedBy { it.seconds }
}

private fun segmentsToSegs(segments: List<CurveSegment>): List<Seg> {
    val result = mutableListOf<Seg>()
    for (segment in segments) {
        val points = segment.points
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val cur = points[i]
            val cs = cur.controlSeconds
            val ch = cur.controlHeight
            if (cs != null && ch != null) {
                val steps = 12
                var px = prev.seconds
                var py = prev.height
                for (s in 1..steps) {
                    val t = s.toFloat() / steps
                    val mt = 1f - t
                    val x = mt * mt * prev.seconds + 2 * mt * t * cs + t * t * cur.seconds
                    val y = mt * mt * prev.height + 2 * mt * t * ch + t * t * cur.height
                    result += Seg(px, py, x, y)
                    px = x
                    py = y
                }
            } else {
                result += Seg(prev.seconds, prev.height, cur.seconds, cur.height)
            }
        }
    }
    return result
}

private fun TimelineShape.returnsToBaseline(): Boolean = when (this) {
    is TimelineShape.Full,
    is TimelineShape.OnsetComeupPeakTotal,
    is TimelineShape.OnsetComeupTotal,
    is TimelineShape.OnsetTotal,
    is TimelineShape.Total -> true
    is TimelineShape.OnsetComeupPeak,
    is TimelineShape.OnsetComeup,
    is TimelineShape.Onset,
    TimelineShape.None -> false
}

internal fun rangeWeightToUse(rangeInSeconds: Float, peakMinInSeconds: Float, horizontalWeight: Float): Float {
    val t = if (peakMinInSeconds > 0f) (rangeInSeconds / peakMinInSeconds).coerceIn(0f, 1f) else 1f
    return horizontalWeight + (0.5f - horizontalWeight) * t
}

private fun rangeSegmentsFor(shape: TimelineShape.Full, line: RawIngestion): List<Seg> {
    val endX = line.endSeconds ?: return emptyList()
    val startX = line.startSeconds
    val rangeInSeconds = endX - startX
    val onsetInSeconds = shape.onset.interpolateAtValueInSeconds(0.5f)
    val comeupInSeconds = shape.comeup.interpolateAtValueInSeconds(0.5f)
    val weightToUse = rangeWeightToUse(rangeInSeconds, shape.peak.minInSeconds, line.horizontalWeight)
    val peakInSeconds = shape.peak.interpolateAtValueInSeconds(weightToUse)
    val offsetInSeconds = shape.offset.interpolateAtValueInSeconds(weightToUse)
    return pointsToSegs(sampleRangeCurve(startX, endX, line.height, onsetInSeconds, comeupInSeconds, peakInSeconds, offsetInSeconds))
}

private fun trapezoidSegmentsFor(shape: TimelineShape.Full, line: RawIngestion): List<Seg> {
    val onsetEndX = line.startSeconds + shape.onset.interpolateAtValueInSeconds(0.5f)
    val comeupEndX = onsetEndX + shape.comeup.interpolateAtValueInSeconds(0.5f)
    val peakEndX = comeupEndX + shape.peak.interpolateAtValueInSeconds(line.horizontalWeight)
    val offsetEndX = peakEndX + shape.offset.interpolateAtValueInSeconds(line.horizontalWeight)
    return listOf(
        Seg(onsetEndX, 0f, comeupEndX, line.height),
        Seg(comeupEndX, line.height, peakEndX, line.height),
        Seg(peakEndX, line.height, offsetEndX, 0f),
    )
}

internal fun buildFull(shape: TimelineShape.Full, lines: List<RawIngestion>): RawTimelineCurve {
    val pointLines = lines.filter { it.endSeconds == null }
    val segs = pointLines.flatMap { trapezoidSegmentsFor(shape, it) } + lines.flatMap { rangeSegmentsFor(shape, it) }
    val ingestionXs = pointLines.map { it.startSeconds }
    val points = superpose(segs, ingestionXs)
    val nonNorm = points.maxOfOrNull { it.height } ?: 0.01f
    val end = points.maxOfOrNull { it.seconds }
        ?: (shape.onset.maxInSeconds + shape.comeup.maxInSeconds + shape.peak.maxInSeconds + shape.offset.maxInSeconds)
    val ingestionDots = ingestionXs.map { RawPoint(seconds = it, height = sumHeightAt(segs, it), isIngestionDot = true) }
    return RawTimelineCurve(
        segments = listOf(CurveSegment(points, dotted = false)),
        ingestionDots = ingestionDots,
        nonNormalisedHeight = nonNorm,
        endSeconds = end,
    )
}

internal fun buildOnsetComeupPeakTotal(shape: TimelineShape.OnsetComeupPeakTotal, line: RawIngestion): List<CurveSegment> {
    val start = line.startSeconds
    val onsetEnd = start + shape.onset.interpolateAtValueInSeconds(0.5f)
    val comeupEnd = onsetEnd + shape.comeup.interpolateAtValueInSeconds(0.5f)
    val peakEnd = comeupEnd + shape.peak.interpolateAtValueInSeconds(line.horizontalWeight)
    val totalEnd = start + shape.total.interpolateAtValueInSeconds(line.horizontalWeight)
    val solid = CurveSegment(
        listOf(
            RawPoint(start, 0f),
            RawPoint(onsetEnd, 0f),
            RawPoint(comeupEnd, line.height),
            RawPoint(peakEnd, line.height),
        ),
        dotted = false,
    )
    val tail = CurveSegment(
        listOf(RawPoint(peakEnd, line.height), RawPoint(totalEnd, 0f)),
        dotted = true,
    )
    return listOf(solid, tail)
}

internal fun buildOnsetComeupTotal(shape: TimelineShape.OnsetComeupTotal, line: RawIngestion): List<CurveSegment> {
    val start = line.startSeconds
    val onsetEnd = start + shape.onset.interpolateAtValueInSeconds(0.5f)
    val comeupEnd = onsetEnd + shape.comeup.interpolateAtValueInSeconds(0.5f)
    val totalEnd = start + shape.total.interpolateAtValueInSeconds(line.horizontalWeight)
    val solid = CurveSegment(
        listOf(RawPoint(start, 0f), RawPoint(onsetEnd, 0f), RawPoint(comeupEnd, line.height)),
        dotted = false,
    )
    val controlSeconds = comeupEnd + (totalEnd - comeupEnd) * 0.5f
    val tail = CurveSegment(
        listOf(
            RawPoint(comeupEnd, line.height),
            RawPoint(totalEnd, 0f, controlSeconds = controlSeconds, controlHeight = line.height),
        ),
        dotted = true,
    )
    return listOf(solid, tail)
}

internal fun buildOnsetTotal(shape: TimelineShape.OnsetTotal, line: RawIngestion): List<CurveSegment> {
    val start = line.startSeconds
    val onsetEnd = start + shape.onset.interpolateAtValueInSeconds(0.5f)
    val totalEnd = start + shape.total.interpolateAtValueInSeconds(line.horizontalWeight)
    val peakSeconds = start + (totalEnd - start) * 0.5f
    val solid = CurveSegment(
        listOf(RawPoint(start, 0f), RawPoint(onsetEnd, 0f)),
        dotted = false,
    )
    val upControl = peakSeconds - (peakSeconds - onsetEnd) * 0.5f
    val downControl = peakSeconds + (totalEnd - peakSeconds) * 0.5f
    val tail = CurveSegment(
        listOf(
            RawPoint(onsetEnd, 0f),
            RawPoint(peakSeconds, line.height, controlSeconds = upControl, controlHeight = line.height),
            RawPoint(totalEnd, 0f, controlSeconds = downControl, controlHeight = line.height),
        ),
        dotted = true,
    )
    return listOf(solid, tail)
}

internal fun buildTotal(shape: TimelineShape.Total, line: RawIngestion): List<CurveSegment> {
    val start = line.startSeconds
    val midSeconds = start + shape.total.minInSeconds / 2f
    val totalEnd = start + shape.total.interpolateAtValueInSeconds(line.horizontalWeight)
    val riseControlSeconds = midSeconds - (midSeconds - start) * 0.5f
    val fallControlSeconds = midSeconds + (totalEnd - midSeconds) * 0.5f
    val dotted = CurveSegment(
        listOf(
            RawPoint(start, 0f),
            RawPoint(midSeconds, line.height, controlSeconds = riseControlSeconds, controlHeight = line.height),
            RawPoint(totalEnd, 0f, controlSeconds = fallControlSeconds, controlHeight = line.height),
        ),
        dotted = true,
    )
    return listOf(dotted)
}

internal fun buildOnsetComeupPeak(shape: TimelineShape.OnsetComeupPeak, line: RawIngestion): List<CurveSegment> {
    val start = line.startSeconds
    val onsetEnd = start + shape.onset.interpolateAtValueInSeconds(0.5f)
    val comeupEnd = onsetEnd + shape.comeup.interpolateAtValueInSeconds(0.5f)
    val peakEnd = comeupEnd + shape.peak.interpolateAtValueInSeconds(line.horizontalWeight)
    val solid = CurveSegment(
        listOf(
            RawPoint(start, 0f),
            RawPoint(onsetEnd, 0f),
            RawPoint(comeupEnd, line.height),
            RawPoint(peakEnd, line.height),
        ),
        dotted = false,
    )
    return listOf(solid)
}

internal fun buildOnsetComeup(shape: TimelineShape.OnsetComeup, line: RawIngestion): List<CurveSegment> {
    val start = line.startSeconds
    val onsetEnd = start + shape.onset.interpolateAtValueInSeconds(0.5f)
    val comeupEnd = onsetEnd + shape.comeup.interpolateAtValueInSeconds(0.5f)
    val solid = CurveSegment(
        listOf(RawPoint(start, 0f), RawPoint(onsetEnd, 0f), RawPoint(comeupEnd, line.height)),
        dotted = false,
    )
    return listOf(solid)
}

internal fun buildOnset(shape: TimelineShape.Onset, line: RawIngestion): List<CurveSegment> {
    val start = line.startSeconds
    val onsetEnd = start + shape.onset.interpolateAtValueInSeconds(0.5f)
    val solid = CurveSegment(
        listOf(RawPoint(start, 0f), RawPoint(onsetEnd, 0f)),
        dotted = false,
    )
    return listOf(solid)
}

fun buildRawCurve(shape: TimelineShape, lines: List<RawIngestion>): RawTimelineCurve {
    val safeLines = lines.map { it.copy(height = it.height.finiteNonNegative()) }
    if (shape is TimelineShape.Full) return buildFull(shape, safeLines)
    val pointLines = safeLines.filter { it.endSeconds == null }
    val ingestionSeconds = pointLines.map { it.startSeconds }

    if (pointLines.size > 1 && shape.returnsToBaseline()) {
        val segs = pointLines.flatMap { segmentsToSegs(buildSimpleSegments(shape, it)) }
        val points = superpose(segs, ingestionSeconds)
        val nonNorm = points.maxOfOrNull { it.height }?.takeIf { it.isFinite() && it > 0f } ?: 0.01f
        val end = points.maxOfOrNull { it.seconds } ?: (ingestionSeconds.maxOrNull() ?: 0f)
        val ingestionDots = ingestionSeconds.map { RawPoint(seconds = it, height = sumHeightAt(segs, it), isIngestionDot = true) }
        return RawTimelineCurve(listOf(CurveSegment(points, dotted = false)), ingestionDots, nonNorm, end)
    }

    val segments = pointLines.flatMap { line -> buildSimpleSegments(shape, line) }
    val allPoints = segments.flatMap { it.points }
    val nonNorm = allPoints.maxOfOrNull { it.height } ?: 0.01f
    val end = allPoints.maxOfOrNull { it.seconds } ?: (ingestionSeconds.maxOrNull() ?: 0f)
    val ingestionDots = pointLines.map { RawPoint(seconds = it.startSeconds, height = 0f, isIngestionDot = true) }
    return RawTimelineCurve(segments, ingestionDots, nonNorm, end)
}

private fun buildSimpleSegments(shape: TimelineShape, line: RawIngestion): List<CurveSegment> =
    when (shape) {
        is TimelineShape.OnsetComeupPeakTotal -> buildOnsetComeupPeakTotal(shape, line)
        is TimelineShape.OnsetComeupTotal -> buildOnsetComeupTotal(shape, line)
        is TimelineShape.OnsetTotal -> buildOnsetTotal(shape, line)
        is TimelineShape.Total -> buildTotal(shape, line)
        is TimelineShape.OnsetComeupPeak -> buildOnsetComeupPeak(shape, line)
        is TimelineShape.OnsetComeup -> buildOnsetComeup(shape, line)
        is TimelineShape.Onset -> buildOnset(shape, line)
        TimelineShape.None, is TimelineShape.Full -> emptyList()
    }
