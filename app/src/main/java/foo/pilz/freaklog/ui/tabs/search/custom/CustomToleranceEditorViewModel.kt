package foo.pilz.freaklog.ui.tabs.search.custom

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceRepository
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithEverything
import foo.pilz.freaklog.ui.main.navigation.graphs.CustomToleranceEditorRoute
import foo.pilz.freaklog.ui.utils.stateInVm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomToleranceEditorViewModel @Inject constructor(
    private val experienceRepo: ExperienceRepository,
    customRepo: CustomSubstanceRepository,
    state: SavedStateHandle,
) : ViewModel() {

    val customSubstanceId = state.toRoute<CustomToleranceEditorRoute>().customSubstanceId

    val substanceFlow = customRepo.getWithEverythingFlow(customSubstanceId)
        .stateInVm(viewModelScope, null as CustomSubstanceWithEverything?)

    val fullFlow = MutableStateFlow("")
    val halfFlow = MutableStateFlow("")
    val zeroFlow = MutableStateFlow("")
    val crossToleranceFlow = customRepo.getCrossTolerancesFlow(customSubstanceId)
        .map { list -> list.map { it.categoryName } }
        .stateInVm(viewModelScope, emptyList())

    private var hasHydrated = false

    init {
        viewModelScope.launch {
            substanceFlow.collect { sub ->
                if (sub != null && !hasHydrated) {
                    fullFlow.value = sub.substance.toleranceFull ?: ""
                    halfFlow.value = sub.substance.toleranceHalf ?: ""
                    zeroFlow.value = sub.substance.toleranceZero ?: ""
                    hasHydrated = true
                }
            }
        }
    }

    fun onFullChange(value: String) {
        fullFlow.value = value
        persist()
    }

    fun onHalfChange(value: String) {
        halfFlow.value = value
        persist()
    }

    fun onZeroChange(value: String) {
        zeroFlow.value = value
        persist()
    }

    private fun persist() {
        if (!hasHydrated) return
        viewModelScope.launch {
            val current = substanceFlow.value?.substance ?: return@launch
            val updated = current.copy(
                toleranceFull = fullFlow.value.ifBlank { null },
                toleranceHalf = halfFlow.value.ifBlank { null },
                toleranceZero = zeroFlow.value.ifBlank { null },
            )
            experienceRepo.update(updated)
        }
    }
}
