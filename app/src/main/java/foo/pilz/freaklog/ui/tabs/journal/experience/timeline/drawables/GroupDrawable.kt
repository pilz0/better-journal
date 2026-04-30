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
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.drawables.timelines.NoTimeline
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.drawables.timelines.toFullTimelines
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.drawables.timelines.toOnsetComeupPeakTimeline
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.drawables.timelines.toOnsetComeupPeakTotalTimeline
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.drawables.timelines.toOnsetComeupTimeline
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.drawables.timelines.toOnsetComeupTotalTimeline
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.drawables.timelines.toOnsetTimeline
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.drawables.timelines.toOnsetTotalTimeline
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.drawables.timelines.toTotalTimeline
import java.time.Duration
import java.time.Instant
import kotlin.math.max

/**
 * One drawable per (substance, route) group.
 *
 * Two rendering modes are supported:
 * - [useBatemanCurve] = true: each ingestion is rendered as a smooth Bateman pharmacokinetic
 *   curve via [IngestionCurveDrawable]. Used by the ingestion logging screen.
 * - [useBatemanCurve] = false (default): the legacy piecewise-linear trapezoid timelines are
 *   used (onset/comeup/peak/offset polygons with progressive fallbacks). Used by the
 *   experience view, substance screen, and stand-alone timeline screen.
 *
 * Time-ranged ingestions (`endTime != null`) are visualised as a horizontal "duration bar"
 * at the bottom of the chart ([TimeRangeDrawable]) in both modes.
 */
