package foo.pilz.freaklog.ui.tabs.settings.substancegroups.edit

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceRepository
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.SubstanceGroupRepository
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroup
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroupItem
import foo.pilz.freaklog.data.room.experiences.relations.SubstanceGroupWithItems
import foo.pilz.freaklog.data.substanceshare.shareSubstanceGroup
import foo.pilz.freaklog.ui.main.navigation.graphs.EditSubstanceGroupRoute
import foo.pilz.freaklog.ui.utils.stateInVm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditSubstanceGroupViewModel @Inject constructor(
    private val groupRepo: SubstanceGroupRepository,
    private val customSubstanceRepo: CustomSubstanceRepository,
    private val experienceRepo: ExperienceRepository,
    state: SavedStateHandle,
) : ViewModel() {

    val id: Int = state.toRoute<EditSubstanceGroupRoute>().groupId

    val groupFlow = groupRepo.getWithItemsFlow(id)
        .stateInVm(viewModelScope, null as SubstanceGroupWithItems?)

    val itemsFlow = groupFlow.map { it?.sortedItems.orEmpty() }
        .stateInVm(viewModelScope, emptyList<SubstanceGroupItem>())

    val nameFlow = MutableStateFlow("")

    private var hasHydrated = false

    init {
        viewModelScope.launch {
            groupFlow.collect { loaded ->
                if (loaded != null && !hasHydrated) {
                    nameFlow.value = loaded.group.name
                    hasHydrated = true
                }
            }
        }
    }

    fun onNameChange(value: String) {
        nameFlow.value = value
        persist()
    }

    private fun persist() {
        if (!hasHydrated) return
        viewModelScope.launch {
            groupRepo.update(SubstanceGroup(id = id, name = nameFlow.value.trim()))
        }
    }

    fun removeItem(item: SubstanceGroupItem) {
        viewModelScope.launch { groupRepo.deleteItem(item) }
    }

    fun delete() {
        viewModelScope.launch {
            groupRepo.delete(SubstanceGroup(id = id, name = nameFlow.value.trim()))
        }
    }

    fun share(context: Context) {
        viewModelScope.launch {
            val current = groupRepo.getWithItems(id) ?: return@launch
            shareSubstanceGroup(context, current, customSubstanceRepo, experienceRepo)
        }
    }
}
