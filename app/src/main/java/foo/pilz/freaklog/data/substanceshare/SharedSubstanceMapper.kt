package foo.pilz.freaklog.data.substanceshare

import foo.pilz.freaklog.data.room.experiences.entities.CustomCrossTolerance
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteraction
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoa
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDose
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDuration
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithEverything

fun CustomSubstanceWithEverything.toShared(): SharedSubstance {
    val byRoute = (roas.map { it.route } + doses.map { it.route } + durations.map { it.route })
        .distinct()
    return SharedSubstance(
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
        roas = byRoute.map { route ->
            SharedRoa(
                route = route,
                dose = doses.firstOrNull { it.route == route }?.let {
                    SharedDose(
                        units = it.units,
                        lightMin = it.lightMin,
                        commonMin = it.commonMin,
                        strongMin = it.strongMin,
                        heavyMin = it.heavyMin,
                    )
                },
                duration = durations.firstOrNull { it.route == route }?.let {
                    SharedDuration(
                        onset = it.onset,
                        comeup = it.comeup,
                        peak = it.peak,
                        offset = it.offset,
                        total = it.total,
                    )
                },
                bioavailability = roas.firstOrNull { it.route == route }?.bioavailability,
            )
        },
        interactions = interactions.map {
            SharedInteraction(
                severity = it.severity,
                targetType = it.targetType,
                targetName = it.targetName,
            )
        },
        crossTolerances = crossTolerances.map { it.categoryName },
    )
}

data class SharedSubstanceExpansion(
    val substance: CustomSubstance,
    val roas: List<CustomRoa>,
    val doses: List<CustomRoaDose>,
    val durations: List<CustomRoaDuration>,
    val interactions: List<CustomInteraction>,
    val crossTolerances: List<CustomCrossTolerance>,
)

fun SharedSubstance.expand(targetName: String = name): SharedSubstanceExpansion {
    val sub = CustomSubstance(
        name = targetName,
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
    val roaEntities = roas.map { CustomRoa(route = it.route, bioavailability = it.bioavailability) }
    val doseEntities = roas.mapNotNull { shared ->
        shared.dose?.let {
            CustomRoaDose(
                route = shared.route,
                units = it.units,
                lightMin = it.lightMin,
                commonMin = it.commonMin,
                strongMin = it.strongMin,
                heavyMin = it.heavyMin,
            )
        }
    }
    val durationEntities = roas.mapNotNull { shared ->
        shared.duration?.let {
            CustomRoaDuration(
                route = shared.route,
                onset = it.onset,
                comeup = it.comeup,
                peak = it.peak,
                offset = it.offset,
                total = it.total,
            )
        }
    }
    val interactionEntities = interactions.map {
        CustomInteraction(
            severity = it.severity,
            targetType = it.targetType,
            targetName = it.targetName
        )
    }
    val crossToleranceEntities = crossTolerances.map { CustomCrossTolerance(categoryName = it) }
    return SharedSubstanceExpansion(
        substance = sub,
        roas = roaEntities,
        doses = doseEntities,
        durations = durationEntities,
        interactions = interactionEntities,
        crossTolerances = crossToleranceEntities,
    )
}
