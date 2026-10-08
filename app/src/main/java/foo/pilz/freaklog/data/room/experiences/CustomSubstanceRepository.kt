package foo.pilz.freaklog.data.room.experiences

import foo.pilz.freaklog.data.room.experiences.entities.CustomCategoryAssignment
import foo.pilz.freaklog.data.room.experiences.entities.CustomCrossTolerance
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteraction
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionSeverity
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoa
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDose
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDuration
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithEverything
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substanceshare.SharedSubstanceExpansion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomSubstanceRepository @Inject constructor(
    private val dao: ExperienceDao,
) {

    fun getWithEverythingFlow(id: Int): Flow<CustomSubstanceWithEverything?> =
        dao.getCustomSubstanceWithEverythingFlow(id)
            .flowOn(Dispatchers.IO)
            .conflate()

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

    suspend fun renameCustom(oldName: String, newName: String) =
        dao.renameCustomSubstance(oldName, newName)

    suspend fun setAllRoaDoseUnits(substanceId: Int, units: String) =
        dao.setAllCustomRoaDoseUnits(substanceId, units)

    suspend fun upsertRoa(substanceId: Int, roa: CustomRoa) {
        roa.customSubstanceId = substanceId
        dao.insert(roa)
    }

    suspend fun deleteRoa(roa: CustomRoa) = dao.delete(roa)

    suspend fun upsertDose(substanceId: Int, dose: CustomRoaDose) =
        dao.upsertCustomRoaDose(substanceId, dose)

    suspend fun deleteDose(dose: CustomRoaDose) = dao.delete(dose)

    suspend fun upsertDuration(substanceId: Int, duration: CustomRoaDuration) {
        duration.customSubstanceId = substanceId
        dao.insert(duration)
    }

    suspend fun deleteDuration(duration: CustomRoaDuration) = dao.delete(duration)

    suspend fun deleteRouteEverywhere(substanceId: Int, route: AdministrationRoute) {
        dao.getCustomRoa(substanceId, route)?.let { dao.delete(it) }
        dao.getCustomRoaDose(substanceId, route)?.let { dao.delete(it) }
        dao.getCustomRoaDurations(substanceId)
            .find { it.route == route }
            ?.let { dao.delete(it) }
    }

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
        severity: CustomInteractionSeverity
    ): List<CustomInteraction> =
        dao.getCustomInteractions(substanceId, severity)

    suspend fun assignCategory(substanceId: Int, categoryName: String) {
        dao.insert(
            CustomCategoryAssignment(
                customSubstanceId = substanceId,
                categoryName = categoryName
            )
        )
    }

    suspend fun unassignCategory(substanceId: Int, categoryName: String) =
        dao.deleteCustomCategory(substanceId, categoryName)

    fun getCategoriesFlow(substanceId: Int): Flow<List<CustomCategoryAssignment>> =
        dao.getCustomCategoriesFlow(substanceId).flowOn(Dispatchers.IO).conflate()

    suspend fun getCategories(substanceId: Int): List<CustomCategoryAssignment> =
        dao.getCustomCategories(substanceId)

    suspend fun assignCrossTolerance(substanceId: Int, categoryName: String) {
        dao.insert(
            CustomCrossTolerance(
                customSubstanceId = substanceId,
                categoryName = categoryName
            )
        )
    }

    suspend fun unassignCrossTolerance(substanceId: Int, categoryName: String) =
        dao.deleteCustomCrossTolerance(substanceId, categoryName)

    fun getCrossTolerancesFlow(substanceId: Int): Flow<List<CustomCrossTolerance>> =
        dao.getCustomCrossTolerancesFlow(substanceId).flowOn(Dispatchers.IO).conflate()

    suspend fun getCrossTolerances(substanceId: Int): List<CustomCrossTolerance> =
        dao.getCustomCrossTolerances(substanceId)

    suspend fun insertFromShared(expansion: SharedSubstanceExpansion): Int {
        val id = dao.insertCustomSubstanceWithFullRoaInfo(
            expansion.substance,
            durations = expansion.durations,
            doses = expansion.doses,
            roas = expansion.roas,
        )
        expansion.interactions.forEach { dao.insert(it.copy(customSubstanceId = id)) }
        expansion.categories.forEach { dao.insert(it.copy(customSubstanceId = id)) }
        expansion.crossTolerances.forEach { dao.insert(it.copy(customSubstanceId = id)) }
        return id
    }
}
