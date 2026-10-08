package foo.pilz.freaklog.ui.tabs.search.custom

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceRepository
import foo.pilz.freaklog.data.substances.classes.Category
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.ui.main.navigation.graphs.CustomCategoriesPickerRoute
import foo.pilz.freaklog.ui.utils.stateInVm
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomCategoriesPickerViewModel @Inject constructor(
    substanceRepo: SubstanceRepository,
    private val customRepo: CustomSubstanceRepository,
    state: SavedStateHandle,
) : ViewModel() {

    val customSubstanceId = state.toRoute<CustomCategoriesPickerRoute>().customSubstanceId

    val allCategories: List<Category> = substanceRepo.getAllCategories()

    val selectedNamesFlow = customRepo.getCategoriesFlow(customSubstanceId)
        .map { list -> list.map { it.categoryName }.toSet() }
        .stateInVm(viewModelScope, emptySet())

    fun toggle(categoryName: String) {
        viewModelScope.launch {
            val current = selectedNamesFlow.value
            if (current.contains(categoryName)) {
                customRepo.unassignCategory(customSubstanceId, categoryName)
            } else {
                customRepo.assignCategory(customSubstanceId, categoryName)
            }
        }
    }
}