@Suppress("LongParameterList", "ComplexMethod", "NestedBlockDepth")
class GroupDrawable(
    val startTimeGraph: Instant,
    val color: AdaptiveColor,
    roaDuration: RoaDuration?,
    weightedLines: List<WeightedLine>,
    val areSubstanceHeightsIndependent: Boolean,
    private val useBatemanCurve: Boolean = false,
) : TimelineDrawable {
    private val timelineDrawables: List<TimelineDrawable>
    private val curveDrawable: IngestionCurveDrawable?
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

        if (useBatemanCurve) {
            // New Bateman pharmacokinetic curve rendering (used on the ingestion logging screen).
            val curves = if (roaDuration != null) {
                weightedLines.mapNotNull { roaDuration.toIngestionCurve(it, startTimeGraph) }
            } else {
                emptyList()
            }
            curveDrawable = IngestionCurveDrawable(curves = curves)
            timelineDrawables = emptyList()
            nonNormalisedMaxOfRoute = curveDrawable.nonNormalisedHeight
            nonNormalisedHeight = nonNormalisedMaxOfRoute
        } else {
            // Legacy trapezoid rendering (used everywhere else, including the experience view).
            curveDrawable = null
            val weightedLinesForPointIngestions = weightedLines.filter { it.endTime == null }
            timelineDrawables = if (weightedLines.isNotEmpty()) {
                buildLegacyTimelineDrawables(roaDuration, weightedLines, weightedLinesForPointIngestions)
            } else {
                emptyList()
            }
            val pointHeights = timelineDrawables.map { it.nonNormalisedHeight }
            nonNormalisedMaxOfRoute = pointHeights.maxOrNull() ?: 1f
            nonNormalisedHeight = nonNormalisedMaxOfRoute
        }
    }

    private fun buildLegacyTimelineDrawables(
        roaDuration: RoaDuration?,
        weightedLines: List<WeightedLine>,
        weightedLinesForPointIngestions: List<WeightedLine>,
    ): List<TimelineDrawable> {
        val fulls = roaDuration?.toFullTimelines(
            weightedLines = weightedLines,
            startTimeGraph = startTimeGraph,
        )
        if (fulls != null) return listOf(fulls)
        if (weightedLinesForPointIngestions.isEmpty()) return emptyList()
        val onsetComeupPeakTotals = weightedLinesForPointIngestions.mapNotNull {
            roaDuration?.toOnsetComeupPeakTotalTimeline(
                peakAndTotalWeight = it.horizontalWeight,
                ingestionTimeRelativeToStartInSeconds = getDistanceFromStartGraphInSeconds(it.startTime),
                nonNormalisedHeight = it.height,
            )
        }
        return onsetComeupPeakTotals.ifEmpty {
            val onsetComeupTotals = weightedLinesForPointIngestions.mapNotNull {
                roaDuration?.toOnsetComeupTotalTimeline(
                    totalWeight = it.horizontalWeight,
                    ingestionTimeRelativeToStartInSeconds = getDistanceFromStartGraphInSeconds(it.startTime),
                    nonNormalisedHeight = it.height,
                )
            }
            onsetComeupTotals.ifEmpty {
                val onsetTotals = weightedLinesForPointIngestions.mapNotNull {
                    roaDuration?.toOnsetTotalTimeline(
                        totalWeight = it.horizontalWeight,
                        ingestionTimeRelativeToStartInSeconds = getDistanceFromStartGraphInSeconds(it.startTime),
                        nonNormalisedHeight = it.height,
                    )
                }
                onsetTotals.ifEmpty {
                    val totals = weightedLinesForPointIngestions.mapNotNull {
                        roaDuration?.toTotalTimeline(
                            totalWeight = it.horizontalWeight,
                            ingestionTimeRelativeToStartInSeconds = getDistanceFromStartGraphInSeconds(it.startTime),
                            nonNormalisedHeight = it.height,
                        )
                    }
                    totals.ifEmpty {
                        val onsetComeupPeaks = weightedLinesForPointIngestions.mapNotNull {
                            roaDuration?.toOnsetComeupPeakTimeline(
                                peakWeight = it.horizontalWeight,
                                ingestionTimeRelativeToStartInSeconds =
                                    getDistanceFromStartGraphInSeconds(it.startTime),
                                nonNormalisedHeight = it.height,
                            )
                        }
                        onsetComeupPeaks.ifEmpty {
                            val onsetComeups = weightedLinesForPointIngestions.mapNotNull {
                                roaDuration?.toOnsetComeupTimeline(
                                    ingestionTimeRelativeToStartInSeconds =
                                        getDistanceFromStartGraphInSeconds(it.startTime),
                                    nonNormalisedHeight = it.height,
                                )
                            }
                            onsetComeups.ifEmpty {
                                val onsets = weightedLinesForPointIngestions.mapNotNull {
                                    roaDuration?.toOnsetTimeline(
                                        ingestionTimeRelativeToStartInSeconds =
                                            getDistanceFromStartGraphInSeconds(it.startTime)
                                    )
                                }
                                onsets.ifEmpty {
                                    weightedLinesForPointIngestions.map {
                                        NoTimeline(
                                            ingestionTimeRelativeToStartInSeconds =
                                                getDistanceFromStartGraphInSeconds(it.startTime)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun normaliseHeight(overallMaxHeight: Float) {
        this.referenceHeight = overallMaxHeight
        val finalNonNormalisedMaxHeight: Float = if (areSubstanceHeightsIndependent) {
            nonNormalisedMaxOfRoute
        } else {
            overallMaxHeight
        }
        curveDrawable?.referenceHeight = finalNonNormalisedMaxHeight
        timelineDrawables.forEach { it.referenceHeight = finalNonNormalisedMaxHeight }
    }

    private fun getDistanceFromStartGraphInSeconds(time: Instant): Float {
        return Duration.between(startTimeGraph, time).seconds.toFloat()
    }

    override fun drawTimeLine(
        drawScope: DrawScope,
        canvasHeight: Float,
        pixelsPerSec: Float,
        color: Color,
        density: Density
    ) {
        curveDrawable?.drawTimeLine(
            drawScope = drawScope,
            canvasHeight = canvasHeight,
            pixelsPerSec = pixelsPerSec,
            color = color,
            density = density
        )
        for (drawable in timelineDrawables) {
            drawable.drawTimeLine(
                drawScope = drawScope,
                canvasHeight = canvasHeight,
                pixelsPerSec = pixelsPerSec,
                color = color,
                density = density
            )
        }
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
            val maxWidthOfPointIngestions = timelineDrawables.maxOfOrNull {
                it.endOfLineRelativeToStartInSeconds
            } ?: 0f
            val maxWidthOfCurve = curveDrawable?.endOfLineRelativeToStartInSeconds ?: 0f
            return max(max(maxWidthOfTimeRangeIngestions, maxWidthOfPointIngestions), maxWidthOfCurve)
        }
}
