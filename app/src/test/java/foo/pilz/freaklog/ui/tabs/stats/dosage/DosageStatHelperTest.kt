/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog (a fork of PsychonautWiki Journal).
 */

package foo.pilz.freaklog.ui.tabs.stats.dosage

import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.substances.AdministrationRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class DosageStatHelperTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val now: Instant = Instant.parse("2026-04-29T12:00:00Z")

    private fun ingestion(
        id: Int,
        timeIso: String,
        dose: Double?,
        units: String? = "mg",
    ) = Ingestion(
        id = id,
        substanceName = "MDMA",
        time = Instant.parse(timeIso),
        administrationRoute = AdministrationRoute.ORAL,
        dose = dose,
        isDoseAnEstimate = false,
        estimatedDoseStandardDeviation = null,
        units = units,
        experienceId = id,
        notes = null,
        stomachFullness = null,
        consumerName = null,
        customUnitId = null,
    )

    @Test
    fun `computeBuckets sums known doses into the right daily buckets`() {
        val result = DosageStatHelper.computeBuckets(
            ingestions = listOf(
                ingestion(1, "2026-04-29T08:00:00Z", 100.0),
                ingestion(2, "2026-04-29T11:00:00Z", 150.0),
                ingestion(3, "2026-04-25T08:00:00Z", 80.0),
                ingestion(4, "2026-03-01T08:00:00Z", 200.0), // outside window
            ),
            range = DosageStatRange.DAYS_30,
            now = now,
            zone = zone,
        )
        assertEquals(30, result.buckets.size)
        // Last bucket = today = 100 + 150 = 250
        assertEquals(250.0, result.buckets.last().totalDose, 1e-9)
        // 5th from end = 2026-04-25 = 80
        assertEquals(80.0, result.buckets[result.buckets.size - 5].totalDose, 1e-9)
        // 200 mg from March 1 is outside the 30-day window
        assertEquals(0, result.unknownDoseCount)
        assertEquals(setOf("mg"), result.unitsUsed)
    }

    @Test
    fun `computeBuckets uses the unknown-dose estimate when supplied`() {
        val ingestions = listOf(
            ingestion(1, "2026-04-29T08:00:00Z", 100.0),
            ingestion(2, "2026-04-29T09:00:00Z", null), // unknown
            ingestion(3, "2026-04-29T10:00:00Z", null), // unknown
        )
        val withoutEstimate = DosageStatHelper.computeBuckets(
            ingestions = ingestions,
            range = DosageStatRange.DAYS_30,
            now = now,
            zone = zone,
            estimatedUnknownDose = null,
        )
        assertEquals(100.0, withoutEstimate.buckets.last().totalDose, 1e-9)
        assertEquals(2, withoutEstimate.unknownDoseCount)

        val withEstimate = DosageStatHelper.computeBuckets(
            ingestions = ingestions,
            range = DosageStatRange.DAYS_30,
            now = now,
            zone = zone,
            estimatedUnknownDose = 90.0,
        )
        // 100 + 90 + 90 = 280 in today's bucket
        assertEquals(280.0, withEstimate.buckets.last().totalDose, 1e-9)
    }

    @Test
    fun `computeBuckets reports mixed units`() {
        val result = DosageStatHelper.computeBuckets(
            ingestions = listOf(
                ingestion(1, "2026-04-29T08:00:00Z", 100.0, units = "mg"),
                ingestion(2, "2026-04-28T08:00:00Z", 100.0, units = "µg"),
                ingestion(3, "2026-04-27T08:00:00Z", 100.0, units = ""),
                ingestion(4, "2026-04-26T08:00:00Z", 100.0, units = null),
            ),
            range = DosageStatRange.DAYS_30,
            now = now,
            zone = zone,
        )
        assertTrue(result.unitsUsed.containsAll(listOf("mg", "µg")))
        assertEquals(2, result.unitsUsed.size)
    }

    @Test
    fun `weeks_26 produces 26 weekly buckets`() {
        val result = DosageStatHelper.computeBuckets(
            ingestions = emptyList(),
            range = DosageStatRange.WEEKS_26,
            now = now,
            zone = zone,
        )
        assertEquals(26, result.buckets.size)
    }

    @Test
    fun `months_12 produces 12 monthly buckets`() {
        val result = DosageStatHelper.computeBuckets(
            ingestions = emptyList(),
            range = DosageStatRange.MONTHS_12,
            now = now,
            zone = zone,
        )
        assertEquals(12, result.buckets.size)
    }

    @Test
    fun `years produces 12 yearly buckets`() {
        val result = DosageStatHelper.computeBuckets(
            ingestions = emptyList(),
            range = DosageStatRange.YEARS,
            now = now,
            zone = zone,
        )
        assertEquals(12, result.buckets.size)
    }
}
