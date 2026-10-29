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
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineGraphGeometryTest {

    private val graphLeft = 24f
    private val graphWidth = 1152f

    @Test
    fun `canRender accepts positive finite span and area`() {
        assertTrue(TimelineGraphGeometry.canRender(widthInSeconds = 3600f, graphWidth = graphWidth, graphHeight = 248f))
    }

    @Test
    fun `canRender rejects non-positive or non-finite time span`() {
        for (span in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertFalse("span $span", TimelineGraphGeometry.canRender(span, graphWidth, 248f))
        }
    }

    @Test
    fun `canRender rejects non-positive or non-finite graph dimensions`() {
        for (dim in listOf(0f, -10f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertFalse("width $dim", TimelineGraphGeometry.canRender(3600f, dim, 248f))
            assertFalse("height $dim", TimelineGraphGeometry.canRender(3600f, graphWidth, dim))
        }
    }

    @Test
    fun `secToPixel maps the window linearly onto the graph`() {
        assertEquals(graphLeft, TimelineGraphGeometry.secToPixel(0f, 3600f, graphLeft, graphWidth), 0.001f)
        assertEquals(
            graphLeft + graphWidth / 2f,
            TimelineGraphGeometry.secToPixel(1800f, 3600f, graphLeft, graphWidth),
            0.001f,
        )
        assertEquals(
            graphLeft + graphWidth,
            TimelineGraphGeometry.secToPixel(3600f, 3600f, graphLeft, graphWidth),
            0.001f,
        )
    }

    @Test
    fun `secToPixel falls back to graphLeft for degenerate time span`() {
        for (span in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val x = TimelineGraphGeometry.secToPixel(100f, span, graphLeft, graphWidth)
            assertEquals("span $span", graphLeft, x, 0f)
        }
    }

    @Test
    fun `model width is always renderable even for an empty window`() {
        val now = java.time.Instant.parse("2024-06-15T12:00:00Z")
        val model = WidgetTimelineModel(windowStart = now, windowEnd = now, now = now, groups = emptyList())
        assertTrue(TimelineGraphGeometry.canRender(model.widthInSeconds, graphWidth, 248f))
        assertTrue(TimelineGraphGeometry.secToPixel(0f, model.widthInSeconds, graphLeft, graphWidth).isFinite())
    }
}
