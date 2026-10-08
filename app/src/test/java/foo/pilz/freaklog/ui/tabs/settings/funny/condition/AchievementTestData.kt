package foo.pilz.freaklog.ui.tabs.settings.funny.condition

import foo.pilz.freaklog.data.room.experiences.entities.Experience
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.entities.Location
import foo.pilz.freaklog.data.room.experiences.entities.StomachFullness
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestions
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.IngestionCategory
import foo.pilz.freaklog.data.substances.classes.roa.DoseClass
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val DEFAULT_TIME: Instant = LocalDate.of(2024, 6, 1).atTime(12, 0).toInstant(ZoneOffset.UTC)

fun ingestion(
    substanceName: String = "LSD",
    route: AdministrationRoute = AdministrationRoute.ORAL,
    dose: Double? = null,
    units: String? = null,
    notes: String? = null,
    consumerName: String? = null,
    stomachFullness: StomachFullness? = null,
    isEstimate: Boolean = false,
    customUnitId: Int? = null,
    experienceId: Int = 1,
    time: Instant = DEFAULT_TIME,
    category: IngestionCategory? = null,
): Ingestion = Ingestion(
    id = 0,
    substanceName = substanceName,
    time = time,
    endTime = null,
    creationDate = null,
    administrationRoute = route,
    dose = dose,
    isDoseAnEstimate = isEstimate,
    estimatedDoseStandardDeviation = null,
    units = units,
    experienceId = experienceId,
    notes = notes,
    stomachFullness = stomachFullness,
    consumerName = consumerName,
    customUnitId = customUnitId,
    category = category,
)

fun experience(
    id: Int = 1,
    title: String = "Experience",
    isFavorite: Boolean = false,
    location: Location? = null,
    sortDate: Instant = DEFAULT_TIME,
): Experience = Experience(
    id = id,
    title = title,
    text = "",
    sortDate = sortDate,
    isFavorite = isFavorite,
    location = location,
)

fun expWith(experience: Experience, ingestions: List<Ingestion>): ExperienceWithIngestions =
    ExperienceWithIngestions(experience, ingestions)

class FakeSubstanceInfo(
    private val categoriesByName: Map<String, Set<String>> = emptyMap(),
    private val doses: Map<String, DoseClass> = emptyMap(),
) : SubstanceInfo {
    override fun categories(substanceName: String): Set<String> =
        categoriesByName[substanceName] ?: emptySet()

    override fun doseClass(
        substanceName: String,
        route: AdministrationRoute,
        dose: Double?,
        units: String?,
    ): DoseClass? = doses[substanceName]
}

fun evalContext(
    experiences: List<ExperienceWithIngestions> = emptyList(),
    ingestions: List<Ingestion> = experiences.flatMap { it.ingestions },
    substanceInfo: SubstanceInfo = FakeSubstanceInfo(),
    interactionCounts: Map<Pair<Int, InteractionFilter>, Int> = emptyMap(),
): EvalContext = EvalContext(
    experiences = experiences,
    ingestions = ingestions,
    substanceInfo = substanceInfo,
    zone = ZoneOffset.UTC,
    interactionCount = { id, filter -> interactionCounts[id to filter] ?: 0 },
)

fun instantAtHour(hour: Int): Instant =
    LocalDate.of(2024, 6, 1).atTime(hour, 0).toInstant(ZoneOffset.UTC)

fun instantAtDay(dayOffset: Int): Instant =
    LocalDate.of(2024, 6, 1).plusDays(dayOffset.toLong()).atTime(12, 0).toInstant(ZoneOffset.UTC)
