package foo.pilz.freaklog.ui.tabs.search.custom

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceRepository
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteraction
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionTargetType
import foo.pilz.freaklog.data.substances.CommonInteractants
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.ui.main.navigation.graphs.CustomInteractionsPickerRoute
import foo.pilz.freaklog.ui.utils.stateInVm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomInteractionsPickerViewModel @Inject constructor(
    substanceRepo: SubstanceRepository,
    experienceRepo: ExperienceRepository,
    private val customRepo: CustomSubstanceRepository,
    state: SavedStateHandle,
) : ViewModel() {

    private val route = state.toRoute<CustomInteractionsPickerRoute>()
    val customSubstanceId = route.customSubstanceId
    val severity = route.severity

    val queryFlow = MutableStateFlow("")

    val allBuiltinSubstances: List<String> = substanceRepo.getAllSubstances()
        .map { it.name }
        .sorted()

    val allBuiltinCategories: List<String> = substanceRepo.getAllCategories()
        .map { it.name }
        .sorted()

    private val builtinNamesSet: Set<String> = allBuiltinSubstances.toSet()

    val allCommon: List<String> = CommonInteractants.all
        .filter { it !in builtinNamesSet }

    private val customSubstancesFlow = MutableStateFlow<List<String>>(emptyList())
    private val existingTargetsFlow = customRepo.getInteractionsFlow(customSubstanceId)
        .map { list -> list.filter { it.severity == severity }.map { it.targetName }.toSet() }
        .stateInVm(viewModelScope, emptySet())

    val sectionsFlow = combine(
        queryFlow,
        customSubstancesFlow,
        existingTargetsFlow,
    ) { query, customs, existing ->
        val needle = query.trim().lowercase()
        fun filter(items: List<String>): List<String> =
            items.filter { it.lowercase().contains(needle) }
                .filter { it !in existing }

        Sections(
            substances = (filter(allBuiltinSubstances) + filter(customs)).distinct(),
            categories = filter(allBuiltinCategories),
            common = filter(allCommon),
        )
    }.stateInVm(viewModelScope, Sections())

    init {
        viewModelScope.launch {
            customSubstancesFlow.value = experienceRepo.getAllCustomSubstances()
                .map { it.name }
                .filter { name ->
                    val self = customRepo.getWithEverything(customSubstanceId)?.substance?.name
                    name != self
                }
                .sorted()
        }
    }

    fun add(targetName: String, targetType: CustomInteractionTargetType, onAdded: () -> Unit) {
        viewModelScope.launch {
            customRepo.upsertInteraction(
                customSubstanceId,
                CustomInteraction(
                    customSubstanceId = customSubstanceId,
                    severity = severity,
                    targetType = targetType,
                    targetName = targetName,
                ),
            )
            onAdded()
        }
    }

    data class Sections(
        val substances: List<String> = emptyList(),
        val categories: List<String> = emptyList(),
        val common: List<String> = emptyList(),
    )
}
