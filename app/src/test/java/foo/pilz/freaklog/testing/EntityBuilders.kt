/*
 * Copyright (c) 2024-2026. Freaklog contributors.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.testing

import foo.pilz.freaklog.data.room.experiences.entities.Experience
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.entities.Location
import foo.pilz.freaklog.data.room.experiences.entities.StomachFullness
import foo.pilz.freaklog.data.substances.AdministrationRoute
import java.time.Instant

/**
 * Test-only builders for the most-used Room entities.
 *
 * Use named overrides to vary one field at a time; everything else stays at a
 * sensible default so the call site stays small. Times are deterministic
 * ([FIXED_INSTANT]) so equality assertions don't drift between machines.
 *
 * Example:
 * ```
 * val ing = EntityBuilders.ingestion(substanceName = "LSD", dose = 100.0, units = "ug")
 * ```
 */
object EntityBuilders {

    /** A stable instant used as the default for builder timestamps (2026-01-01T00:00:00Z). */
    val FIXED_INSTANT: Instant = Instant.parse("2026-01-01T00:00:00Z")

    fun experience(
        id: Int = 0,
        title: String = "Test Experience",
        text: String = "",
        creationDate: Instant = FIXED_INSTANT,
        sortDate: Instant = FIXED_INSTANT,
        isFavorite: Boolean = false,
        location: Location? = null,
    ): Experience = Experience(
        id = id,
        title = title,
        text = text,
        creationDate = creationDate,
        sortDate = sortDate,
        isFavorite = isFavorite,
        location = location,
    )

    @Suppress("LongParameterList")
    fun ingestion(
        id: Int = 0,
        substanceName: String = "Caffeine",
        time: Instant = FIXED_INSTANT,
        endTime: Instant? = null,
        creationDate: Instant? = FIXED_INSTANT,
        administrationRoute: AdministrationRoute = AdministrationRoute.ORAL,
        dose: Double? = 100.0,
        isDoseAnEstimate: Boolean = false,
        estimatedDoseStandardDeviation: Double? = null,
        units: String? = "mg",
        experienceId: Int = 0,
        notes: String? = null,
        stomachFullness: StomachFullness? = null,
        consumerName: String? = null,
        customUnitId: Int? = null,
        webhookMessageId: String? = null,
        administrationSite: String? = null,
    ): Ingestion = Ingestion(
        id = id,
        substanceName = substanceName,
        time = time,
        endTime = endTime,
        creationDate = creationDate,
        administrationRoute = administrationRoute,
        dose = dose,
        isDoseAnEstimate = isDoseAnEstimate,
        estimatedDoseStandardDeviation = estimatedDoseStandardDeviation,
        units = units,
        experienceId = experienceId,
        notes = notes,
        stomachFullness = stomachFullness,
        consumerName = consumerName,
        customUnitId = customUnitId,
        webhookMessageId = webhookMessageId,
        administrationSite = administrationSite,
    )
}

/** Read [EntityBuilders.FIXED_INSTANT] as a Long epoch-milli (e.g. for Room columns). */
val fixedEpochMilli: Long get() = EntityBuilders.FIXED_INSTANT.toEpochMilli()
