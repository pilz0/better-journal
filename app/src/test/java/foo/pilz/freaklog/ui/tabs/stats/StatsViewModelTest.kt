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
import kotlin.math.sqrt

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val experienceRepo = mockk<ExperienceRepository>()
    private val skinEligibility = mockk<UltraFunSkinEligibility>(relaxed = true)
    private val excelSkinController = mockk<ExcelSkinController>(relaxed = true)
    private val viewModels = mutableListOf<StatsViewModel>()

    private fun createViewModel() =
        StatsViewModel(experienceRepo, skinEligibility, excelSkinController).also { viewModels.add(it) }

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
        viewModels.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun createIngestionWithData(
        dose: Double?,
        units: String?,
        isEstimate: Boolean = false,
        estimatedDoseStandardDeviation: Double? = null,
        customUnit: CustomUnit? = null,
        time: Instant = Instant.parse("2026-05-01T12:00:00Z"),
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
            time = time,
            notes = null,
            stomachFullness = null,
            consumerName = null,
            customUnitId = customUnit?.id,
        )
        return IngestionWithCompanionAndCustomUnit(
            ingestion = ingestion,
            substanceCompanion = null,
            customUnit = customUnit,
        )
    }

    @Test
    fun `getTotalDose returns null for empty list or conflicting units`() {
        val viewModel = createViewModel()

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
    }

    private fun customUnit(
        id: Int = 42,
        dose: Double = 10.0,
        deviation: Double? = null,
        isEstimate: Boolean = false,
        originalUnit: String = "mg",
    ) = CustomUnit(
        id = id,
        substanceName = "Substance A",
        name = "Drop",
        creationDate = Instant.EPOCH,
        administrationRoute = AdministrationRoute.ORAL,
        dose = dose,
        estimatedDoseStandardDeviation = deviation,
        isEstimate = isEstimate,
        isArchived = false,
        unit = "drop",
        originalUnit = originalUnit,
        note = "",
    )

    @Test
    fun `getTotalDose rejects missing units regardless of ingestion order`() {
        val viewModel = createViewModel()
        val known = createIngestionWithData(10.0, "mg")
        val unknown = createIngestionWithData(20.0, null)

        assertNull(viewModel.getTotalDose(listOf(unknown)))
        assertNull(viewModel.getTotalDose(listOf(known, unknown)))
        assertNull(viewModel.getTotalDose(listOf(unknown, known)))
    }

    @Test
    fun `getTotalDose rejects nonfinite raw and converted doses`() {
        val viewModel = createViewModel()
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertNull(viewModel.getTotalDose(listOf(createIngestionWithData(invalid, "mg"))))
            val invalidCustomUnit = listOf(
                createIngestionWithData(1.0, "drop", customUnit = customUnit(dose = invalid)),
            )
            assertNull(viewModel.getTotalDose(invalidCustomUnit))
        }
    }

    @Test
    fun `getTotalDose matches microgram aliases without converting different scales`() {
        val viewModel = createViewModel()
        val ingestions = listOf(
            createIngestionWithData(10.0, "ug"),
            createIngestionWithData(20.0, " μg "),
            createIngestionWithData(30.0, "MCG"),
        )
        val total = requireNotNull(viewModel.getTotalDose(ingestions))

        assertEquals(60.0, total.dose, 1e-9)
        assertEquals("ug", total.units)
        val mixedScales = listOf(
            createIngestionWithData(1.0, "g"),
            createIngestionWithData(1000.0, "mg"),
        )
        assertNull(viewModel.getTotalDose(mixedScales))
    }

    @Test
    fun `getTotalDose accepts zero and estimates without a known deviation`() {
        val ingestions = listOf(
            createIngestionWithData(0.0, "mg", isEstimate = true),
            createIngestionWithData(0.0, "mg", estimatedDoseStandardDeviation = 0.0),
        )
        val total = requireNotNull(createViewModel().getTotalDose(ingestions))

        assertEquals(0.0, total.dose, 0.0)
        assertTrue(total.isEstimate)
        assertNull(total.estimatedDoseStandardDeviation)
    }

    @Test
    fun `getTotalDose combines three independent variances and ignores absent deviations`() {
        val ingestions = listOf(
            createIngestionWithData(10.0, "mg", true, 2.0),
            createIngestionWithData(20.0, "mg", true, 3.0),
            createIngestionWithData(30.0, "mg", true, 6.0),
            createIngestionWithData(40.0, "mg"),
            createIngestionWithData(50.0, "mg", true),
        )
        val total = requireNotNull(createViewModel().getTotalDose(ingestions))

        assertEquals(150.0, total.dose, 1e-9)
        assertTrue(total.isEstimate)
        assertEquals(7.0, requireNotNull(total.estimatedDoseStandardDeviation), 1e-9)
    }

    @Test
    fun `getTotalDose converts fractional custom units and sums with base units`() {
        val ingestions = listOf(
            createIngestionWithData(0.5, "drop", customUnit = customUnit(dose = 20.0)),
            createIngestionWithData(1.5, "drops", customUnit = customUnit(id = 43, dose = 4.0)),
            createIngestionWithData(4.0, "mg"),
        )
        val total = requireNotNull(createViewModel().getTotalDose(ingestions))

        assertEquals(20.0, total.dose, 1e-9)
        assertEquals("mg", total.units)
        assertFalse(total.isEstimate)
        assertNull(total.estimatedDoseStandardDeviation)
    }

    @Test
    fun `getTotalDose rejects custom units with incompatible base units`() {
        val incompatible = listOf(
            createIngestionWithData(1.0, "drop", customUnit = customUnit()),
            createIngestionWithData(1.0, "drop", customUnit = customUnit(id = 43, originalUnit = "ml")),
        )
        assertNull(createViewModel().getTotalDose(incompatible))
    }

    @Test
    fun `getTotalDose scales quantity uncertainty by an exact conversion factor`() {
        val unit = customUnit(dose = 10.0)
        val ingestions = listOf(
            createIngestionWithData(2.0, "drops", true, 0.3, unit),
            createIngestionWithData(3.0, "drops", true, 0.4, unit),
        )
        val total = requireNotNull(createViewModel().getTotalDose(ingestions))

        assertEquals(50.0, total.dose, 1e-9)
        assertEquals(5.0, requireNotNull(total.estimatedDoseStandardDeviation), 1e-9)
    }

    @Test
    fun `getTotalDose includes quantity conversion and product variances for a shared estimate`() {
        val unit = customUnit(dose = 10.0, deviation = 2.0, isEstimate = true)
        val ingestions = listOf(
            createIngestionWithData(2.0, "drops", true, 0.3, unit),
            createIngestionWithData(3.0, "drops", true, 0.4, unit),
            createIngestionWithData(5.0, "mg", true, 4.0),
        )
        val total = requireNotNull(createViewModel().getTotalDose(ingestions))

        // Var(X)=0.25, E(X)=5, E(Y)=10, Var(Y)=4:
        // Var(XY)=0.25*100 + 25*4 + 0.25*4 = 126; raw dose adds 16.
        assertEquals(55.0, total.dose, 1e-9)
        assertEquals(sqrt(142.0), requireNotNull(total.estimatedDoseStandardDeviation), 1e-9)
        assertTrue(total.isEstimate)
    }

    @Test
    fun `getTotalDose keeps estimates from distinct custom unit ids independent`() {
        val first = customUnit(id = 42, deviation = 1.0, isEstimate = true)
        val second = first.copy(id = 43)
        val ingestions = listOf(
            createIngestionWithData(3.0, "drops", customUnit = first),
            createIngestionWithData(4.0, "drops", customUnit = second),
        )
        val total = requireNotNull(createViewModel().getTotalDose(ingestions))

        assertEquals(70.0, total.dose, 1e-9)
        assertEquals(5.0, requireNotNull(total.estimatedDoseStandardDeviation), 1e-9)
    }

    @Test
    fun `getTotalDose groups copies by custom unit id rather than object identity`() {
        val unit = customUnit(deviation = 1.0, isEstimate = true)
        val ingestions = listOf(
            createIngestionWithData(3.0, "drops", customUnit = unit),
            createIngestionWithData(4.0, "drops", customUnit = unit.copy()),
        )
        val total = requireNotNull(createViewModel().getTotalDose(ingestions))

        assertEquals(70.0, total.dose, 1e-9)
        assertEquals(7.0, requireNotNull(total.estimatedDoseStandardDeviation), 1e-9)
    }

    @Test
    fun `getTotalDose is independent of negative epoch times and ingestion order`() {
        val viewModel = createViewModel()
        val ingestions = listOf(
            createIngestionWithData(10.0, "mg", true, 3.0, time = Instant.ofEpochSecond(-3600)),
            createIngestionWithData(20.0, "mg", true, 4.0, time = Instant.ofEpochSecond(-1)),
        )
        val total = requireNotNull(viewModel.getTotalDose(ingestions))

        assertEquals(30.0, total.dose, 1e-9)
        assertEquals(5.0, requireNotNull(total.estimatedDoseStandardDeviation), 1e-9)
        assertEquals(total, viewModel.getTotalDose(ingestions.reversed()))
    }

    @Test
    fun `getTotalDose calculates dose sum and standard deviation in quadrature`() {
        val viewModel = createViewModel()

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
        assertEquals(5.0, requireNotNull(totalDose.estimatedDoseStandardDeviation), 0.001)
    }

    @Test
    fun `getTotalDose preserves exact dose without estimate flags when exact`() {
        val viewModel = createViewModel()

        val ingestions = listOf(
            createIngestionWithData(dose = 10.0, units = "mg"),
            createIngestionWithData(dose = 20.0, units = "mg"),
        )

        val totalDose = viewModel.getTotalDose(ingestions)
        assertNotNull(totalDose)
        assertEquals(30.0, totalDose!!.dose, 0.001)
        assertFalse(totalDose.isEstimate)
        assertNull(totalDose.estimatedDoseStandardDeviation)
    }

    @Test
    fun `getTotalDose accounts for correlated conversion errors when ingestions share a custom unit estimate`() {
        val viewModel = createViewModel()

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
        assertEquals(2.0, requireNotNull(totalDose.estimatedDoseStandardDeviation), 0.001)
    }
}
