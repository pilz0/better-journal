package foo.pilz.freaklog.ui.graph.scene.builders

import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.DurationUnits
import foo.pilz.freaklog.data.substances.classes.roa.RoaDuration
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test

class TimelineCurveMathTest {

    private fun range(min: Float, max: Float) =
        DurationRange(min = min, max = max, units = DurationUnits.SECONDS)

    @Test
    fun selectsFullWhenAllFourPhasesPresent() {
        val roa = RoaDuration(
            onset = range(60f, 60f), comeup = range(60f, 60f),
            peak = range(60f, 60f), offset = range(60f, 60f), total = null, afterglow = null,
        )
        assertEquals(TimelineShape.Full::class, selectTimelineShape(roa)::class)
    }

    @Test
    fun fallsBackToTotalWhenOnlyTotalPresent() {
        val roa = RoaDuration(
            onset = null, comeup = null, peak = null, offset = null,
            total = range(3600f, 3600f), afterglow = null,
        )
        assertEquals(TimelineShape.Total::class, selectTimelineShape(roa)::class)
    }

    @Test
    fun fallsBackToNoneWhenNothingPresent() {
        val roa = RoaDuration(null, null, null, null, null, null)
        assertEquals(TimelineShape.None::class, selectTimelineShape(roa)::class)
    }

    @Test
    fun samplePointsReturnsEmptyWhenStartAfterEnd() {
        val pts = sampleRangeCurve(startX = 100f, endX = 50f, hMax = 1f, onset = 10f, comeup = 10f, peak = 10f, offset = 10f)
        assertEquals(0, pts.size)
    }

    @Test
    fun zeroLengthRangeProducesNoNaNOrInfinity() {
        val pts = sampleRangeCurve(startX = 100f, endX = 100f, hMax = 1f, onset = 10f, comeup = 10f, peak = 10f, offset = 10f)
        assertEquals(true, pts.all { it.height.isFinite() })
    }

    @Test
    fun zeroComeupProducesNoNaNOrInfinity() {
        val pts = sampleRangeCurve(startX = 0f, endX = 600f, hMax = 1f, onset = 10f, comeup = 0f, peak = 10f, offset = 10f)
        assertEquals(true, pts.all { it.height.isFinite() })
    }

    @Test
    fun normalizationDividesByReferenceHeightAndWidth() {
        val raw = RawTimelineCurve(
            segments = listOf(
                CurveSegment(
                    points = listOf(RawPoint(0f, 0f), RawPoint(50f, 2f), RawPoint(100f, 0f)),
                    dotted = false,
                )
            ),
            ingestionDots = listOf(RawPoint(0f, 0f)),
            nonNormalisedHeight = 2f,
            endSeconds = 100f,
        )
        val norm = normalizeCurve(raw, referenceHeight = 4f, widthSeconds = 200f)
        val peakY = norm.segments.first().points.maxOf { it.height }
        val peakX = norm.segments.first().points.maxOf { it.seconds }
        assertEquals(0.5, peakY.toDouble(), 1e-6)
        assertEquals(0.5, peakX.toDouble(), 1e-6)
        assertEquals(0.5, norm.endFraction.toDouble(), 1e-6)
        assertEquals(0.0, norm.ingestionDots.first().seconds.toDouble(), 1e-6)
    }

    @Test
    fun normalizationGuardsZeroReferenceAndWidth() {
        val raw = RawTimelineCurve(
            segments = listOf(CurveSegment(listOf(RawPoint(10f, 1f)), dotted = false)),
            ingestionDots = listOf(RawPoint(10f, 0f)),
            nonNormalisedHeight = 0f,
            endSeconds = 10f,
        )
        val norm = normalizeCurve(raw, referenceHeight = 0f, widthSeconds = 0f)
        assertEquals(true, norm.segments.first().points.all { it.height.isFinite() && it.seconds.isFinite() })
    }

    @Test
    fun overlappingPointDosesSumHeights() {
        val full = TimelineShape.Full(
            onset = FullDurationRange(0f, 0f), comeup = FullDurationRange(0f, 0f),
            peak = FullDurationRange(100f, 100f), offset = FullDurationRange(0f, 0f),
        )
        val lines = listOf(
            RawIngestion(startSeconds = 0f, endSeconds = null, horizontalWeight = 0.5f, height = 1f),
            RawIngestion(startSeconds = 0f, endSeconds = null, horizontalWeight = 0.5f, height = 1f),
        )
        val curve = buildFull(full, lines)
        assertEquals(2.0, curve.nonNormalisedHeight.toDouble(), 1e-6)
    }

