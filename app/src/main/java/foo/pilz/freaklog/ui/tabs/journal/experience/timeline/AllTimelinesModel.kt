/*
 * Copyright (c) 2022-2023. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.ui.tabs.journal.experience.timeline

import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.substances.classes.roa.curve.IngestionCurve
import foo.pilz.freaklog.ui.graph.scene.builders.CurveSegment
import foo.pilz.freaklog.ui.graph.scene.builders.NormalizedTimeRange
import foo.pilz.freaklog.ui.graph.scene.builders.RawIngestion
import foo.pilz.freaklog.ui.graph.scene.builders.RawPoint
import foo.pilz.freaklog.ui.graph.scene.builders.RawTimelineCurve
import foo.pilz.freaklog.ui.graph.scene.builders.TimelineGroup
import foo.pilz.freaklog.ui.graph.scene.builders.buildRawCurve
import foo.pilz.freaklog.ui.graph.scene.builders.buildRawTimeRanges
import foo.pilz.freaklog.ui.graph.scene.builders.normalizeCurve
import foo.pilz.freaklog.ui.graph.scene.builders.selectTimelineShape
import foo.pilz.freaklog.ui.tabs.journal.experience.components.DataForOneEffectLine
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.curve.toIngestionCurve
import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class AllTimelinesModel(
    dataForLines: List<DataForOneEffectLine>,
    val dataForRatings: List<DataForOneRating>,
    val timedNotes: List<DataForOneTimedNote>,
    areSubstanceHeightsIndependent: Boolean,
    fixedTimeRange: ClosedRange<Instant>? = null,
    useBatemanCurve: Boolean = false,
) {
    val startTime: Instant
    val widthInSeconds: Float
    private val colors: List<AdaptiveColor>
    private val colorlessGroups: List<TimelineGroup>

    fun timelineGroups(isDarkTheme: Boolean): List<TimelineGroup> =
        colorlessGroups.mapIndexed { index, group -> group.copy(color = colors[index].getComposeColor(isDarkTheme)) }

    private data class RoaGroup(
        val color: AdaptiveColor,
        val ingestions: List<RawIngestion>,
        val curve: RawTimelineCurve,
    )

    init {
        val ratingTimes = dataForRatings.map { it.time }
        val ingestionTimes = dataForLines.map { it.startTime }
        val noteTimes = timedNotes.map { it.time }
        startTime = fixedTimeRange?.start
            ?: ((ratingTimes + ingestionTimes + noteTimes).minOrNull() ?: Instant.now())

        val roaGroups = dataForLines.groupBy { it.substanceName }
            .flatMap { substanceGroup ->
                substanceGroup.value.groupBy { it.route }.map { routeGroup ->
                    val linesPerRoute = routeGroup.value
                    val ingestions = linesPerRoute.map { line ->
                        RawIngestion(
                            startSeconds = Duration.between(startTime, line.startTime).seconds.toFloat(),
                            endSeconds = line.endTime?.let { Duration.between(startTime, it).seconds.toFloat() },
                            horizontalWeight = line.horizontalWeight,
                            height = line.height,
                        )
                    }
                    val roaDuration = linesPerRoute.first().roaDuration
                    val curve = if (useBatemanCurve) {
                        batemanRawCurve(
                            linesPerRoute.mapNotNull { line ->
                                roaDuration?.toIngestionCurve(
                                    WeightedLine(line.startTime, line.endTime, line.horizontalWeight, line.height),
                                    startTime,
                                )
                            }
                        )
                    } else {
                        buildRawCurve(selectTimelineShape(roaDuration), ingestions)
                    }
                    RoaGroup(
                        color = linesPerRoute.first().color,
                        ingestions = ingestions,
                        curve = curve,
                    )
                }
            }

        val overallMaxHeight = roaGroups.maxOfOrNull { it.curve.nonNormalisedHeight } ?: 1f
        val rangesByGroup = roaGroups.map { buildRawTimeRanges(it.ingestions) }

        widthInSeconds = if (fixedTimeRange != null) {
            Duration.between(fixedTimeRange.start, fixedTimeRange.endInclusive).seconds.toFloat()
        } else {
            val maxWidthOfGroups = roaGroups.mapIndexed { index, group ->
                val curveEnd = group.curve.endSeconds
                val rangeEnd = rangesByGroup[index].maxOfOrNull { it.endSeconds } ?: 0f
                max(curveEnd, rangeEnd)
            }.maxOrNull() ?: 0f

            val maxWidthRating = ratingTimes.maxOrNull()
                ?.let { Duration.between(startTime, it).seconds.toFloat() } ?: 0f
            val maxWidthNote = noteTimes.maxOrNull()
                ?.let { Duration.between(startTime, it).seconds.toFloat() } ?: 0f

            listOf(
                maxWidthOfGroups,
                maxWidthRating,
                maxWidthNote,
                2.hours.inWholeSeconds.toFloat(),
            ).max() + 10.minutes.inWholeSeconds.toFloat()
        }

        colors = roaGroups.map { it.color }
        colorlessGroups = roaGroups.mapIndexed { index, group ->
            val referenceHeight =
                if (areSubstanceHeightsIndependent) group.curve.nonNormalisedHeight else overallMaxHeight
            TimelineGroup(
                color = null,
                curve = normalizeCurve(group.curve, referenceHeight, widthInSeconds),
                timeRanges = rangesByGroup[index].map {
                    NormalizedTimeRange(
                        startFraction = it.startSeconds / widthInSeconds,
                        endFraction = it.endSeconds / widthInSeconds,
                        intersectionCount = it.intersectionCount,
                    )
                },
            )
        }
    }
}

private const val BATEMAN_SAMPLES = 200

/** Samples the summed Bateman curves of one substance and route so the scene painter can draw them. */
private fun batemanRawCurve(curves: List<IngestionCurve>): RawTimelineCurve {
    if (curves.isEmpty()) return RawTimelineCurve(emptyList(), emptyList(), 0f, 0f)
    val start = curves.minOf { it.ingestionStartSec }
    val end = curves.maxOf { it.curveEndSec }
    val step = max(1f, end - start) / BATEMAN_SAMPLES
    val points = (0..BATEMAN_SAMPLES).map { index ->
        val seconds = start + index * step
        RawPoint(seconds = seconds, height = curves.sumOf { it.valueAt(seconds).toDouble() }.toFloat())
    }
    return RawTimelineCurve(
        segments = listOf(CurveSegment(points, dotted = !curves.all { it.isCertain })),
        ingestionDots = curves.map { RawPoint(it.ingestionStartSec, 0f, isIngestionDot = true) },
        nonNormalisedHeight = points.maxOf { it.height },
        endSeconds = end,
    )
}
