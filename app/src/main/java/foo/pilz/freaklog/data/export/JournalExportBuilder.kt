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

package foo.pilz.freaklog.data.export

import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.relations.CustomRecipeWithComponents
import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import foo.pilz.freaklog.data.room.webhooks.WebhookRepository
import kotlinx.serialization.json.Json

/** Reads every journal row an export (or a clinician report) is built from. */
suspend fun loadExportData(experienceRepository: ExperienceRepository): ExportData =
    ExportData(
        experiences =
            experienceRepository.getAllExperiencesWithIngestionsTimedNotesAndRatingsSorted(),
        customUnits = experienceRepository.getAllCustomUnitsSorted(),
        substanceCompanions = experienceRepository.getAllSubstanceCompanions(),
        customSubstances = experienceRepository.getAllCustomSubstances(),
        intakeLimits = experienceRepository.getAllIntakeLimits(),
        customSubstanceDetails = experienceRepository.getAllCustomSubstanceDetails(),
        bloodPressureReadings = experienceRepository.getAllBloodPressureReadings(),
    )

/**
 * Reads the whole journal and serializes it, applying [filter] first.
 *
 * Reminders and webhooks are settings rather than journal content, so a filtered (partial) export
 * leaves them out; a full export keeps them.
 */
suspend fun buildJournalExportJson(
    experienceRepository: ExperienceRepository,
    webhookRepository: WebhookRepository,
    filter: ExportFilter = ExportFilter(),
): String {
  val data = loadExportData(experienceRepository).filtered(filter)
  val journalExport =
      data.toJournalExport(
          reminders = if (filter.isActive) emptyList() else experienceRepository.getAllReminders(),
          webhooks =
              if (filter.isActive) {
                emptyList()
              } else {
                webhookRepository.getAll().map { WebhookSerializable.fromEntity(it) }
              },
          substanceGroups =
              if (filter.isActive) {
                emptyList()
              } else {
                experienceRepository.getAllSubstanceGroupsWithItems().map {
                  SubstanceGroupSerializable(name = it.group.name, items = it.sortedItems)
                }
              },
          // Recipes are presets like substance groups, so a partial export leaves them out too.
          customRecipes =
              if (filter.isActive) {
                emptyList()
              } else {
                experienceRepository.getAllCustomRecipesWithComponents().map { it.toSerializable() }
              },
      )
  return Json.encodeToString(journalExport)
}

fun ExportData.toJournalExport(
    reminders: List<Reminder> = emptyList(),
    webhooks: List<WebhookSerializable> = emptyList(),
    substanceGroups: List<SubstanceGroupSerializable> = emptyList(),
    customRecipes: List<CustomRecipeSerializable> = emptyList(),
): JournalExport =
    toJournalExport(
        reminders = reminders,
        webhooks = webhooks,
        substanceGroups = substanceGroups,
        customRecipes = customRecipes,
        bpByExperienceId = bloodPressureReadings.groupBy { it.experienceId },
    )

private fun ExportData.toJournalExport(
    reminders: List<Reminder>,
    webhooks: List<WebhookSerializable>,
    substanceGroups: List<SubstanceGroupSerializable>,
    customRecipes: List<CustomRecipeSerializable>,
    bpByExperienceId:
        Map<Int, List<foo.pilz.freaklog.data.room.experiences.entities.BloodPressureReading>>,
): JournalExport =
    JournalExport(
        substanceGroups = substanceGroups,
        customRecipes = customRecipes,
        experiences =
            experiences.map { experience ->
              val readings = bpByExperienceId[experience.experience.id].orEmpty()
              experience
                  .toSerializable()
                  .copy(
                      bloodPressure =
                          readings.map {
                            BloodPressureSerializable(it.time, it.systolic, it.diastolic, it.pulse)
                          }
                  )
            },
        substanceCompanions = substanceCompanions,
        customSubstances = customSubstances,
        customUnits =
            customUnits.map {
              CustomUnitSerializable(
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
                  defaultCategory = it.defaultCategory,
              )
            },
        reminders = reminders,
        webhooks = webhooks,
        customSubstanceDetails = customSubstanceDetails,
        intakeLimits =
            intakeLimits.map {
              IntakeLimitSerializable(
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
            },
    )

private fun foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsTimedNotesAndRatings
    .toSerializable() =
    ExperienceSerializable(
        title = experience.title,
        text = experience.text,
        creationDate = experience.creationDate,
        sortDate = experience.sortDate,
        isFavorite = experience.isFavorite,
        ingestions =
            ingestions.map { ingestion ->
              IngestionSerializable(
                  substanceName = ingestion.substanceName,
                  time = ingestion.time,
                  endTime = ingestion.endTime,
                  creationDate = ingestion.creationDate,
                  administrationRoute = ingestion.administrationRoute,
                  dose = ingestion.dose,
                  estimatedDoseStandardDeviation = ingestion.estimatedDoseStandardDeviation,
                  isDoseAnEstimate = ingestion.isDoseAnEstimate,
                  units = ingestion.units,
                  notes = ingestion.notes,
                  stomachFullness = ingestion.stomachFullness,
                  consumerName = ingestion.consumerName,
                  customUnitId = ingestion.customUnitId,
                  administrationSite = ingestion.administrationSite,
                  formulationName = ingestion.formulationName,
                  saltForm = ingestion.saltForm,
                  category = ingestion.category,
              )
            },
        location =
            experience.location?.let {
              LocationSerializable(name = it.name, latitude = it.latitude, longitude = it.longitude)
            },
        ratings =
            ratings.map { rating ->
              RatingSerializable(
                  option = rating.option,
                  time = rating.time,
                  creationDate = rating.creationDate,
              )
            },
        timedNotes =
            timedNotes.map { timedNote ->
              TimedNoteSerializable(
                  creationDate = timedNote.creationDate,
                  time = timedNote.time,
                  note = timedNote.note,
                  color = timedNote.color,
                  isPartOfTimeline = timedNote.isPartOfTimeline,
              )
            },
    )

private fun CustomRecipeWithComponents.toSerializable() =
    CustomRecipeSerializable(
        id = recipe.id,
        name = recipe.name,
        creationDate = recipe.creationDate,
        lastUsedDate = recipe.lastUsedDate,
        isArchived = recipe.isArchived,
        note = recipe.note,
        components =
            components
                .sortedBy { it.componentOrder }
                .map {
                  CustomRecipeComponentSerializable(
                      substanceName = it.substanceName,
                      dose = it.dose,
                      isDoseAnEstimate = it.isDoseAnEstimate,
                      estimatedDoseStandardDeviation = it.estimatedDoseStandardDeviation,
                      units = it.units,
                      administrationRoute = it.administrationRoute,
                      customUnitId = it.customUnitId,
                      componentOrder = it.componentOrder,
                  )
                },
    )
