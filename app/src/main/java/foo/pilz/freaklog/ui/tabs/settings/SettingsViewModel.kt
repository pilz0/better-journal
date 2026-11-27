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

package foo.pilz.freaklog.ui.tabs.settings

import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.scheduled.NotificationScheduler
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import foo.pilz.freaklog.ui.tabs.settings.funny.FunnyConfig
import foo.pilz.freaklog.ui.tabs.settings.lock.BiometricAuthManager
import foo.pilz.freaklog.ui.tabs.settings.lock.BiometricAvailability
import foo.pilz.freaklog.ui.tabs.settings.lock.LockTimeOption
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    private val experienceRepository: ExperienceRepository,
    private val userPreferences: UserPreferences,
    private val biometricAuthManager: BiometricAuthManager,
    private val notificationScheduler: NotificationScheduler,
) : ViewModel() {

  fun saveDosageDotsAreHidden(value: Boolean) = viewModelScope.launch {
    userPreferences.saveDosageDotsAreHidden(value)
  }

  fun saveAreSubstanceHeightsIndependent(value: Boolean) = viewModelScope.launch {
    userPreferences.saveAreSubstanceHeightsIndependent(value)
  }

  fun saveFunnyConfig(funnyConfig: FunnyConfig) = viewModelScope.launch {
    userPreferences.saveFunnyConfig(funnyConfig)
  }

  val funnyConfigFlow =
      userPreferences.funnyConfigFlow.stateIn(
          initialValue = FunnyConfig(),
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
      )

  fun saveTimelineNotificationAutoStart(value: Boolean) = viewModelScope.launch {
    userPreferences.saveTimelineNotificationAutoStartEnabled(value)
  }

  val isTimelineNotificationAutoStartFlow =
      userPreferences.isTimelineNotificationAutoStartEnabledFlow.stateIn(
          initialValue = false,
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
      )

  fun saveIsTimelineHidden(value: Boolean) = viewModelScope.launch {
    userPreferences.saveIsTimelineHidden(value)
  }

  val isTimelineHiddenFlow =
      userPreferences.isTimelineHiddenFlow.stateIn(
          initialValue = false,
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
      )

  fun saveIsStatsHidden(value: Boolean) = viewModelScope.launch {
    userPreferences.saveIsStatsHidden(value)
  }

  val isStatsHiddenFlow =
      userPreferences.isStatsHiddenFlow.stateIn(
          initialValue = false,
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
      )

  fun saveIsDrugsHidden(value: Boolean) = viewModelScope.launch {
    userPreferences.saveIsDrugsHidden(value)
  }

  val isDrugsHiddenFlow =
      userPreferences.isDrugsHiddenFlow.stateIn(
          initialValue = false,
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
      )

  val areSubstanceHeightsIndependentFlow =
      userPreferences.areSubstanceHeightsIndependentFlow.stateIn(
          initialValue = false,
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
      )

  val areDosageDotsHiddenFlow =
      userPreferences.areDosageDotsHiddenFlow.stateIn(
          initialValue = false,
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
      )

  fun saveActivateSafer(value: Boolean) = viewModelScope.launch {
    userPreferences.saveActivateSafer(value)
  }

  val activateSaferFlow =
      userPreferences.activateSaferFlow.stateIn(
          initialValue = false,
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
      )

  val isRedoseShownFlow =
      userPreferences.isRedoseShownFlow.stateIn(
          initialValue = false,
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
      )

  fun saveRedoseShown(value: Boolean) = viewModelScope.launch {
    userPreferences.saveRedoseShown(value)
  }

  val redoseOnsetFractionFlow =
      userPreferences.redoseOnsetFractionFlow.stateIn(
          initialValue = 1.0f,
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
      )

  val redoseComeupFractionFlow =
      userPreferences.redoseComeupFractionFlow.stateIn(
          initialValue = 1.0f,
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
      )

  val redosePeakFractionFlow =
      userPreferences.redosePeakFractionFlow.stateIn(
          initialValue = 0.5f,
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
      )

  fun saveRedoseFractions(onset: Float, comeup: Float, peak: Float) = viewModelScope.launch {
    userPreferences.saveRedoseFractions(onset, comeup, peak)
  }

  fun saveHapticFeedbackEnabled(value: Boolean) = viewModelScope.launch {
    userPreferences.saveHapticFeedbackEnabled(value)
  }

  val isHapticFeedbackEnabledFlow =
      userPreferences.isHapticFeedbackEnabledFlow.stateIn(
          initialValue = true,
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
      )

  val aiAssistantEnabledFlow =
      userPreferences.aiAssistantEnabledFlow.stateIn(
          initialValue = false,
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
      )

  fun saveAiAssistantEnabled(value: Boolean) = viewModelScope.launch {
    userPreferences.saveAiAssistantEnabled(value)
  }

  val useIntranasalForRectalFlow =
      userPreferences.useIntranasalForRectalFlow.stateIn(
          initialValue = false,
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
      )

  fun saveUseIntranasalForRectal(value: Boolean) = viewModelScope.launch {
    userPreferences.saveUseIntranasalForRectal(value)
  }

  // ---- App lock ----

  val isLockEnabledFlow =
      userPreferences.isLockEnabledFlow.stateIn(
          initialValue = false,
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
      )

  val lockTimeOptionFlow =
      userPreferences.lockTimeOptionFlow.stateIn(
          initialValue = LockTimeOption.DEFAULT,
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
      )

  fun biometricAvailability(): BiometricAvailability = biometricAuthManager.availability()

  fun saveLockEnabled(value: Boolean) {
    biometricAuthManager.setLockEnabled(value)
  }

  fun saveLockTimeOption(value: LockTimeOption) {
    biometricAuthManager.setLockTimeOption(value)
  }

  val snackbarHostState = SnackbarHostState()

  fun deleteEverything() {
    if (biometricAuthManager.shouldLock.value) return
    viewModelScope.launch {
      if (biometricAuthManager.shouldLock.value) return@launch
      experienceRepository.getAllReminders().forEach { notificationScheduler.cancelReminder(it.id) }
      experienceRepository.deleteEverything()
    }
  }
}
