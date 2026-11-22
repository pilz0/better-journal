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

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import foo.pilz.freaklog.data.room.AppDatabase
import foo.pilz.freaklog.data.room.experiences.entities.CustomFormulation
import foo.pilz.freaklog.data.room.experiences.entities.CustomRecipe
import foo.pilz.freaklog.data.room.experiences.entities.CustomRecipeComponent
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.experiences.entities.IntakeLimit
import foo.pilz.freaklog.data.room.experiences.entities.IntakeLimitType
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroup
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroupItem
import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.testing.EntityBuilders
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RenameCustomSubstanceTest {
  private lateinit var db: AppDatabase
  private lateinit var dao: ExperienceDao

  private val tables =
      listOf(
          "ingestion",
          "IntakeLimit",
          "SubstanceGroupItem",
          "custom_formulation",
          "CustomRecipeComponent",
          "reminder",
      )

  @Before
  fun setUp() {
    db =
        Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                AppDatabase::class.java,
            )
            .allowMainThreadQueries()
            .build()
    dao = db.experienceDao()
  }

  @After fun tearDown() = db.close()

  private fun count(table: String, name: String): Int =
      db.query("SELECT COUNT(*) FROM $table WHERE substanceName = ?", arrayOf(name)).use {
        it.moveToFirst()
        it.getInt(0)
      }

  private suspend fun seed(name: String) {
    dao.insert(CustomSubstance(name = name, units = "mg", description = ""))
    val experienceId = dao.insert(EntityBuilders.experience()).toInt()
    dao.insert(EntityBuilders.ingestion(substanceName = name, experienceId = experienceId))
    dao.insert(
        IntakeLimit(
            substanceName = name,
            limitType = IntakeLimitType.COUNT,
            maxDose = null,
            unit = null,
            maxCount = 2,
            windowSeconds = 86_400,
            warningPercent = 80,
        )
    )
    dao.insertSubstanceGroupWithItems(
        SubstanceGroup(name = "Group"),
        listOf(
            SubstanceGroupItem(
                sortOrder = 0,
                substanceName = name,
                isCustomSubstance = true,
                administrationRoute = AdministrationRoute.ORAL,
            )
        ),
    )
    db.customFormulationDao()
        .insert(
            CustomFormulation(
                substanceName = name,
                baseRoa = AdministrationRoute.ORAL,
                name = "Retard",
            )
        )
    db.customRecipeDao()
        .insertRecipeWithComponents(
            CustomRecipe(name = "Recipe"),
            listOf(
                CustomRecipeComponent(
                    customRecipeId = 0,
                    substanceName = name,
                    dose = 1.0,
                    isDoseAnEstimate = false,
                    estimatedDoseStandardDeviation = null,
                    units = "mg",
                    administrationRoute = AdministrationRoute.ORAL,
                )
            ),
        )
    db.reminderDao()
        .insert(
            Reminder(
                title = "Take it",
                hour = 8,
                minute = 0,
                isEnabled = true,
                substanceName = name,
            )
        )
  }

  @Test
  fun renameMovesEveryReference() = runTest {
    seed("Old")

    assertThat(dao.renameCustomSubstance("Old", "New")).isTrue()

    tables.forEach { table ->
      assertThat(count(table, "Old")).isEqualTo(0)
      assertThat(count(table, "New")).isEqualTo(1)
    }
  }

  @Test
  fun renameIntoATakenNameChangesNothing() = runTest {
    seed("Old")
    dao.insert(SubstanceCompanion(substanceName = "Caffeine"))

    assertThat(dao.renameCustomSubstance("Old", "Caffeine")).isFalse()

    tables.forEach { assertThat(count(it, "Old")).isEqualTo(1) }
    assertThat(dao.getCustomSubstanceWithEverythingByName("Old")).isNotNull()
  }
}
