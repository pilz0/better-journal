package foo.pilz.freaklog.data.freakquery

import com.ndm4.freakquery.FreakQuery
import com.ndm4.freakquery.FreakQueryConfig
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.Experience
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Singleton
class FreakQueryRepository
@Inject
constructor(private val experienceRepository: ExperienceRepository) {
  // Mapping every ingestion and evaluating the query is CPU work; keep it off the main thread.
  suspend fun getLogs(): List<Map<String, Any?>> =
      experienceRepository.getSortedExperiencesWithIngestionsFlow().first().let { experiences ->
        withContext(Dispatchers.Default) { experiences.toFreakQueryRows() }
      }

  suspend fun query(tag: String, config: FreakQueryConfig = FreakQueryConfig()): String {
    val logs = getLogs()
    return withContext(Dispatchers.Default) { FreakQuery.query(tag, logs, config) }
  }

  suspend fun render(template: String, config: FreakQueryConfig = FreakQueryConfig()): String {
    val logs = getLogs()
    return withContext(Dispatchers.Default) { FreakQuery.render(template, logs, config) }
  }
}

internal fun List<ExperienceWithIngestions>.toFreakQueryRows(): List<Map<String, Any?>> =
    flatMap { row ->
          row.ingestions.map { ingestion ->
            ingestion.toFreakQueryRow(row.experience)
          }
        }
        .sortedBy { it["time"] as Long }

private fun Ingestion.toFreakQueryRow(experience: Experience): Map<String, Any?> =
    linkedMapOf(
        "id" to id,
        "experienceId" to experienceId,
        "experienceTitle" to experience.title,
        "experienceNotes" to experience.text,
        "experienceFavorite" to experience.isFavorite,
        "time" to time.toEpochMilli(),
        "creationDate" to creationDate?.toEpochMilli(),
        "endTime" to endTime?.toEpochMilli(),
        "substance" to substanceName,
        "substanceName" to substanceName,
        "route" to administrationRoute.displayText,
        "administrationRoute" to administrationRoute.displayText,
        "dose" to dose,
        "unit" to units,
        "units" to units,
        "site" to administrationSite,
        "administrationSite" to administrationSite,
        "notes" to notes,
        "estimated" to isDoseAnEstimate,
        "isDoseAnEstimate" to isDoseAnEstimate,
        "estimatedDoseStandardDeviation" to estimatedDoseStandardDeviation,
        "consumerName" to consumerName,
        "stomachFullness" to stomachFullness?.name,
        "customUnitId" to customUnitId,
    )
