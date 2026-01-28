/*
 * Copyright (c) 2022-2024. Isaak Hanimann.
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

package foo.pilz.freaklog.ui.tabs.journal.experience.timeline.drawables

import foo.pilz.freaklog.ui.utils.getInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.temporal.ChronoUnit

class AxisDrawableTest {

    // ===== getInstantsBetween Tests =====

    @Test
    fun testGetInstantsBetween_6hours_step1() {
        val startTime = getInstant(2022, 6, 5, 14, 20)!!
        val endTime = getInstant(2022, 6, 5, 20, 20)!!
        val fullHours = AxisDrawable.getInstantsBetween(
            startTime = startTime,
            endTime = endTime,
            stepSizeInHours = 1
        )
        assertEquals(6, fullHours.size)
    }

    @Test
    fun testGetInstantsBetween_6hours_step2() {
        val startTime = getInstant(2022, 6, 5, 14, 0)!!
        val endTime = getInstant(2022, 6, 5, 20, 0)!!
        val fullHours = AxisDrawable.getInstantsBetween(
            startTime = startTime,
            endTime = endTime,
            stepSizeInHours = 2
        )
        assertEquals(3, fullHours.size)
    }

    @Test
    fun testGetInstantsBetween_12hours_step3() {
        val startTime = getInstant(2022, 6, 5, 12, 0)!!
        val endTime = startTime.plus(12, ChronoUnit.HOURS)
        val fullHours = AxisDrawable.getInstantsBetween(
            startTime = startTime,
            endTime = endTime,
            stepSizeInHours = 3
        )
        assertEquals(4, fullHours.size)
    }

    @Test
    fun testGetInstantsBetween_24hours_step6() {
        val startTime = getInstant(2022, 6, 5, 0, 0)!!
        val endTime = startTime.plus(24, ChronoUnit.HOURS)
        val fullHours = AxisDrawable.getInstantsBetween(
            startTime = startTime,
            endTime = endTime,
            stepSizeInHours = 6
        )
        assertEquals(4, fullHours.size)
    }

    @Test
    fun testGetInstantsBetween_emptyRange() {
        val startTime = getInstant(2022, 6, 5, 14, 30)!!
        val endTime = getInstant(2022, 6, 5, 14, 45)!!
        val fullHours = AxisDrawable.getInstantsBetween(
            startTime = startTime,
            endTime = endTime,
            stepSizeInHours = 1
        )
        // First full hour after 14:30 is 15:00, which is after 14:45
        assertEquals(0, fullHours.size)
    }

    @Test
    fun testGetInstantsBetween_startsAtFullHour() {
        val startTime = getInstant(2022, 6, 5, 14, 0)!!
        val endTime = getInstant(2022, 6, 5, 17, 0)!!
        val fullHours = AxisDrawable.getInstantsBetween(
            startTime = startTime,
            endTime = endTime,
            stepSizeInHours = 1
        )
        // 15:00, 16:00 (17:00 is not before endTime)
        assertEquals(2, fullHours.size)
    }

    @Test
    fun testGetInstantsBetween_crossesMidnight() {
        val startTime = getInstant(2022, 6, 5, 22, 0)!!
        val endTime = getInstant(2022, 6, 6, 4, 0)!!
        val fullHours = AxisDrawable.getInstantsBetween(
            startTime = startTime,
            endTime = endTime,
            stepSizeInHours = 1
        )
        // 23:00, 00:00, 01:00, 02:00, 03:00 = 5 hours
        assertEquals(5, fullHours.size)
    }

    @Test
    fun testGetInstantsBetween_resultIsOrdered() {
        val startTime = getInstant(2022, 6, 5, 10, 30)!!
        val endTime = getInstant(2022, 6, 5, 18, 30)!!
        val fullHours = AxisDrawable.getInstantsBetween(
            startTime = startTime,
            endTime = endTime,
            stepSizeInHours = 1
        )
        for (i in 0 until fullHours.size - 1) {
            assertTrue("Hours should be in chronological order",
                fullHours[i].isBefore(fullHours[i + 1]))
        }
    }

    @Test
    fun testGetInstantsBetween_allHoursAreAfterStart() {
        val startTime = getInstant(2022, 6, 5, 10, 30)!!
        val endTime = getInstant(2022, 6, 5, 18, 30)!!
        val fullHours = AxisDrawable.getInstantsBetween(
            startTime = startTime,
            endTime = endTime,
            stepSizeInHours = 1
        )
        fullHours.forEach { hour ->
            assertTrue("All hours should be after start time", hour.isAfter(startTime))
        }
    }

    @Test
    fun testGetInstantsBetween_allHoursAreBeforeEnd() {
        val startTime = getInstant(2022, 6, 5, 10, 30)!!
        val endTime = getInstant(2022, 6, 5, 18, 30)!!
        val fullHours = AxisDrawable.getInstantsBetween(
            startTime = startTime,
            endTime = endTime,
            stepSizeInHours = 1
        )
        fullHours.forEach { hour ->
            assertTrue("All hours should be before end time", hour.isBefore(endTime))
        }
    }

    // ===== nearestFullHourInTheFuture Tests =====

    @Test
    fun testNearestFullHour_midHour() {
        val time = getInstant(2022, 6, 5, 14, 30)!!
        val result = time.nearestFullHourInTheFuture()
        assertNotNull(result)
        // Should be 15:00
        assertEquals(15, result.atZone(java.time.ZoneId.systemDefault()).hour)
        assertEquals(0, result.atZone(java.time.ZoneId.systemDefault()).minute)
    }

    @Test
    fun testNearestFullHour_justBeforeHour() {
        val time = getInstant(2022, 6, 5, 14, 59)!!
        val result = time.nearestFullHourInTheFuture()
        // Should be 15:00
        assertEquals(15, result.atZone(java.time.ZoneId.systemDefault()).hour)
        assertEquals(0, result.atZone(java.time.ZoneId.systemDefault()).minute)
    }

    @Test
    fun testNearestFullHour_atExactHour() {
        val time = getInstant(2022, 6, 5, 14, 0)!!
        val result = time.nearestFullHourInTheFuture()
        // Should be 15:00 (one hour later)
        assertEquals(15, result.atZone(java.time.ZoneId.systemDefault()).hour)
        assertEquals(0, result.atZone(java.time.ZoneId.systemDefault()).minute)
    }

    @Test
    fun testNearestFullHour_justAfterMidnight() {
        val time = getInstant(2022, 6, 5, 0, 1)!!
        val result = time.nearestFullHourInTheFuture()
        // Should be 01:00
        assertEquals(1, result.atZone(java.time.ZoneId.systemDefault()).hour)
        assertEquals(0, result.atZone(java.time.ZoneId.systemDefault()).minute)
    }

    @Test
    fun testNearestFullHour_beforeMidnight() {
        val time = getInstant(2022, 6, 5, 23, 30)!!
        val result = time.nearestFullHourInTheFuture()
        // Should be 00:00 next day
        assertEquals(0, result.atZone(java.time.ZoneId.systemDefault()).hour)
        assertEquals(0, result.atZone(java.time.ZoneId.systemDefault()).minute)
        // Should be June 6th
        assertEquals(6, result.atZone(java.time.ZoneId.systemDefault()).dayOfMonth)
    }

    // ===== AxisDrawable Instance Tests =====

    @Test
    fun testAxisDrawable_creation() {
        val startTime = getInstant(2022, 6, 5, 14, 0)!!
        val drawable = AxisDrawable(startTime, widthInSeconds = 21600f) // 6 hours
        assertNotNull(drawable)
        assertEquals(startTime, drawable.startTime)
        assertEquals(21600f, drawable.widthInSeconds, 0.01f)
    }

    // ===== FullHour Tests =====

    @Test
    fun testFullHour_creation() {
        val fullHour = FullHour(distanceFromStart = 100f, label = "14")
        assertEquals(100f, fullHour.distanceFromStart, 0.01f)
        assertEquals("14", fullHour.label)
    }
}
