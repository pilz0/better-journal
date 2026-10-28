package foo.pilz.freaklog.ui.tabs.settings.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.ai.profiles.AiProfile
import foo.pilz.freaklog.data.ai.profiles.AiProfileStore
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val profileStore: AiProfileStore,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    init {
        viewModelScope.launch { profileStore.migrateLegacySettings() }
    }

    val isEnabled: StateFlow<Boolean> = userPreferences.aiAssistantEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val profiles: StateFlow<List<AiProfile>> = profileStore.profilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProfileId: StateFlow<String?> = profileStore.activeProfileIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val profilesWithKey: StateFlow<Set<String>> = profileStore.profilesWithKeyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val isAnodyneEnabled: StateFlow<Boolean> = userPreferences.aiAnodyneEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val anodyneBaseUrl: StateFlow<String> = userPreferences.aiAnodyneBaseUrlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setEnabled(value: Boolean) = viewModelScope.launch { userPreferences.saveAiAssistantEnabled(value) }

    fun setAnodyneEnabled(value: Boolean) = viewModelScope.launch { userPreferences.saveAiAnodyneEnabled(value) }

    /** Blank resets to [foo.pilz.freaklog.data.ai.anodyne.AnodyneWikiClient.DEFAULT_BASE_URL]. */
    fun setAnodyneBaseUrl(value: String) = viewModelScope.launch { userPreferences.saveAiAnodyneBaseUrl(value) }

    fun setActive(id: String) = viewModelScope.launch { profileStore.setActive(id) }
}
