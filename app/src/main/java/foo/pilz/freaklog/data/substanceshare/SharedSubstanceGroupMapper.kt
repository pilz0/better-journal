package foo.pilz.freaklog.data.substanceshare

import foo.pilz.freaklog.data.room.experiences.entities.CustomUnit
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithEverything
import foo.pilz.freaklog.data.room.experiences.relations.SubstanceGroupWithItems
import foo.pilz.freaklog.data.substances.classes.IngestionCategory

fun SubstanceGroupWithItems.toShared(
    referencedCustomSubstances: List<CustomSubstanceWithEverything>,
    referencedCustomUnits: List<CustomUnit>,
): SharedGroup {
    val unitById = referencedCustomUnits.associateBy { it.id }
    val sharedItems = sortedItems.map { item ->
        val ref = item.customUnitId?.let { unitById[it] }?.let {
            SharedCustomUnitRef(
                substanceName = it.substanceName,
                name = it.name,
                administrationRoute = it.administrationRoute,
            )
        }
        SharedGroupItem(
            sortOrder = item.sortOrder,
            substanceName = item.substanceName,
            isCustomSubstance = item.isCustomSubstance,
            administrationRoute = item.administrationRoute,
            dose = item.dose,
            units = item.units,
            isEstimate = item.isEstimate,
            estimatedDoseStandardDeviation = item.estimatedDoseStandardDeviation,
            customUnitRef = ref,
        )
    }
    return SharedGroup(
        name = group.name,
        items = sharedItems,
        customSubstances = referencedCustomSubstances.map { it.toShared() },
        customUnits = referencedCustomUnits.map { it.toSharedCustomUnit() },
    )
}

fun CustomUnit.toSharedCustomUnit() = SharedCustomUnit(
    substanceName = substanceName,
    name = name,
    administrationRoute = administrationRoute,
    dose = dose,
    estimatedDoseStandardDeviation = estimatedDoseStandardDeviation,
    isEstimate = isEstimate,
    unit = unit,
    unitPlural = unitPlural,
    originalUnit = originalUnit,
    note = note,
    defaultCategory = defaultCategory?.name,
)

fun SharedCustomUnit.toEntity(): CustomUnit = CustomUnit(
    substanceName = substanceName,
    name = name,
    administrationRoute = administrationRoute,
    dose = dose,
    estimatedDoseStandardDeviation = estimatedDoseStandardDeviation,
    isEstimate = isEstimate,
    isArchived = false,
    unit = unit,
    unitPlural = unitPlural,
    originalUnit = originalUnit,
    note = note,
    defaultCategory = defaultCategory?.let {
        runCatching { IngestionCategory.valueOf(it) }.getOrNull()
    },
)
