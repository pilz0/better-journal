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

import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.DurationUnits
import foo.pilz.freaklog.data.substances.classes.roa.RoaDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class WidgetTimelineModelTest {

    private val now: Instant = Instant.parse("2024-06-15T12:00:00Z")

    private fun mins(min: Float, max: Float) =
        DurationRange(min = min, max = max, units = DurationUnits.MINUTES)

    private fun hours(min: Float, max: Float) =
        DurationRange(min = min, max = max, units = DurationUnits.HOURS)

    private val mdmaOral = RoaDuration(
        onset = mins(20f, 40f),
        comeup = mins(15f, 30f),
        peak = hours(2f, 3f),
        offset = hours(1f, 2f),
        total = hours(4f, 6f),
        afterglow = null,
    )

    private val totalOnly = RoaDuration(
        onset = null,
        comeup = null,
        peak = null,
        offset = null,
        total = hours(4f, 6f),
        afterglow = null,
    )

    private fun durationsOf(vararg pairs: Pair<String, RoaDuration>):
            Map<String, Map<AdministrationRoute, RoaDuration?>> =
        pairs.associate { (name, roa) ->
            name to mapOf(AdministrationRoute.ORAL to roa)
        }

    @Test
    fun `empty input returns default window with no groups`() {
        val model = WidgetTimelineModel.build(
            now = now,
            ingestions = emptyList(),
            durationsBySubstance = emptyMap(),
        )
        assertTrue(model.groups.isEmpty())
        assertFalse(model.hasContent)
        // Default window: 1h before to 3h after now.
        assertEquals(now.minusSeconds(3600), model.windowStart)
        assertEquals(now.plusSeconds(3 * 3600), model.windowEnd)
    }

    @Test
    fun `expired ingestion is filtered out`() {
        // MDMA taken 12 hours ago — well past its predicted offset (~6h median).
        val expired = WidgetTimelineModel.IngestionInput(
            substanceName = "MDMA",
            route = AdministrationRoute.ORAL,
            time = now.minus(Duration.ofHours(12)),
            color = AdaptiveColor.PINK,
        )
        val model = WidgetTimelineModel.build(
            now = now,
            ingestions = listOf(expired),
            durationsBySubstance = durationsOf("MDMA" to mdmaOral),
        )
        assertTrue("expired ingestion should be filtered", model.groups.isEmpty())
    }

    @Test
    fun `recent ingestion produces a curve with a peak`() {
        val ingestion = WidgetTimelineModel.IngestionInput(
            substanceName = "MDMA",
            route = AdministrationRoute.ORAL,
            time = now.minus(Duration.ofMinutes(30)),
            color = AdaptiveColor.PINK,
        )
        val model = WidgetTimelineModel.build(
            now = now,
            ingestions = listOf(ingestion),
            durationsBySubstance = durationsOf("MDMA" to mdmaOral),
        )
        assertEquals(1, model.groups.size)
        val group = model.groups.first()
        assertTrue("should be drawn solid (full RoA)", group.isComplete)
        assertTrue("must contain at least 2 sample points", group.points.size >= 2)
        // Heights must be normalised to [0..1] with at least one point at 1.
        val maxH = group.points.maxOf { it.height }
        assertEquals(1f, maxH, 0.0001f)
        // Single ingestion dot.
        assertEquals(1, group.ingestionDotsSecondsFromStart.size)
    }

    @Test
    fun `total-only RoA produces a dotted curve`() {
        val ingestion = WidgetTimelineModel.IngestionInput(
            substanceName = "MysterySubstance",
            route = AdministrationRoute.ORAL,
            time = now,
            color = AdaptiveColor.GREEN,
        )
        val model = WidgetTimelineModel.build(
            now = now,
            ingestions = listOf(ingestion),
            durationsBySubstance = durationsOf("MysterySubstance" to totalOnly),
        )
        assertEquals(1, model.groups.size)
        assertFalse(
            "total-only data should render dotted (isComplete=false)",
            model.groups.first().isComplete,
        )
    }

    @Test
    fun `unknown substance falls back to default duration`() {
        val ingestion = WidgetTimelineModel.IngestionInput(
            substanceName = "UnknownStuff",
            route = AdministrationRoute.ORAL,
            time = now,
            color = AdaptiveColor.GREEN,
        )
        val model = WidgetTimelineModel.build(
            now = now,
            ingestions = listOf(ingestion),
            durationsBySubstance = emptyMap(),
        )
        assertEquals(1, model.groups.size)
        assertFalse("unknown substances render as dotted/uncertain", model.groups.first().isComplete)
        assertTrue(model.hasContent)
    }

    @Test
    fun `two doses of the same substance stack heights`() {
        val a = WidgetTimelineModel.IngestionInput(
            substanceName = "MDMA",
            route = AdministrationRoute.ORAL,
            time = now,
            color = AdaptiveColor.PINK,
        )
        val b = WidgetTimelineModel.IngestionInput(
            substanceName = "MDMA",
            route = AdministrationRoute.ORAL,
            time = now.plus(Duration.ofMinutes(45)),
            color = AdaptiveColor.PINK,
        )
        val singleModel = WidgetTimelineModel.build(
            now = now,
            ingestions = listOf(a),
            durationsBySubstance = durationsOf("MDMA" to mdmaOral),
        )
        val doubleModel = WidgetTimelineModel.build(
            now = now,
            ingestions = listOf(a, b),
            durationsBySubstance = durationsOf("MDMA" to mdmaOral),
        )
        // Both groups normalise to a max of 1, so we compare RAW (un-normalised)
        // by looking at the underlying line segments via re-construction:
        val doubleGroup = doubleModel.groups.single()
        // There must be 2 ingestion dots.
        assertEquals(2, doubleGroup.ingestionDotsSecondsFromStart.size)
        // Both models normalise their max height to 1.0. With a single dose the
        // entire peak phase is at the normalised max. With two overlapping doses
        // the un-overlapped portions only reach height 0.5 after normalising
        // against the summed max — so the count of plateau samples (~1.0)
        // *shrinks* with stacking, while the curve gains samples near 0.5.
        // This is what genuinely proves the stacking math worked.
        val singlePeakCount = singleModel.groups.single().points.count { it.height >= 0.99f }
        val doublePeakCount = doubleGroup.points.count { it.height >= 0.99f }
        assertTrue(
            "two-dose plateau should be narrower than single-dose plateau " +
                    "after normalising against the stacked maximum, " +
                    "single=$singlePeakCount, double=$doublePeakCount",
            doublePeakCount < singlePeakCount,
        )
        // Two-dose curve must contain a "single-dose shoulder" at ~0.5 height
        // that the single-dose curve never has on its plateau.
        val doubleHasShoulder = doubleGroup.points.any { it.height in 0.45f..0.55f }
        assertTrue("two-dose curve should expose a half-height shoulder", doubleHasShoulder)
        assertTrue(singlePeakCount >= 1)
    }

    @Test
    fun `two different substances produce two separate groups`() {
        val mdma = WidgetTimelineModel.IngestionInput(
            substanceName = "MDMA",
            route = AdministrationRoute.ORAL,
            time = now,
            color = AdaptiveColor.PINK,
        )
        val caffeine = WidgetTimelineModel.IngestionInput(
            substanceName = "Caffeine",
            route = AdministrationRoute.ORAL,
            time = now,
            color = AdaptiveColor.BROWN,
        )
        val model = WidgetTimelineModel.build(
            now = now,
            ingestions = listOf(mdma, caffeine),
            durationsBySubstance = durationsOf(
                "MDMA" to mdmaOral,
                "Caffeine" to mdmaOral,
            ),
        )
        assertEquals(2, model.groups.size)
        // Distinct colors should be preserved.
        val colors = model.groups.map { it.color }.toSet()
        assertTrue(colors.contains(AdaptiveColor.PINK))
        assertTrue(colors.contains(AdaptiveColor.BROWN))
    }

    @Test
    fun `window expands forward to include long-acting ingestions`() {
        // Synthesise a substance with a 10h offset (very long).
        val longRoa = RoaDuration(
            onset = mins(15f, 30f),
            comeup = mins(15f, 30f),
            peak = hours(4f, 6f),
            offset = hours(8f, 10f),
            total = hours(13f, 17f),
            afterglow = null,
        )
        val ingestion = WidgetTimelineModel.IngestionInput(
            substanceName = "LSD",
            route = AdministrationRoute.ORAL,
            time = now.minus(Duration.ofMinutes(30)),
            color = AdaptiveColor.PURPLE,
        )
        val model = WidgetTimelineModel.build(
            now = now,
            ingestions = listOf(ingestion),
            durationsBySubstance = durationsOf("LSD" to longRoa),
        )
        // Window must extend well past the default +3h to fit the offset.
        val windowDurationHours = Duration.between(model.windowStart, model.windowEnd).toHours()
        assertTrue(
            "window should span well past 3h to fit a long substance, was ${windowDurationHours}h",
            windowDurationHours >= 5,
        )
    }

    @Test
    fun `window does not exceed maximum width`() {
        // Build many old ingestions that would otherwise stretch the window.
        val ingestions = (1..5).map { i ->
            WidgetTimelineModel.IngestionInput(
                substanceName = "MDMA",
                route = AdministrationRoute.ORAL,
                time = now.minus(Duration.ofHours(i.toLong())),
                color = AdaptiveColor.PINK,
            )
        }
        val model = WidgetTimelineModel.build(
            now = now,
            ingestions = ingestions,
            durationsBySubstance = durationsOf("MDMA" to mdmaOral),
        )
        val span = Duration.between(model.windowStart, model.windowEnd).toHours()
        assertTrue("window must never exceed 24h, was ${span}h", span <= 24)
    }

    @Test
    fun `nowSecondsFromStart is within window for live data`() {
        val ingestion = WidgetTimelineModel.IngestionInput(
            substanceName = "MDMA",
            route = AdministrationRoute.ORAL,
            time = now.minus(Duration.ofMinutes(15)),
            color = AdaptiveColor.PINK,
        )
        val model = WidgetTimelineModel.build(
            now = now,
            ingestions = listOf(ingestion),
            durationsBySubstance = durationsOf("MDMA" to mdmaOral),
        )
        assertTrue(model.nowSecondsFromStart > 0f)
        assertTrue(model.nowSecondsFromStart < model.widthInSeconds)
    }

    @Test
    fun `sample points are sorted by secondsFromStart`() {
        val ingestion = WidgetTimelineModel.IngestionInput(
            substanceName = "MDMA",
            route = AdministrationRoute.ORAL,
            time = now,
            color = AdaptiveColor.PINK,
        )
        val model = WidgetTimelineModel.build(
            now = now,
            ingestions = listOf(ingestion),
            durationsBySubstance = durationsOf("MDMA" to mdmaOral),
        )
        val xs = model.groups.single().points.map { it.secondsFromStart }
        assertEquals(xs.sorted(), xs)
    }

    @Test
    fun `all heights are in 0 to 1 range`() {
        val a = WidgetTimelineModel.IngestionInput("A", AdministrationRoute.ORAL, now, AdaptiveColor.RED)
        val b = WidgetTimelineModel.IngestionInput("A", AdministrationRoute.ORAL, now.plusSeconds(1800), AdaptiveColor.RED)
        val c = WidgetTimelineModel.IngestionInput("B", AdministrationRoute.ORAL, now.plusSeconds(900), AdaptiveColor.BLUE)
        val model = WidgetTimelineModel.build(
            now = now,
            ingestions = listOf(a, b, c),
            durationsBySubstance = durationsOf("A" to mdmaOral, "B" to mdmaOral),
        )
        model.groups.forEach { group ->
            group.points.forEach { p ->
                assertTrue("height ${p.height} out of range", p.height in 0f..1f)
            }
        }
    }
}
