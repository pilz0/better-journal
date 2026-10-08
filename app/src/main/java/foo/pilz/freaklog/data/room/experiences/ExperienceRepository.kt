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

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import foo.pilz.freaklog.data.export.JournalExport
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoa
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDose
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDuration
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.experiences.entities.CustomUnit
import foo.pilz.freaklog.data.room.experiences.entities.Experience
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.entities.IntakeLimit
import foo.pilz.freaklog.data.room.experiences.entities.ShulginRating
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroup
import foo.pilz.freaklog.data.room.experiences.entities.TimedNote
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithDurations
import foo.pilz.freaklog.data.room.experiences.relations.CustomUnitWithIngestions
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestions
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsAndCompanions
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsCompanionsAndRatings
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsTimedNotesAndRatings
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanion
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithExperienceAndCustomUnit
import foo.pilz.freaklog.data.room.experiences.relations.SubstanceGroupWithItems
import foo.pilz.freaklog.data.room.reminders.ReminderDao
import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import foo.pilz.freaklog.data.substanceshare.expand
import foo.pilz.freaklog.data.substanceshare.toShared
import foo.pilz.freaklog.provider.JournalContract
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
    private val ingestionWebhookMessageDao: foo.pilz.freaklog.data.room.webhooks.IngestionWebhookMessageDao,
    @ApplicationContext private val context: Context
) {
    private fun notifyJournalChanged() {
        val resolver = context.contentResolver
        resolver.notifyChange(JournalContract.ingestionsUri(context), null)
        resolver.notifyChange(JournalContract.ingestionsPublicUri(context), null)
        resolver.notifyChange(JournalContract.experiencesUri(context), null)
        resolver.notifyChange(JournalContract.ingestionChangesUri(context), null)
    }

    suspend fun insert(rating: ShulginRating) = experienceDao.insert(rating)
    suspend fun insert(customUnit: CustomUnit) = experienceDao.insert(customUnit).toInt()
    suspend fun insert(timedNote: TimedNote) = experienceDao.insert(timedNote)
    suspend fun update(experience: Experience) {
        experienceDao.update(experience)
        notifyJournalChanged()
    }
    suspend fun update(ingestion: Ingestion) {
        experienceDao.update(ingestion)
        notifyJournalChanged()
    }
    suspend fun update(rating: ShulginRating) = experienceDao.update(rating)
    suspend fun update(customUnit: CustomUnit) = experienceDao.update(customUnit)
    suspend fun update(timedNote: TimedNote) = experienceDao.update(timedNote)

    suspend fun migrateBenzydamine() {
        experienceDao.migrateBenzydamine()
        notifyJournalChanged()
    }
    suspend fun migrateCannabisAndMushroomUnits() {
        experienceDao.migrateCannabisAndMushroomUnits()
        notifyJournalChanged()
    }
    suspend fun insertIngestionExperienceAndCompanion(
        ingestion: Ingestion,
        experience: Experience,
        substanceCompanion: SubstanceCompanion
    ): Long {
        val id = experienceDao.insertIngestionExperienceAndCompanion(ingestion, experience, substanceCompanion)
        notifyJournalChanged()
        return id
    }

    suspend fun insertEverything(
        journalExport: JournalExport
    ) {
        experienceDao.insertEverything(journalExport)
        journalExport.reminders.forEach { reminderDao.insert(it) }
        journalExport.webhooks.forEach { webhookDao.insert(it.toEntity()) }
        val validCustomUnitIds = journalExport.customUnits.map { it.id }.toSet()
        journalExport.substanceGroups.forEach { group ->
            experienceDao.insertSubstanceGroupWithItems(
                group = SubstanceGroup(name = group.name),
                items = group.items.map { item ->
                    item.copy(customUnitId = item.customUnitId?.takeIf { it in validCustomUnitIds })
                },
            )
        }
        // The substance rows were just inserted with new ids; attach each one's details by name.
        journalExport.customSubstanceDetails.forEach { shared ->
            val substanceId = experienceDao.getCustomSubstanceWithEverythingByName(shared.name)?.substance?.id
                ?: return@forEach
            val expansion = shared.expand()
            expansion.roas.forEach { experienceDao.insert(it.copy(customSubstanceId = substanceId)) }
            expansion.doses.forEach { experienceDao.insert(it.copy(customSubstanceId = substanceId)) }
            expansion.durations.forEach { experienceDao.insert(it.copy(customSubstanceId = substanceId)) }
            expansion.interactions.forEach { experienceDao.insert(it.copy(customSubstanceId = substanceId)) }
            expansion.categories.forEach { experienceDao.insert(it.copy(customSubstanceId = substanceId)) }
            expansion.crossTolerances.forEach { experienceDao.insert(it.copy(customSubstanceId = substanceId)) }
        }
        notifyJournalChanged()
    }

    suspend fun insertIngestionAndCompanion(
        ingestion: Ingestion,
        substanceCompanion: SubstanceCompanion
    ): Long {
        val id = experienceDao.insertIngestionAndCompanion(ingestion, substanceCompanion)
        notifyJournalChanged()
        return id
    }

    suspend fun deleteEverything() {
        experienceDao.deleteEverything()
        reminderDao.deleteAll()
        ingestionWebhookMessageDao.deleteAll()
        webhookDao.deleteAll()
        notifyJournalChanged()
    }

    suspend fun delete(ingestion: Ingestion) {
        experienceDao.delete(ingestion)
        notifyJournalChanged()
    }
    suspend fun delete(customUnit: CustomUnit) = experienceDao.delete(customUnit)

    suspend fun deleteEverythingOfExperience(experienceId: Int) {
        experienceDao.deleteEverythingOfExperience(experienceId)
        notifyJournalChanged()
    }

    suspend fun delete(experience: Experience) {
        experienceDao.delete(experience)
        notifyJournalChanged()
    }

    suspend fun delete(rating: ShulginRating) =
        experienceDao.delete(rating)

    suspend fun delete(timedNote: TimedNote) =
        experienceDao.delete(timedNote)

    suspend fun delete(experienceWithIngestions: ExperienceWithIngestions) {
        experienceDao.deleteExperienceWithIngestions(experienceWithIngestions)
        notifyJournalChanged()
    }

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

    suspend fun getAllIntakeLimits(): List<IntakeLimit> = experienceDao.getIntakeLimits()
    suspend fun insert(intakeLimit: IntakeLimit): Int = experienceDao.insert(intakeLimit).toInt()
    suspend fun update(intakeLimit: IntakeLimit) = experienceDao.update(intakeLimit)
    suspend fun delete(intakeLimit: IntakeLimit) = experienceDao.delete(intakeLimit)

    fun getIntakeLimitsFlow(): Flow<List<IntakeLimit>> = experienceDao.getIntakeLimitsFlow()
        .flowOn(Dispatchers.IO)
        .conflate()

    suspend fun getIntakeLimit(id: Int): IntakeLimit? = experienceDao.getIntakeLimit(id)

    suspend fun getIntakeLimitsForSubstance(substanceName: String): List<IntakeLimit> =
        experienceDao.getIntakeLimitsForSubstance(substanceName)

    suspend fun getIngestionsWithCustomUnitsForSubstanceSince(
        substanceName: String,
        since: Instant
    ): List<IngestionWithCompanionAndCustomUnit> =
        experienceDao.getIngestionsWithCustomUnitsForSubstanceSince(substanceName, since)

    /** Details of every custom substance that has any; substances with only a name are left out. */
    suspend fun getAllCustomSubstanceDetails(): List<foo.pilz.freaklog.data.substanceshare.SharedSubstance> =
        experienceDao.getAllCustomSubstancesWithEverything()
            .filter {
                it.roas.isNotEmpty() || it.doses.isNotEmpty() || it.durations.isNotEmpty() ||
                    it.interactions.isNotEmpty() || it.crossTolerances.isNotEmpty()
            }
            .map { it.toShared() }

    suspend fun insert(reading: foo.pilz.freaklog.data.room.experiences.entities.BloodPressureReading) = experienceDao.insert(reading)
    suspend fun update(reading: foo.pilz.freaklog.data.room.experiences.entities.BloodPressureReading) = experienceDao.update(reading)
    suspend fun delete(reading: foo.pilz.freaklog.data.room.experiences.entities.BloodPressureReading) = experienceDao.delete(reading)
    fun getBloodPressureReadingsFlow(experienceId: Int): Flow<List<foo.pilz.freaklog.data.room.experiences.entities.BloodPressureReading>> =
        experienceDao.getBloodPressureReadingsFlow(experienceId).flowOn(Dispatchers.IO).conflate()
    suspend fun getAllBloodPressureReadings(): List<foo.pilz.freaklog.data.room.experiences.entities.BloodPressureReading> =
        experienceDao.getAllBloodPressureReadings()

    suspend fun upsert(substanceCompanion: SubstanceCompanion) =
        experienceDao.upsert(substanceCompanion)

    fun getCustomSubstanceFlow(name: String): Flow<CustomSubstance?> =
        experienceDao.getCustomSubstanceFlow(name)

    suspend fun getCustomSubstanceWithDurations(id: Int): CustomSubstanceWithDurations? =
        experienceDao.getCustomSubstanceWithDurations(id)

    suspend fun getAllCustomRoaDurations(): List<CustomRoaDuration> =
        experienceDao.getAllCustomRoaDurations()

    fun getAllCustomRoaDurationsFlow(): Flow<List<CustomRoaDuration>> =
        experienceDao.getAllCustomRoaDurationsFlow()

    suspend fun getCustomRoaDurations(substanceId: Int): List<CustomRoaDuration> =
        experienceDao.getCustomRoaDurations(substanceId)

    suspend fun getCustomRoaDurations(substanceName: String): List<CustomRoaDuration> =
        experienceDao.getCustomRoaDurations(substanceName)

    fun getCustomRoaDurationsFlow(substanceId: Int): Flow<List<CustomRoaDuration>> =
        experienceDao.getCustomRoaDurationsFlow(substanceId)

    suspend fun getAllIngestionsWithCompanions(): List<IngestionWithCompanion> =
        experienceDao.getAllIngestionsWithCompanions()

    fun getMaxExperienceIdFlow(): Flow<Int?> = experienceDao.getMaxExperienceIdFlow()
        .flowOn(Dispatchers.IO)
        .conflate()

    suspend fun getIngestionsWithCompanionsSince(since: Instant) =
        experienceDao.getIngestionsWithCompanionsSince(since)

    suspend fun getAllStandaloneIngestions(): List<Ingestion> =
        experienceDao.getAllStandaloneIngestions()

    suspend fun getCustomUnitsByIds(ids: List<Int>): List<CustomUnit> =
        if (ids.isEmpty()) emptyList() else experienceDao.getCustomUnitsByIds(ids)

    suspend fun findMatchingCustomUnit(
        substanceName: String,
        name: String,
        route: foo.pilz.freaklog.data.substances.AdministrationRoute,
    ): CustomUnit? = experienceDao.findCustomUnitByKey(substanceName, name, route)

    fun getAllExperiencesFlow() =
        experienceDao.getAllExperiencesFlow()
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getAllCustomCategoryAssignmentsFlow() =
        experienceDao.getAllCustomCategoryAssignmentsFlow()
            .flowOn(Dispatchers.IO)
            .conflate()

    fun getCustomInteractionCountsFlow() =
        experienceDao.getCustomInteractionCountsFlow()
            .flowOn(Dispatchers.IO)
            .conflate()

    suspend fun getAllCustomSubstancesWithRoaDurations(): List<CustomSubstanceWithDurations> =
        experienceDao.getAllCustomSubstancesWithRoaDurations()

    suspend fun getAllSubstanceGroupsWithItems(): List<SubstanceGroupWithItems> =
        experienceDao.getSubstanceGroupsWithItems()

    suspend fun getSubstanceCompanion(substanceName: String): SubstanceCompanion? =
        experienceDao.getSubstanceCompanion(substanceName)

    suspend fun insert(customRoaDuration: CustomRoaDuration): Int =
        experienceDao.insert(customRoaDuration).toInt()

    suspend fun delete(customRoaDuration: CustomRoaDuration) =
        experienceDao.delete(customRoaDuration)

    suspend fun update(customRoaDuration: CustomRoaDuration) =
        experienceDao.update(customRoaDuration)

    suspend fun insert(customRoaDose: CustomRoaDose) =
        experienceDao.insert(customRoaDose)

    suspend fun update(customRoaDose: CustomRoaDose) =
        experienceDao.update(customRoaDose)

    suspend fun delete(customRoaDose: CustomRoaDose) =
        experienceDao.delete(customRoaDose)

    suspend fun upsertCustomRoaDose(substanceId: Int, dose: CustomRoaDose) =
        experienceDao.upsertCustomRoaDose(substanceId, dose)

    suspend fun getCustomRoaDoses(substanceId: Int): List<CustomRoaDose> =
        experienceDao.getCustomRoaDoses(substanceId)

    suspend fun getCustomRoaDoses(substanceName: String): List<CustomRoaDose> =
        experienceDao.getCustomRoaDoses(substanceName)

    fun getCustomRoaDosesFlow(substanceId: Int): Flow<List<CustomRoaDose>> =
        experienceDao.getCustomRoaDosesFlow(substanceId)

    fun getAllCustomRoaDosesFlow(): Flow<List<CustomRoaDose>> =
        experienceDao.getAllCustomRoaDosesFlow()

    suspend fun getCustomRoaDose(
        substanceId: Int,
        route: foo.pilz.freaklog.data.substances.AdministrationRoute
    ): CustomRoaDose? =
        experienceDao.getCustomRoaDose(substanceId, route)

    suspend fun insert(customRoa: CustomRoa) =
        experienceDao.insert(customRoa)

    suspend fun update(customRoa: CustomRoa) =
        experienceDao.update(customRoa)

    suspend fun delete(customRoa: CustomRoa) =
        experienceDao.delete(customRoa)

    suspend fun getCustomRoas(substanceId: Int): List<CustomRoa> =
        experienceDao.getCustomRoas(substanceId)

    suspend fun getCustomRoa(
        substanceId: Int,
        route: foo.pilz.freaklog.data.substances.AdministrationRoute
    ): CustomRoa? =
        experienceDao.getCustomRoa(substanceId, route)

    suspend fun updateCustomSubstanceDurations(
        substance: CustomSubstance,
        durations: List<CustomRoaDuration>
    ) =
        experienceDao.updateCustomSubstanceDurations(substance.id, durations)

    suspend fun insertCustomSubstanceWithDurations(
        substance: CustomSubstance,
        durations: List<CustomRoaDuration>
    ): Int =
        experienceDao.insertCustomSubstanceWithDurations(substance, durations)
}
