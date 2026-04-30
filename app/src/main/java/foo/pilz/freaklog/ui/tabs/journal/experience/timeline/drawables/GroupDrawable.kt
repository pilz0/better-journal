/*
 * Copyright (c) 2022-2026.
 * This file is part of FreakLog.
 *
 * FreakLog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.ui.tabs.journal.experience.timeline.drawables

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.substances.classes.roa.RoaDuration
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.WeightedLine
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.curve.toIngestionCurve
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.drawTimeRange
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.drawables.timelines.IngestionCurveDrawable
import java.time.Duration
import java.time.Instant
import kotlin.math.max

/**
 * One drawable per (substance, route) group.
 *
 * Time-ranged ingestions (`endTime != null`) are visualised both:
 * - as a horizontal "duration bar" at the bottom of the chart ([TimeRangeDrawable]) and
 * - as a continuous infusion curve in the projected timeline (via [IngestionCurveDrawable]).
 *
 * Point ingestions are visualised only as a curve.
 */
class GroupDrawable(
    val startTimeGraph: Instant,
    val color: AdaptiveColor,
    roaDuration: RoaDuration?,
    weightedLines: List<WeightedLine>,
    val areSubstanceHeightsIndependent: Boolean,
) : TimelineDrawable {
    private val curveDrawable: IngestionCurveDrawable
    private val timeRangeDrawables: List<TimeRangeDrawable>
    private val nonNormalisedMaxOfRoute: Float

    override var referenceHeight: Float = 1f
    override val nonNormalisedHeight: Float

    init {
        // Build the duration-bar drawables for time-ranged ingestions.
        val intermediateRanges = weightedLines.mapNotNull {
            if (it.endTime != null) {
                TimeRangeDrawable.IntermediateRepresentation(
                    startInSeconds = Duration.between(startTimeGraph, it.startTime).seconds.toFloat(),
                    endInSeconds = Duration.between(startTimeGraph, it.endTime).seconds.toFloat(),
                    fullTimelineDurations = roaDuration?.toFullTimelineDurations(),
                    height = it.height
                )
            } else null
        }.sortedBy { it.startInSeconds }
        timeRangeDrawables = intermediateRanges.mapIndexed { index, currentRange ->
            val intersectionCount = intermediateRanges.subList(0, index).count {
                it.startInSeconds <= currentRange.endInSeconds &&
                    it.endInSeconds >= currentRange.startInSeconds
            }
            TimeRangeDrawable(
                color = color,
                ingestionStartInSeconds = currentRange.startInSeconds,
                ingestionEndInSeconds = currentRange.endInSeconds,
                intersectionCountWithPreviousRanges = intersectionCount,
            )
        }

        // Build the projected curve(s).
        val curves = if (roaDuration != null) {
            weightedLines.mapNotNull { roaDuration.toIngestionCurve(it, startTimeGraph) }
        } else {
            emptyList()
        }
        curveDrawable = IngestionCurveDrawable(curves = curves)

        nonNormalisedMaxOfRoute = curveDrawable.nonNormalisedHeight
        nonNormalisedHeight = nonNormalisedMaxOfRoute
    }

    fun normaliseHeight(overallMaxHeight: Float) {
        this.referenceHeight = overallMaxHeight
        val finalNonNormalisedMaxHeight: Float = if (areSubstanceHeightsIndependent) {
            nonNormalisedMaxOfRoute
        } else {
            overallMaxHeight
        }
        curveDrawable.referenceHeight = finalNonNormalisedMaxHeight
    }

    override fun drawTimeLine(
        drawScope: DrawScope,
        canvasHeight: Float,
        pixelsPerSec: Float,
        color: Color,
        density: Density
    ) {
        curveDrawable.drawTimeLine(
            drawScope = drawScope,
            canvasHeight = canvasHeight,
            pixelsPerSec = pixelsPerSec,
            color = color,
            density = density
        )
        for (rangeDrawable in timeRangeDrawables) {
            drawScope.drawTimeRange(
                timeRangeDrawable = rangeDrawable,
                canvasHeight = canvasHeight,
                pixelsPerSec = pixelsPerSec,
                color = color,
            )
        }
    }

    override val endOfLineRelativeToStartInSeconds: Float
        get() {
            val maxWidthOfTimeRangeIngestions =
                timeRangeDrawables.maxOfOrNull { it.ingestionEndInSeconds } ?: 0f
            return max(maxWidthOfTimeRangeIngestions, curveDrawable.endOfLineRelativeToStartInSeconds)
        }
}
