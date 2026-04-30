/*
 * Copyright (c) 2024. Isaak Hanimann.
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

package foo.pilz.freaklog.ui.tabs.stats.tolerance

import androidx.compose.ui.graphics.Color
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

object ToleranceChartCalculator {

    fun getToleranceWindows(
        substanceAndDays: List<SubstanceAndDay>,
        substanceCompanions: List<SubstanceCompanion>,
        substanceRepository: SubstanceRepository
    ): List<ToleranceWindow> {
        val cleanedSubstanceAndDays = removeMultipleSubstancesInADay(substanceAndDays)
        val substanceIntervals = getSubstanceIntervals(cleanedSubstanceAndDays, substanceRepository)
        val substanceIntervalsGroupedBySubstance = getSubstanceIntervalsGroupedBySubstance(substanceIntervals)
        val mergedSubstanceIntervals = getMergedSubstanceIntervals(substanceIntervalsGroupedBySubstance)
        return getToleranceWindows(mergedSubstanceIntervals, substanceCompanions)
    }

    private fun removeMultipleSubstancesInADay(substanceAndDays: List<SubstanceAndDay>): List<SubstanceAndDay> {
        return substanceAndDays
            .groupBy { it.day.atZone(ZoneId.systemDefault()).toLocalDate() }
            .flatMap { (_, daySubstances) ->
                daySubstances
                    .groupBy { it.substanceName }
                    .map { (_, sameSubstanceList) ->
                        sameSubstanceList.minByOrNull { it.day }!!
                    }
            }
    }

    private data class SubstanceInterval(
        val substanceName: String,
        val start: Instant,
        val end: Instant,
        val toleranceType: ToleranceType
    )

    private fun getSubstanceIntervals(
        substanceAndDayPairs: List<SubstanceAndDay>,
        substanceRepository: SubstanceRepository
    ): List<SubstanceInterval> {
        return substanceAndDayPairs.flatMap { pair ->
            val tolerance = substanceRepository.getSubstance(pair.substanceName)?.tolerance
            val result = mutableListOf<SubstanceInterval>()
            var startOfHalfTolerance = pair.day

            // Parse tolerance strings to get hours
            val halfToleranceHours = parseToleranceToHours(tolerance?.half)
            val zeroToleranceHours = parseToleranceToHours(tolerance?.zero)

            if (halfToleranceHours != null && halfToleranceHours > 24) {
                startOfHalfTolerance = pair.day.plus(Duration.ofHours(halfToleranceHours.toLong()))
                result.add(
                    SubstanceInterval(
                        substanceName = pair.substanceName,
                        start = pair.day,
                        end = startOfHalfTolerance,
                        toleranceType = ToleranceType.FULL
                    )
                )
            }

            if (zeroToleranceHours != null && zeroToleranceHours > 24) {
                val startOfZeroTolerance = pair.day.plus(Duration.ofHours(zeroToleranceHours.toLong()))
                result.add(
                    SubstanceInterval(
                        substanceName = pair.substanceName,
                        start = startOfHalfTolerance,
                        end = startOfZeroTolerance,
                        toleranceType = ToleranceType.HALF
                    )
                )
            }

            result
        }
    }

    private fun parseToleranceToHours(toleranceString: String?): Double? {
        if (toleranceString == null) return null
        
        // Try to parse patterns like "1-2 weeks", "3-4 days", "2 weeks", etc.
        val weeksMatch = Regex("(\\d+)(?:-(\\d+))?\\s*weeks?", RegexOption.IGNORE_CASE).find(toleranceString)
        if (weeksMatch != null) {
            val min = weeksMatch.groupValues[1].toDoubleOrNull() ?: return null
            val max = weeksMatch.groupValues[2].toDoubleOrNull() ?: min
            return ((min + max) / 2) * 7 * 24
        }

        val daysMatch = Regex("(\\d+)(?:-(\\d+))?\\s*days?", RegexOption.IGNORE_CASE).find(toleranceString)
        if (daysMatch != null) {
            val min = daysMatch.groupValues[1].toDoubleOrNull() ?: return null
            val max = daysMatch.groupValues[2].toDoubleOrNull() ?: min
            return ((min + max) / 2) * 24
        }

        return null
    }

    private fun getSubstanceIntervalsGroupedBySubstance(
        substanceIntervals: List<SubstanceInterval>
    ): Map<String, List<SubstanceInterval>> {
        return substanceIntervals.groupBy { it.substanceName }
    }

    private fun getMergedSubstanceIntervals(
        intervalsGroupedBySubstance: Map<String, List<SubstanceInterval>>
    ): List<SubstanceInterval> {
        return intervalsGroupedBySubstance.flatMap { (substanceName, intervals) ->
            val fullToleranceIntervals = intervals.filter { it.toleranceType == ToleranceType.FULL }
                .map { it.start to it.end }
            val halfToleranceIntervals = intervals.filter { it.toleranceType == ToleranceType.HALF }
                .map { it.start to it.end }

            val mergedFullIntervals = mergeIntervals(fullToleranceIntervals)
            val halfWithoutFullIntervals = subtractIntervals(halfToleranceIntervals, mergedFullIntervals)
            val mergedHalfIntervals = mergeIntervals(halfWithoutFullIntervals)

            val resultFull = mergedFullIntervals.map { (start, end) ->
                SubstanceInterval(
                    substanceName = substanceName,
                    start = start,
                    end = end,
                    toleranceType = ToleranceType.FULL
                )
            }

            val resultHalf = mergedHalfIntervals.map { (start, end) ->
                SubstanceInterval(
                    substanceName = substanceName,
                    start = start,
                    end = end,
                    toleranceType = ToleranceType.HALF
                )
            }

            resultFull + resultHalf
        }
    }

    private fun mergeIntervals(intervals: List<Pair<Instant, Instant>>): List<Pair<Instant, Instant>> {
        if (intervals.isEmpty()) return emptyList()

        val sorted = intervals.sortedBy { it.first }
        val result = mutableListOf<Pair<Instant, Instant>>()
        var current = sorted.first()

        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.first <= current.second) {
                // Overlapping, extend current
                current = current.first to maxOf(current.second, next.second)
            } else {
                result.add(current)
                current = next
            }
        }
        result.add(current)

        return result
    }

    private fun subtractIntervals(
        intervals: List<Pair<Instant, Instant>>,
        subtracting: List<Pair<Instant, Instant>>
    ): List<Pair<Instant, Instant>> {
        var result = intervals.toMutableList()

        for (sub in subtracting) {
            result = result.flatMap { interval ->
                val (start, end) = interval
                val (subStart, subEnd) = sub

                // No overlap
                if (end <= subStart || start >= subEnd) {
                    listOf(interval)
                } else {
                    val parts = mutableListOf<Pair<Instant, Instant>>()
                    if (start < subStart) {
                        parts.add(start to subStart)
                    }
                    if (end > subEnd) {
                        parts.add(subEnd to end)
                    }
                    parts
                }
            }.toMutableList()
        }

        return result
    }

    private fun getToleranceWindows(
        substanceIntervals: List<SubstanceInterval>,
        substanceCompanions: List<SubstanceCompanion>
    ): List<ToleranceWindow> {
        return substanceIntervals.map { interval ->
            val companion = substanceCompanions.find { it.substanceName == interval.substanceName }
            val color = companion?.color?.getComposeColor(false) ?: Color.Red

            ToleranceWindow(
                substanceName = interval.substanceName,
                start = interval.start,
                end = interval.end,
                toleranceType = interval.toleranceType,
                substanceColor = color
            )
        }
    }
}
