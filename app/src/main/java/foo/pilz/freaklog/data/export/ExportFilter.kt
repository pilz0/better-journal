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

import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.experiences.entities.CustomUnit
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.entities.IntakeLimit
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsTimedNotesAndRatings
import foo.pilz.freaklog.data.substances.classes.IngestionCategory

/**
 * What a partial export keeps. The defaults keep everything, so
 * `ExportFilter()` is a full export.
 */
data class ExportFilter(
    val category: IngestionCategory? = null,
    val substanceNames: Set<String>? = null,
    val includeOtherConsumers: Boolean = true,
    val includeNotes: Boolean = true,
    val includeTimedNotes: Boolean = true,
    val includeLocations: Boolean = true,
    val includeRatings: Boolean = true,
) {
    /** True when the filter can drop whole ingestions (and with them their dependants). */
    val hasIngestionFilters: Boolean
        get() = category != null || substanceNames != null || !includeOtherConsumers

    val isActive: Boolean
        get() = hasIngestionFilters ||
            !includeNotes || !includeTimedNotes || !includeLocations || !includeRatings
}

/** The journal rows an export is built from, before serialization. */
data class ExportData(
    val experiences: List<ExperienceWithIngestionsTimedNotesAndRatings>,
    val customUnits: List<CustomUnit>,
    val substanceCompanions: List<SubstanceCompanion>,
    val customSubstances: List<CustomSubstance>,
    val intakeLimits: List<IntakeLimit> = emptyList(),
    val customSubstanceDetails: List<foo.pilz.freaklog.data.substanceshare.SharedSubstance> = emptyList(),
) {
    fun filtered(filter: ExportFilter): ExportData {
        if (!filter.isActive) return this

        val unitsById = customUnits.associateBy { it.id }
        val companionsByName = substanceCompanions.associateBy { it.substanceName }

        fun matches(ingestion: Ingestion): Boolean {
            val computedCategory = ingestion.computedCategory(
                customUnit = ingestion.customUnitId?.let { unitsById[it] },
                substanceCompanion = companionsByName[ingestion.substanceName],
            )
            return (filter.includeOtherConsumers || ingestion.consumerName == null) &&
                (filter.substanceNames == null || ingestion.substanceName in filter.substanceNames) &&
                (filter.category == null || computedCategory == filter.category)
        }

        fun scrub(ingestion: Ingestion): Ingestion =
            if (filter.includeNotes) ingestion else ingestion.copy(notes = null)

        val filteredExperiences = experiences
            .map { exp ->
                exp.copy(
                    experience = exp.experience.copy(
                        text = if (filter.includeNotes) exp.experience.text else "",
                        location = if (filter.includeLocations) exp.experience.location else null,
                    ),
                    ingestions = exp.ingestions.filter(::matches).map(::scrub),
                    timedNotes = if (filter.includeTimedNotes) exp.timedNotes else emptyList(),
                    ratings = if (filter.includeRatings) exp.ratings else emptyList(),
                )
            }
            .filter { !filter.hasIngestionFilters || it.ingestions.isNotEmpty() }

        val keptIngestions = filteredExperiences.flatMap { it.ingestions }
        val keptSubstanceNames = keptIngestions.map { it.substanceName }.toSet()
        val keptUnitIds = keptIngestions.mapNotNull { it.customUnitId }.toSet()
        val prune = filter.hasIngestionFilters

        return ExportData(
            experiences = filteredExperiences,
            customUnits = customUnits
                .filter { !prune || it.id in keptUnitIds }
                .map { if (filter.includeNotes) it else it.copy(note = "") },
            substanceCompanions = substanceCompanions.filter { !prune || it.substanceName in keptSubstanceNames },
            customSubstances = customSubstances.filter { !prune || it.name in keptSubstanceNames },
            intakeLimits = intakeLimits.filter { !prune || it.substanceName in keptSubstanceNames },
            customSubstanceDetails = customSubstanceDetails.filter { !prune || it.name in keptSubstanceNames },
        )
    }
}
