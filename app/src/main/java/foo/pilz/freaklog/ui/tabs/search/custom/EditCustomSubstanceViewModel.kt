package foo.pilz.freaklog.ui.tabs.search.custom

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceRepository
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithEverything
import foo.pilz.freaklog.data.substances.classes.IngestionCategory
import foo.pilz.freaklog.data.substanceshare.shareCustomSubstance
import foo.pilz.freaklog.ui.main.navigation.graphs.EditCustomSubstanceRoute
import foo.pilz.freaklog.ui.utils.stateInVm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditCustomSubstanceViewModel @Inject constructor(
    val experienceRepo: ExperienceRepository,
    private val customRepo: CustomSubstanceRepository,
    state: SavedStateHandle,
) : ViewModel() {

    val id = state.toRoute<EditCustomSubstanceRoute>().customSubstanceId

    val substanceFlow = customRepo.getWithEverythingFlow(id)
        .stateInVm(viewModelScope, null as CustomSubstanceWithEverything?)

    val nameFlow = MutableStateFlow("")
    val unitsFlow = MutableStateFlow("")
    val summaryFlow = MutableStateFlow("")
    val categoryFlow = MutableStateFlow<IngestionCategory?>(null)

    private var hasHydrated = false

    init {
        viewModelScope.launch {
            substanceFlow.collect { sub ->
                if (sub != null && !hasHydrated) {
                    nameFlow.value = sub.substance.name
                    unitsFlow.value = sub.substance.units
                    summaryFlow.value = sub.substance.summary
                        ?: sub.substance.description.takeIf { it.isNotBlank() }
                                ?: ""
                    categoryFlow.value = experienceRepo
                        .getSubstanceCompanion(sub.substance.name)?.defaultCategory
                    hasHydrated = true
                }
            }
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
        if (!hasHydrated) return
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
        if (!hasHydrated) return
        viewModelScope.launch {
            val name = nameFlow.value
            val companion = experienceRepo.getSubstanceCompanion(name)
                ?: SubstanceCompanion(name)
            experienceRepo.upsert(companion.copy(defaultCategory = categoryFlow.value))
        }
    }

    fun share(context: Context) {
        viewModelScope.launch {
            val current = customRepo.getWithEverything(id) ?: return@launch
            shareCustomSubstance(context, current)
        }
    }

    fun deleteCustomSubstance() {
        viewModelScope.launch {
            val current = substanceFlow.value?.substance ?: return@launch
            experienceRepo.delete(current)
        }
    }
}
