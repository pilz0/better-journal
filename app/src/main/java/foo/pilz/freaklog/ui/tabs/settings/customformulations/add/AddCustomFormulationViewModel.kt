package foo.pilz.freaklog.ui.tabs.settings.customformulations.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.CustomFormulationRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomFormulation
import foo.pilz.freaklog.data.room.experiences.entities.FormulationDurationOverrides
import foo.pilz.freaklog.data.substances.AdministrationRoute
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CustomFormulationDraft(
    val substanceName: String,
    val baseRoa: AdministrationRoute,
    val name: String,
    val durationOverrides: FormulationDurationOverrides,
)

@HiltViewModel
class AddCustomFormulationViewModel
@Inject
constructor(private val repository: CustomFormulationRepository) : ViewModel() {
  private var isSaving = false

  fun saveFormulation(draft: CustomFormulationDraft, onSaved: () -> Unit = {}) {
    if (isSaving) return
    isSaving = true
    viewModelScope.launch {
      try {
        val formulation =
            CustomFormulation(
                substanceName = draft.substanceName,
                baseRoa = draft.baseRoa,
                name = draft.name,
                durationOverrides = draft.durationOverrides,
            )
        repository.insert(formulation)
        withContext(Dispatchers.Main) { onSaved() }
      } finally {
        isSaving = false
      }
    }
  }
}
