package foo.pilz.freaklog.data.room.experiences.relations

import androidx.room.Embedded
import androidx.room.Relation
import foo.pilz.freaklog.data.room.experiences.entities.CustomCategoryAssignment
import foo.pilz.freaklog.data.room.experiences.entities.CustomCrossTolerance
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteraction
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoa
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDose
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDuration
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.substances.AdministrationRoute

data class CustomSubstanceWithEverything(
    @Embedded val substance: CustomSubstance,

    @Relation(
        entity = CustomRoa::class,
        parentColumn = "id",
        entityColumn = "customSubstanceId",
    )
    val roas: List<CustomRoa> = emptyList(),

    @Relation(
        entity = CustomRoaDose::class,
        parentColumn = "id",
        entityColumn = "customSubstanceId",
    )
    val doses: List<CustomRoaDose> = emptyList(),

    @Relation(
        entity = CustomRoaDuration::class,
        parentColumn = "id",
        entityColumn = "customSubstanceId",
    )
    val durations: List<CustomRoaDuration> = emptyList(),

    @Relation(
        entity = CustomInteraction::class,
        parentColumn = "id",
        entityColumn = "customSubstanceId",
    )
    val interactions: List<CustomInteraction> = emptyList(),

    @Relation(
        entity = CustomCategoryAssignment::class,
        parentColumn = "id",
        entityColumn = "customSubstanceId",
    )
    val categories: List<CustomCategoryAssignment> = emptyList(),

    @Relation(
        entity = CustomCrossTolerance::class,
        parentColumn = "id",
        entityColumn = "customSubstanceId",
    )
    val crossTolerances: List<CustomCrossTolerance> = emptyList(),
) {
    val roaInfos: List<CustomRoaInfo>
        get() {
            val routes =
                (roas.map { it.route } + doses.map { it.route } + durations.map { it.route }).distinct()
            return routes.map { route ->
                CustomRoaInfo(
                    route = route,
                    roa = roas.find { it.route == route },
                    dose = doses.find { it.route == route },
                    duration = durations.find { it.route == route },
                )
            }.sortedBy { it.route.ordinal }
        }

    fun interactionsBySeverity(): Map<foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionSeverity, List<CustomInteraction>> =
        interactions.groupBy { it.severity }
}

fun List<CustomRoaInfo>.byRoute(route: AdministrationRoute): CustomRoaInfo? =
    find { it.route == route }
