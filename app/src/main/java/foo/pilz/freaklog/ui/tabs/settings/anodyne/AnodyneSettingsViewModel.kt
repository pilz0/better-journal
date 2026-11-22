package foo.pilz.freaklog.ui.tabs.settings.anodyne

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.anodyne.AnodyneRepository
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AnodyneSettingsViewModel
@Inject
constructor(
    private val userPreferences: UserPreferences,
    private val anodyneRepository: AnodyneRepository,
) : ViewModel() {

  val isAutoLoadEnabled: StateFlow<Boolean> =
      userPreferences.anodyneAutoLoadEnabledFlow.stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(5000),
          false,
      )

  val configuredBaseUrl: StateFlow<String> =
      userPreferences.anodyneBaseUrlFlow.stateIn(
          viewModelScope,
          SharingStarted.WhileSubscribed(5000),
          AnodyneRepository.DEFAULT_INSTANCE_URL,
      )

  private val _draftUrl = MutableStateFlow("")
  val draftUrl: StateFlow<String> = _draftUrl.asStateFlow()

  private val _isDraftValid = MutableStateFlow(true)
  val isDraftValid: StateFlow<Boolean> = _isDraftValid.asStateFlow()

  private val _cacheSizeBytes = MutableStateFlow(0L)
  val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

  init {
    viewModelScope.launch {
      val initialUrl = userPreferences.anodyneBaseUrlFlow.first()
      _draftUrl.value = if (initialUrl == AnodyneRepository.DEFAULT_INSTANCE_URL) "" else initialUrl
      refreshCacheSize()
    }
  }

  fun onAutoLoadChanged(enabled: Boolean) {
    viewModelScope.launch {
      userPreferences.saveAnodyneAutoLoadEnabled(enabled)
    }
  }

  fun onDraftUrlChange(newUrl: String) {
    _draftUrl.value = newUrl
    _isDraftValid.value = newUrl.isBlank() || AnodyneRepository.normalizeUrl(newUrl) != null
  }

  fun applyDraftUrl() {
    val draft = _draftUrl.value.trim()
    val normalized =
        if (draft.isBlank()) AnodyneRepository.DEFAULT_INSTANCE_URL
        else AnodyneRepository.normalizeUrl(draft)
    if (normalized != null) {
      viewModelScope.launch {
        userPreferences.saveAnodyneBaseUrl(normalized)
        anodyneRepository.clearCache()
        refreshCacheSize()
      }
    }
  }

  fun resetToDefaultUrl() {
    _draftUrl.value = ""
    _isDraftValid.value = true
    viewModelScope.launch {
      userPreferences.saveAnodyneBaseUrl(AnodyneRepository.DEFAULT_INSTANCE_URL)
      anodyneRepository.clearCache()
      refreshCacheSize()
    }
  }

  fun clearCache() {
    viewModelScope.launch {
      anodyneRepository.clearCache()
      refreshCacheSize()
    }
  }

  fun refreshCacheSize() {
    _cacheSizeBytes.value = anodyneRepository.getCacheSizeBytes()
  }
}
