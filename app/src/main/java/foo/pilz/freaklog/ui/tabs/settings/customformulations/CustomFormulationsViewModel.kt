package foo.pilz.freaklog.ui.tabs.settings.customformulations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.CustomFormulationRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomFormulation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomFormulationsViewModel @Inject constructor(
    private val repository: CustomFormulationRepository
) : ViewModel() {

    val formulations: StateFlow<List<CustomFormulation>> = repository.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun delete(formulation: CustomFormulation) {
        viewModelScope.launch {
            repository.delete(formulation)
        }
    }
}
