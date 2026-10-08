package foo.pilz.freaklog.data.substanceshare

import foo.pilz.freaklog.data.substances.AdministrationRoute
import kotlinx.serialization.Serializable

const val SHARED_SUBSTANCE_GROUP_FORMAT = "gay.cybercrime.journal.substance-group"
const val SHARED_SUBSTANCE_GROUP_VERSION = 1

@Serializable
data class SharedGroupEnvelope(
    val format: String,
    val version: Int,
    val group: SharedGroup,
)

@Serializable
data class SharedGroup(
    val name: String,
    val items: List<SharedGroupItem> = emptyList(),
    val customSubstances: List<SharedSubstance> = emptyList(),
    val customUnits: List<SharedCustomUnit> = emptyList(),
)

@Serializable
data class SharedGroupItem(
    val sortOrder: Int,
    val substanceName: String,
    val isCustomSubstance: Boolean,
    val administrationRoute: AdministrationRoute,
    val dose: Double? = null,
    val units: String? = null,
    val isEstimate: Boolean = false,
    val estimatedDoseStandardDeviation: Double? = null,
    val customUnitRef: SharedCustomUnitRef? = null,
)

@Serializable
data class SharedCustomUnitRef(
    val substanceName: String,
    val name: String,
    val administrationRoute: AdministrationRoute,
)

@Serializable
data class SharedCustomUnit(
    val substanceName: String,
    val name: String,
    val administrationRoute: AdministrationRoute,
    val dose: Double? = null,
    val estimatedDoseStandardDeviation: Double? = null,
    val isEstimate: Boolean = false,
    val unit: String,
    val unitPlural: String? = null,
    val originalUnit: String,
    val note: String,
    val defaultCategory: String? = null,
)
