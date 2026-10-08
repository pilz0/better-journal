/*
 * Copyright (c) 2026. Freaklog.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Freaklog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Freaklog.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.data.room.experiences

import foo.pilz.freaklog.data.room.experiences.entities.CustomInteraction
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
    private val dao: CustomSubstanceDao,
) {
    fun getWithEverythingFlow(id: Int): Flow<CustomSubstanceWithEverything?> =
        dao.getWithEverythingFlow(id).flowOn(Dispatchers.IO).conflate()

    suspend fun getWithEverything(id: Int): CustomSubstanceWithEverything? = dao.getWithEverything(id)

    suspend fun getWithEverythingByName(name: String): CustomSubstanceWithEverything? =
        dao.getWithEverythingByName(name)

    suspend fun getAllWithEverything(): List<CustomSubstanceWithEverything> = dao.getAllWithEverything()

    suspend fun getAllNames(): Set<String> = dao.getAllNames().toSet()

    suspend fun update(substance: CustomSubstance) = dao.update(substance)

    suspend fun delete(substance: CustomSubstance) = dao.delete(substance)

    suspend fun replaceRoute(substanceId: Int, roa: CustomRoa, dose: CustomRoaDose?, duration: CustomRoaDuration?) =
        dao.replaceRoute(substanceId, roa, dose, duration)

    suspend fun deleteRoute(substanceId: Int, route: AdministrationRoute) =
        dao.deleteRouteEverywhere(substanceId, route)

    suspend fun upsertInteraction(substanceId: Int, interaction: CustomInteraction) =
        dao.insert(interaction.copy(customSubstanceId = substanceId))

    suspend fun deleteInteraction(id: Int) = dao.deleteInteraction(id)

    suspend fun replaceCrossTolerances(substanceId: Int, names: List<String>) =
        dao.replaceCrossTolerances(substanceId, names)

    suspend fun insertFromShared(expansion: SharedSubstanceExpansion): Int =
        dao.insertWithEverything(
            substance = expansion.substance,
            roas = expansion.roas,
            doses = expansion.doses,
            durations = expansion.durations,
            interactions = expansion.interactions,
            crossTolerances = expansion.crossTolerances,
        )
}
