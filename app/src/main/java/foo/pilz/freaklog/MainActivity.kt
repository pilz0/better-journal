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
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import foo.pilz.freaklog.ui.MyAppWidget
import foo.pilz.freaklog.ui.main.MainScreen
import foo.pilz.freaklog.ui.tabs.journal.experience.notification.TimelineNotificationService.Companion.GLOBAL_TIMELINE_ID
import foo.pilz.freaklog.ui.tabs.settings.lock.BiometricAuthManager
import foo.pilz.freaklog.ui.theme.JournalTheme
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @javax.inject.Inject lateinit var notificationScheduler: foo.pilz.freaklog.scheduled.NotificationScheduler

    @javax.inject.Inject lateinit var biometricAuthManager: BiometricAuthManager

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

        // Re-arm all reminder alarms on each launch. AlarmManager forgets alarms across
        // process death/reboot, and BootCompletedReceiver is the first line of defence;
        // this catches the cases where the receiver was disabled (e.g. force-stopped app).
        lifecycleScope.launch {
            notificationScheduler.rescheduleAll()
        }

        setContent {
            JournalTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    foo.pilz.freaklog.ui.tabs.settings.lock.BiometricAuthWrapper(
                        manager = biometricAuthManager,
                        activity = this@MainActivity,
                    ) {
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
    }

    override fun onPause() {
        super.onPause()
        biometricAuthManager.onAppPaused()
    }

    override fun onResume() {
        super.onResume()
        biometricAuthManager.onAppResumed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            "${packageName}$ACTION_ADD_INGESTION" -> {
                shouldNavigateToJournalScreen = false
                shouldNavigateToExperienceId = null
                shouldNavigateToAddIngestion = true
            }
            "${packageName}$ACTION_JOURNAL_SCREEN" -> {
                shouldNavigateToAddIngestion = false
                shouldNavigateToExperienceId = null
                shouldNavigateToJournalScreen = true
            }
            "${packageName}$ACTION_OPEN_EXPERIENCE" -> {
                val experienceId = intent.getIntExtra("experienceId", -1)
                shouldNavigateToAddIngestion = false
                if (experienceId == GLOBAL_TIMELINE_ID) {
                    shouldNavigateToExperienceId = null
                    shouldNavigateToJournalScreen = true
                } else if (experienceId > 0) {
                    shouldNavigateToJournalScreen = false
                    shouldNavigateToExperienceId = experienceId
                }
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
