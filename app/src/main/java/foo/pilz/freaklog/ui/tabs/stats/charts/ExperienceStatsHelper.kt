/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog (a fork of PsychonautWiki Journal).
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.ui.tabs.stats.charts

import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import foo.pilz.freaklog.ui.tabs.stats.ColorCount
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Shared, pure helpers used by the new Daily / Monthly / Yearly charts and the fractional
 * substance-counting screen.
 *
 * These functions do not depend on Android (only `java.time` and the project's plain
 * data classes) so they can be unit-tested on the JVM.
 */
object ExperienceStatsHelper {

    private fun colorFor(ingestion: IngestionWithCompanionAndCustomUnit): AdaptiveColor =
        ingestion.substanceCompanion?.color ?: AdaptiveColor.AUBURN

    /**
     * Daily ingestion counts for the [days] most recent days, ordered oldest → newest.
     *
     * Each bucket is a list of [ColorCount] items so the existing BarChart composable
     * can render multi-substance stacks per day.
     */
    fun dailyBuckets(
        ingestions: List<IngestionWithCompanionAndCustomUnit>,
        days: Int,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<List<ColorCount>> {
        require(days > 0) { "days must be > 0" }
        val today = LocalDate.ofInstant(now, zone)
        val startDay = today.minusDays((days - 1).toLong())
        val byDay: Map<LocalDate, List<IngestionWithCompanionAndCustomUnit>> = ingestions.groupBy {
            LocalDate.ofInstant(it.ingestion.time, zone)
        }
        return (0 until days).map { offset ->
            val day = startDay.plusDays(offset.toLong())
            countsByColor(byDay[day].orEmpty())
        }
    }

    /**
     * Monthly ingestion counts for the [months] most recent calendar months.
     */
    fun monthlyBuckets(
        ingestions: List<IngestionWithCompanionAndCustomUnit>,
        months: Int,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<List<ColorCount>> {
        require(months > 0) { "months must be > 0" }
        val current = YearMonth.from(LocalDate.ofInstant(now, zone))
        val startMonth = current.minusMonths((months - 1).toLong())
        val byMonth: Map<YearMonth, List<IngestionWithCompanionAndCustomUnit>> = ingestions.groupBy {
            YearMonth.from(LocalDate.ofInstant(it.ingestion.time, zone))
        }
        return (0 until months).map { offset ->
            val ym = startMonth.plusMonths(offset.toLong())
            countsByColor(byMonth[ym].orEmpty())
        }
    }

    /**
     * Yearly ingestion counts for every year between the earliest ingestion and `now`.
     *
     * If [ingestions] is empty an empty list is returned.
     */
    fun yearlyBuckets(
        ingestions: List<IngestionWithCompanionAndCustomUnit>,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<List<ColorCount>> {
        if (ingestions.isEmpty()) return emptyList()
        val byYear: Map<Int, List<IngestionWithCompanionAndCustomUnit>> = ingestions.groupBy {
            LocalDate.ofInstant(it.ingestion.time, zone).year
        }
        val firstYear = byYear.keys.min()
        val lastYear = LocalDate.ofInstant(now, zone).year
        return (firstYear..lastYear).map { year -> countsByColor(byYear[year].orEmpty()) }
    }

    private fun countsByColor(
        ingestions: List<IngestionWithCompanionAndCustomUnit>,
    ): List<ColorCount> {
        if (ingestions.isEmpty()) return emptyList()
        return ingestions
            .groupBy { colorFor(it) }
            .map { (color, list) -> ColorCount(color = color, count = list.size) }
            .sortedByDescending { it.count }
    }

    /**
     * Fractional substance "credit" within [ingestions], grouped per experience.
     *
     * If an experience contains N **distinct** substances, each substance gets a `1/N`
     * credit. The result is a list of substance → fractional contribution, sorted by
     * descending contribution.
     */
    fun fractionalSubstanceCounts(
        ingestions: List<IngestionWithCompanionAndCustomUnit>,
    ): List<SubstanceFraction> {
        if (ingestions.isEmpty()) return emptyList()
        val byExperience = ingestions.groupBy { it.ingestion.experienceId }
        val totals = HashMap<String, Double>()
        val colors = HashMap<String, AdaptiveColor>()
        for ((_, experienceIngestions) in byExperience) {
            val substancesInExperience = experienceIngestions
                .map { it.ingestion.substanceName }
                .toSet()
            if (substancesInExperience.isEmpty()) continue
            val credit = 1.0 / substancesInExperience.size
            for (substance in substancesInExperience) {
                totals[substance] = (totals[substance] ?: 0.0) + credit
                colors.getOrPut(substance) {
                    experienceIngestions
                        .firstOrNull { it.ingestion.substanceName == substance }
                        ?.let(::colorFor)
                        ?: AdaptiveColor.AUBURN
                }
            }
        }
        return totals.entries
            .map { (name, count) ->
                SubstanceFraction(substanceName = name, fractionalCount = count, color = colors[name] ?: AdaptiveColor.AUBURN)
            }
            .sortedByDescending { it.fractionalCount }
    }

    /**
     * Number of experiences that contain at least one ingestion of [substanceName] within
     * the half-open instant range `[start, end)`.
     */
    fun substanceExperienceCount(
        ingestions: List<IngestionWithCompanionAndCustomUnit>,
        substanceName: String,
        start: Instant?,
        end: Instant,
    ): Int {
        return ingestions
            .asSequence()
            .filter { it.ingestion.substanceName == substanceName }
            .filter { (start == null || it.ingestion.time >= start) && it.ingestion.time < end }
            .map { it.ingestion.experienceId }
            .toSet()
            .size
    }
}

data class SubstanceFraction(
    val substanceName: String,
    val fractionalCount: Double,
    val color: AdaptiveColor,
)
