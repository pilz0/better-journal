package foo.pilz.freaklog.ui.tabs.settings.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.ai.profiles.AiProfile
import foo.pilz.freaklog.data.ai.profiles.AiProfileStore
import foo.pilz.freaklog.data.ai.web.AiWebSearchEngine
import foo.pilz.freaklog.data.ai.web.AiWebSettingsStore
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AiSettingsViewModel
@Inject
constructor(
    private val profileStore: AiProfileStore,
    private val userPreferences: UserPreferences,
    private val webSettings: AiWebSettingsStore,
) : ViewModel() {

  init {
    viewModelScope.launch { profileStore.migrateLegacySettings() }
  }

  val isEnabled: StateFlow<Boolean> =
      userPreferences.aiAssistantEnabledFlow.stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(5000),
          false,
      )

  val profiles: StateFlow<List<AiProfile>> =
      profileStore.profilesFlow.stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(5000),
          emptyList(),
      )

  val activeProfileId: StateFlow<String?> =
      profileStore.activeProfileIdFlow.stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(5000),
          null,
      )

  val profilesWithKey: StateFlow<Set<String>> =
      profileStore.profilesWithKeyFlow.stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(5000),
          emptySet(),
      )

  val isAnodyneEnabled: StateFlow<Boolean> =
      userPreferences.aiAnodyneEnabledFlow.stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(5000),
          true,
      )

  val isWebEnabled: StateFlow<Boolean> =
      webSettings.isEnabledFlow.stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(5000),
          false,
      )

  val webSearchEngine: StateFlow<AiWebSearchEngine> =
      webSettings.searchEngineFlow.stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(5000),
          AiWebSearchEngine.DUCKDUCKGO,
      )

  val searxngUrl: StateFlow<String> =
      webSettings.searxngUrlFlow.stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(5000),
          "",
      )

  val hasBraveApiKey: StateFlow<Boolean> =
      webSettings.hasBraveApiKeyFlow.stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(5000),
          false,
      )

  val isVitalsEnabled: StateFlow<Boolean> =
      userPreferences.aiVitalsEnabledFlow.stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(5000),
          false,
      )

  fun setEnabled(value: Boolean) = viewModelScope.launch {
    userPreferences.saveAiAssistantEnabled(value)
  }

  fun setAnodyneEnabled(value: Boolean) = viewModelScope.launch {
    userPreferences.saveAiAnodyneEnabled(value)
  }

  fun setWebEnabled(value: Boolean) = viewModelScope.launch { webSettings.setEnabled(value) }

  fun setWebSearchEngine(engine: AiWebSearchEngine) = viewModelScope.launch {
    webSettings.setSearchEngine(engine)
  }

  fun setSearxngUrl(url: String) = viewModelScope.launch { webSettings.setSearxngUrl(url) }

  fun saveBraveApiKey(apiKey: String) = viewModelScope.launch { webSettings.setBraveApiKey(apiKey) }

  fun removeBraveApiKey() = viewModelScope.launch { webSettings.setBraveApiKey("") }

  fun setVitalsEnabled(value: Boolean) = viewModelScope.launch {
    userPreferences.saveAiVitalsEnabled(value)
  }

  fun setActive(id: String) = viewModelScope.launch { profileStore.setActive(id) }
}
