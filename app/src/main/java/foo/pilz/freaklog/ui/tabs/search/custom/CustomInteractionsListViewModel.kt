package foo.pilz.freaklog.ui.tabs.search.custom

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteraction
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionSeverity
import foo.pilz.freaklog.ui.main.navigation.graphs.CustomInteractionsListRoute
import foo.pilz.freaklog.ui.utils.stateInVm
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomInteractionsListViewModel @Inject constructor(
    private val customRepo: CustomSubstanceRepository,
    state: SavedStateHandle,
) : ViewModel() {
    val customSubstanceId = state.toRoute<CustomInteractionsListRoute>().customSubstanceId

    val groupedFlow = customRepo.getInteractionsFlow(customSubstanceId)
        .map { list -> list.groupBy { it.severity } }
        .stateInVm(viewModelScope, emptyMap<CustomInteractionSeverity, List<CustomInteraction>>())

    fun remove(interaction: CustomInteraction) {
        viewModelScope.launch {
            customRepo.deleteInteraction(interaction)
        }
    }
}
