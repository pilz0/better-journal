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

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import kotlin.math.sqrt

/**
 * Pure-Kotlin model for the year-long activity heatmap shown in the heatmap widget.
 *
 * Lays out 53 weekly columns × 7 daily rows (Sun–Sat).  Each cell is assigned
 * an intensity bucket between 0 and [INTENSITY_BUCKETS] using a perceptual
 * (sqrt) scale so a single big day no longer washes out the rest of the year.
 */
data class WidgetHeatmapModel(
    /** First date in column 0. Always a Sunday. */
    val startDate: LocalDate,
    /** Last date in the grid (inclusive). Always a Saturday. */
    val endDate: LocalDate,
    /** Number of weekly columns laid out. */
    val weeks: Int,
    /** Cells, one per visible day. */
    val cells: List<Cell>,
    /** Month tick labels positioned along the top of the heatmap. */
    val monthLabels: List<MonthLabel>,
    /** Total ingestion count over the visible window. */
    val totalIngestions: Int,
) {
    data class Cell(
        val date: LocalDate,
        val column: Int,        // 0..weeks-1
        val row: Int,           // 0 = Sunday .. 6 = Saturday
        val count: Int,
        /** 0 = empty, 1..[INTENSITY_BUCKETS] otherwise. */
        val intensity: Int,
    )

    data class MonthLabel(
        val month: java.time.Month,
        /** Column index in the grid where this month's first Sunday-aligned week starts. */
        val column: Int,
    )

    companion object {
        const val INTENSITY_BUCKETS = 4

        /**
         * Builds a heatmap whose right edge is the Saturday of the week
         * containing [today], spanning [weeks] columns to the left.
         *
         * @param ingestionDates one entry per ingestion (duplicates allowed; counted per day).
         */
        fun build(
            today: LocalDate,
            ingestionDates: List<LocalDate>,
            weeks: Int = 53,
        ): WidgetHeatmapModel {
            require(weeks >= 1)
            val endDate = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            // Start on the Sunday so the first column is fully Sun..Sat aligned.
            val startDate = endDate.minusDays((weeks * 7L) - 1L)

            // Count ingestions per date over the whole visible range.
            val counts = mutableMapOf<LocalDate, Int>()
            for (d in ingestionDates) {
                if (!d.isBefore(startDate) && !d.isAfter(endDate)) {
                    counts[d] = (counts[d] ?: 0) + 1
                }
            }

            val maxCount = counts.values.maxOrNull() ?: 0

            val cells = ArrayList<Cell>(weeks * 7)
            var date = startDate
            var column = 0
            // Iterate column-major so each column groups Sun..Sat.
            while (column < weeks) {
                for (row in 0..6) {
                    val count = counts[date] ?: 0
                    cells.add(
                        Cell(
                            date = date,
                            column = column,
                            row = row,
                            count = count,
                            intensity = bucketFor(count, maxCount),
                        )
                    )
                    date = date.plusDays(1)
                }
                column++
            }

            // Month labels: first column whose Sunday falls in each month.
            val monthLabels = mutableListOf<MonthLabel>()
            var lastMonth: YearMonth? = null
            for (col in 0 until weeks) {
                val sunday = startDate.plusDays(col * 7L)
                val ym = YearMonth.from(sunday)
                if (ym != lastMonth) {
                    monthLabels.add(MonthLabel(month = ym.month, column = col))
                    lastMonth = ym
                }
            }

            return WidgetHeatmapModel(
                startDate = startDate,
                endDate = endDate,
                weeks = weeks,
                cells = cells,
                monthLabels = monthLabels,
                totalIngestions = counts.values.sum(),
            )
        }

        /**
         * Maps a per-day count to a bucket in `0..INTENSITY_BUCKETS` using a
         * perceptual sqrt scale so a single high-count day doesn't flatten
         * everything else to bucket 1.
         */
        internal fun bucketFor(count: Int, maxCount: Int): Int {
            if (count <= 0 || maxCount <= 0) return 0
            // sqrt-scaled ratio in [0..1].
            val ratio = sqrt(count.toDouble()) / sqrt(maxCount.toDouble())
            // Map to 1..INTENSITY_BUCKETS.
            val bucket = (ratio * INTENSITY_BUCKETS).toInt() + 1
            return bucket.coerceIn(1, INTENSITY_BUCKETS)
        }
    }
}
