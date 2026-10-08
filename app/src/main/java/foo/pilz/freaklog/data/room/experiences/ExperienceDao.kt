/*
 * Copyright (c) 2022-2023. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.data.room.experiences

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import foo.pilz.freaklog.data.export.JournalExport
import foo.pilz.freaklog.data.room.experiences.entities.CustomCategoryAssignment
import foo.pilz.freaklog.data.room.experiences.entities.CustomCrossTolerance
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteraction
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionSeverity
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoa
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDose
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDuration
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.experiences.entities.CustomUnit
import foo.pilz.freaklog.data.room.experiences.entities.Experience
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.entities.IntakeLimit
import foo.pilz.freaklog.data.room.experiences.entities.Location
import foo.pilz.freaklog.data.room.experiences.entities.ShulginRating
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroup
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroupItem
import foo.pilz.freaklog.data.room.experiences.entities.TimedNote
import foo.pilz.freaklog.data.room.experiences.relations.CustomInteractionCount
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithDurations
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithEverything
import foo.pilz.freaklog.data.room.experiences.relations.CustomUnitWithIngestions
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestions
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsAndCompanions
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsCompanionsAndRatings
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsTimedNotesAndRatings
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanion
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithExperienceAndCustomUnit
import foo.pilz.freaklog.data.room.experiences.relations.SubstanceGroupWithItems
import foo.pilz.freaklog.data.substances.AdministrationRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.time.Instant
import kotlin.enums.enumEntries

/**
 * Escapes the wildcard characters in a parameter being used with a `LIKE ... ESCAPE '\'` clause,
 * so that user-supplied input containing `%`, `_` or `\` is matched literally.
 */
