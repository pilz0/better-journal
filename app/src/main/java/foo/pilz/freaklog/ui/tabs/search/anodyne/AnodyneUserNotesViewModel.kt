package foo.pilz.freaklog.ui.tabs.search.anodyne

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.anodyne.AnodyneRepository
import foo.pilz.freaklog.data.anodyne.model.AnodyneUserNote
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AnodyneUserNotesViewModel
@Inject
constructor(
    private val repository: AnodyneRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

  val substanceName: String = savedStateHandle.get<String>("substanceName") ?: ""

  private val _notes = MutableStateFlow<List<AnodyneUserNote>>(emptyList())
  val notes: StateFlow<List<AnodyneUserNote>> = _notes.asStateFlow()

  private val _isLoading = MutableStateFlow(true)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error.asStateFlow()

  init {
    loadNotes()
  }

  fun loadNotes() {
    if (substanceName.isBlank()) {
      _isLoading.value = false
      return
    }

    viewModelScope.launch {
      _isLoading.value = true
      _error.value = null
      val result = repository.getUserNotes(listOf(substanceName))
      result.fold(
          onSuccess = { list ->
            _notes.value = list
          },
          onFailure = { err ->
            _error.value = err.message ?: "Failed to load user notes"
          },
      )
      _isLoading.value = false
    }
  }
}
