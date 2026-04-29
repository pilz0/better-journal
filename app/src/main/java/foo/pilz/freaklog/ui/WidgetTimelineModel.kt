/*
 * Copyright (c) 2024-2025.
 * This file is part of FreakLog.
 *
 * FreakLog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.ui

import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.RoaDuration
import java.time.Duration
import java.time.Instant

/**
 * Pure-Kotlin model that turns a list of recent ingestions into the data needed
 * to draw the timeline graph in the home-screen widget.
 *
 * The widget runs in a [androidx.work.CoroutineWorker] without access to a
 * Compose [androidx.compose.ui.graphics.drawscope.DrawScope], so this model
 * deliberately stays free of Android / Compose dependencies and produces plain
 * lists of sample points which the worker can draw onto a [android.graphics.Canvas].
 *
 * Compared to the previous implementation, the model:
 *  * picks an **adaptive time window** that contains all currently-active
 *    effects (including a small lookback before the earliest active ingestion
 *    and a small lookahead past the latest predicted offset end) instead of a
 *    fixed -2h..+3h window;
 *  * **sums overlapping doses of the same substance** so two doses an hour
 *    apart produce one taller curve, mirroring the in-app
 *    [foo.pilz.freaklog.ui.tabs.journal.experience.timeline.drawables.timelines.FullTimelines];
 *  * uses the substance's actual min/max [RoaDuration] ranges (with
 *    `horizontalWeight = 0.5`) instead of hard-coded fallback values, only
 *    falling back to a total-duration triangle when full RoA data is
 *    unavailable.
 */
