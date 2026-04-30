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

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import foo.pilz.freaklog.data.room.webhooks.WebhookSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class JournalApplication : Application() {

    @Inject
    lateinit var webhookSeeder: WebhookSeeder

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            // Migrate the legacy single-webhook configuration into the new
            // multi-webhook tables on first launch after upgrade. Idempotent.
            // Wrap in try/catch so a failed migration cannot crash app startup.
            try {
                webhookSeeder.seedIfNeeded()
            } catch (e: Exception) {
                android.util.Log.e("JournalApplication", "Webhook seeding failed", e)
            }
        }
    }
}