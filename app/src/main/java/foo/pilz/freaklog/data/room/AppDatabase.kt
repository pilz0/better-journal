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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import foo.pilz.freaklog.data.room.experiences.CustomRecipeDao
import foo.pilz.freaklog.data.room.experiences.ExperienceDao
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColorConverter
import foo.pilz.freaklog.data.room.experiences.entities.AdministrationRouteConverter
import foo.pilz.freaklog.data.room.experiences.entities.CustomRecipe
import foo.pilz.freaklog.data.room.experiences.entities.CustomRecipeComponent
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.experiences.entities.CustomUnit
import foo.pilz.freaklog.data.room.experiences.entities.Experience
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.entities.IngestionChangeLog
import foo.pilz.freaklog.data.room.experiences.entities.InstantConverter
import foo.pilz.freaklog.data.room.experiences.entities.ShulginRating
import foo.pilz.freaklog.data.room.experiences.entities.Spray
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.room.experiences.entities.TimedNote
import foo.pilz.freaklog.data.room.inventory.InventoryDao
import foo.pilz.freaklog.data.room.inventory.InventoryItem
import foo.pilz.freaklog.data.room.reminders.ReminderDao
import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import foo.pilz.freaklog.data.room.webhooks.IngestionWebhookMessageDao
import foo.pilz.freaklog.data.room.webhooks.WebhookDao
import foo.pilz.freaklog.data.room.webhooks.entities.IngestionWebhookMessage
import foo.pilz.freaklog.data.room.webhooks.entities.Webhook

@TypeConverters(InstantConverter::class, AdaptiveColorConverter::class, AdministrationRouteConverter::class)
@Database(
    version = 25,
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
        InventoryItem::class,
        Webhook::class,
        IngestionWebhookMessage::class,
        IngestionChangeLog::class,
        foo.pilz.freaklog.data.room.experiences.entities.CustomFormulation::class,
        foo.pilz.freaklog.data.room.experiences.entities.IntakeLimit::class,
        foo.pilz.freaklog.data.room.experiences.entities.CustomRoa::class,
        foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDose::class,
        foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDuration::class,
        foo.pilz.freaklog.data.room.experiences.entities.CustomInteraction::class,
        foo.pilz.freaklog.data.room.experiences.entities.CustomCrossTolerance::class
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
        AutoMigration (from = 17, to = 18),
        AutoMigration (from = 18, to = 19, spec = AppDatabase.Migration18To19::class),
        AutoMigration (from = 19, to = 20),
        AutoMigration (from = 20, to = 21, spec = AppDatabase.Migration20To21::class),
        AutoMigration (from = 22, to = 23),
        AutoMigration (from = 23, to = 24),
        AutoMigration (from = 24, to = 25),
    ]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun experienceDao(): ExperienceDao
    abstract fun sprayDao(): SprayDao
    abstract fun reminderDao(): ReminderDao
    abstract fun customRecipeDao(): CustomRecipeDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun webhookDao(): WebhookDao
    abstract fun ingestionWebhookMessageDao(): IngestionWebhookMessageDao
    abstract fun customFormulationDao(): foo.pilz.freaklog.data.room.experiences.CustomFormulationDao
    abstract fun customSubstanceDao(): foo.pilz.freaklog.data.room.experiences.CustomSubstanceDao

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

    class Migration18To19 : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE Ingestion SET administrationRoute = 'ORAL', formulationName = 'medikinet' WHERE administrationRoute = 'MEDIKINET'")
            db.execSQL("UPDATE Ingestion SET administrationRoute = 'ORAL', formulationName = 'kinecteen' WHERE administrationRoute = 'KINECTEEN'")
        }
    }

    /**
     * Cleans up any remaining KINECTEEN/MEDIKINET values from tables that were
     * not covered by Migration18To19 (custom_unit, custom_recipe_component,
     * custom_formulation). A defensive [AdministrationRouteConverter] is also
     * registered so unknown enum values will fall back to ORAL instead of crashing.
     */
    class Migration20To21 : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            val routeColumns = listOf(
                "CustomUnit" to "administrationRoute",
                "CustomRecipeComponent" to "administrationRoute",
                "custom_formulation" to "baseRoa",
            )
            for ((table, column) in routeColumns) {
                db.execSQL("UPDATE $table SET $column = 'ORAL' WHERE $column IN ('KINECTEEN','MEDIKINET')")
            }
            // Reminder stores the route as a plain nullable String — clear invalid values.
            db.execSQL("UPDATE reminder SET administrationRoute = NULL WHERE administrationRoute IN ('KINECTEEN','MEDIKINET')")
        }
    }

    companion object {
        /**
         * The current schema version. Kept in sync with the `version = ` field
         * on the `@Database` annotation above. Exposed for migration tests so
         * they don't have to hard-code the value.
         */
        const val LATEST_SCHEMA_VERSION: Int = 25

        /**
         * Installs change-log triggers on the Ingestion table. Called from both
         * [MIGRATION_21_22] (upgrade path) and the RoomDatabase.Callback in
         * AppModule (fresh-install path) so triggers exist regardless of how
         * the database was created.
         */
        fun createChangeLogTriggers(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `ingestion_change_log_insert`" +
                    " AFTER INSERT ON `Ingestion` BEGIN" +
                    " INSERT INTO `ingestion_change_log`" +
                    " (`ingestion_id`, `op`, `changed_at`)" +
                    " VALUES (NEW.`id`, 'INSERT'," +
                    " CAST(strftime('%s','now') AS INTEGER)); END"
            )

            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `ingestion_change_log_update`" +
                    " AFTER UPDATE ON `Ingestion` BEGIN" +
                    " INSERT INTO `ingestion_change_log`" +
                    " (`ingestion_id`, `op`, `changed_at`)" +
                    " VALUES (NEW.`id`, 'UPDATE'," +
                    " CAST(strftime('%s','now') AS INTEGER)); END"
            )

            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `ingestion_change_log_delete`" +
                    " AFTER DELETE ON `Ingestion` BEGIN" +
                    " INSERT INTO `ingestion_change_log`" +
                    " (`ingestion_id`, `op`, `changed_at`)" +
                    " VALUES (OLD.`id`, 'DELETE'," +
                    " CAST(strftime('%s','now') AS INTEGER)); END"
            )

            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `ingestion_change_log_cap`" +
                    " AFTER INSERT ON `ingestion_change_log` BEGIN" +
                    " DELETE FROM `ingestion_change_log`" +
                    " WHERE `seq` <= NEW.`seq` - 1000; END"
            )
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ingestion_change_log`" +
                        " (`seq` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                        " `ingestion_id` INTEGER NOT NULL," +
                        " `op` TEXT NOT NULL," +
                        " `changed_at` INTEGER NOT NULL)"
                )
                createChangeLogTriggers(db)
            }
        }
    }
}