data class WidgetTimelineModel(
    /** Start of the visible window. */
    val windowStart: Instant,
    /** End of the visible window (exclusive). */
    val windowEnd: Instant,
    /** Reference time used as the "now" marker. */
    val now: Instant,
    /** One drawable per substance + route group. Heights are normalised to [0..1]. */
    val groups: List<TimelineGroup>,
) {
    val widthInSeconds: Float
        get() = Duration.between(windowStart, windowEnd).seconds.toFloat().coerceAtLeast(1f)

    /** Seconds from [windowStart] to [now] (may be negative or past [widthInSeconds]). */
    val nowSecondsFromStart: Float
        get() = Duration.between(windowStart, now).seconds.toFloat()

    /** True when there is at least one curve to draw. */
    val hasContent: Boolean get() = groups.any { it.points.size >= 2 }

    data class TimelineGroup(
        val color: AdaptiveColor,
        /** True if drawn solidly (full RoA available); false for a dotted/uncertain shape. */
        val isComplete: Boolean,
        /**
         * Sample points along the (already-summed) curve, sorted by `secondsFromStart`,
         * in seconds relative to [windowStart].  Heights are in `[0..1]` after
         * normalisation against the overall maximum across all groups.
         */
        val points: List<SamplePoint>,
        /** Ingestion times in seconds relative to [windowStart] (for drawing dots). */
        val ingestionDotsSecondsFromStart: List<Float>,
    )

    data class SamplePoint(
        val secondsFromStart: Float,
        val height: Float,
    )

    companion object {
        /** Default lookback when there is no early active ingestion. */
        private const val DEFAULT_LOOKBACK_SECONDS = 60L * 60L          // 1 hour
        /** Default lookahead when there is no late effect ending. */
        private const val DEFAULT_LOOKAHEAD_SECONDS = 3L * 60L * 60L    // 3 hours
        /** Padding added before the earliest active ingestion. */
        private const val START_PADDING_SECONDS = 30L * 60L             // 30 minutes
        /** Padding added after the latest predicted effect end. */
        private const val END_PADDING_SECONDS = 30L * 60L               // 30 minutes
        /** Maximum total window width. Beyond this the graph becomes useless. */
        private const val MAX_WINDOW_SECONDS = 24L * 60L * 60L          // 24 hours
        /** Maximum lookback. */
        private const val MAX_LOOKBACK_SECONDS = 12L * 60L * 60L        // 12 hours
        /** Default total duration when nothing is known about a substance. */
        private const val DEFAULT_TOTAL_DURATION_SECONDS = 6L * 60L * 60L // 6 hours
        /** Drop ingestions whose effect ended this long before [now]. */
        private const val INACTIVE_GRACE_SECONDS = 30L * 60L            // 30 minutes
        /** Number of curve sample points per substance group. */
        private const val SAMPLES_PER_GROUP = 60

        /**
         * @param now reference time used for "now" and active-ingestion filtering.
         * @param ingestions all candidate ingestions (typically last 48h). The model
         *   filters out ones whose effect already ended.
         * @param durationsBySubstance per-substance route → [RoaDuration] map.
         */
        fun build(
            now: Instant,
            ingestions: List<IngestionInput>,
            durationsBySubstance: Map<String, Map<AdministrationRoute, RoaDuration?>>,
        ): WidgetTimelineModel {
            // Resolve the duration shape for each ingestion up front.
            val resolved = ingestions.map { ing ->
                val roa = durationsBySubstance[ing.substanceName]?.get(ing.route)
                ResolvedIngestion(
                    input = ing,
                    shape = resolveShape(roa),
                )
            }.filter { resolved ->
                // Only include ingestions whose effect overlaps the window of interest:
                // started before now + max lookahead and ends after now - grace.
                val start = resolved.input.time
                val end = start.plusSeconds(resolved.shape.totalSeconds.toLong())
                end.isAfter(now.minusSeconds(INACTIVE_GRACE_SECONDS))
            }

            if (resolved.isEmpty()) {
                val windowStart = now.minusSeconds(DEFAULT_LOOKBACK_SECONDS)
                val windowEnd = now.plusSeconds(DEFAULT_LOOKAHEAD_SECONDS)
                return WidgetTimelineModel(
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                    now = now,
                    groups = emptyList(),
                )
            }

            // ----- Compute adaptive window -----
            val earliestStart = resolved.minOf { it.input.time }
            val latestEnd = resolved.maxOf { it.input.time.plusSeconds(it.shape.totalSeconds.toLong()) }

            val maxLookback = now.minusSeconds(MAX_LOOKBACK_SECONDS)
            val rawStart = earliestStart.minusSeconds(START_PADDING_SECONDS)
            val defaultStart = now.minusSeconds(DEFAULT_LOOKBACK_SECONDS)
            // Choose the earliest of (rawStart, defaultStart) but never further back than maxLookback.
            val candidateStart = if (rawStart.isBefore(defaultStart)) rawStart else defaultStart
            val windowStart = if (candidateStart.isBefore(maxLookback)) maxLookback else candidateStart

            val rawEnd = latestEnd.plusSeconds(END_PADDING_SECONDS)
            val defaultEnd = now.plusSeconds(DEFAULT_LOOKAHEAD_SECONDS)
            // Choose the latest of (rawEnd, defaultEnd).
            var windowEnd = if (rawEnd.isAfter(defaultEnd)) rawEnd else defaultEnd

            // Cap the total window width.
            val maxEnd = windowStart.plusSeconds(MAX_WINDOW_SECONDS)
            if (windowEnd.isAfter(maxEnd)) {
                windowEnd = maxEnd
            }

            // ----- Build per-substance + route groups with summed heights -----
            val grouped = resolved.groupBy { GroupKey(it.input.substanceName, it.input.route) }

            val rawGroups = grouped.map { (_, items) ->
                val color = items.first().input.color
                // All ingestions in a group share the same shape (same substance+route).
                val isComplete = items.first().shape is Shape.Full
                val segments = items.flatMap { it.toLineSegments(windowStart) }
                val ingestionXs = items.map {
                    Duration.between(windowStart, it.input.time).seconds.toFloat()
                }
                RawGroup(
                    color = color,
                    isComplete = isComplete,
                    segments = segments,
                    ingestionXs = ingestionXs,
                )
            }

            val widthInSeconds = Duration.between(windowStart, windowEnd).seconds.toFloat().coerceAtLeast(1f)

            // Sample each group's summed curve.
            val sampledGroups = rawGroups.map { group ->
                val pts = sampleSummedCurve(
                    segments = group.segments,
                    fromSeconds = 0f,
                    toSeconds = widthInSeconds,
                    samples = SAMPLES_PER_GROUP,
                )
                group to pts
            }

            // Find the overall maximum height across groups for normalisation.
            val overallMax = sampledGroups.flatMap { it.second }.maxOfOrNull { it.height } ?: 1f
            val divisor = if (overallMax > 0f) overallMax else 1f

            val groups = sampledGroups.map { (group, points) ->
                TimelineGroup(
                    color = group.color,
                    isComplete = group.isComplete,
                    points = points.map {
                        SamplePoint(
                            secondsFromStart = it.secondsFromStart,
                            height = (it.height / divisor).coerceIn(0f, 1f),
                        )
                    },
                    ingestionDotsSecondsFromStart = group.ingestionXs,
                )
            }

            return WidgetTimelineModel(
                windowStart = windowStart,
                windowEnd = windowEnd,
                now = now,
                groups = groups,
            )
        }

        private fun resolveShape(roa: RoaDuration?): Shape {
            if (roa != null) {
                val onset = roa.onset?.toRange()
                val comeup = roa.comeup?.toRange()
                val peak = roa.peak?.toRange()
                val offset = roa.offset?.toRange()
                if (onset != null && comeup != null && peak != null && offset != null) {
                    return Shape.Full(onset, comeup, peak, offset)
                }
                val total = roa.total?.toRange()
                if (total != null) {
                    return Shape.TotalOnly(total)
                }
            }
            return Shape.TotalOnly(Range(DEFAULT_TOTAL_DURATION_SECONDS.toFloat(), DEFAULT_TOTAL_DURATION_SECONDS.toFloat()))
        }
    }

    /** Input for the model. Independent of Room entities so the model is testable. */
    data class IngestionInput(
        val substanceName: String,
        val route: AdministrationRoute,
        val time: Instant,
        val color: AdaptiveColor,
    )

    private data class GroupKey(val substanceName: String, val route: AdministrationRoute)

    private data class RawGroup(
        val color: AdaptiveColor,
        val isComplete: Boolean,
        val segments: List<LineSegment>,
        val ingestionXs: List<Float>,
    )

    private data class ResolvedIngestion(
        val input: IngestionInput,
        val shape: Shape,
    ) {
        fun toLineSegments(windowStart: Instant): List<LineSegment> {
            val startX = Duration.between(windowStart, input.time).seconds.toFloat()
            return shape.toLineSegments(startX, height = 1f)
        }
    }

    /** Inclusive range of seconds. Always min <= max. */
    internal data class Range(val minSeconds: Float, val maxSeconds: Float) {
        fun interpolate(t: Float): Float = minSeconds + (maxSeconds - minSeconds) * t
    }

    /** A line segment in (x = seconds from window start, y = height) space. */
    internal data class LineSegment(val x0: Float, val y0: Float, val x1: Float, val y1: Float) {
        /**
         * Half-open on the right so adjacent segments sharing an endpoint
         * (e.g. comeup→peak) don't double-count at that x and create a 2x spike.
         */
        fun contains(x: Float): Boolean = x1 > x0 && x >= x0 && x < x1
        fun heightAt(x: Float): Float {
            val span = x1 - x0
            if (span <= 0f) return 0f
            val t = (x - x0) / span
            return y0 + t * (y1 - y0)
        }
    }

    internal sealed class Shape {
        abstract val totalSeconds: Float
        abstract fun toLineSegments(startX: Float, height: Float): List<LineSegment>

        data class Full(
            val onset: Range,
            val comeup: Range,
            val peak: Range,
            val offset: Range,
        ) : Shape() {
            override val totalSeconds: Float
                // Use median for the typical end-of-effect; horizontalWeight=0.5
                get() = onset.interpolate(0.5f) + comeup.interpolate(0.5f) +
                        peak.interpolate(0.5f) + offset.interpolate(0.5f)

            override fun toLineSegments(startX: Float, height: Float): List<LineSegment> {
                val onsetEnd = startX + onset.interpolate(0.5f)
                val comeupEnd = onsetEnd + comeup.interpolate(0.5f)
                val peakEnd = comeupEnd + peak.interpolate(0.5f)
                val offsetEnd = peakEnd + offset.interpolate(0.5f)
                return listOf(
                    // Flat zero through onset.
                    LineSegment(startX, 0f, onsetEnd, 0f),
                    // Comeup ramp.
                    LineSegment(onsetEnd, 0f, comeupEnd, height),
                    // Plateau peak.
                    LineSegment(comeupEnd, height, peakEnd, height),
                    // Offset ramp down.
                    LineSegment(peakEnd, height, offsetEnd, 0f),
                )
            }
        }

        data class TotalOnly(val total: Range) : Shape() {
            override val totalSeconds: Float get() = total.interpolate(0.5f)

            override fun toLineSegments(startX: Float, height: Float): List<LineSegment> {
                val totalSec = total.interpolate(0.5f)
                if (totalSec <= 0f) return emptyList()
                // Approximate the curve as a triangle peaking at 1/3 of the total duration.
                val peakX = startX + totalSec * 0.33f
                val endX = startX + totalSec
                return listOf(
                    LineSegment(startX, 0f, peakX, height),
                    LineSegment(peakX, height, endX, 0f),
                )
            }
        }
    }
}

