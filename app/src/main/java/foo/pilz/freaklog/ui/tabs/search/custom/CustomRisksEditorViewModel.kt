package foo.pilz.freaklog.ui.tabs.search.custom

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceRepository
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithEverything
import foo.pilz.freaklog.ui.main.navigation.graphs.CustomRisksEditorRoute
import foo.pilz.freaklog.ui.utils.stateInVm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomRisksEditorViewModel @Inject constructor(
    private val experienceRepo: ExperienceRepository,
    customRepo: CustomSubstanceRepository,
    state: SavedStateHandle,
) : ViewModel() {

    val customSubstanceId = state.toRoute<CustomRisksEditorRoute>().customSubstanceId

    val substanceFlow = customRepo.getWithEverythingFlow(customSubstanceId)
        .stateInVm(viewModelScope, null as CustomSubstanceWithEverything?)

    val effectsFlow = MutableStateFlow("")
    val generalRisksFlow = MutableStateFlow("")
    val longTermRisksFlow = MutableStateFlow("")

    private var hasHydrated = false

    init {
        viewModelScope.launch {
            substanceFlow.collect { sub ->
                if (sub != null && !hasHydrated) {
                    effectsFlow.value = sub.substance.effectsText ?: ""
                    generalRisksFlow.value = sub.substance.generalRisks ?: ""
                    longTermRisksFlow.value = sub.substance.longTermRisks ?: ""
                    hasHydrated = true
                }
            }
        }
    }

    fun onEffectsChange(value: String) {
        effectsFlow.value = value
        persist()
    }

    fun onGeneralRisksChange(value: String) {
        generalRisksFlow.value = value
        persist()
    }

    fun onLongTermRisksChange(value: String) {
        longTermRisksFlow.value = value
        persist()
    }

    private fun persist() {
        if (!hasHydrated) return
        viewModelScope.launch {
            val current = substanceFlow.value?.substance ?: return@launch
            val updated = current.copy(
                effectsText = effectsFlow.value.ifBlank { null },
                generalRisks = generalRisksFlow.value.ifBlank { null },
                longTermRisks = longTermRisksFlow.value.ifBlank { null },
            )
            experienceRepo.update(updated)
        }
    }
}
