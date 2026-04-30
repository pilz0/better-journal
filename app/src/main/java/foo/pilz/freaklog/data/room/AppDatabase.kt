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

package foo.pilz.freaklog.data.room

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import foo.pilz.freaklog.data.room.experiences.CustomRecipeDao
import foo.pilz.freaklog.data.room.experiences.ExperienceDao
import foo.pilz.freaklog.data.room.experiences.entities.CustomRecipe
import foo.pilz.freaklog.data.room.experiences.entities.CustomRecipeComponent
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColorConverter
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.experiences.entities.CustomUnit
import foo.pilz.freaklog.data.room.experiences.entities.Experience
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.entities.InstantConverter
import foo.pilz.freaklog.data.room.experiences.entities.ShulginRating
import foo.pilz.freaklog.data.room.experiences.entities.Spray
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.room.experiences.entities.TimedNote
import foo.pilz.freaklog.data.room.inventory.InventoryDao
import foo.pilz.freaklog.data.room.inventory.InventoryItem
import foo.pilz.freaklog.data.room.reminders.ReminderDao
import foo.pilz.freaklog.data.room.reminders.entities.Reminder

@TypeConverters(InstantConverter::class, AdaptiveColorConverter::class)
@Database(
    version = 17,
    entities = [
        Experience::class,
        Ingestion::class,
        SubstanceCompanion::class,
        CustomSubstance::class,
        ShulginRating::class,
        TimedNote::class,
        CustomUnit::class,
        Spray::class,
        Reminder::class,
        CustomRecipe::class,
        CustomRecipeComponent::class,
        InventoryItem::class
    ],
    autoMigrations = [
        AutoMigration (from = 1, to = 2),
        AutoMigration (from = 2, to = 3),
        AutoMigration (from = 3, to = 4),
        AutoMigration (from = 4, to = 5),
        AutoMigration (from = 5, to = 6),
        AutoMigration (from = 6, to = 7),
        AutoMigration (from = 7, to = 8),
        AutoMigration (from = 8, to = 9),
        AutoMigration (from = 9, to = 10),
        AutoMigration (from = 10, to = 11),
        AutoMigration (from = 11, to = 12),
        AutoMigration (from = 12, to = 14),
        AutoMigration (from = 14, to = 15),
        AutoMigration (from = 15, to = 16),
        AutoMigration (from = 16, to = 17, spec = AppDatabase.ReminderV16To17::class),
    ]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun experienceDao(): ExperienceDao
    abstract fun sprayDao(): SprayDao
    abstract fun reminderDao(): ReminderDao
    abstract fun customRecipeDao(): CustomRecipeDao
    abstract fun inventoryDao(): InventoryDao

    /**
     * Legacy reminders (schema ≤ 16) only had interval-based scheduling. The v17 schema adds
     * a `scheduleType` column whose default is `DAILY_AT_TIMES`, but applying that default
     * to existing rows would silently disable them (no `timesOfDay` set). Force-update legacy
     * rows to keep their interval semantics.
     */
    class ReminderV16To17 : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE reminder SET scheduleType = 'INTERVAL'")
        }
    }

    companion object {
        /**
         * The current schema version. Kept in sync with the `version = ` field
         * on the `@Database` annotation above. Exposed for migration tests so
         * they don't have to hard-code the value.
         */
        const val LATEST_SCHEMA_VERSION: Int = 17
    }
}