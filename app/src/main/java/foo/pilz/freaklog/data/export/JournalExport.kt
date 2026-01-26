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

package foo.pilz.freaklog.data.export

import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.experiences.entities.ShulginRatingOption
import foo.pilz.freaklog.data.room.experiences.entities.StomachFullness
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.ui.tabs.settings.ShulginRatingOptionSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class JournalExport(
    val experiences: List<ExperienceSerializable> = emptyList(),
    val substanceCompanions: List<SubstanceCompanion> = emptyList(),
    val customSubstances: List<CustomSubstance> = emptyList(),
    val customUnits: List<CustomUnitSerializable> = emptyList(),
    val customRecipes: List<CustomRecipeSerializable> = emptyList(),
    val reminders: List<Reminder> = emptyList()
)

@Serializable
data class ExperienceSerializable(
    val title: String,
    val text: String,
    @Serializable(with= InstantSerializer::class) val creationDate: Instant = Instant.now(),
    @Serializable(with= InstantSerializer::class) val sortDate: Instant,
    val isFavorite: Boolean = false,
    val ingestions: List<IngestionSerializable> = emptyList(),
    val location: LocationSerializable? = null,
    val ratings: List<RatingSerializable> = emptyList(),
    val timedNotes: List<TimedNoteSerializable> = emptyList()
)

@Serializable
data class CustomUnitSerializable (
    val id: Int = 0,
    val substanceName: String,
    val name: String,
    @Serializable(with= InstantSerializer::class) val creationDate: Instant = Instant.now(),
    val administrationRoute: AdministrationRoute,
    var dose: Double? = null,
    var estimatedDoseStandardDeviation: Double? = null,
    var isEstimate: Boolean,
    var isArchived: Boolean,
    var unit: String,
    var unitPlural: String?,
    val originalUnit: String,
    var note: String
)

@Serializable
data class RatingSerializable(
    @Serializable(with= ShulginRatingOptionSerializer::class) val option: ShulginRatingOption,
    @Serializable(with= InstantSerializer::class) var time: Instant? = null,
    @Serializable(with= InstantSerializer::class) var creationDate: Instant? = Instant.now()
)

@Serializable
data class IngestionSerializable(
    val substanceName: String,
    @Serializable(with= InstantSerializer::class) var time: Instant,
    @Serializable(with= InstantSerializer::class) var endTime: Instant?,
    @Serializable(with= InstantSerializer::class) var creationDate: Instant? = Instant.now(),
    val administrationRoute: AdministrationRoute,
    var dose: Double? = null,
    var isDoseAnEstimate: Boolean,
    var estimatedDoseStandardDeviation: Double? = null,
    var units: String? = null,
    var notes: String? = null,
    var stomachFullness: StomachFullness? = null,
    var consumerName: String? = null,
    var customUnitId: Int? = null,
    var administrationSite: String? = null
)

@Serializable
data class LocationSerializable(
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Serializable
data class TimedNoteSerializable(
    @Serializable(with= InstantSerializer::class) var creationDate: Instant,
    @Serializable(with= InstantSerializer::class) var time: Instant,
    var note: String,
    var color: AdaptiveColor,
    var isPartOfTimeline: Boolean
)

@Serializable
data class CustomRecipeSerializable(
    val id: Int,
    val name: String,
    @Serializable(with= InstantSerializer::class) val creationDate: Instant,
    @Serializable(with= InstantSerializer::class) val lastUsedDate: Instant? = null,
    val isArchived: Boolean,
    val note: String,
    val components: List<CustomRecipeComponentSerializable> = emptyList()
)

@Serializable
data class CustomRecipeComponentSerializable(
    val substanceName: String,
    val dose: Double?,
    val isDoseAnEstimate: Boolean,
    val estimatedDoseStandardDeviation: Double?,
    val units: String?,
    val administrationRoute: AdministrationRoute,
    val customUnitId: Int? = null,
    val componentOrder: Int
)
