package foo.pilz.freaklog.data.substanceshare

import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionSeverity
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionTargetType
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.Bioavailability
import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import kotlinx.serialization.Serializable

/**
 * Format id written into shared files. It is the Codeberg fork's id, so files
 * can be exchanged with that app; [SHARED_SUBSTANCE_FORMATS] lists what is accepted.
 */
const val SHARED_SUBSTANCE_FORMAT = "gay.cybercrime.journal.substance"
val SHARED_SUBSTANCE_FORMATS = setOf(SHARED_SUBSTANCE_FORMAT, "foo.pilz.freaklog.substance")
const val SHARED_SUBSTANCE_VERSION = 1

@Serializable
data class SharedSubstanceEnvelope(
    val format: String,
    val version: Int,
    val substance: SharedSubstance,
)

@Serializable
data class SharedSubstance(
    val name: String,
    val units: String,
    val description: String,
    val summary: String? = null,
    val toleranceFull: String? = null,
    val toleranceHalf: String? = null,
    val toleranceZero: String? = null,
    val effectsText: String? = null,
    val generalRisks: String? = null,
    val longTermRisks: String? = null,
    val roas: List<SharedRoa> = emptyList(),
    val interactions: List<SharedInteraction> = emptyList(),
    val categories: List<String> = emptyList(),
    val crossTolerances: List<String> = emptyList(),
)

@Serializable
data class SharedRoa(
    val route: AdministrationRoute,
    val dose: SharedDose? = null,
    val duration: SharedDuration? = null,
    val bioavailability: Bioavailability? = null,
)

@Serializable
data class SharedDose(
    val units: String? = null,
    val lightMin: Double? = null,
    val commonMin: Double? = null,
    val strongMin: Double? = null,
    val heavyMin: Double? = null,
)

@Serializable
data class SharedDuration(
    val onset: DurationRange? = null,
    val comeup: DurationRange? = null,
    val peak: DurationRange? = null,
    val offset: DurationRange? = null,
    val total: DurationRange? = null,
)

@Serializable
data class SharedInteraction(
    val severity: CustomInteractionSeverity,
    val targetType: CustomInteractionTargetType,
    val targetName: String,
)