    @Test
    fun rangedIngestionProducesFiniteCurve() {
        val full = TimelineShape.Full(
            onset = FullDurationRange(60f, 60f), comeup = FullDurationRange(120f, 120f),
            peak = FullDurationRange(600f, 600f), offset = FullDurationRange(300f, 300f),
        )
        val lines = listOf(
            RawIngestion(startSeconds = 0f, endSeconds = 1800f, horizontalWeight = 0.5f, height = 1f),
        )
        val curve = buildFull(full, lines)
        val pts = curve.segments.single().points
        assertEquals(true, pts.isNotEmpty())
        assertEquals(true, pts.all { it.height.isFinite() && it.seconds.isFinite() })
    }

    @Test
    fun singlePointDosePeaksAtItsHeight() {
        val full = TimelineShape.Full(
            onset = FullDurationRange(0f, 0f), comeup = FullDurationRange(0f, 0f),
            peak = FullDurationRange(100f, 100f), offset = FullDurationRange(0f, 0f),
        )
        val lines = listOf(RawIngestion(0f, null, 0.5f, 0.8f))
        val curve = buildFull(full, lines)
        assertEquals(0.8, curve.nonNormalisedHeight.toDouble(), 1e-6)
    }

    @Test
    fun buildRawCurveNoneHasNoSegmentsButKeepsIngestionDots() {
        val curve = buildRawCurve(TimelineShape.None, listOf(RawIngestion(120f, null, 0.5f, 1f)))
        assertEquals(0, curve.segments.size)
        assertEquals(1, curve.ingestionDots.size)
        assertEquals(0.01, curve.nonNormalisedHeight.toDouble(), 1e-6)
    }

    @Test
    fun buildRawCurveSimpleShapeUsesPointIngestionsOnly() {
        val shape = TimelineShape.OnsetComeupPeak(
            FullDurationRange(60f, 60f), FullDurationRange(60f, 60f), FullDurationRange(60f, 60f),
        )
        val lines = listOf(
            RawIngestion(0f, null, 0.5f, 1f),
            RawIngestion(0f, 600f, 0.5f, 1f),
        )
        val curve = buildRawCurve(shape, lines)
        assertEquals(1, curve.segments.size)
        assertEquals(1, curve.ingestionDots.size)
    }

    @Test
    fun buildRawCurveSimpleShapeWithOnlyRangedIngestionsHasNoSegments() {
        val shape = TimelineShape.OnsetComeup(FullDurationRange(60f, 60f), FullDurationRange(60f, 60f))
        val curve = buildRawCurve(shape, listOf(RawIngestion(0f, 600f, 0.5f, 1f)))
        assertEquals(0, curve.segments.size)
        assertEquals(0, curve.ingestionDots.size)
    }

    @Test
    fun onsetComeupPeakTotalHasSolidThenDottedSegments() {
        val shape = TimelineShape.OnsetComeupPeakTotal(
            FullDurationRange(60f, 60f), FullDurationRange(60f, 60f),
            FullDurationRange(60f, 60f), FullDurationRange(600f, 600f),
        )
        val segs = buildOnsetComeupPeakTotal(shape, RawIngestion(0f, null, 0.5f, 1f))
        assertEquals(2, segs.size)
        assertEquals(false, segs[0].dotted)
        assertEquals(true, segs[1].dotted)
    }

    @Test
    fun timeRangeIntersectionCountsEarlierOverlaps() {
        val ingestions = listOf(
            RawIngestion(0f, 100f, 0.5f, 1f),
            RawIngestion(50f, 150f, 0.5f, 1f),
            RawIngestion(200f, 300f, 0.5f, 1f),
        )
        val ranges = buildRawTimeRanges(ingestions)
        assertEquals(3, ranges.size)
        assertEquals(0, ranges[0].intersectionCount)
        assertEquals(1, ranges[1].intersectionCount)
        assertEquals(0, ranges[2].intersectionCount)
    }

    @Test
    fun normalizeGuardsNonFiniteReferenceHeight() {
        val raw = RawTimelineCurve(
            segments = listOf(CurveSegment(listOf(RawPoint(10f, 1f)), dotted = false)),
            ingestionDots = listOf(RawPoint(10f, 0f)),
            nonNormalisedHeight = 1f,
            endSeconds = 10f,
        )
        val withNaN = normalizeCurve(raw, Float.NaN, 100f)
        val withInf = normalizeCurve(raw, Float.POSITIVE_INFINITY, 100f)
        assertTrue(withNaN.segments.first().points.all { it.height.isFinite() })
        assertTrue(withInf.segments.first().points.all { it.height.isFinite() })
    }

