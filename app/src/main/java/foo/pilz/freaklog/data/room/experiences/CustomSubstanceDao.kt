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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import foo.pilz.freaklog.data.room.experiences.entities.CustomCrossTolerance
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteraction
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoa
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDose
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDuration
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithEverything
import foo.pilz.freaklog.data.substances.AdministrationRoute
import kotlinx.coroutines.flow.Flow

/** Routes, doses, durations, interactions and cross-tolerances attached to a [CustomSubstance]. */
@Dao
@Suppress("TooManyFunctions")
interface CustomSubstanceDao {

    @Transaction
    @Query("SELECT * FROM customsubstance WHERE id = :id")
    fun getWithEverythingFlow(id: Int): Flow<CustomSubstanceWithEverything?>

    @Transaction
    @Query("SELECT * FROM customsubstance WHERE id = :id")
    suspend fun getWithEverything(id: Int): CustomSubstanceWithEverything?

    @Transaction
    @Query("SELECT * FROM customsubstance WHERE name = :name LIMIT 1")
    suspend fun getWithEverythingByName(name: String): CustomSubstanceWithEverything?

    @Transaction
    @Query("SELECT * FROM customsubstance ORDER BY name")
    suspend fun getAllWithEverything(): List<CustomSubstanceWithEverything>

    @Query("SELECT name FROM customsubstance")
    suspend fun getAllNames(): List<String>

    @Insert
    suspend fun insert(substance: CustomSubstance): Long

    @Update
    suspend fun update(substance: CustomSubstance)

    @Delete
    suspend fun delete(substance: CustomSubstance)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(roa: CustomRoa)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dose: CustomRoaDose)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(duration: CustomRoaDuration)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(interaction: CustomInteraction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(crossTolerance: CustomCrossTolerance)

    @Query("DELETE FROM customroa WHERE customSubstanceId = :substanceId AND route = :route")
    suspend fun deleteRoa(substanceId: Int, route: AdministrationRoute)

    @Query("DELETE FROM customroadose WHERE customSubstanceId = :substanceId AND route = :route")
    suspend fun deleteDose(substanceId: Int, route: AdministrationRoute)

    @Query("DELETE FROM customroaduration WHERE customSubstanceId = :substanceId AND route = :route")
    suspend fun deleteDuration(substanceId: Int, route: AdministrationRoute)

    @Query("DELETE FROM custominteraction WHERE id = :id")
    suspend fun deleteInteraction(id: Int)

    @Query("DELETE FROM customcrosstolerance WHERE customSubstanceId = :substanceId")
    suspend fun deleteCrossTolerances(substanceId: Int)

    /** Replaces everything stored for one route in a single transaction. */
    @Transaction
    suspend fun replaceRoute(substanceId: Int, roa: CustomRoa, dose: CustomRoaDose?, duration: CustomRoaDuration?) {
        deleteRouteEverywhere(substanceId, roa.route)
        insert(roa.copy(customSubstanceId = substanceId))
        dose?.let { insert(it.copy(customSubstanceId = substanceId)) }
        duration?.let { insert(it.copy(customSubstanceId = substanceId)) }
    }

    @Transaction
    suspend fun deleteRouteEverywhere(substanceId: Int, route: AdministrationRoute) {
        deleteRoa(substanceId, route)
        deleteDose(substanceId, route)
        deleteDuration(substanceId, route)
    }

    @Transaction
    suspend fun replaceCrossTolerances(substanceId: Int, names: List<String>) {
        deleteCrossTolerances(substanceId)
        names.forEach { insert(CustomCrossTolerance(customSubstanceId = substanceId, categoryName = it)) }
    }

    /** Inserts a substance together with all of its details; returns the new substance id. */
    @Transaction
    suspend fun insertWithEverything(
        substance: CustomSubstance,
        roas: List<CustomRoa>,
        doses: List<CustomRoaDose>,
        durations: List<CustomRoaDuration>,
        interactions: List<CustomInteraction>,
        crossTolerances: List<CustomCrossTolerance>,
    ): Int {
        val id = insert(substance).toInt()
        roas.forEach { insert(it.copy(customSubstanceId = id)) }
        doses.forEach { insert(it.copy(customSubstanceId = id)) }
        durations.forEach { insert(it.copy(customSubstanceId = id)) }
        interactions.forEach { insert(it.copy(customSubstanceId = id)) }
        crossTolerances.forEach { insert(it.copy(customSubstanceId = id)) }
        return id
    }
}
