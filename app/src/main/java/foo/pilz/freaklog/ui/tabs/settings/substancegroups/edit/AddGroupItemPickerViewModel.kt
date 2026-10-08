package foo.pilz.freaklog.ui.tabs.settings.substancegroups.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.substances.repositories.SearchRepository
import foo.pilz.freaklog.ui.tabs.search.SubstanceModel
import foo.pilz.freaklog.ui.utils.stateInVm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddGroupItemPickerViewModel @Inject constructor(
    experienceRepo: ExperienceRepository,
    private val searchRepo: SearchRepository,
) : ViewModel() {

    private val _searchTextFlow = MutableStateFlow("")
    val searchTextFlow = _searchTextFlow.asStateFlow()

    fun onSearch(text: String) {
        viewModelScope.launch { _searchTextFlow.emit(text) }
    }

    val filteredSubstancesFlow = combine(
        searchTextFlow,
        experienceRepo.getSortedLastUsedSubstanceNamesFlow(limit = 200),
    ) { searchText, recents ->
        searchRepo.getMatchingSubstances(
            searchText = searchText,
            filterCategories = emptyList(),
            recentlyUsedSubstanceNamesSorted = recents,
        ).map { it.toSubstanceModel() }
    }.stateInVm(viewModelScope, emptyList<SubstanceModel>())

    val filteredCustomSubstancesFlow = experienceRepo.getCustomSubstancesFlow()
        .combine(_searchTextFlow) { customs, query ->
            customs.filter { it.name.contains(query, ignoreCase = true) }
        }.stateInVm(viewModelScope, emptyList<CustomSubstance>())
}