    @Test
    fun buildRawCurveSanitizesNonFiniteHeights() {
        val shape = TimelineShape.OnsetComeupPeak(
            FullDurationRange(60f, 60f), FullDurationRange(60f, 60f), FullDurationRange(60f, 60f),
        )
        val lines = listOf(
            RawIngestion(0f, null, 0.5f, Float.NaN),
            RawIngestion(120f, null, 0.5f, Float.POSITIVE_INFINITY),
        )
        val curve = buildRawCurve(shape, lines)
        assertTrue(curve.segments.flatMap { it.points }.all { it.height.isFinite() && it.seconds.isFinite() })
        assertTrue(curve.nonNormalisedHeight.isFinite())
    }

    @Test
    fun rangeWeightIsContinuousAtThreshold() {
        val peakMin = 600f
        val weight = 0.8f
        val below = rangeWeightToUse(599f, peakMin, weight)
        val at = rangeWeightToUse(600f, peakMin, weight)
        val above = rangeWeightToUse(601f, peakMin, weight)
        assertEquals(0.5, at.toDouble(), 1e-6)
        assertEquals(0.5, above.toDouble(), 1e-6)
        assertTrue(kotlin.math.abs(below - at) < 0.01f)
    }

    @Test
    fun rangeWeightEndpointsAndZeroGuard() {
        assertEquals(0.8, rangeWeightToUse(0f, 600f, 0.8f).toDouble(), 1e-6)
        assertEquals(0.5, rangeWeightToUse(100f, 0f, 0.8f).toDouble(), 1e-6)
    }

    @Test
    fun multipleTotalDosesAreSummedIntoOneSolidCurve() {
        val shape = TimelineShape.OnsetComeupTotal(
            FullDurationRange(60f, 60f), FullDurationRange(60f, 60f), FullDurationRange(600f, 600f),
        )
        val lines = listOf(
            RawIngestion(0f, null, 0.5f, 1f),
            RawIngestion(0f, null, 0.5f, 1f),
        )
        val curve = buildRawCurve(shape, lines)
        assertEquals(1, curve.segments.size)
        assertEquals(false, curve.segments.first().dotted)
        assertTrue(curve.nonNormalisedHeight > 1.5f)
    }

    @Test
    fun singleTotalDoseKeepsSolidPlusDottedSegments() {
        val shape = TimelineShape.OnsetComeupTotal(
            FullDurationRange(60f, 60f), FullDurationRange(60f, 60f), FullDurationRange(600f, 600f),
        )
        val curve = buildRawCurve(shape, listOf(RawIngestion(0f, null, 0.5f, 1f)))
        assertEquals(2, curve.segments.size)
        assertTrue(curve.segments.any { it.dotted })
    }

    @Test
    fun multipleOpenEndedDosesAreNotSummed() {
        val shape = TimelineShape.OnsetComeup(FullDurationRange(60f, 60f), FullDurationRange(60f, 60f))
        val lines = listOf(
            RawIngestion(0f, null, 0.5f, 1f),
            RawIngestion(0f, null, 0.5f, 1f),
        )
        val curve = buildRawCurve(shape, lines)
        assertEquals(2, curve.segments.size)
    }

    @Test
    fun redoseDotSitsOnExistingCurveHeight() {
        val shape = TimelineShape.Full(
            onset = FullDurationRange(60f, 60f),
            comeup = FullDurationRange(60f, 60f),
            peak = FullDurationRange(1000f, 1000f),
            offset = FullDurationRange(60f, 60f),
        )
        val lines = listOf(
            RawIngestion(0f, null, 0.5f, 1f),
            RawIngestion(300f, null, 0.5f, 1f),
        )
        val curve = buildFull(shape, lines)
        val redoseDot = curve.ingestionDots.first { it.seconds == 300f }
        assertTrue(redoseDot.height > 0.5f)
    }

    @Test
    fun firstDoseDotIsAtBaseline() {
        val shape = TimelineShape.Full(
            onset = FullDurationRange(60f, 60f),
            comeup = FullDurationRange(60f, 60f),
            peak = FullDurationRange(600f, 600f),
            offset = FullDurationRange(60f, 60f),
        )
        val curve = buildFull(shape, listOf(RawIngestion(0f, null, 0.5f, 1f)))
        val dot = curve.ingestionDots.first { it.seconds == 0f }
        assertEquals(0.0, dot.height.toDouble(), 1e-6)
    }
}
