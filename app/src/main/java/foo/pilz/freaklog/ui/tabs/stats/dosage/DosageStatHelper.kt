/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog (a fork of PsychonautWiki Journal).
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.ui.tabs.stats.dosage

import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.ui.tabs.stats.substancecompanion.DosageBucket
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Time-range picker for [DosageStatScreen].
 *
 * Each option defines how many buckets are produced and the bucket [step] (e.g. 30 daily
 * buckets for `DAYS_30`).
 */
enum class DosageStatRange(
    val displayText: String,
    val title: String,
    val period: Period,
    val bucketCount: Int,
    val step: Period,
    val labelPattern: String,
) {
    DAYS_30("30D", "Last 30 days", Period.ofDays(30), 30, Period.ofDays(1), "dd"),
    WEEKS_26("26W", "Last 26 weeks", Period.ofWeeks(26), 26, Period.ofWeeks(1), "dd.MM"),
    MONTHS_12("12M", "Last 12 months", Period.ofMonths(12), 12, Period.ofMonths(1), "MMM"),
    YEARS("Y", "Last 12 years", Period.ofYears(12), 12, Period.ofYears(1), "yyyy"),
}

/**
 * Pure helpers for computing the bucketed totals shown by [DosageStatScreen].
 *
 * Kept Android-free so the tricky bits (estimation, unit-mismatch detection, time
 * bucketing) can be unit-tested on the JVM.
 */
object DosageStatHelper {

    /**
     * Result of [computeBuckets]: the per-bucket totals plus diagnostic counts the UI uses
     * for warnings and the "Estimate unknown doses as" hint.
     */
    data class DosageStatResult(
        val buckets: List<DosageBucket>,
        val unknownDoseCount: Int,
        val unitsUsed: Set<String>,
    )

    /**
     * Bucket [ingestions] for the given [range], summing doses (with [estimatedUnknownDose]
     * substituted for any ingestion whose `dose` is null when [estimatedUnknownDose] is
     * non-null).
     *
     * The resulting [DosageBucket] list is in chronological order (oldest → newest), so it
     * can be handed straight to the existing `DosageBarChart` composable.
     */
    fun computeBuckets(
        ingestions: List<Ingestion>,
        range: DosageStatRange,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        estimatedUnknownDose: Double? = null,
    ): DosageStatResult {
        // Diagnostics ("Estimate unknown doses" hint and mixed-units warning) only describe
        // the visible time window — otherwise the warnings can fire for ancient ingestions
        // that aren't in the current chart at all.
        val windowStart = now.atZone(zone).minus(range.period).toInstant()
        val ingestionsInWindow = ingestions.filter { it.time >= windowStart && it.time < now }
        val unitsUsed = ingestionsInWindow
            .mapNotNull { it.units?.takeIf { u -> u.isNotBlank() } }
            .toSet()
        val unknownCount = ingestionsInWindow.count { it.dose == null }

        val buckets = ArrayDeque<DosageBucket>()
        var bucketEnd = now.atZone(zone)
        repeat(range.bucketCount) {
            val bucketStart = bucketEnd.minus(range.step)
            val startInstant = bucketStart.toInstant()
            val endInstant = bucketEnd.toInstant()
            val inBucket = ingestions.filter { it.time >= startInstant && it.time < endInstant }
            val totalDose = inBucket.sumOf { ingestion ->
                ingestion.dose ?: (estimatedUnknownDose ?: 0.0)
            }
            val label = DateTimeFormatter.ofPattern(range.labelPattern).format(bucketStart)
            val fullDate = DateTimeFormatter.ofPattern("dd MMM yyyy").format(bucketStart)
            val unit = inBucket.firstOrNull { !it.units.isNullOrBlank() }?.units
                ?: ingestions.firstOrNull { !it.units.isNullOrBlank() }?.units
                ?: ""
            buckets.addFirst(DosageBucket(label, fullDate, totalDose, inBucket.size, unit))
            bucketEnd = bucketStart
        }
        return DosageStatResult(
            buckets = buckets.toList(),
            unknownDoseCount = unknownCount,
            unitsUsed = unitsUsed,
        )
    }
}
