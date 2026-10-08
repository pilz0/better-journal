package foo.pilz.freaklog.data.room.experiences.relations

import androidx.room.Embedded
import androidx.room.Relation
import foo.pilz.freaklog.data.room.experiences.entities.CustomCategoryAssignment
import foo.pilz.freaklog.data.room.experiences.entities.CustomCrossTolerance
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteraction
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionSeverity
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionTargetType
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoa
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDose
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDuration
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.substances.classes.IngestionCategory
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

data class CustomSubstanceWithDurations(
    @Embedded val substance: CustomSubstance,

    @Relation(
        entity = CustomRoaDuration::class,
        parentColumn = "id",
        entityColumn = "customSubstanceId"
    )
    var durations: List<CustomRoaDuration> = listOf<CustomRoaDuration>(),

    @Relation(
        entity = CustomRoaDose::class,
        parentColumn = "id",
        entityColumn = "customSubstanceId"
    )
    var doses: List<CustomRoaDose> = listOf<CustomRoaDose>(),

    @Relation(
        entity = CustomRoa::class,
        parentColumn = "id",
        entityColumn = "customSubstanceId"
    )
    var roas: List<CustomRoa> = listOf<CustomRoa>(),

    @Relation(
        entity = CustomInteraction::class,
        parentColumn = "id",
        entityColumn = "customSubstanceId"
    )
    var interactions: List<CustomInteraction> = listOf<CustomInteraction>(),

    @Relation(
        entity = CustomCategoryAssignment::class,
        parentColumn = "id",
        entityColumn = "customSubstanceId"
    )
    var categories: List<CustomCategoryAssignment> = listOf<CustomCategoryAssignment>(),

    @Relation(
        entity = CustomCrossTolerance::class,
        parentColumn = "id",
        entityColumn = "customSubstanceId"
    )
    var crossTolerances: List<CustomCrossTolerance> = listOf<CustomCrossTolerance>(),
) {
    fun toSerializable(): CustomSubstanceWithDurationsSerializable {
        return CustomSubstanceWithDurationsSerializable(
            id = substance.id,
            name = substance.name,
            units = substance.units,
            description = substance.description,
            summary = substance.summary,
            toleranceFull = substance.toleranceFull,
            toleranceHalf = substance.toleranceHalf,
            toleranceZero = substance.toleranceZero,
            effectsText = substance.effectsText,
            generalRisks = substance.generalRisks,
            longTermRisks = substance.longTermRisks,
            durations = durations.map { d ->
                val matchingDose = doses.find { it.route == d.route }
                CustomRoaDurationSerializable(
                    route = d.route,
                    onset = d.onset,
                    comeup = d.comeup,
                    peak = d.peak,
                    offset = d.offset,
                    total = d.total,
                    lightDoseMin = matchingDose?.lightMin,
                    commonDoseMin = matchingDose?.commonMin,
                    strongDoseMin = matchingDose?.strongMin,
                    heavyDoseMin = matchingDose?.heavyMin,
                    bioavailability = roas.find { it.route == d.route }?.bioavailability,
                )
            },
            categories = categories.map { it.categoryName },
            crossTolerances = crossTolerances.map { it.categoryName },
            interactions = interactions.map {
                CustomInteractionSerializable(
                    severity = it.severity,
                    targetType = it.targetType,
                    targetName = it.targetName,
                )
            },
        )
    }
}

@Serializable
data class CustomSubstanceWithDurationsSerializable(
    @Transient
    val id: Int = 0,
    val name: String,
    var units: String,
    var description: String,
    var summary: String? = null,
    var toleranceFull: String? = null,
    var toleranceHalf: String? = null,
    var toleranceZero: String? = null,
    var effectsText: String? = null,
    var generalRisks: String? = null,
    var longTermRisks: String? = null,
    var durations: List<CustomRoaDurationSerializable> = listOf<CustomRoaDurationSerializable>(),
    val categories: List<String> = emptyList(),
    val crossTolerances: List<String> = emptyList(),
    val interactions: List<CustomInteractionSerializable> = emptyList(),

    // To keep compatibility with exports from before this was migrated to SubstanceCompanion
    val defaultCategory: IngestionCategory? = null
) {
    fun toEntity(): CustomSubstanceWithDurations {
        val substance = CustomSubstance(
            id = id,
            name = name,
            units = units,
            description = description,
            summary = summary,
            toleranceFull = toleranceFull,
            toleranceHalf = toleranceHalf,
            toleranceZero = toleranceZero,
            effectsText = effectsText,
            generalRisks = generalRisks,
            longTermRisks = longTermRisks,
        )
        return CustomSubstanceWithDurations(
            substance = substance,
            durations = durations.map { d ->
                CustomRoaDuration(
                    route = d.route,
                    customSubstanceId = id,
                    onset = d.onset,
                    comeup = d.comeup,
                    peak = d.peak,
                    offset = d.offset,
                    total = d.total,
                )
            },
            doses = durations.mapNotNull { d ->
                if (d.lightDoseMin == null && d.commonDoseMin == null &&
                    d.strongDoseMin == null && d.heavyDoseMin == null
                ) null
                else CustomRoaDose(
                    customSubstanceId = id,
                    route = d.route,
                    units = units,
                    lightMin = d.lightDoseMin,
                    commonMin = d.commonDoseMin,
                    strongMin = d.strongDoseMin,
                    heavyMin = d.heavyDoseMin,
                )
            },
            roas = durations.mapNotNull { d ->
                d.bioavailability?.let {
                    CustomRoa(
                        customSubstanceId = id,
                        route = d.route,
                        bioavailability = it,
                    )
                }
            },
            interactions = interactions.map {
                CustomInteraction(
                    severity = it.severity,
                    targetType = it.targetType,
                    targetName = it.targetName,
                )
            },
            categories = categories.map { CustomCategoryAssignment(categoryName = it) },
            crossTolerances = crossTolerances.map { CustomCrossTolerance(categoryName = it) },
        )
    }
}

@Serializable
data class CustomInteractionSerializable(
    val severity: CustomInteractionSeverity,
    val targetType: CustomInteractionTargetType,
    val targetName: String,
)
