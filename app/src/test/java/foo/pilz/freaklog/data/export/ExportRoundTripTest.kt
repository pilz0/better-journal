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

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import foo.pilz.freaklog.data.room.AppDatabase
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomCategoryAssignment
import foo.pilz.freaklog.data.room.experiences.entities.CustomRecipe
import foo.pilz.freaklog.data.room.experiences.entities.CustomRecipeComponent
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.webhooks.WebhookRepository
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.testing.EntityBuilders
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Everything a backup writes must come back on restore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExportRoundTripTest {
  private lateinit var db: AppDatabase
  private lateinit var repository: ExperienceRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    repository =
        ExperienceRepository(
            db,
            db.experienceDao(),
            db.reminderDao(),
            db.webhookDao(),
            db.ingestionWebhookMessageDao(),
            context,
        )
  }

  @After fun tearDown() = db.close()

  @Test
  fun recipesFormulationsIngestionDetailsAndCategoriesSurviveARestore() = runTest {
    val dao = db.experienceDao()
    val experienceId = dao.insert(EntityBuilders.experience()).toInt()
    dao.insert(
        EntityBuilders.ingestion(
                substanceName = "Methylphenidate",
                experienceId = experienceId,
                administrationSite = "left nostril",
            )
            .copy(formulationName = "Medikinet retard")
    )
    db.customRecipeDao()
        .insertRecipeWithComponents(
            CustomRecipe(name = "Morning"),
            listOf(
                CustomRecipeComponent(
                    customRecipeId = 0,
                    substanceName = "Caffeine",
                    dose = 100.0,
                    isDoseAnEstimate = false,
                    estimatedDoseStandardDeviation = null,
                    units = "mg",
                    administrationRoute = AdministrationRoute.ORAL,
                )
            ),
        )
    val substanceId =
        dao.insert(CustomSubstance(name = "Only categories", units = "mg", description = ""))
            .toInt()
    dao.insert(
        CustomCategoryAssignment(customSubstanceId = substanceId, categoryName = "stimulant")
    )

    val json = buildJournalExportJson(repository, WebhookRepository(db.webhookDao()))
    repository.deleteEverything()
    repository.replaceEverything(Json { ignoreUnknownKeys = true }.decodeFromString(json))

    val ingestion = dao.getSortedIngestionsFlow().first().single()
    assertThat(ingestion.administrationSite).isEqualTo("left nostril")
    assertThat(ingestion.formulationName).isEqualTo("Medikinet retard")
    val recipe = db.customRecipeDao().getAllRecipesWithComponents().single()
    assertThat(recipe.recipe.name).isEqualTo("Morning")
    assertThat(recipe.components.map { it.substanceName }).containsExactly("Caffeine")
    assertThat(
            dao.getCustomSubstanceWithEverythingByName("Only categories")!!.categories.map {
              it.categoryName
            }
        )
        .containsExactly("stimulant")
  }

  @Test
  fun legacyExportsWithCustomFormulationsStillRestoreFormulationName() = runTest {
    val legacyJson =
        """
        {
          "experiences": [
            {
              "title": "Study session",
              "text": "",
              "creationDate": 1700000000000,
              "sortDate": 1700000000000,
              "ingestions": [
                {
                  "substanceName": "Methylphenidate",
                  "time": 1700000000000,
                  "endTime": null,
                  "creationDate": 1700000000000,
                  "administrationRoute": "ORAL",
                  "dose": 20.0,
                  "isDoseAnEstimate": false,
                  "estimatedDoseStandardDeviation": null,
                  "units": "mg",
                  "formulationName": "Medikinet retard",
                  "customFormulationId": 7
                }
              ]
            }
          ],
          "customFormulations": [
            {
              "id": 7,
              "substanceName": "Methylphenidate",
              "baseRoa": "ORAL",
              "name": "Medikinet retard"
            }
          ]
        }
        """
            .trimIndent()

    val export = Json { ignoreUnknownKeys = true }.decodeFromString<JournalExport>(legacyJson)
    repository.replaceEverything(export)

    val ingestion = db.experienceDao().getSortedIngestionsFlow().first().single()
    assertThat(ingestion.substanceName).isEqualTo("Methylphenidate")
    assertThat(ingestion.formulationName).isEqualTo("Medikinet retard")
  }
}
