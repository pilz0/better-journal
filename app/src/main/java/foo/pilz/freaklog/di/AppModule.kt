/*
 * Copyright (c) 2022. Isaak Hanimann.
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

package foo.pilz.freaklog.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import foo.pilz.freaklog.data.room.AppDatabase
import foo.pilz.freaklog.data.room.experiences.CustomRecipeDao
import foo.pilz.freaklog.data.room.reminders.ReminderDao
import foo.pilz.freaklog.data.room.SprayDao
import foo.pilz.freaklog.data.room.experiences.ExperienceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideExperiencesDao(appDatabase: AppDatabase): ExperienceDao =
        appDatabase.experienceDao()

    @Singleton
    @Provides
    fun provideSprayDao(appDatabase: AppDatabase): SprayDao =
        appDatabase.sprayDao()

    @Singleton
    @Provides
    fun provideCustomRecipeDao(appDatabase: AppDatabase): CustomRecipeDao =
        appDatabase.customRecipeDao()

    @Singleton
    @Provides
    fun provideReminderDao(appDatabase: AppDatabase): ReminderDao =
        appDatabase.reminderDao()

    @Singleton
    @Provides
    fun provideInventoryDao(appDatabase: AppDatabase): foo.pilz.freaklog.data.room.inventory.InventoryDao =
        appDatabase.inventoryDao()

    @Singleton
    @Provides
    fun provideWebhookDao(appDatabase: AppDatabase): foo.pilz.freaklog.data.room.webhooks.WebhookDao =
        appDatabase.webhookDao()

    @Singleton
    @Provides
    fun provideIngestionWebhookMessageDao(
        appDatabase: AppDatabase
    ): foo.pilz.freaklog.data.room.webhooks.IngestionWebhookMessageDao =
        appDatabase.ingestionWebhookMessageDao()

    @Singleton
    @Provides
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "experiences_db"
        ).build()

    @Singleton
    @Provides
    fun providePreferencesDataStore(@ApplicationContext appContext: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler(
                produceNewData = { emptyPreferences() }
            ),
            produceFile = { appContext.preferencesDataStoreFile("user_preferences") }
        )
    }

    @Singleton
    @Provides
    @ApplicationScope
    fun providesCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class ApplicationScope