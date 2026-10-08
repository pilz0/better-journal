package foo.pilz.freaklog.ui.tabs.settings.funny.condition

import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestions
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.DoseClass
import java.time.ZoneId

enum class InteractionFilter { DANGEROUS, UNSAFE, UNCERTAIN, ANY }

interface SubstanceInfo {
    fun categories(substanceName: String): Set<String>
    fun doseClass(
        substanceName: String,
        route: AdministrationRoute,
        dose: Double?,
        units: String?,
    ): DoseClass?
}

class EvalContext(
    val experiences: List<ExperienceWithIngestions>,
    val ingestions: List<Ingestion>,
    val substanceInfo: SubstanceInfo,
    val zone: ZoneId = ZoneId.systemDefault(),
    val interactionCount: (experienceId: Int, filter: InteractionFilter) -> Int = { _, _ -> 0 },
)
