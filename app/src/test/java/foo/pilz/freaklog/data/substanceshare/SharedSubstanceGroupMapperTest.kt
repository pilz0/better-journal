package foo.pilz.freaklog.data.substanceshare

import foo.pilz.freaklog.data.room.experiences.entities.CustomUnit
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroup
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroupItem
import foo.pilz.freaklog.data.room.experiences.relations.SubstanceGroupWithItems
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.IngestionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SharedSubstanceGroupMapperTest {

    @Test
    fun `maps substance group with items and custom unit references`() {
        val customUnit = CustomUnit(
            id = 42,
            substanceName = "LSD",
            name = "Voidrealm",
            administrationRoute = AdministrationRoute.SUBLINGUAL,
            dose = 105.0,
            estimatedDoseStandardDeviation = null,
            isEstimate = false,
            isArchived = false,
            unit = "tab",
            unitPlural = "tabs",
            originalUnit = "ug",
            note = "Tested",
            defaultCategory = IngestionCategory.DEFAULT_INGESTION_CATEGORY,
        )

        val item1 = SubstanceGroupItem(
            id = 1,
            groupId = 10,
            sortOrder = 0,
            substanceName = "LSD",
            administrationRoute = AdministrationRoute.SUBLINGUAL,
            dose = 1.0,
            units = "tab",
            customUnitId = 42,
        )

        val item2 = SubstanceGroupItem(
            id = 2,
            groupId = 10,
            sortOrder = 1,
            substanceName = "MDMA",
            administrationRoute = AdministrationRoute.ORAL,
            dose = 120.0,
            units = "mg",
            customUnitId = null,
        )

        val groupWithItems = SubstanceGroupWithItems(
            group = SubstanceGroup(id = 10, name = "Candyflip"),
            items = listOf(item2, item1), // out of order
        )

        val shared = groupWithItems.toShared(
            referencedCustomSubstances = emptyList(),
            referencedCustomUnits = listOf(customUnit),
        )

        assertEquals("Candyflip", shared.name)
        assertEquals(2, shared.items.size)
        // sortedItems sorts by sortOrder
        assertEquals("LSD", shared.items[0].substanceName)
        assertNotNull(shared.items[0].customUnitRef)
        assertEquals("Voidrealm", shared.items[0].customUnitRef?.name)
        assertEquals("MDMA", shared.items[1].substanceName)
        assertNull(shared.items[1].customUnitRef)

        val entityUnit = shared.customUnits.first().toEntity()
        assertEquals(customUnit.name, entityUnit.name)
        assertEquals(customUnit.substanceName, entityUnit.substanceName)
        assertEquals(customUnit.dose, entityUnit.dose)
    }

    @Test
    fun `shared custom unit with unknown category falls back to null`() {
        val sharedUnit = SharedCustomUnit(
            substanceName = "Test",
            name = "One",
            administrationRoute = AdministrationRoute.ORAL,
            unit = "pill",
            originalUnit = "mg",
            note = "",
            defaultCategory = "UNKNOWN_NONEXISTENT_CATEGORY",
        )

        val entity = sharedUnit.toEntity()
        assertNull(entity.defaultCategory)
    }
}
