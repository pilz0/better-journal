package foo.pilz.freaklog.ui.tabs.settings.substancegroups.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.SubstanceGroupRepository
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroup
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroupItem
import foo.pilz.freaklog.ui.utils.stateInVm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddSubstanceGroupViewModel @Inject constructor(
    private val groupRepo: SubstanceGroupRepository,
) : ViewModel() {

    private val _idFlow = MutableStateFlow<Int?>(null)
    val idFlow = _idFlow.asStateFlow()

    val nameFlow = MutableStateFlow("")

    val itemsFlow = _idFlow
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else groupRepo.getWithItemsFlow(id).map { it?.sortedItems.orEmpty() }
        }
        .stateInVm(viewModelScope, emptyList<SubstanceGroupItem>())

    init {
        viewModelScope.launch {
            _idFlow.value = groupRepo.create(SubstanceGroup(name = ""))
        }
    }

    fun onNameChange(value: String) {
        nameFlow.value = value
        persist()
    }

    private fun persist() {
        val id = _idFlow.value ?: return
        viewModelScope.launch {
            groupRepo.update(SubstanceGroup(id = id, name = nameFlow.value.trim()))
        }
    }

    fun removeItem(item: SubstanceGroupItem) {
        viewModelScope.launch { groupRepo.deleteItem(item) }
    }

    fun deleteDraft() {
        val id = _idFlow.value ?: return
        viewModelScope.launch {
            groupRepo.delete(SubstanceGroup(id = id, name = nameFlow.value.trim()))
        }
    }
}
