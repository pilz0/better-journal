/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 */

package foo.pilz.freaklog.data.room.experiences

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import foo.pilz.freaklog.data.export.ExperienceSerializable
import foo.pilz.freaklog.data.export.IngestionSerializable
import foo.pilz.freaklog.data.export.JournalExport
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.room.AppDatabase
import foo.pilz.freaklog.testing.EntityBuilders
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReplaceEverythingTest {

  private lateinit var db: AppDatabase
  private lateinit var dao: ExperienceDao

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    dao = db.experienceDao()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `replaceEverything atomically replaces existing experiences and ingestions with new export`() =
      runTest {
        val oldId = dao.insert(EntityBuilders.experience(title = "Old experience")).toInt()
        dao.insert(EntityBuilders.ingestion(experienceId = oldId))

        val export =
            JournalExport(
                experiences =
                    listOf(
                        ExperienceSerializable(
                            title = "Imported experience",
                            text = "Freshly imported",
                            creationDate = Instant.parse("2026-02-01T12:00:00Z"),
                            sortDate = Instant.parse("2026-02-01T12:00:00Z"),
                            isFavorite = true,
                            location = null,
                            ingestions = emptyList(),
                            ratings = emptyList(),
                            timedNotes = emptyList(),
                            bloodPressure = emptyList(),
                        )
                    ),
                customUnits = emptyList(),
                reminders = emptyList(),
                webhooks = emptyList(),
                substanceGroups = emptyList(),
                customSubstances = emptyList(),
                customSubstanceDetails = emptyList(),
                intakeLimits = emptyList(),
            )

        dao.replaceEverything(export)

        val imported = dao.getExperience(1)
        assertNotNull(imported)
        assertEquals("Imported experience", imported!!.title)
      }

  @Test
  fun `replaceEverything preserves administrationSite on imported ingestions`() = runTest {
    val export =
        JournalExport(
            experiences =
                listOf(
                    ExperienceSerializable(
                        title = "Test",
                        text = "",
                        creationDate = FIXED_INSTANT,
                        sortDate = FIXED_INSTANT,
                        isFavorite = false,
                        location = null,
                        ingestions =
                            listOf(
                                IngestionSerializable(
                                    substanceName = "Test Substance",
                                    time = FIXED_INSTANT,
                                    endTime = null,
                                    creationDate = FIXED_INSTANT,
                                    administrationRoute = AdministrationRoute.ORAL,
                                    dose = 100.0,
                                    isDoseAnEstimate = false,
                                    estimatedDoseStandardDeviation = null,
                                    units = "mg",
                                    notes = null,
                                    stomachFullness = null,
                                    consumerName = null,
                                    customUnitId = null,
                                    administrationSite = "left arm",
                                    webhookMessageId = "legacy-msg-123",
                                    formulationName = "Ritalin LA",
                                    customFormulationId = 42,
                                    saltForm = "HCl",
                                    category = null,
                                )
                            ),
                        ratings = emptyList(),
                        timedNotes = emptyList(),
                        bloodPressure = emptyList(),
                    )
                ),
            customUnits = emptyList(),
            reminders = emptyList(),
            webhooks = emptyList(),
            substanceGroups = emptyList(),
            customSubstances = emptyList(),
            customSubstanceDetails = emptyList(),
            intakeLimits = emptyList(),
        )

    dao.replaceEverything(export)

    val importedIngestion = dao.getIngestionsSortedDescendingFlow().first().first()
    assertNotNull(importedIngestion)
    assertEquals("left arm", importedIngestion.administrationSite)
    assertEquals("HCl", importedIngestion.saltForm)
    assertEquals("legacy-msg-123", importedIngestion.webhookMessageId)
    assertEquals("Ritalin LA", importedIngestion.formulationName)
    assertEquals(42, importedIngestion.customFormulationId)
  }

  companion object {
    private val FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z")
  }
}
