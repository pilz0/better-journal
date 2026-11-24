package foo.pilz.freaklog.data.room.experiences

import foo.pilz.freaklog.data.room.experiences.entities.CustomCategoryAssignment
import foo.pilz.freaklog.data.room.experiences.entities.CustomCrossTolerance
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteraction
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionSeverity
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoa
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDose
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithEverything
import foo.pilz.freaklog.data.substanceshare.SharedSubstanceExpansion
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn

@Singleton
class CustomSubstanceRepository @Inject constructor(private val dao: ExperienceDao) {

  fun getWithEverythingFlow(id: Int): Flow<CustomSubstanceWithEverything?> =
      dao.getCustomSubstanceWithEverythingFlow(id).flowOn(Dispatchers.IO).conflate()

  suspend fun getWithEverything(id: Int): CustomSubstanceWithEverything? =
      dao.getCustomSubstanceWithEverything(id)

  suspend fun getWithEverythingByName(name: String): CustomSubstanceWithEverything? =
      dao.getCustomSubstanceWithEverythingByName(name)

  suspend fun getWithEverythingByNames(names: List<String>): List<CustomSubstanceWithEverything> =
      if (names.isEmpty()) emptyList() else dao.getCustomSubstancesWithEverythingByNames(names)

  suspend fun getExistingNames(names: List<String>): Set<String> =
      if (names.isEmpty()) emptySet() else dao.getExistingCustomSubstanceNames(names).toSet()

  suspend fun update(substance: CustomSubstance) = dao.update(substance)

  suspend fun delete(substance: CustomSubstance) = dao.delete(substance)

  /** False when [newName] is already taken; see [ExperienceDao.renameCustomSubstance]. */
  suspend fun renameCustom(oldName: String, newName: String): Boolean =
      dao.renameCustomSubstance(oldName, newName)

  suspend fun setAllRoaDoseUnits(substanceId: Int, units: String) =
      dao.setAllCustomRoaDoseUnits(substanceId, units)

  suspend fun upsertRoa(substanceId: Int, roa: CustomRoa) {
    roa.customSubstanceId = substanceId
    dao.insert(roa)
  }

  suspend fun upsertDose(substanceId: Int, dose: CustomRoaDose) =
      dao.upsertCustomRoaDose(substanceId, dose)

  suspend fun upsertInteraction(substanceId: Int, interaction: CustomInteraction) {
    interaction.customSubstanceId = substanceId
    dao.insert(interaction)
  }

  suspend fun deleteInteraction(interaction: CustomInteraction) = dao.delete(interaction)

  suspend fun deleteInteraction(id: Int) = dao.deleteCustomInteraction(id)

  fun getInteractionsFlow(substanceId: Int): Flow<List<CustomInteraction>> =
      dao.getCustomInteractionsFlow(substanceId).flowOn(Dispatchers.IO).conflate()

  suspend fun getInteractions(substanceId: Int): List<CustomInteraction> =
      dao.getCustomInteractions(substanceId)

  suspend fun getInteractions(substanceName: String): List<CustomInteraction> =
      dao.getCustomInteractions(substanceName)

  suspend fun getInteractions(
      substanceId: Int,
      severity: CustomInteractionSeverity,
  ): List<CustomInteraction> = dao.getCustomInteractions(substanceId, severity)

  suspend fun assignCategory(substanceId: Int, categoryName: String) {
    dao.insert(
        CustomCategoryAssignment(
            customSubstanceId = substanceId,
            categoryName = categoryName,
        )
    )
  }

  suspend fun unassignCategory(substanceId: Int, categoryName: String) =
      dao.deleteCustomCategory(substanceId, categoryName)

  fun getCategoriesFlow(substanceId: Int): Flow<List<CustomCategoryAssignment>> =
      dao.getCustomCategoriesFlow(substanceId).flowOn(Dispatchers.IO).conflate()

  suspend fun assignCrossTolerance(substanceId: Int, categoryName: String) {
    dao.insert(
        CustomCrossTolerance(
            customSubstanceId = substanceId,
            categoryName = categoryName,
        )
    )
  }

  suspend fun unassignCrossTolerance(substanceId: Int, categoryName: String) =
      dao.deleteCustomCrossTolerance(substanceId, categoryName)

  fun getCrossTolerancesFlow(substanceId: Int): Flow<List<CustomCrossTolerance>> =
      dao.getCustomCrossTolerancesFlow(substanceId).flowOn(Dispatchers.IO).conflate()

  suspend fun insertFromShared(expansion: SharedSubstanceExpansion): Int =
      dao.insertCustomSubstanceWithFullRoaInfo(
          substance = expansion.substance,
          durations = expansion.durations,
          doses = expansion.doses,
          roas = expansion.roas,
          interactions = expansion.interactions,
          categories = expansion.categories,
          crossTolerances = expansion.crossTolerances,
      )

  suspend fun replaceFromShared(
      existing: CustomSubstance,
      expansion: SharedSubstanceExpansion,
  ): Int =
      dao.replaceCustomSubstanceWithFullRoaInfo(
          existing = existing,
          substance = expansion.substance,
          durations = expansion.durations,
          doses = expansion.doses,
          roas = expansion.roas,
          interactions = expansion.interactions,
          categories = expansion.categories,
          crossTolerances = expansion.crossTolerances,
      )
}
