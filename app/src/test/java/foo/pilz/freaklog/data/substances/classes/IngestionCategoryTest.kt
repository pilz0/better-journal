/*
 * Copyright (c) 2024. Freaklog.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Freaklog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Freaklog.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.data.substances.classes

import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.room.experiences.entities.CustomUnit
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.substances.AdministrationRoute
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class IngestionCategoryTest {

    private fun makeIngestion(category: IngestionCategory?) = Ingestion(
        substanceName = "TestSubstance",
        administrationRoute = AdministrationRoute.ORAL,
        dose = 10.0,
        isDoseAnEstimate = false,
        estimatedDoseStandardDeviation = null,
        units = "mg",
        experienceId = 1,
        notes = null,
        stomachFullness = null,
        consumerName = null,
        customUnitId = null,
        category = category,
        time = Instant.now()
    )

    private fun makeCustomUnit(defaultCategory: IngestionCategory?) = CustomUnit(
        name = "tab",
        substanceName = "TestSubstance",
        administrationRoute = AdministrationRoute.ORAL,
        dose = 100.0,
        estimatedDoseStandardDeviation = null,
        isEstimate = false,
        unit = "tab",
        originalUnit = "µg",
        note = "",
        isArchived = false,
        defaultCategory = defaultCategory
    )

    private fun makeCompanion(defaultCategory: IngestionCategory?) = SubstanceCompanion(
        substanceName = "TestSubstance",
        color = AdaptiveColor.BLUE,
        defaultCategory = defaultCategory
    )

    @Test
    fun `computedCategory uses ingestion category when set`() {
        val ingestion = makeIngestion(IngestionCategory.MEDICINAL)
        val customUnit = makeCustomUnit(IngestionCategory.RECREATIONAL)
        val companion = makeCompanion(IngestionCategory.RECREATIONAL)
        assertEquals(IngestionCategory.MEDICINAL, ingestion.computedCategory(customUnit, companion))
    }

    @Test
    fun `computedCategory falls back to customUnit defaultCategory`() {
        val ingestion = makeIngestion(null)
        val customUnit = makeCustomUnit(IngestionCategory.MEDICINAL)
        val companion = makeCompanion(IngestionCategory.RECREATIONAL)
        assertEquals(IngestionCategory.MEDICINAL, ingestion.computedCategory(customUnit, companion))
    }

    @Test
    fun `computedCategory falls back to companion defaultCategory when customUnit has none`() {
        val ingestion = makeIngestion(null)
        val customUnit = makeCustomUnit(null)
        val companion = makeCompanion(IngestionCategory.MEDICINAL)
        assertEquals(IngestionCategory.MEDICINAL, ingestion.computedCategory(customUnit, companion))
    }

    @Test
    fun `computedCategory falls back to companion when no customUnit`() {
        val ingestion = makeIngestion(null)
        val companion = makeCompanion(IngestionCategory.MEDICINAL)
        assertEquals(IngestionCategory.MEDICINAL, ingestion.computedCategory(null, companion))
    }

    @Test
    fun `computedCategory falls back to DEFAULT when all null`() {
        val ingestion = makeIngestion(null)
        assertEquals(IngestionCategory.DEFAULT_INGESTION_CATEGORY, ingestion.computedCategory(null, null))
    }

    @Test
    fun `computedCategory returns RECREATIONAL as default`() {
        val ingestion = makeIngestion(null)
        assertEquals(IngestionCategory.RECREATIONAL, ingestion.computedCategory(null, null))
    }

    @Test
    fun `computedCategory ignores customUnit when ingestion category is explicitly set`() {
        val ingestion = makeIngestion(IngestionCategory.RECREATIONAL)
        val customUnit = makeCustomUnit(IngestionCategory.MEDICINAL)
        assertEquals(IngestionCategory.RECREATIONAL, ingestion.computedCategory(customUnit, null))
    }
}
