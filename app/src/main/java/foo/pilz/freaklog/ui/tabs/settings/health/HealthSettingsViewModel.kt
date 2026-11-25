package foo.pilz.freaklog.ui.tabs.settings.health

import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.VitalKind
import foo.pilz.freaklog.ui.tabs.settings.combinations.BloodPressureDisplay
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HealthSettingsViewModel
@Inject
constructor(
    private val userPreferences: UserPreferences,
) : ViewModel() {

  val isVitalsEnabledFlow: StateFlow<Boolean> =
      userPreferences.isVitalsEnabledFlow.stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
          initialValue = false,
      )

  val sleepStagesEnabledFlow: StateFlow<Boolean> =
      userPreferences.areSleepStagesEnabledFlow.stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
          initialValue = false,
      )

  val bloodPressureDisplayFlow: StateFlow<BloodPressureDisplay> =
      userPreferences.bloodPressureDisplayFlow.stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
          initialValue = BloodPressureDisplay.COMBINED,
      )

  val extraVitalsFlow: StateFlow<Set<VitalKind>> =
      userPreferences.extraVitalsFlow.stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
          initialValue = emptySet(),
      )

  val aiAssistantEnabledFlow: StateFlow<Boolean> =
      userPreferences.aiAssistantEnabledFlow.stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
          initialValue = false,
      )

  val aiVitalsEnabledFlow: StateFlow<Boolean> =
      userPreferences.aiVitalsEnabledFlow.stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
          initialValue = false,
      )

  val snackbarHostState = SnackbarHostState()

  fun saveIsVitalsEnabled(value: Boolean) = viewModelScope.launch {
    userPreferences.saveIsVitalsEnabled(value)
  }

  fun saveSleepStagesEnabled(value: Boolean) = viewModelScope.launch {
    userPreferences.saveAreSleepStagesEnabled(value)
  }

  fun saveBloodPressureDisplay(value: BloodPressureDisplay) = viewModelScope.launch {
    userPreferences.saveBloodPressureDisplay(value)
  }

  fun saveExtraVitalEnabled(kind: VitalKind, enabled: Boolean) = viewModelScope.launch {
    userPreferences.saveExtraVitalEnabled(kind, enabled)
  }

  fun saveAiVitalsEnabled(value: Boolean) = viewModelScope.launch {
    userPreferences.saveAiVitalsEnabled(value)
  }

  fun showMessage(message: String) = viewModelScope.launch {
    snackbarHostState.showSnackbar(message)
  }
}
