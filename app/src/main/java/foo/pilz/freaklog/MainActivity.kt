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

package foo.pilz.freaklog

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import foo.pilz.freaklog.ui.MyAppWidget
import foo.pilz.freaklog.ui.main.MainScreen
import foo.pilz.freaklog.ui.theme.JournalTheme
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_ADD_INGESTION = ".ADD_INGESTION"
        const val ACTION_JOURNAL_SCREEN = ".JOURNAL_SCREEN"
        const val ACTION_OPEN_EXPERIENCE = ".OPEN_EXPERIENCE"
    }

    private var shouldNavigateToAddIngestion by mutableStateOf(false)
    private var shouldNavigateToJournalScreen by mutableStateOf(false)
    private var shouldNavigateToExperienceId by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        // Update all widgets on launch
        lifecycleScope.launch {
            val manager = GlanceAppWidgetManager(this@MainActivity)
            val glanceIds = manager.getGlanceIds(MyAppWidget::class.java)
            glanceIds.forEach { glanceId ->
                foo.pilz.freaklog.ui.enqueueRefresh(
                    context = this@MainActivity,
                    appWidgetId = manager.getAppWidgetId(glanceId)
                )
            }
        }

        setContent {
            JournalTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        shouldNavigateToAddIngestion = shouldNavigateToAddIngestion,
                        onAddIngestionNavigated = ::onAddIngestionNavigated,
                        shouldNavigateToJournalScreen = shouldNavigateToJournalScreen,
                        onJournalScreenNavigated = ::onJournalScreenNavigated,
                        shouldNavigateToExperienceId = shouldNavigateToExperienceId,
                        onExperienceNavigated = ::onExperienceNavigated
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "${packageName}$ACTION_ADD_INGESTION") {
            shouldNavigateToAddIngestion = true
        } else if (intent?.action == "${packageName}$ACTION_JOURNAL_SCREEN") {
            shouldNavigateToJournalScreen = true
        } else if (intent?.action == "${packageName}$ACTION_OPEN_EXPERIENCE") {
            val experienceId = intent.getIntExtra("experienceId", -1)
            if (experienceId != -1) {
                shouldNavigateToExperienceId = experienceId
            }
        }
    }

    private fun onAddIngestionNavigated() {
        shouldNavigateToAddIngestion = false
    }

    private fun onJournalScreenNavigated() {
        shouldNavigateToJournalScreen = false
    }

    private fun onExperienceNavigated() {
        shouldNavigateToExperienceId = null
    }
}
