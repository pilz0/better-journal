/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.ui.tabs.stats.tolerance

import androidx.compose.ui.graphics.Color
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.substances.classes.Substance
import foo.pilz.freaklog.data.substances.classes.Tolerance
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ToleranceChartCalculatorTest {

    private val substanceRepo = mockk<SubstanceRepository>()

    private fun createSubstance(
        name: String,
        halfTolerance: String? = null,
        zeroTolerance: String? = null
    ): Substance {
        return Substance(
            name = name,
            commonNames = emptyList(),
            url = "https://example.com/$name",
            isApproved = true,
            tolerance = Tolerance(
                full = null,
                half = halfTolerance,
                zero = zeroTolerance
            ),
            crossTolerances = emptyList(),
            addictionPotential = null,
            toxicities = emptyList(),
            categories = emptyList(),
            summary = null,
            effectsSummary = null,
            dosageRemark = null,
            generalRisks = null,
            longtermRisks = null,
            saferUse = emptyList(),
            interactions = null,
            roas = emptyList()
        )
    }

    @Test
    fun `returns empty list when input is empty`() {
        val result = ToleranceChartCalculator.getToleranceWindows(
            substanceAndDays = emptyList(),
            substanceCompanions = emptyList(),
            substanceRepository = substanceRepo
        )
        assertThat(result).isEmpty()
    }

    @Test
    fun `returns empty list when substance has no tolerance information`() {
        every { substanceRepo.getSubstance("UnknownSubstance") } returns null

        val day = Instant.parse("2026-06-01T10:00:00Z")
        val result = ToleranceChartCalculator.getToleranceWindows(
            substanceAndDays = listOf(SubstanceAndDay("UnknownSubstance", day)),
            substanceCompanions = emptyList(),
            substanceRepository = substanceRepo
        )
        assertThat(result).isEmpty()
    }

    @Test
    fun `returns empty list when tolerance durations are 24 hours or less`() {
        val lsd = createSubstance("FastTol", halfTolerance = "1 day", zeroTolerance = "24 hours")
        every { substanceRepo.getSubstance("FastTol") } returns lsd

        val day = Instant.parse("2026-06-01T10:00:00Z")
        val result = ToleranceChartCalculator.getToleranceWindows(
            substanceAndDays = listOf(SubstanceAndDay("FastTol", day)),
            substanceCompanions = emptyList(),
            substanceRepository = substanceRepo
        )
        // parseToleranceToHours("1 day") = 24.0, which is not > 24, so no interval added
        assertThat(result).isEmpty()
    }

    @Test
    fun `deduplicates multiple ingestions of same substance on same day by picking earliest`() {
        val sub = createSubstance("LSD", halfTolerance = "3-4 days", zeroTolerance = "1-2 weeks")
        every { substanceRepo.getSubstance("LSD") } returns sub

        val dayEarly = Instant.parse("2026-06-01T08:00:00Z")
        val dayLate = Instant.parse("2026-06-01T20:00:00Z")

        val result = ToleranceChartCalculator.getToleranceWindows(
            substanceAndDays = listOf(
                SubstanceAndDay("LSD", dayLate),
                SubstanceAndDay("LSD", dayEarly)
            ),
            substanceCompanions = emptyList(),
            substanceRepository = substanceRepo
        )

        // Half tolerance: (3 + 4) / 2 * 24 = 84 hours = 3.5 days
        // Zero tolerance: (1 + 2) / 2 * 7 * 24 = 252 hours = 10.5 days
        // FULL window starts at dayEarly (08:00) and ends at dayEarly + 84h
        val fullWindow = result.first { it.toleranceType == ToleranceType.FULL }
        assertThat(fullWindow.start).isEqualTo(dayEarly)
        assertThat(fullWindow.end).isEqualTo(dayEarly.plus(Duration.ofHours(84)))
    }

    @Test
    fun `handles multiple distinct substances on same day without dropping either`() {
        val lsd = createSubstance("LSD", halfTolerance = "3 days", zeroTolerance = "2 weeks")
        val mdma = createSubstance("MDMA", halfTolerance = "4 days", zeroTolerance = "1 week")
        every { substanceRepo.getSubstance("LSD") } returns lsd
        every { substanceRepo.getSubstance("MDMA") } returns mdma

        val day = Instant.parse("2026-06-01T12:00:00Z")
        val result = ToleranceChartCalculator.getToleranceWindows(
            substanceAndDays = listOf(
                SubstanceAndDay("LSD", day),
                SubstanceAndDay("MDMA", day)
            ),
            substanceCompanions = emptyList(),
            substanceRepository = substanceRepo
        )

        val names = result.map { it.substanceName }.distinct()
        assertThat(names).containsExactly("LSD", "MDMA")
    }

    @Test
    fun `generates correct FULL and HALF tolerance intervals`() {
        val sub = createSubstance("2C-B", halfTolerance = "2 days", zeroTolerance = "4 days")
        every { substanceRepo.getSubstance("2C-B") } returns sub

        val start = Instant.parse("2026-06-01T00:00:00Z")
        val result = ToleranceChartCalculator.getToleranceWindows(
            substanceAndDays = listOf(SubstanceAndDay("2C-B", start)),
            substanceCompanions = emptyList(),
            substanceRepository = substanceRepo
        )

        assertThat(result).hasSize(2)
        val full = result.first { it.toleranceType == ToleranceType.FULL }
        val half = result.first { it.toleranceType == ToleranceType.HALF }

        // 2 days = 48 hours
        assertThat(full.start).isEqualTo(start)
        assertThat(full.end).isEqualTo(start.plus(Duration.ofHours(48)))

        // 4 days = 96 hours
        assertThat(half.start).isEqualTo(start.plus(Duration.ofHours(48)))
        assertThat(half.end).isEqualTo(start.plus(Duration.ofHours(96)))
    }

    @Test
    fun `merges overlapping full tolerance intervals`() {
        // Ingestion day 1: FULL [T, T + 72h]
        // Ingestion day 2 (T + 24h): FULL [T + 24h, T + 96h]
        // Merged FULL should be [T, T + 96h]
        val sub = createSubstance("Ketamine", halfTolerance = "3 days", zeroTolerance = null)
        every { substanceRepo.getSubstance("Ketamine") } returns sub

        val day1 = Instant.parse("2026-06-01T00:00:00Z")
        val day2 = Instant.parse("2026-06-02T00:00:00Z")

        val result = ToleranceChartCalculator.getToleranceWindows(
            substanceAndDays = listOf(
                SubstanceAndDay("Ketamine", day1),
                SubstanceAndDay("Ketamine", day2)
            ),
            substanceCompanions = emptyList(),
            substanceRepository = substanceRepo
        )

        val fullWindows = result.filter { it.toleranceType == ToleranceType.FULL }
        assertThat(fullWindows).hasSize(1)
        assertThat(fullWindows[0].start).isEqualTo(day1)
        assertThat(fullWindows[0].end).isEqualTo(day2.plus(Duration.ofHours(72)))
    }

    @Test
    fun `zero tolerance at or before half tolerance produces only full windows`() {
        val start = Instant.parse("2026-06-01T00:00:00Z")
        for (zeroTolerance in listOf("2 days", "3 days")) {
            every { substanceRepo.getSubstance("LSD") } returns createSubstance(
                "LSD", halfTolerance = "3 days", zeroTolerance = zeroTolerance
            )
            // Separate windows also exercise subtraction without overlap masking an invalid interval.
            val result = ToleranceChartCalculator.getToleranceWindows(
                substanceAndDays = listOf(
                    SubstanceAndDay("LSD", start),
                    SubstanceAndDay("LSD", start.plus(Duration.ofDays(7))),
                ),
                substanceCompanions = emptyList(),
                substanceRepository = substanceRepo,
            )

            assertThat(result).hasSize(2)
            result.forEach { window ->
                assertThat(window.toleranceType).isEqualTo(ToleranceType.FULL)
                assertThat(Duration.between(window.start, window.end)).isEqualTo(Duration.ofDays(3))
            }
        }
    }

    @Test
    fun `assigns companion color to ToleranceWindow and falls back to Color Red`() {
        val sub = createSubstance("LSD", halfTolerance = "3 days", zeroTolerance = null)
        every { substanceRepo.getSubstance("LSD") } returns sub

        val day = Instant.parse("2026-06-01T00:00:00Z")

        val companion = SubstanceCompanion(
            substanceName = "LSD",
            color = AdaptiveColor.CYAN
        )

        val resultWithCompanion = ToleranceChartCalculator.getToleranceWindows(
            substanceAndDays = listOf(SubstanceAndDay("LSD", day)),
            substanceCompanions = listOf(companion),
            substanceRepository = substanceRepo
        )
        assertThat(resultWithCompanion[0].substanceColor).isEqualTo(companion.color.getComposeColor(false))

        val resultWithoutCompanion = ToleranceChartCalculator.getToleranceWindows(
            substanceAndDays = listOf(SubstanceAndDay("LSD", day)),
            substanceCompanions = emptyList(),
            substanceRepository = substanceRepo
        )
        assertThat(resultWithoutCompanion[0].substanceColor).isEqualTo(Color.Red)
    }

    @Test
    fun `ToleranceWindow barColor adjusts alpha for HALF tolerance`() {
        val windowFull = ToleranceWindow(
            substanceName = "LSD",
            start = Instant.EPOCH,
            end = Instant.EPOCH.plusSeconds(3600),
            toleranceType = ToleranceType.FULL,
            substanceColor = Color.Blue
        )
        assertThat(windowFull.barColor).isEqualTo(Color.Blue)

        val windowHalf = ToleranceWindow(
            substanceName = "LSD",
            start = Instant.EPOCH,
            end = Instant.EPOCH.plusSeconds(3600),
            toleranceType = ToleranceType.HALF,
            substanceColor = Color.Blue
        )
        assertThat(windowHalf.barColor).isEqualTo(Color.Blue.copy(alpha = 0.5f))
    }

    @Test
    fun `ToleranceWindow contains returns true within range and false outside`() {
        val start = Instant.parse("2026-06-01T10:00:00Z")
        val end = Instant.parse("2026-06-01T20:00:00Z")
        val window = ToleranceWindow(
            substanceName = "LSD",
            start = start,
            end = end,
            toleranceType = ToleranceType.FULL,
            substanceColor = Color.Green
        )

        assertThat(window.contains(start)).isTrue()
        assertThat(window.contains(end)).isTrue()
        assertThat(window.contains(Instant.parse("2026-06-01T15:00:00Z"))).isTrue()
        assertThat(window.contains(Instant.parse("2026-06-01T09:59:59Z"))).isFalse()
        assertThat(window.contains(Instant.parse("2026-06-01T20:00:01Z"))).isFalse()
    }
}
