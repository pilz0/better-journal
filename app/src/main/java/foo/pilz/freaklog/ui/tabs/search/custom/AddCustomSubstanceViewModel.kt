package foo.pilz.freaklog.ui.tabs.search.custom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceRepository
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithEverything
import foo.pilz.freaklog.data.substances.classes.IngestionCategory
import foo.pilz.freaklog.ui.utils.stateInVm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddCustomSubstanceViewModel @Inject constructor(
    val experienceRepo: ExperienceRepository,
    private val customRepo: CustomSubstanceRepository,
) : ViewModel() {

    private val _idFlow = MutableStateFlow<Int?>(null)
    val idFlow = _idFlow.asStateFlow()

    val nameFlow = MutableStateFlow("")
    val unitsFlow = MutableStateFlow("")
    val summaryFlow = MutableStateFlow("")
    val categoryFlow = MutableStateFlow<IngestionCategory?>(null)

    val substanceFlow = _idFlow
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else customRepo.getWithEverythingFlow(id)
        }
        .stateInVm(viewModelScope, null as CustomSubstanceWithEverything?)

    init {
        viewModelScope.launch {
            val stub = CustomSubstance(name = "", units = "", description = "")
            _idFlow.value = experienceRepo.insert(stub)
        }
    }

    fun onNameChange(value: String) {
        nameFlow.value = value
        persistSubstance()
    }

    fun onUnitsChange(value: String) {
        unitsFlow.value = value
        persistSubstance()
    }

    fun onSummaryChange(value: String) {
        summaryFlow.value = value
        persistSubstance()
    }

    fun onCategoryChange(value: IngestionCategory?) {
        categoryFlow.value = value
        persistCategory()
    }

    private fun persistSubstance() {
        viewModelScope.launch {
            val current = substanceFlow.value?.substance ?: return@launch
            val newName = nameFlow.value
            val newUnits = unitsFlow.value
            if (current.name != newName && newName.isNotBlank()) {
                customRepo.renameCustom(current.name, newName)
            }
            if (current.units != newUnits) {
                customRepo.setAllRoaDoseUnits(current.id, newUnits)
            }
            val updated = current.copy(
                name = newName,
                units = newUnits,
                description = "",
                summary = summaryFlow.value.ifBlank { null },
            )
            experienceRepo.update(updated)
        }
    }

    private fun persistCategory() {
        viewModelScope.launch {
            val name = nameFlow.value
            if (name.isBlank()) return@launch
            val companion = experienceRepo.getSubstanceCompanion(name)
                ?: SubstanceCompanion(name)
            experienceRepo.upsert(companion.copy(defaultCategory = categoryFlow.value))
        }
    }

    fun deleteDraft() {
        viewModelScope.launch {
            val current = substanceFlow.value?.substance ?: return@launch
            experienceRepo.delete(current)
        }
    }
}
