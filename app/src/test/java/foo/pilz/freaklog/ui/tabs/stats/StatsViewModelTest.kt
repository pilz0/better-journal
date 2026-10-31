/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.ui.tabs.stats

import androidx.lifecycle.viewModelScope
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomUnit
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.ui.tabs.settings.funny.UltraFunSkinEligibility
import foo.pilz.freaklog.ui.tabs.stats.excelskin.ExcelSkinController
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val experienceRepo = mockk<ExperienceRepository>()
    private val skinEligibility = mockk<UltraFunSkinEligibility>(relaxed = true)
    private val excelSkinController = mockk<ExcelSkinController>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every {
            experienceRepo.getSortedExperiencesWithIngestionsAndCustomUnitsFlow()
        } returns flowOf(emptyList())
        every {
            experienceRepo.getAllSubstanceCompanionsFlow()
        } returns flowOf(emptyList())
        every {
            experienceRepo.getSortedIngestions(any())
        } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createIngestionWithData(
        dose: Double?,
        units: String?,
        isEstimate: Boolean = false,
        estimatedDoseStandardDeviation: Double? = null,
    ): IngestionWithCompanionAndCustomUnit {
        val ingestion = Ingestion(
            id = 1,
            experienceId = 1,
            substanceName = "Substance A",
            administrationRoute = AdministrationRoute.ORAL,
            dose = dose,
            units = units,
            isDoseAnEstimate = isEstimate,
            estimatedDoseStandardDeviation = estimatedDoseStandardDeviation,
            time = Instant.parse("2026-05-01T12:00:00Z"),
            notes = null,
            stomachFullness = null,
            consumerName = null,
            customUnitId = null,
        )
        return IngestionWithCompanionAndCustomUnit(
            ingestion = ingestion,
            substanceCompanion = null,
            customUnit = null,
        )
    }

    @Test
    fun `getTotalDose returns null for empty list or conflicting units`() {
        val viewModel = StatsViewModel(experienceRepo, skinEligibility, excelSkinController)

        assertNull(viewModel.getTotalDose(emptyList()))

        val mixedUnits = listOf(
            createIngestionWithData(dose = 50.0, units = "mg"),
            createIngestionWithData(dose = 50.0, units = "ug"),
        )
        assertNull(viewModel.getTotalDose(mixedUnits))

        val nullDose = listOf(
            createIngestionWithData(dose = 50.0, units = "mg"),
            createIngestionWithData(dose = null, units = "mg"),
        )
        assertNull(viewModel.getTotalDose(nullDose))

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `getTotalDose calculates dose sum and standard deviation in quadrature`() {
        val viewModel = StatsViewModel(experienceRepo, skinEligibility, excelSkinController)

        // 30 +- 3 mg and 40 +- 4 mg -> total dose 70 mg, combined uncertainty sqrt(3^2 + 4^2) = 5.0 mg
        val ingestions = listOf(
            createIngestionWithData(
                dose = 30.0,
                units = "mg",
                isEstimate = true,
                estimatedDoseStandardDeviation = 3.0,
            ),
            createIngestionWithData(
                dose = 40.0,
                units = "mg",
                isEstimate = true,
                estimatedDoseStandardDeviation = 4.0,
            ),
        )

        val totalDose = viewModel.getTotalDose(ingestions)
        assertNotNull(totalDose)
        assertEquals(70.0, totalDose!!.dose, 0.001)
        assertEquals("mg", totalDose.units)
        assertTrue(totalDose.isEstimate)
        assertEquals(5.0, totalDose.estimatedDoseStandardDeviation ?: 0.0, 0.001)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `getTotalDose preserves exact dose without estimate flags when exact`() {
        val viewModel = StatsViewModel(experienceRepo, skinEligibility, excelSkinController)

        val ingestions = listOf(
            createIngestionWithData(dose = 10.0, units = "mg"),
            createIngestionWithData(dose = 20.0, units = "mg"),
        )

        val totalDose = viewModel.getTotalDose(ingestions)
        assertNotNull(totalDose)
        assertEquals(30.0, totalDose!!.dose, 0.001)
        assertFalse(totalDose.isEstimate)
        assertNull(totalDose.estimatedDoseStandardDeviation)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `getTotalDose accounts for correlated conversion errors when ingestions share a custom unit estimate`() {
        val viewModel = StatsViewModel(experienceRepo, skinEligibility, excelSkinController)

        val customUnit = CustomUnit(
            id = 42,
            substanceName = "Substance A",
            name = "Drop",
            administrationRoute = AdministrationRoute.ORAL,
            dose = 10.0,
            estimatedDoseStandardDeviation = 1.0,
            isEstimate = true,
            isArchived = false,
            unit = "drop",
            unitPlural = "drops",
            originalUnit = "mg",
            note = "",
        )

        // Two ingestions each taking 1 drop of a 10 +- 1 mg custom unit.
        // Sum of custom units = 2 drops.
        // Linear conversion gives 20 mg total with (1+1) * 1.0 = 2.0 mg uncertainty (correlated),
        // whereas independent quadrature would have yielded sqrt(1^2 + 1^2) = 1.414 mg.
        val ingestionA = IngestionWithCompanionAndCustomUnit(
            ingestion = Ingestion(
                id = 1,
                experienceId = 1,
                substanceName = "Substance A",
                administrationRoute = AdministrationRoute.ORAL,
                dose = 1.0,
                units = "drops",
                isDoseAnEstimate = false,
                estimatedDoseStandardDeviation = null,
                time = Instant.parse("2026-05-01T12:00:00Z"),
                notes = null,
                stomachFullness = null,
                consumerName = null,
                customUnitId = 42,
            ),
            substanceCompanion = null,
            customUnit = customUnit,
        )
        val ingestionB = IngestionWithCompanionAndCustomUnit(
            ingestion = Ingestion(
                id = 2,
                experienceId = 1,
                substanceName = "Substance A",
                administrationRoute = AdministrationRoute.ORAL,
                dose = 1.0,
                units = "drops",
                isDoseAnEstimate = false,
                estimatedDoseStandardDeviation = null,
                time = Instant.parse("2026-05-01T13:00:00Z"),
                notes = null,
                stomachFullness = null,
                consumerName = null,
                customUnitId = 42,
            ),
            substanceCompanion = null,
            customUnit = customUnit,
        )

        val totalDose = viewModel.getTotalDose(listOf(ingestionA, ingestionB))
        assertNotNull(totalDose)
        assertEquals(20.0, totalDose!!.dose, 0.001)
        assertEquals("mg", totalDose.units)
        assertTrue(totalDose.isEstimate)
        assertEquals(2.0, totalDose.estimatedDoseStandardDeviation ?: 0.0, 0.001)

        viewModel.viewModelScope.cancel()
    }
}
