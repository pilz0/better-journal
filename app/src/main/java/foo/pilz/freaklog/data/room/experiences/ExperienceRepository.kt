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

import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.experiences.entities.CustomUnit
import foo.pilz.freaklog.data.room.experiences.entities.Experience
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.entities.ShulginRating
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.room.experiences.entities.TimedNote
import foo.pilz.freaklog.data.room.experiences.relations.CustomUnitWithIngestions
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestions
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsAndCompanions
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsCompanionsAndRatings
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsTimedNotesAndRatings
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanion
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithExperienceAndCustomUnit
import foo.pilz.freaklog.data.export.JournalExport
import foo.pilz.freaklog.data.room.reminders.ReminderDao
import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExperienceRepository @Inject constructor(
    private val experienceDao: ExperienceDao,
    private val reminderDao: ReminderDao,
    private val webhookDao: foo.pilz.freaklog.data.room.webhooks.WebhookDao,
    private val ingestionWebhookMessageDao: foo.pilz.freaklog.data.room.webhooks.IngestionWebhookMessageDao
) {
    suspend fun insert(rating: ShulginRating) = experienceDao.insert(rating)
    suspend fun insert(customUnit: CustomUnit) = experienceDao.insert(customUnit).toInt()
    suspend fun insert(timedNote: TimedNote) = experienceDao.insert(timedNote)
    suspend fun update(experience: Experience) = experienceDao.update(experience)
    suspend fun update(ingestion: Ingestion) = experienceDao.update(ingestion)
    suspend fun update(rating: ShulginRating) = experienceDao.update(rating)
    suspend fun update(customUnit: CustomUnit) = experienceDao.update(customUnit)
    suspend fun update(timedNote: TimedNote) = experienceDao.update(timedNote)

    suspend fun migrateBenzydamine() = experienceDao.migrateBenzydamine()
    suspend fun migrateCannabisAndMushroomUnits() = experienceDao.migrateCannabisAndMushroomUnits()
    suspend fun insertIngestionExperienceAndCompanion(
        ingestion: Ingestion,
        experience: Experience,
        substanceCompanion: SubstanceCompanion
    ) = experienceDao.insertIngestionExperienceAndCompanion(
        ingestion,
        experience,
        substanceCompanion
    )

    suspend fun insertEverything(
        journalExport: JournalExport
    ) {
        experienceDao.insertEverything(journalExport)
        journalExport.reminders.forEach { reminderDao.insert(it) }
        journalExport.webhooks.forEach { webhookDao.insert(it.toEntity()) }
    }

    suspend fun insertIngestionAndCompanion(
        ingestion: Ingestion,
        substanceCompanion: SubstanceCompanion
    ) = experienceDao.insertIngestionAndCompanion(
        ingestion,
        substanceCompanion
    )

    suspend fun deleteEverything() {
        experienceDao.deleteEverything()
        reminderDao.deleteAll()
        ingestionWebhookMessageDao.deleteAll()
        webhookDao.deleteAll()
    }

    suspend fun delete(ingestion: Ingestion) = experienceDao.delete(ingestion)
    suspend fun delete(customUnit: CustomUnit) = experienceDao.delete(customUnit)

    suspend fun deleteEverythingOfExperience(experienceId: Int) =
        experienceDao.deleteEverythingOfExperience(experienceId)

    suspend fun delete(experience: Experience) =
        experienceDao.delete(experience)

    suspend fun delete(rating: ShulginRating) =
        experienceDao.delete(rating)

    suspend fun delete(timedNote: TimedNote) =
        experienceDao.delete(timedNote)

    suspend fun delete(experienceWithIngestions: ExperienceWithIngestions) =
        experienceDao.deleteExperienceWithIngestions(experienceWithIngestions)

    suspend fun deleteUnusedSubstanceCompanions() =
        experienceDao.deleteUnusedSubstanceCompanions()

    suspend fun getSortedExperiencesWithIngestionsWithSortDateBetween(
        fromInstant: Instant,
        toInstant: Instant
    ): List<ExperienceWithIngestions> =
        experienceDao.getSortedExperiencesWithIngestionsWithSortDateBetween(fromInstant, toInstant)

    fun getSortedExperienceWithIngestionsCompanionsAndRatingsFlow(): Flow<List<ExperienceWithIngestionsCompanionsAndRatings>> =
        experienceDao.getSortedExperienceWithIngestionsCompanionsAndRatingsFlow()
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getSortedExperiencesWithIngestionsFlow(): Flow<List<ExperienceWithIngestions>> =
        experienceDao.getSortedExperiencesWithIngestionsFlow()
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getSortedExperiencesWithIngestionsAndCustomUnitsFlow(): Flow<List<ExperienceWithIngestionsAndCompanions>> =
        experienceDao.getSortedExperiencesWithIngestionsAndCustomUnitsFlow()
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getCustomSubstancesFlow(): Flow<List<CustomSubstance>> =
        experienceDao.getCustomSubstancesFlow()
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getCustomSubstanceFlow(id: Int): Flow<CustomSubstance?> =
        experienceDao.getCustomSubstanceFlow(id)
            .flowOn(Dispatchers.IO)
            .conflate()

    suspend fun getCustomSubstance(name: String): CustomSubstance? =
        experienceDao.getCustomSubstance(name)

    fun getIngestionsWithExperiencesFlow(
        fromInstant: Instant,
        toInstant: Instant
    ): Flow<List<IngestionWithExperienceAndCustomUnit>> =
        experienceDao.getIngestionWithExperiencesFlow(fromInstant, toInstant)
            .flowOn(Dispatchers.IO)
            .conflate()

    suspend fun getIngestionsWithCompanions(
        fromInstant: Instant,
        toInstant: Instant
    ): List<IngestionWithCompanion> =
        experienceDao.getIngestionsWithCompanions(fromInstant, toInstant)

    fun getSortedLastUsedSubstanceNamesFlow(limit: Int): Flow<List<String>> =
        experienceDao.getSortedLastUsedSubstanceNamesFlow(limit).flowOn(Dispatchers.IO).conflate()

    suspend fun getExperience(id: Int): Experience? = experienceDao.getExperience(id)
    suspend fun getExperienceWithIngestionsCompanionsAndRatings(id: Int): ExperienceWithIngestionsCompanionsAndRatings? =
        experienceDao.getExperienceWithIngestionsCompanionsAndRatings(id)

    suspend fun getIngestionsWithCompanions(experienceId: Int) =
        experienceDao.getIngestionsWithCompanions(experienceId)

    suspend fun getRating(id: Int): ShulginRating? = experienceDao.getRating(id)
    suspend fun getTimedNote(id: Int): TimedNote? = experienceDao.getTimedNote(id)
    suspend fun getCustomUnit(id: Int): CustomUnit? = experienceDao.getCustomUnit(id)
    suspend fun getCustomUnitWithIngestions(id: Int): CustomUnitWithIngestions? = experienceDao.getCustomUnitWithIngestions(id)
    fun getIngestionFlow(id: Int) = experienceDao.getIngestionFlow(id)
        .flowOn(Dispatchers.IO)
        .conflate()

    fun getIngestionsWithCompanionsFlow(experienceId: Int) =
        experienceDao.getIngestionsWithCompanionsFlow(experienceId)
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getRatingsFlow(experienceId: Int) =
        experienceDao.getRatingsFlow(experienceId)
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getTimedNotesFlowSorted(experienceId: Int) =
        experienceDao.getTimedNotesFlowSorted(experienceId)
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getExperienceFlow(experienceId: Int) =
        experienceDao.getExperienceFlow(experienceId)
            .flowOn(Dispatchers.IO)
            .conflate()

    suspend fun getLatestIngestionOfEverySubstanceSinceDate(instant: Instant): List<Ingestion> =
        experienceDao.getLatestIngestionOfEverySubstanceSinceDate(instant)

    suspend fun getIngestionsSince(substanceNames: List<String>, since: Instant): List<Ingestion> =
        experienceDao.getIngestionsSince(substanceNames, since)

    suspend fun getLastIngestion(substanceName: String): Ingestion? =
        experienceDao.getLastIngestion(substanceName)

    suspend fun getAllExperiencesWithIngestionsTimedNotesAndRatingsSorted(): List<ExperienceWithIngestionsTimedNotesAndRatings> =
        experienceDao.getAllExperiencesWithIngestionsTimedNotesAndRatingsSorted()

    fun getAllExperiencesWithIngestionsTimedNotesAndRatingsFlow() =
        experienceDao.getAllExperiencesWithIngestionsTimedNotesAndRatingsFlow()

    suspend fun getAllCustomUnitsSorted(): List<CustomUnit> =
        experienceDao.getAllCustomUnitsSorted()

    suspend fun getAllCustomSubstances(): List<CustomSubstance> =
        experienceDao.getAllCustomSubstances()

    suspend fun getAllSubstanceCompanions(): List<SubstanceCompanion> =
        experienceDao.getAllSubstanceCompanions()

    suspend fun getTimedNotes(experienceId: Int): List<TimedNote> =
        experienceDao.getTimedNotes(experienceId)

    suspend fun delete(substanceCompanion: SubstanceCompanion) =
        experienceDao.delete(substanceCompanion)

    suspend fun update(substanceCompanion: SubstanceCompanion) =
        experienceDao.update(substanceCompanion)

    suspend fun insert(customSubstance: CustomSubstance): Int =
        experienceDao.insert(customSubstance).toInt()

    suspend fun delete(customSubstance: CustomSubstance) =
        experienceDao.delete(customSubstance)

    suspend fun update(customSubstance: CustomSubstance) =
        experienceDao.update(customSubstance)

    fun getSortedIngestionsWithSubstanceCompanionsFlow(limit: Int) =
        experienceDao.getSortedIngestionsWithSubstanceCompanionsFlow(limit)
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getSortedIngestions(limit: Int) =
        experienceDao.getSortedIngestions(limit)
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getSortedIngestionsFlow(substanceName: String, limit: Int) =
        experienceDao.getSortedIngestionsFlow(substanceName, limit)
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getSortedIngestionsWithExperienceAndCustomUnitFlow(substanceName: String) =
        experienceDao.getSortedIngestionsWithExperienceAndCustomUnitFlow(substanceName)
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getAllSubstanceCompanionsFlow() = experienceDao.getAllSubstanceCompanionsFlow()
        .flowOn(Dispatchers.IO)
        .conflate()

    fun getCustomUnitsFlow(isArchived: Boolean) = experienceDao.getSortedCustomUnitsFlow(isArchived)
        .flowOn(Dispatchers.IO)
        .conflate()

    fun getUnArchivedCustomUnitsFlow(substanceName: String) =
        experienceDao.getSortedCustomUnitsFlowBasedOnName(substanceName, false)
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getAllCustomUnitsFlow() = experienceDao.getAllCustomUnitsFlow()
        .flowOn(Dispatchers.IO)
        .conflate()

    fun getSubstanceCompanionFlow(substanceName: String) =
        experienceDao.getSubstanceCompanionFlow(substanceName)
            .flowOn(Dispatchers.IO)
            .conflate()

    suspend fun getAllReminders(): List<Reminder> = reminderDao.getAllReminders()

    /**
     * Returns the (dose, units) pair to display in webhook messages for the given ingestion,
     * converting custom-unit quantities to their base units when applicable.
     */
    suspend fun getWebhookDisplayValues(ingestion: Ingestion): Pair<Double?, String?> {
        val customUnitId = ingestion.customUnitId ?: return Pair(ingestion.dose, ingestion.units)
        val customUnit = getCustomUnit(customUnitId) ?: return Pair(ingestion.dose, ingestion.units)
        val ingestionDose = ingestion.dose
        val unitDose = customUnit.dose
        if (ingestionDose != null && unitDose != null) {
            return Pair(ingestionDose * unitDose, customUnit.originalUnit)
        }
        return Pair(ingestion.dose, ingestion.units)
    }
}