internal fun escapeLikeParameter(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

@Dao
interface ExperienceDao {

    @Query("SELECT * FROM experience ORDER BY creationDate DESC")
    fun getSortedExperiencesFlow(): Flow<List<Experience>>

    @Query("SELECT * FROM ingestion ORDER BY time DESC")
    fun getIngestionsSortedDescendingFlow(): Flow<List<Ingestion>>

    @Query(
        "SELECT * FROM ingestion as i" +
                " INNER JOIN (SELECT id, MAX(time) AS time FROM ingestion WHERE time > :instant GROUP BY substanceName) as sub" +
                " ON i.id = sub.id AND i.time = sub.time" +
                " ORDER BY time DESC"
    )
    suspend fun getLatestIngestionOfEverySubstanceSinceDate(instant: Instant): List<Ingestion>

    @Transaction
    @Query("SELECT * FROM experience ORDER BY sortDate")
    suspend fun getAllExperiencesWithIngestionsTimedNotesAndRatingsSorted(): List<ExperienceWithIngestionsTimedNotesAndRatings>

    @Transaction
    @Query("SELECT * FROM experience ORDER BY sortDate DESC")
    fun getAllExperiencesWithIngestionsTimedNotesAndRatingsFlow(): Flow<List<ExperienceWithIngestionsTimedNotesAndRatings>>

    @Query("SELECT * FROM customunit ORDER BY creationDate")
    suspend fun getAllCustomUnitsSorted(): List<CustomUnit>

    @Query("SELECT * FROM customsubstance")
    suspend fun getAllCustomSubstances(): List<CustomSubstance>

    @Query("SELECT * FROM substancecompanion")
    suspend fun getAllSubstanceCompanions(): List<SubstanceCompanion>

    @Transaction
    @Query("SELECT * FROM experience WHERE sortDate > :fromInstant AND sortDate < :toInstant ORDER BY sortDate DESC")
    suspend fun getSortedExperiencesWithIngestionsWithSortDateBetween(fromInstant: Instant, toInstant: Instant): List<ExperienceWithIngestions>

    @Query("SELECT substanceName FROM ingestion ORDER BY time DESC LIMIT :limit")
    fun getSortedLastUsedSubstanceNamesFlow(limit: Int): Flow<List<String>>

    @Transaction
    @Query("SELECT * FROM experience ORDER BY sortDate DESC")
    fun getSortedExperiencesWithIngestionsAndCompanionsFlow(): Flow<List<ExperienceWithIngestionsAndCompanions>>

    @Transaction
    @Query("SELECT * FROM experience ORDER BY sortDate DESC")
    fun getSortedExperienceWithIngestionsCompanionsAndRatingsFlow(): Flow<List<ExperienceWithIngestionsCompanionsAndRatings>>

    @Transaction
    @Query("SELECT * FROM experience ORDER BY sortDate DESC LIMIT :limit")
    fun getSortedExperiencesWithIngestionsAndCompanionsFlow(limit: Int): Flow<List<ExperienceWithIngestionsAndCompanions>>

    @Transaction
    @Query("SELECT * FROM experience ORDER BY sortDate DESC")
    fun getSortedExperiencesWithIngestionsFlow(): Flow<List<ExperienceWithIngestions>>

    @Transaction
    @Query("SELECT * FROM experience ORDER BY sortDate DESC")
    fun getSortedExperiencesWithIngestionsAndCustomUnitsFlow(): Flow<List<ExperienceWithIngestionsAndCompanions>>

    @Transaction
    @Query("SELECT * FROM ingestion ORDER BY time DESC")
    fun getSortedIngestionsWithSubstanceCompanionsFlow(): Flow<List<IngestionWithCompanionAndCustomUnit>>

    @Transaction
    @Query("SELECT * FROM ingestion ORDER BY creationDate DESC LIMIT :limit")
    fun getSortedIngestionsWithSubstanceCompanionsFlow(limit: Int): Flow<List<IngestionWithCompanionAndCustomUnit>>

    @Query("SELECT * FROM ingestion ORDER BY time DESC LIMIT :limit")
    fun getSortedIngestions(limit: Int): Flow<List<Ingestion>>

    @Query("SELECT * FROM ingestion WHERE webhookMessageId IS NOT NULL")
    suspend fun getIngestionsWithLegacyWebhookMessageId(): List<Ingestion>

    @Query("SELECT * FROM ingestion ORDER BY time DESC")
    fun getSortedIngestionsFlow(): Flow<List<Ingestion>>

    @Query("SELECT * FROM ingestion WHERE substanceName = :substanceName ORDER BY time DESC LIMIT :limit")
    fun getSortedIngestionsFlow(substanceName: String, limit: Int): Flow<List<Ingestion>>

    @Query("SELECT * FROM customsubstance")
    fun getCustomSubstancesFlow(): Flow<List<CustomSubstance>>

    @Query("SELECT * FROM customsubstance WHERE id = :id")
    fun getCustomSubstanceFlow(id: Int): Flow<CustomSubstance?>

    @Query("SELECT * FROM customsubstance WHERE name = :name")
    suspend fun getCustomSubstance(name: String): CustomSubstance?

    @Query("SELECT * FROM ingestion WHERE substanceName = :substanceName ORDER BY time DESC")
    fun getSortedIngestionsFlow(substanceName: String): Flow<List<Ingestion>>

    @Transaction
    @Query("SELECT * FROM ingestion WHERE substanceName = :substanceName ORDER BY time DESC")
    fun getSortedIngestionsWithExperienceAndCustomUnitFlow(substanceName: String): Flow<List<IngestionWithExperienceAndCustomUnit>>

    @Query("SELECT * FROM experience WHERE id =:id")
    suspend fun getExperience(id: Int): Experience?

    @Query(
        "SELECT * FROM experience" +
                " WHERE title LIKE '%' || :escapedQuery || '%' ESCAPE '\\'" +
                " OR text LIKE '%' || :escapedQuery || '%' ESCAPE '\\'" +
                " ORDER BY sortDate DESC LIMIT :limit"
    )
    suspend fun searchExperiencesEscaped(escapedQuery: String, limit: Int): List<Experience>

    suspend fun searchExperiences(query: String, limit: Int): List<Experience> =
        searchExperiencesEscaped(escapeLikeParameter(query), limit)

    @Query(
        "SELECT DISTINCT e.* FROM experience e" +
                " INNER JOIN ingestion i ON i.experienceId = e.id" +
                " WHERE i.substanceName LIKE '%' || :escapedSubstanceName || '%' ESCAPE '\\'" +
                " ORDER BY e.sortDate DESC LIMIT :limit"
    )
    suspend fun searchExperiencesBySubstanceEscaped(
        escapedSubstanceName: String,
        limit: Int
    ): List<Experience>

    suspend fun searchExperiencesBySubstance(substanceName: String, limit: Int): List<Experience> =
        searchExperiencesBySubstanceEscaped(escapeLikeParameter(substanceName), limit)

    @Transaction
    @Query("SELECT * FROM experience ORDER BY sortDate DESC LIMIT :limit")
    suspend fun getRecentExperiencesWithIngestionsTimedNotesAndRatingsSorted(
        limit: Int
    ): List<ExperienceWithIngestionsTimedNotesAndRatings>

    @Query("SELECT * FROM ingestion WHERE time > :fromInstant ORDER BY time DESC LIMIT :limit")
    suspend fun getIngestionsSince(fromInstant: Instant, limit: Int): List<Ingestion>

    @Query(
        "SELECT * FROM ingestion" +
                " WHERE time > :fromInstant" +
                " AND substanceName LIKE '%' || :escapedSubstance || '%' ESCAPE '\\'" +
                " ORDER BY time DESC LIMIT :limit"
    )
    suspend fun getIngestionsSinceFilteredEscaped(
        fromInstant: Instant,
        escapedSubstance: String,
        limit: Int
    ): List<Ingestion>

    suspend fun getIngestionsSinceFiltered(
        fromInstant: Instant,
        substance: String,
        limit: Int
    ): List<Ingestion> = getIngestionsSinceFilteredEscaped(
        fromInstant,
        escapeLikeParameter(substance),
        limit
    )

    @Transaction
    @Query("SELECT * FROM experience WHERE id =:id")
    suspend fun getExperienceWithIngestionsCompanionsAndRatings(id: Int): ExperienceWithIngestionsCompanionsAndRatings?

    @Transaction
    @Query("SELECT * FROM ingestion WHERE experienceId =:experienceId")
    suspend fun getIngestionsWithCompanions(experienceId: Int): List<IngestionWithCompanionAndCustomUnit>

    @Query("SELECT * FROM shulginrating WHERE id =:id")
    suspend fun getRating(id: Int): ShulginRating?

    @Query("SELECT * FROM timednote WHERE id =:id")
    suspend fun getTimedNote(id: Int): TimedNote?

    @Query("SELECT * FROM customunit WHERE id =:id")
    suspend fun getCustomUnit(id: Int): CustomUnit?

    @Transaction
    @Query("SELECT * FROM customunit WHERE id =:id")
    suspend fun getCustomUnitWithIngestions(id: Int): CustomUnitWithIngestions?

    @Query("SELECT * FROM experience WHERE id =:id")
    fun getExperienceFlow(id: Int): Flow<Experience?>

    @Transaction
    @Query("SELECT * FROM ingestion WHERE id =:id")
    fun getIngestionWithExperienceFlow(id: Int): Flow<IngestionWithExperienceAndCustomUnit?>

    @Transaction
    @Query("SELECT * FROM ingestion WHERE time > :fromInstant AND time < :toInstant")
    fun getIngestionWithExperiencesFlow(
        fromInstant: Instant,
        toInstant: Instant
    ): Flow<List<IngestionWithExperienceAndCustomUnit>>

    @Transaction
    @Query("SELECT * FROM ingestion WHERE time > :fromInstant AND time < :toInstant")
    suspend fun getIngestionsWithCompanions(
        fromInstant: Instant,
        toInstant: Instant
    ): List<IngestionWithCompanion>

    @Transaction
    @Query("SELECT * FROM ingestion WHERE id =:id")
    fun getIngestionFlow(id: Int): Flow<IngestionWithCompanionAndCustomUnit?>

    @Transaction
    @Query("SELECT * FROM ingestion WHERE id =:id")
    fun getIngestionWithCompanionFlow(id: Int): Flow<IngestionWithCompanionAndCustomUnit?>

    @Transaction
    @Query("UPDATE ingestion SET units = 'mg', dose = dose * 1000 WHERE substanceName = 'Benzydamine' AND units = 'g'")
    suspend fun migrateBenzydamine()


    @Transaction
    suspend fun migrateCannabisAndMushroomUnits() {
        migrateCannabisIngestionUnits()
        migrateMushroomsIngestionUnits()
        migrateCannabisCustomUnits()
        migrateMushroomsCustomUnits()
    }

    @Query("UPDATE ingestion SET units = 'mg THC' WHERE substanceName = 'Cannabis' AND units = 'mg'")
    suspend fun migrateCannabisIngestionUnits()

    @Query("UPDATE ingestion SET units = 'mg Psilocybin' WHERE substanceName = 'Psilocybin mushrooms' AND units = 'mg'")
    suspend fun migrateMushroomsIngestionUnits()

    @Query("UPDATE customunit SET originalUnit = 'mg THC' WHERE substanceName = 'Cannabis' AND originalUnit = 'mg'")
    suspend fun migrateCannabisCustomUnits()

    @Query("UPDATE customunit SET originalUnit = 'mg Psilocybin' WHERE substanceName = 'Psilocybin mushrooms' AND originalUnit = 'mg'")
    suspend fun migrateMushroomsCustomUnits()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(experience: Experience): Long

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(experience: Experience)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(ingestion: Ingestion)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(rating: ShulginRating)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(customUnit: CustomUnit)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(timedNote: TimedNote)

    @Delete
    suspend fun delete(experience: Experience)

    @Delete
    suspend fun delete(rating: ShulginRating)

    @Delete
    suspend fun delete(timedNote: TimedNote)

    @Transaction
    suspend fun deleteExperienceWithIngestions(experienceWithIngestions: ExperienceWithIngestions) {
        delete(experience = experienceWithIngestions.experience)
        experienceWithIngestions.ingestions.forEach {
            delete(it)
        }
    }

    @Transaction
    @Query("DELETE FROM substancecompanion WHERE substanceName NOT IN (SELECT substanceName FROM ingestion)")
    suspend fun deleteUnusedSubstanceCompanions()

    @Delete
    suspend fun delete(ingestion: Ingestion)

    @Delete
    suspend fun delete(customUnit: CustomUnit)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: foo.pilz.freaklog.data.room.experiences.entities.BloodPressureReading): Long

    @Update
    suspend fun update(reading: foo.pilz.freaklog.data.room.experiences.entities.BloodPressureReading)

    @Delete
    suspend fun delete(reading: foo.pilz.freaklog.data.room.experiences.entities.BloodPressureReading)

    @Query("SELECT * FROM bloodpressurereading WHERE experienceId = :experienceId ORDER BY time")
    fun getBloodPressureReadingsFlow(experienceId: Int): Flow<List<foo.pilz.freaklog.data.room.experiences.entities.BloodPressureReading>>

    @Query("SELECT * FROM bloodpressurereading ORDER BY time")
    suspend fun getAllBloodPressureReadings(): List<foo.pilz.freaklog.data.room.experiences.entities.BloodPressureReading>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(intakeLimit: IntakeLimit): Long

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(intakeLimit: IntakeLimit)

    @Delete
    suspend fun delete(intakeLimit: IntakeLimit)

    @Query("SELECT * FROM intakelimit ORDER BY creationDate DESC")
    fun getIntakeLimitsFlow(): Flow<List<IntakeLimit>>

    @Query("SELECT * FROM intakelimit WHERE id = :id")
    suspend fun getIntakeLimit(id: Int): IntakeLimit?

    @Query("SELECT * FROM intakelimit WHERE substanceName = :substanceName")
    suspend fun getIntakeLimitsForSubstance(substanceName: String): List<IntakeLimit>

    @Query("SELECT * FROM intakelimit ORDER BY creationDate")
    suspend fun getIntakeLimits(): List<IntakeLimit>

    @Query("DELETE FROM intakelimit")
    suspend fun deleteAllIntakeLimits()

    @Transaction
    @Query(
        "SELECT * FROM ingestion WHERE substanceName = :substanceName AND time > :since" +
            " AND consumerName IS NULL ORDER BY time DESC"
    )
    suspend fun getIngestionsWithCustomUnitsForSubstanceSince(
        substanceName: String,
        since: Instant
    ): List<IngestionWithCompanionAndCustomUnit>

    @Transaction
    suspend fun deleteEverything() {
        deleteAllIngestions()
        deleteAllTimedNotes()
        deleteAllExperiences()
        deleteAllSubstanceCompanions()
        deleteAllCustomSubstances()
        deleteAllRatings()
        deleteAllCustomUnits()
        deleteAllIntakeLimits()
        deleteAllSubstanceGroups()
    }

    @Transaction
    suspend fun deleteEverythingOfExperience(experienceId: Int) {
        deleteIngestions(experienceId)
        deleteRatings(experienceId)
        deleteExperience(experienceId)
    }


    @Transaction
    @Query("DELETE FROM ingestion")
    suspend fun deleteAllIngestions()

    @Transaction
    @Query("DELETE FROM timedNote")
    suspend fun deleteAllTimedNotes()

    @Transaction
    @Query("DELETE FROM ingestion WHERE experienceId = :experienceId")
    suspend fun deleteIngestions(experienceId: Int)

    @Transaction
    @Query("DELETE FROM shulginrating WHERE experienceId = :experienceId")
    suspend fun deleteRatings(experienceId: Int)

    @Transaction
    @Query("DELETE FROM experience WHERE id = :experienceId")
    suspend fun deleteExperience(experienceId: Int)

    @Transaction
    @Query("DELETE FROM shulginrating")
    suspend fun deleteAllRatings()

    @Transaction
    @Query("DELETE FROM customunit")
    suspend fun deleteAllCustomUnits()

    @Transaction
    @Query("DELETE FROM experience")
    suspend fun deleteAllExperiences()

    @Transaction
    @Query("DELETE FROM substancecompanion")
    suspend fun deleteAllSubstanceCompanions()

    @Transaction
    @Query("DELETE FROM customsubstance")
    suspend fun deleteAllCustomSubstances()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ingestion: Ingestion): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rating: ShulginRating)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customUnit: CustomUnit): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(timedNote: TimedNote)

    @Transaction
    suspend fun insertIngestionExperienceAndCompanion(
        ingestion: Ingestion,
        experience: Experience,
        substanceCompanion: SubstanceCompanion
    ): Long {
        val ingestionId = insert(ingestion)
        insert(experience)
        insert(substanceCompanion)
        return ingestionId
    }

    @Transaction
    suspend fun insertEverything(
        journalExport: JournalExport
    ) {
        journalExport.experiences.forEachIndexed { indexExperience, experienceSerializable ->
            val experienceID = indexExperience + 1
            val newExperience = Experience(
                id = experienceID,
                title = experienceSerializable.title,
                text = experienceSerializable.text,
                creationDate = experienceSerializable.creationDate,
                sortDate = experienceSerializable.sortDate,
                isFavorite = experienceSerializable.isFavorite,
                location = if (experienceSerializable.location != null) {
                    Location(
                        name = experienceSerializable.location.name,
                        longitude = experienceSerializable.location.longitude,
                        latitude = experienceSerializable.location.latitude
                    )
                } else {
                    null
                }
            )
            insert(newExperience)
            experienceSerializable.ingestions.forEach { ingestionSerializable ->
                val newIngestion = Ingestion(
                    substanceName = ingestionSerializable.substanceName,
                    time = ingestionSerializable.time,
                    endTime = ingestionSerializable.endTime,
                    creationDate = ingestionSerializable.creationDate,
                    administrationRoute = ingestionSerializable.administrationRoute,
                    dose = ingestionSerializable.dose,
                    isDoseAnEstimate = ingestionSerializable.isDoseAnEstimate,
                    estimatedDoseStandardDeviation = ingestionSerializable.estimatedDoseStandardDeviation,
                    units = ingestionSerializable.units,
                    experienceId = experienceID,
                    notes = ingestionSerializable.notes,
                    stomachFullness = ingestionSerializable.stomachFullness,
                    consumerName = ingestionSerializable.consumerName,
                    customUnitId = ingestionSerializable.customUnitId,
                    saltForm = ingestionSerializable.saltForm,
                    category = ingestionSerializable.category
                )
                insert(newIngestion)
            }
            experienceSerializable.timedNotes.forEach { timedNoteSerializable ->
                val newTimedNote = TimedNote(
                    time = timedNoteSerializable.time,
                    creationDate = timedNoteSerializable.creationDate,
                    experienceId = experienceID,
                    isPartOfTimeline = timedNoteSerializable.isPartOfTimeline,
                    color = timedNoteSerializable.color,
                    note = timedNoteSerializable.note
                )
                insert(newTimedNote)
            }
            experienceSerializable.ratings.forEach { ratingSerializable ->
                val newRating = ShulginRating(
                    time = ratingSerializable.time,
                    creationDate = ratingSerializable.creationDate,
                    option = ratingSerializable.option,
                    experienceId = experienceID
                )
                insert(newRating)
            }
            experienceSerializable.bloodPressure.forEach {
                insert(
                    foo.pilz.freaklog.data.room.experiences.entities.BloodPressureReading(
                        experienceId = experienceID,
                        time = it.time,
                        systolic = it.systolic,
                        diastolic = it.diastolic,
                        pulse = it.pulse,
                    )
                )
            }
        }
        journalExport.substanceCompanions.forEach { insert(it) }
        journalExport.customSubstances.forEach { insert(it) }
        journalExport.customUnits.forEach {
            insert(
                CustomUnit(
                    id = it.id,
                    substanceName = it.substanceName,
                    name = it.name,
                    creationDate = it.creationDate,
                    administrationRoute = it.administrationRoute,
                    dose = it.dose,
                    estimatedDoseStandardDeviation = it.estimatedDoseStandardDeviation,
                    isEstimate = it.isEstimate,
                    isArchived = it.isArchived,
                    unit = it.unit,
                    unitPlural = it.unitPlural,
                    originalUnit = it.originalUnit,
                    note = it.note,
                    defaultCategory = it.defaultCategory
                )
            )
        }
        journalExport.intakeLimits.forEach {
            insert(
                IntakeLimit(
                    substanceName = it.substanceName,
                    creationDate = it.creationDate,
                    limitType = it.limitType,
                    maxDose = it.maxDose,
                    unit = it.unit,
                    maxCount = it.maxCount,
                    windowSeconds = it.windowSeconds,
                    warningPercent = it.warningPercent,
                    isEnabled = it.isEnabled,
                )
            )
        }
    }

    @Transaction
    suspend fun insertIngestionAndCompanion(
        ingestion: Ingestion,
        substanceCompanion: SubstanceCompanion
    ): Long {
        val ingestionId = insert(ingestion)
        insert(substanceCompanion)
        return ingestionId
    }

    @Transaction
    @Query("SELECT * FROM experience WHERE id = :experienceId")
    fun getExperienceWithIngestionsAndCompanionsFlow(experienceId: Int): Flow<ExperienceWithIngestionsAndCompanions?>

    @Transaction
    @Query("SELECT * FROM ingestion WHERE experienceId = :experienceId")
    fun getIngestionsWithCompanionsFlow(experienceId: Int): Flow<List<IngestionWithCompanionAndCustomUnit>>

    @Query("SELECT * FROM shulginrating WHERE experienceId = :experienceId")
    fun getRatingsFlow(experienceId: Int): Flow<List<ShulginRating>>

    @Query("SELECT * FROM timednote WHERE experienceId = :experienceId ORDER BY time")
    fun getTimedNotesFlowSorted(experienceId: Int): Flow<List<TimedNote>>

    @Query("SELECT * FROM ingestion WHERE substanceName = :substanceName ORDER BY time DESC LIMIT 1")
    suspend fun getLastIngestion(substanceName: String): Ingestion?

    @Query("SELECT * FROM ingestion WHERE substanceName IN (:substanceNames) AND time > :since ORDER BY time DESC")
    suspend fun getIngestionsSince(substanceNames: List<String>, since: Instant): List<Ingestion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(substanceCompanion: SubstanceCompanion)

    @Upsert
    suspend fun upsert(substanceCompanion: SubstanceCompanion)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customSubstance: CustomSubstance): Long

    @Delete
    suspend fun delete(customSubstance: CustomSubstance)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(customSubstance: CustomSubstance)

    @Delete
    suspend fun delete(substanceCompanion: SubstanceCompanion)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(substanceCompanion: SubstanceCompanion)

    @Query("SELECT * FROM substancecompanion WHERE substanceName =:substanceName")
    fun getSubstanceCompanionFlow(substanceName: String): Flow<SubstanceCompanion?>

    @Query("SELECT * FROM substancecompanion")
    fun getAllSubstanceCompanionsFlow(): Flow<List<SubstanceCompanion>>

    @Query("SELECT * FROM timednote")
    fun getAllTimedNotesFlow(): Flow<List<TimedNote>>

    @Query("SELECT * FROM customunit WHERE isArchived = :isArchived ORDER BY creationDate DESC")
    fun getSortedCustomUnitsFlow(isArchived: Boolean): Flow<List<CustomUnit>>

    @Query("SELECT * FROM customunit WHERE isArchived = :isArchived AND substanceName = :substanceName ORDER BY creationDate DESC")
    fun getSortedCustomUnitsFlowBasedOnName(substanceName: String, isArchived: Boolean): Flow<List<CustomUnit>>

    @Query("SELECT * FROM customunit ORDER BY creationDate DESC")
    fun getAllCustomUnitsFlow(): Flow<List<CustomUnit>>

    @Query("SELECT * FROM timednote WHERE experienceId =:experienceId")
    suspend fun getTimedNotes(experienceId: Int): List<TimedNote>

    // ── ContentProvider cursor queries ──────────────────────────────────────

    @Query(
        """
        SELECT id AS _id, substanceName AS substance_name, time AS time_epoch_s,
               endTime AS end_time, creationDate AS creation_date,
               administrationRoute AS administration_route, dose AS dose,
               isDoseAnEstimate AS is_dose_estimate,
               estimatedDoseStandardDeviation AS estimated_dose_sd, units AS units,
               experienceId AS experience_id, notes AS notes,
               stomachFullness AS stomach_fullness, consumerName AS consumer_name,
               customUnitId AS custom_unit_id, webhookMessageId AS webhook_message_id,
               administrationSite AS administration_site,
               formulationName AS formulation_name, saltForm AS salt_form
        FROM Ingestion WHERE id > :since ORDER BY id ASC LIMIT :limit
        """
    )
    fun providerIngestions(since: Int, limit: Int): Cursor

    @Query(
        """
        SELECT id AS _id, substanceName AS substance_name, time AS time_epoch_s,
               dose AS dose, units AS units,
               administrationRoute AS administration_route,
               NULL AS category
        FROM Ingestion WHERE id > :since ORDER BY id ASC LIMIT :limit
        """
    )
    fun providerIngestionsPublic(since: Int, limit: Int): Cursor

    @Query(
        """
        SELECT id AS _id, substanceName AS substance_name, time AS time_epoch_s,
               dose AS dose, units AS units,
               administrationRoute AS administration_route,
               NULL AS category
        FROM Ingestion WHERE id = :id
        """
    )
    fun providerIngestionPublicById(id: Int): Cursor

    @Query(
        """
        SELECT id AS _id, substanceName AS substance_name, time AS time_epoch_s,
               endTime AS end_time, creationDate AS creation_date,
               administrationRoute AS administration_route, dose AS dose,
               isDoseAnEstimate AS is_dose_estimate,
               estimatedDoseStandardDeviation AS estimated_dose_sd, units AS units,
               experienceId AS experience_id, notes AS notes,
               stomachFullness AS stomach_fullness, consumerName AS consumer_name,
               customUnitId AS custom_unit_id, webhookMessageId AS webhook_message_id,
               administrationSite AS administration_site,
               formulationName AS formulation_name, saltForm AS salt_form
        FROM Ingestion WHERE id = :id
        """
    )
    fun providerIngestionById(id: Int): Cursor

    @Query("SELECT seq, ingestion_id, op FROM ingestion_change_log WHERE seq > :since ORDER BY seq ASC LIMIT :limit")
    fun providerIngestionChanges(since: Long, limit: Int): Cursor

    @Query("SELECT MIN(seq) AS min_seq, MAX(seq) AS max_seq, COUNT(*) AS row_count FROM ingestion_change_log")
    fun providerIngestionChangesMeta(): Cursor

    @Query(
        """
        SELECT id AS _id, title, text,
               creationDate AS creation_date_epoch_s,
               sortDate AS sort_date_epoch_s,
               isFavorite AS is_favorite
        FROM Experience ORDER BY id ASC LIMIT :limit
        """
    )
    fun providerExperiences(limit: Int): Cursor

    @Query("SELECT MAX(id) FROM experience")
    fun getMaxExperienceIdFlow(): Flow<Int?>

    @Transaction
    @Query("SELECT * FROM customsubstance")
    suspend fun getAllCustomSubstancesWithRoaDurations(): List<CustomSubstanceWithDurations>

    @Query("SELECT * FROM customsubstance WHERE name = :name")
    fun getCustomSubstanceFlow(name: String): Flow<CustomSubstance?>

    @Query("SELECT * FROM customroaduration")
    suspend fun getAllCustomRoaDurations(): List<CustomRoaDuration>

    @Query("SELECT * FROM customroaduration")
    fun getAllCustomRoaDurationsFlow(): Flow<List<CustomRoaDuration>>

    @Query("SELECT * FROM customroaduration WHERE customSubstanceId = :substanceId")
    suspend fun getCustomRoaDurations(substanceId: Int): List<CustomRoaDuration>

    @Query("SELECT * FROM customroaduration WHERE customSubstanceId = (SELECT id FROM customsubstance WHERE name = :substanceName)")
    suspend fun getCustomRoaDurations(substanceName: String): List<CustomRoaDuration>

    @Query("SELECT * FROM customroaduration WHERE customSubstanceId = :substanceId")
    fun getCustomRoaDurationsFlow(substanceId: Int): Flow<List<CustomRoaDuration>>

    @Transaction
    suspend fun getCustomSubstanceWithDurations(id: Int): CustomSubstanceWithDurations? {
        val substance = getCustomSubstanceFlow(id).firstOrNull() ?: return null
        val durations = getCustomRoaDurations(id)
        return CustomSubstanceWithDurations(substance, durations)
    }

    @Transaction
    @Query("SELECT * FROM ingestion WHERE time > :since")
    suspend fun getIngestionsWithCompanionsSince(since: Instant): List<IngestionWithCompanionAndCustomUnit>

    @Query("SELECT * FROM ingestion WHERE experienceId IS NULL ORDER BY time")
    suspend fun getAllStandaloneIngestions(): List<Ingestion>

    @Query("SELECT * FROM customunit WHERE id IN (:ids)")
    suspend fun getCustomUnitsByIds(ids: List<Int>): List<CustomUnit>

    @Query("SELECT * FROM customunit WHERE substanceName = :substanceName AND name = :name AND administrationRoute = :route LIMIT 1")
    suspend fun findCustomUnitByKey(
        substanceName: String,
        name: String,
        route: AdministrationRoute
    ): CustomUnit?

    @Query("SELECT * FROM experience")
    fun getAllExperiencesFlow(): Flow<List<Experience>>

    @Transaction
    @Query("SELECT * FROM ingestion")
    suspend fun getAllIngestionsWithCompanions(): List<IngestionWithCompanion>

    @Query("DELETE FROM substancegroup")
    suspend fun deleteAllSubstanceGroups()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngestions(ingestions: List<Ingestion>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRatings(ratings: List<ShulginRating>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimedNotes(timedNotes: List<TimedNote>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customRoaDuration: CustomRoaDuration): Long

    @Delete
    suspend fun delete(customRoaDuration: CustomRoaDuration)

    @Query("DELETE FROM customroaduration WHERE route NOT IN (:route) AND customSubstanceId = :substanceId")
    suspend fun deleteCustomDurationsForSubstanceExcept(
        substanceId: Int,
        route: List<AdministrationRoute>
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customRoaDose: CustomRoaDose)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(customRoaDose: CustomRoaDose)

    @Delete
    suspend fun delete(customRoaDose: CustomRoaDose)

    @Query("SELECT * FROM customroadose WHERE customSubstanceId = :substanceId")
    suspend fun getCustomRoaDoses(substanceId: Int): List<CustomRoaDose>

    @Query("SELECT * FROM customroadose WHERE customSubstanceId = (SELECT id FROM customsubstance WHERE name = :substanceName)")
    suspend fun getCustomRoaDoses(substanceName: String): List<CustomRoaDose>

    @Query("SELECT * FROM customroadose WHERE customSubstanceId = :substanceId")
    fun getCustomRoaDosesFlow(substanceId: Int): Flow<List<CustomRoaDose>>

    @Query("SELECT * FROM customroadose")
    fun getAllCustomRoaDosesFlow(): Flow<List<CustomRoaDose>>

    @Query("SELECT * FROM customroadose WHERE customSubstanceId = :substanceId AND route = :route")
    suspend fun getCustomRoaDose(substanceId: Int, route: AdministrationRoute): CustomRoaDose?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customRoa: CustomRoa)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(customRoa: CustomRoa)

    @Delete
    suspend fun delete(customRoa: CustomRoa)

    @Query("SELECT * FROM customroa WHERE customSubstanceId = :substanceId")
    suspend fun getCustomRoas(substanceId: Int): List<CustomRoa>

    @Query("SELECT * FROM customroa WHERE customSubstanceId = :substanceId AND route = :route")
    suspend fun getCustomRoa(substanceId: Int, route: AdministrationRoute): CustomRoa?

    @Transaction
    suspend fun updateCustomSubstanceDurations(
        substanceId: Int,
        durations: List<CustomRoaDuration>
    ) {
        deleteCustomDurationsForSubstanceExcept(
            substanceId,
            enumEntries<AdministrationRoute>()
                .filter { route -> durations.any { it.route == route } }
        )

        for (d in durations) {
            d.customSubstanceId = substanceId
            insert(d)
        }
    }

    @Transaction
    suspend fun upsertCustomRoaDose(
        substanceId: Int,
        dose: CustomRoaDose,
    ) {
        dose.customSubstanceId = substanceId
        insert(dose)
    }

    @Transaction
    suspend fun insertCustomSubstanceWithDurations(
        substance: CustomSubstance,
        durations: List<CustomRoaDuration>
    ): Int {
        val id = insert(substance).toInt()
        updateCustomSubstanceDurations(id, durations)
        return id
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customInteraction: CustomInteraction): Long

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(customInteraction: CustomInteraction)

    @Delete
    suspend fun delete(customInteraction: CustomInteraction)

    @Query("DELETE FROM custominteraction WHERE id = :id")
    suspend fun deleteCustomInteraction(id: Int)

    @Query("SELECT * FROM custominteraction WHERE customSubstanceId = :substanceId")
    suspend fun getCustomInteractions(substanceId: Int): List<CustomInteraction>

    @Query("SELECT * FROM custominteraction WHERE customSubstanceId = :substanceId AND severity = :severity")
    suspend fun getCustomInteractions(
        substanceId: Int,
        severity: CustomInteractionSeverity
    ): List<CustomInteraction>

    @Query("SELECT * FROM custominteraction WHERE customSubstanceId = (SELECT id FROM customsubstance WHERE name = :substanceName)")
    suspend fun getCustomInteractions(substanceName: String): List<CustomInteraction>

    @Query("SELECT * FROM custominteraction WHERE customSubstanceId = :substanceId")
    fun getCustomInteractionsFlow(substanceId: Int): Flow<List<CustomInteraction>>

    @Query("UPDATE customsubstance SET name = :new WHERE name = :old")
    suspend fun renameCustomSubstanceRow(old: String, new: String)

    @Query("UPDATE ingestion SET substanceName = :new WHERE substanceName = :old")
    suspend fun renameInIngestions(old: String, new: String)

    @Query("UPDATE customunit SET substanceName = :new WHERE substanceName = :old")
    suspend fun renameInCustomUnits(old: String, new: String)

    @Query("UPDATE custominteraction SET targetName = :new WHERE targetName = :old AND targetType = 'SUBSTANCE'")
    suspend fun renameInCustomInteractionTargets(old: String, new: String)

    @Query("UPDATE substancecompanion SET substanceName = :new WHERE substanceName = :old")
    suspend fun renameSubstanceCompanion(old: String, new: String)

    @Transaction
    suspend fun renameCustomSubstance(old: String, new: String) {
        if (old == new || new.isBlank()) return
        renameCustomSubstanceRow(old, new)
        renameSubstanceCompanion(old, new)
        renameInIngestions(old, new)
        renameInCustomUnits(old, new)
        renameInCustomInteractionTargets(old, new)
    }

    @Query("UPDATE customroadose SET units = :new WHERE customSubstanceId = :substanceId")
    suspend fun setAllCustomRoaDoseUnits(substanceId: Int, new: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(assignment: CustomCategoryAssignment)

    @Delete
    suspend fun delete(assignment: CustomCategoryAssignment)

    @Query("DELETE FROM customcategoryassignment WHERE customSubstanceId = :substanceId AND categoryName = :categoryName")
    suspend fun deleteCustomCategory(substanceId: Int, categoryName: String)

    @Query("SELECT * FROM customcategoryassignment WHERE customSubstanceId = :substanceId")
    suspend fun getCustomCategories(substanceId: Int): List<CustomCategoryAssignment>

    @Query("SELECT * FROM customcategoryassignment WHERE customSubstanceId = :substanceId")
    fun getCustomCategoriesFlow(substanceId: Int): Flow<List<CustomCategoryAssignment>>

    @Query("SELECT * FROM customcategoryassignment")
    fun getAllCustomCategoryAssignmentsFlow(): Flow<List<CustomCategoryAssignment>>

    @Query("SELECT customSubstanceId, COUNT(*) as count FROM custominteraction GROUP BY customSubstanceId")
    fun getCustomInteractionCountsFlow(): Flow<List<CustomInteractionCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(crossTolerance: CustomCrossTolerance)

    @Delete
    suspend fun delete(crossTolerance: CustomCrossTolerance)

    @Query("DELETE FROM customcrosstolerance WHERE customSubstanceId = :substanceId AND categoryName = :categoryName")
    suspend fun deleteCustomCrossTolerance(substanceId: Int, categoryName: String)

    @Query("SELECT * FROM customcrosstolerance WHERE customSubstanceId = :substanceId")
    suspend fun getCustomCrossTolerances(substanceId: Int): List<CustomCrossTolerance>

    @Query("SELECT * FROM customcrosstolerance WHERE customSubstanceId = :substanceId")
    fun getCustomCrossTolerancesFlow(substanceId: Int): Flow<List<CustomCrossTolerance>>

    @Transaction
    @Query("SELECT * FROM customsubstance WHERE id = :id")
    fun getCustomSubstanceWithEverythingFlow(id: Int): Flow<CustomSubstanceWithEverything?>

    @Transaction
    @Query("SELECT * FROM customsubstance WHERE id = :id")
    suspend fun getCustomSubstanceWithEverything(id: Int): CustomSubstanceWithEverything?

    @Transaction
    @Query("SELECT * FROM customsubstance WHERE name = :name")
    suspend fun getCustomSubstanceWithEverythingByName(name: String): CustomSubstanceWithEverything?

    @Transaction
    @Query("SELECT * FROM customsubstance WHERE name IN (:names)")
    suspend fun getCustomSubstancesWithEverythingByNames(names: List<String>): List<CustomSubstanceWithEverything>

    @Query("SELECT name FROM customsubstance WHERE name IN (:names)")
    suspend fun getExistingCustomSubstanceNames(names: List<String>): List<String>

    @Transaction
    suspend fun insertCustomSubstanceWithFullRoaInfo(
        substance: CustomSubstance,
        durations: List<CustomRoaDuration>,
        doses: List<CustomRoaDose>,
        roas: List<CustomRoa>,
        interactions: List<CustomInteraction> = emptyList(),
        categories: List<CustomCategoryAssignment> = emptyList(),
        crossTolerances: List<CustomCrossTolerance> = emptyList(),
    ): Int {
        val id = insert(substance).toInt()
        updateCustomSubstanceDurations(id, durations)
        for (d in doses) {
            d.customSubstanceId = id
            insert(d)
        }
        for (r in roas) {
            r.customSubstanceId = id
            insert(r)
        }
        for (i in interactions) {
            i.customSubstanceId = id
            insert(i)
        }
        for (c in categories) {
            c.customSubstanceId = id
            insert(c)
        }
        for (t in crossTolerances) {
            t.customSubstanceId = id
            insert(t)
        }
        return id
    }

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(customRoaDuration: CustomRoaDuration)

    @Query("SELECT * FROM substancecompanion WHERE substanceName = :substanceName")
    suspend fun getSubstanceCompanion(substanceName: String): SubstanceCompanion?

    @Insert
    suspend fun insert(group: SubstanceGroup): Long

    @Insert
    suspend fun insert(item: SubstanceGroupItem): Long

    @Update
    suspend fun update(group: SubstanceGroup)

    @Delete
    suspend fun delete(group: SubstanceGroup)

    @Delete
    suspend fun delete(item: SubstanceGroupItem)

    @Query("SELECT * FROM substancegroup ORDER BY name COLLATE NOCASE ASC")
    fun getSubstanceGroupsFlow(): Flow<List<SubstanceGroup>>

    @Transaction
    @Query("SELECT * FROM substancegroup ORDER BY name COLLATE NOCASE ASC")
    fun getSubstanceGroupsWithItemsFlow(): Flow<List<SubstanceGroupWithItems>>

    @Transaction
    @Query("SELECT * FROM substancegroup WHERE id = :id")
    fun getSubstanceGroupWithItemsFlow(id: Int): Flow<SubstanceGroupWithItems?>

    @Transaction
    @Query("SELECT * FROM substancegroup WHERE id = :id")
    suspend fun getSubstanceGroupWithItems(id: Int): SubstanceGroupWithItems?

    @Transaction
    @Query("SELECT * FROM substancegroup WHERE name = :name")
    suspend fun getSubstanceGroupWithItemsByName(name: String): SubstanceGroupWithItems?

    @Transaction
    @Query("SELECT * FROM substancegroup")
    suspend fun getSubstanceGroupsWithItems(): List<SubstanceGroupWithItems>

    @Query("DELETE FROM substancegroupitem WHERE groupId = :groupId")
    suspend fun deleteSubstanceGroupItems(groupId: Int)

    @Transaction
    suspend fun replaceSubstanceGroupItems(groupId: Int, items: List<SubstanceGroupItem>) {
        deleteSubstanceGroupItems(groupId)
        for (i in items) {
            i.groupId = groupId
            insert(i)
        }
    }

    @Transaction
    suspend fun insertSubstanceGroupWithItems(
        group: SubstanceGroup,
        items: List<SubstanceGroupItem>
    ): Int {
        val id = insert(group).toInt()
        for (i in items) {
            i.groupId = id
            insert(i)
        }
        return id
    }

    @Transaction
    @Query("SELECT * FROM customsubstance ORDER BY name")
    suspend fun getAllCustomSubstancesWithEverything(): List<CustomSubstanceWithEverything>

    @Transaction
    @Query("SELECT * FROM customsubstance")
    fun getAllCustomSubstancesWithEverythingFlow(): Flow<List<CustomSubstanceWithEverything>>
}