private fun foo.pilz.freaklog.data.substances.classes.roa.DurationRange.toRange(): WidgetTimelineModel.Range? {
    val mn = minInSec
    val mx = maxInSec
    return if (mn != null && mx != null) WidgetTimelineModel.Range(mn, mx) else null
}

/**
 * Sample the *sum* of all line segments at evenly spaced x positions plus all
 * segment endpoints inside the window, so corners don't get smoothed out.
 */
internal fun sampleSummedCurve(
    segments: List<WidgetTimelineModel.LineSegment>,
    fromSeconds: Float,
    toSeconds: Float,
    samples: Int,
): List<WidgetTimelineModel.SamplePoint> {
    if (segments.isEmpty() || toSeconds <= fromSeconds) return emptyList()
    val xs = sortedSetOf<Float>()
    xs.add(fromSeconds)
    xs.add(toSeconds)
    if (samples > 1) {
        val step = (toSeconds - fromSeconds) / samples
        var i = 1
        while (i < samples) {
            xs.add(fromSeconds + step * i)
            i++
        }
    }
    // Include corner x-coordinates so peaks/plateaus stay sharp.
    for (s in segments) {
        if (s.x0 in fromSeconds..toSeconds) xs.add(s.x0)
        if (s.x1 in fromSeconds..toSeconds) xs.add(s.x1)
    }
    return xs.map { x ->
        val total = segments.sumOf { seg ->
            if (seg.contains(x)) seg.heightAt(x).toDouble() else 0.0
        }
        WidgetTimelineModel.SamplePoint(secondsFromStart = x, height = total.toFloat().coerceAtLeast(0f))
    }
}
