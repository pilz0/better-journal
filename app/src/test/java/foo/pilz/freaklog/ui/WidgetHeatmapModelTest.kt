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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WidgetHeatmapModelTest {

    private val today: LocalDate = LocalDate.of(2024, 6, 15) // Saturday

    @Test
    fun `grid ends on the Saturday of the current week`() {
        val model = WidgetHeatmapModel.build(today = today, ingestionDates = emptyList())
        assertEquals(DayOfWeek.SATURDAY, model.endDate.dayOfWeek)
    }

    @Test
    fun `grid starts on a Sunday so each column is Sun to Sat`() {
        val model = WidgetHeatmapModel.build(today = today, ingestionDates = emptyList())
        assertEquals(DayOfWeek.SUNDAY, model.startDate.dayOfWeek)
    }

    @Test
    fun `default 53 weeks produces 371 cells`() {
        val model = WidgetHeatmapModel.build(today = today, ingestionDates = emptyList())
        assertEquals(53, model.weeks)
        assertEquals(53 * 7, model.cells.size)
    }

    @Test
    fun `each cell is in column 0 to weeks-1 and row 0 to 6`() {
        val model = WidgetHeatmapModel.build(today = today, ingestionDates = emptyList())
        model.cells.forEach { cell ->
            assertTrue(cell.column in 0 until model.weeks)
            assertTrue(cell.row in 0..6)
            assertEquals(0, cell.count)
            assertEquals(0, cell.intensity)
        }
    }

    @Test
    fun `row order is Sunday=0 through Saturday=6`() {
        val model = WidgetHeatmapModel.build(today = today, ingestionDates = emptyList())
        // The first column starts at the Sunday of startDate.
        val firstColumn = model.cells.filter { it.column == 0 }.sortedBy { it.row }
        assertEquals(DayOfWeek.SUNDAY, firstColumn[0].date.dayOfWeek)
        assertEquals(DayOfWeek.SATURDAY, firstColumn[6].date.dayOfWeek)
    }

    @Test
    fun `ingestion counts are tallied per day`() {
        val day = today.minusDays(5)
        val model = WidgetHeatmapModel.build(
            today = today,
            ingestionDates = listOf(day, day, day, today),
        )
        val cellForDay = model.cells.first { it.date == day }
        val cellForToday = model.cells.first { it.date == today }
        assertEquals(3, cellForDay.count)
        assertEquals(1, cellForToday.count)
        assertTrue(cellForDay.intensity in 1..WidgetHeatmapModel.INTENSITY_BUCKETS)
        assertTrue(cellForToday.intensity in 1..WidgetHeatmapModel.INTENSITY_BUCKETS)
    }

    @Test
    fun `ingestions outside the window are ignored`() {
        val outside = today.minusYears(5)
        val model = WidgetHeatmapModel.build(today = today, ingestionDates = listOf(outside))
        assertEquals(0, model.totalIngestions)
        assertTrue(model.cells.all { it.count == 0 })
    }

    @Test
    fun `bucket scaling is monotonic and never exceeds INTENSITY_BUCKETS`() {
        // Simulate a year with a few high-count days plus many low-count days.
        val maxBucket = WidgetHeatmapModel.INTENSITY_BUCKETS
        for (count in 1..50) {
            val bucket = WidgetHeatmapModel.bucketFor(count = count, maxCount = 50)
            assertTrue("bucket $bucket out of range for count=$count", bucket in 1..maxBucket)
        }
        // Empty input always yields bucket 0.
        assertEquals(0, WidgetHeatmapModel.bucketFor(0, 10))
        assertEquals(0, WidgetHeatmapModel.bucketFor(5, 0))
        // Highest count maps to the top bucket.
        assertEquals(maxBucket, WidgetHeatmapModel.bucketFor(50, 50))
    }

    @Test
    fun `sqrt scaling spreads small counts beyond bucket 1`() {
        // Linear scaling would compress counts 1..5 with maxCount=100 all to the lowest bucket;
        // sqrt scaling should give count 25 a higher bucket than count 1.
        val low = WidgetHeatmapModel.bucketFor(count = 1, maxCount = 100)
        val mid = WidgetHeatmapModel.bucketFor(count = 25, maxCount = 100)
        assertTrue("mid bucket ($mid) should be greater than low ($low)", mid > low)
    }

    @Test
    fun `month labels are produced at column boundaries`() {
        val model = WidgetHeatmapModel.build(today = today, ingestionDates = emptyList())
        assertNotNull(model.monthLabels)
        // We always span ~12 months so we expect at least 10 labels.
        assertTrue(
            "expected many month labels but got ${model.monthLabels.size}",
            model.monthLabels.size in 10..14,
        )
        // First label corresponds to startDate's month.
        assertEquals(model.startDate.month, model.monthLabels.first().month)
    }

    @Test
    fun `consecutive month labels are non-decreasing in column`() {
        val model = WidgetHeatmapModel.build(today = today, ingestionDates = emptyList())
        for (i in 0 until model.monthLabels.size - 1) {
            assertTrue(model.monthLabels[i].column < model.monthLabels[i + 1].column)
        }
    }

    @Test
    fun `today is the last cell of the right-most column when today is Saturday`() {
        val model = WidgetHeatmapModel.build(today = today, ingestionDates = emptyList())
        val lastCol = model.cells.filter { it.column == model.weeks - 1 }.sortedBy { it.row }
        assertEquals(today, lastCol[6].date)
    }

    @Test
    fun `for a non-Saturday today, the right-most Saturday is in the future`() {
        val tuesday = LocalDate.of(2024, 6, 11) // Tuesday
        val model = WidgetHeatmapModel.build(today = tuesday, ingestionDates = emptyList())
        assertEquals(DayOfWeek.SATURDAY, model.endDate.dayOfWeek)
        assertFalse(model.endDate.isBefore(tuesday))
    }
}
