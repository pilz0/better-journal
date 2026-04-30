/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog (a fork of PsychonautWiki Journal).
 */

package foo.pilz.freaklog.ui.tabs.stats.charts

import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import foo.pilz.freaklog.data.substances.AdministrationRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class ExperienceStatsHelperTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val now: Instant = Instant.parse("2026-04-29T12:00:00Z")

    private fun ing(
        id: Int,
        experienceId: Int,
        substance: String,
        timeIso: String,
        color: AdaptiveColor = AdaptiveColor.AUBURN,
    ) = IngestionWithCompanionAndCustomUnit(
        ingestion = Ingestion(
            id = id,
            substanceName = substance,
            time = Instant.parse(timeIso),
            administrationRoute = AdministrationRoute.ORAL,
            dose = 100.0,
            isDoseAnEstimate = false,
            estimatedDoseStandardDeviation = null,
            units = "mg",
            experienceId = experienceId,
            notes = null,
            stomachFullness = null,
            consumerName = null,
            customUnitId = null,
        ),
        substanceCompanion = SubstanceCompanion(substanceName = substance, color = color),
        customUnit = null,
    )

    @Test
    fun `dailyBuckets has the right size and bucketing`() {
        val buckets = ExperienceStatsHelper.dailyBuckets(
            ingestions = listOf(
                ing(1, 1, "MDMA", "2026-04-29T08:00:00Z"),
                ing(2, 1, "MDMA", "2026-04-29T11:00:00Z"),
                ing(3, 2, "LSD", "2026-04-25T20:00:00Z"),
                ing(4, 3, "Caffeine", "2026-03-01T10:00:00Z"), // outside the 30-day window
            ),
            days = 30,
            now = now,
            zone = zone,
        )
        assertEquals(30, buckets.size)
        // Last bucket is "today" (2026-04-29) — 2 MDMA ingestions
        assertEquals(2, buckets.last().sumOf { it.count })
        // 5th from the end is 2026-04-25 — 1 LSD ingestion
        assertEquals(1, buckets[buckets.size - 5].sumOf { it.count })
        // March 1 is outside the window so total = 2 + 1 = 3
        assertEquals(3, buckets.sumOf { bucket -> bucket.sumOf { it.count } })
    }

    @Test
    fun `monthlyBuckets bucket the right number of months`() {
        val buckets = ExperienceStatsHelper.monthlyBuckets(
            ingestions = listOf(
                ing(1, 1, "MDMA", "2026-04-15T08:00:00Z"),
                ing(2, 2, "MDMA", "2026-03-10T08:00:00Z"),
                ing(3, 3, "LSD", "2025-05-01T08:00:00Z"),
            ),
            months = 12,
            now = now,
            zone = zone,
        )
        assertEquals(12, buckets.size)
        // last bucket = April 2026 = 1 ingestion
        assertEquals(1, buckets.last().sumOf { it.count })
        // 2nd-from-last = March 2026 = 1 ingestion
        assertEquals(1, buckets[buckets.size - 2].sumOf { it.count })
    }

    @Test
    fun `yearlyBuckets spans first ingestion year through current year`() {
        val buckets = ExperienceStatsHelper.yearlyBuckets(
            ingestions = listOf(
                ing(1, 1, "MDMA", "2024-01-01T00:00:00Z"),
                ing(2, 2, "MDMA", "2026-04-29T00:00:00Z"),
            ),
            now = now,
            zone = zone,
        )
        // 2024, 2025, 2026
        assertEquals(3, buckets.size)
        assertEquals(1, buckets.first().sumOf { it.count }) // 2024
        assertEquals(0, buckets[1].sumOf { it.count }) // 2025
        assertEquals(1, buckets.last().sumOf { it.count }) // 2026
    }

    @Test
    fun `fractionalSubstanceCounts splits credit per experience`() {
        val ingestions = listOf(
            // Experience 1: MDMA + LSD (2 substances) → 0.5 each
            ing(1, 1, "MDMA", "2026-04-29T08:00:00Z"),
            ing(2, 1, "LSD", "2026-04-29T09:00:00Z"),
            // Experience 2: MDMA only → 1.0
            ing(3, 2, "MDMA", "2026-04-28T08:00:00Z"),
            // Experience 3: MDMA + LSD + Caffeine (3 substances) → 1/3 each
            ing(4, 3, "MDMA", "2026-04-27T08:00:00Z"),
            ing(5, 3, "LSD", "2026-04-27T09:00:00Z"),
            ing(6, 3, "Caffeine", "2026-04-27T10:00:00Z"),
            // Same substance twice in one experience: should still be a single 1/N credit
            ing(7, 4, "MDMA", "2026-04-26T08:00:00Z"),
            ing(8, 4, "MDMA", "2026-04-26T09:00:00Z"),
        )
        val result = ExperienceStatsHelper.fractionalSubstanceCounts(ingestions)
        val mdma = result.first { it.substanceName == "MDMA" }
        val lsd = result.first { it.substanceName == "LSD" }
        val caffeine = result.first { it.substanceName == "Caffeine" }
        // MDMA: 0.5 + 1.0 + 1/3 + 1.0 = 2.833…
        assertEquals(0.5 + 1.0 + 1.0 / 3.0 + 1.0, mdma.fractionalCount, 1e-9)
        // LSD: 0.5 + 1/3
        assertEquals(0.5 + 1.0 / 3.0, lsd.fractionalCount, 1e-9)
        // Caffeine: 1/3
        assertEquals(1.0 / 3.0, caffeine.fractionalCount, 1e-9)
        // Sorted by descending count.
        assertTrue(result[0].fractionalCount >= result[1].fractionalCount)
    }

    @Test
    fun `substanceExperienceCount counts unique experiences in range`() {
        val ingestions = listOf(
            ing(1, 1, "MDMA", "2026-04-29T08:00:00Z"),
            ing(2, 1, "MDMA", "2026-04-29T11:00:00Z"), // same experience
            ing(3, 2, "MDMA", "2026-04-20T08:00:00Z"),
            ing(4, 3, "MDMA", "2025-12-01T08:00:00Z"),
            ing(5, 4, "LSD", "2026-04-29T08:00:00Z"),
        )
        val count30d = ExperienceStatsHelper.substanceExperienceCount(
            ingestions,
            "MDMA",
            start = Instant.parse("2026-03-30T12:00:00Z"),
            end = now.plusSeconds(60),
        )
        // Two distinct experience IDs (1 and 2)
        assertEquals(2, count30d)
    }
}
