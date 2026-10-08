package foo.pilz.freaklog.ui.tabs.settings.substancegroups.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroupItem
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.ui.main.navigation.graphs.AddGroupItemConfigRoute
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddGroupItemConfigViewModel @Inject constructor(
    private val substanceRepo: SubstanceRepository,
    private val experienceRepo: ExperienceRepository,
    private val groupRepo: foo.pilz.freaklog.data.room.experiences.SubstanceGroupRepository,
    state: SavedStateHandle,
) : ViewModel() {

    private val route = state.toRoute<AddGroupItemConfigRoute>()
    val groupId: Int = route.groupId
    val substanceName: String = route.substanceName
    val isCustomSubstance: Boolean = route.isCustomSubstance

    var availableRoutes by mutableStateOf<List<AdministrationRoute>>(AdministrationRoute.entries.toList())
        private set
    private var defaultUnitsByRoute: Map<AdministrationRoute, String> = emptyMap()

    var administrationRoute by mutableStateOf(AdministrationRoute.ORAL)
        private set
    var doseText by mutableStateOf("")
    var unitsText by mutableStateOf("mg")
    var isEstimate by mutableStateOf(false)
    var estimatedStdDevText by mutableStateOf("")

    init {
        viewModelScope.launch {
            if (isCustomSubstance) {
                val custom = experienceRepo.getCustomSubstance(substanceName)
                unitsText = custom?.units ?: "mg"
                administrationRoute = AdministrationRoute.ORAL
            } else {
                val substance = substanceRepo.getSubstance(substanceName)
                if (substance != null) {
                    val routes = substance.roas.map { it.route }
                    if (routes.isNotEmpty()) {
                        availableRoutes = routes
                        administrationRoute = routes.first()
                    }
                    defaultUnitsByRoute = substance.roas.mapNotNull { roa ->
                        roa.roaDose?.units?.let { roa.route to it }
                    }.toMap()
                    unitsText = defaultUnitsByRoute[administrationRoute] ?: "mg"
                }
            }
        }
    }

    fun onRouteChange(newRoute: AdministrationRoute) {
        administrationRoute = newRoute
        defaultUnitsByRoute[newRoute]?.let { unitsText = it }
    }

    fun onDoseChange(value: String) {
        doseText = value
    }

    fun onUnitsChange(value: String) {
        unitsText = value
    }

    fun onEstimateChange(value: Boolean) {
        isEstimate = value
        if (!value) estimatedStdDevText = ""
    }

    fun onEstimatedStdDevChange(value: String) {
        estimatedStdDevText = value
    }

    val canSave: Boolean
        get() = doseText.replace(',', '.').toDoubleOrNull() != null &&
                (!isEstimate || estimatedStdDevText.isBlank() || estimatedStdDevText.replace(
                    ',',
                    '.'
                ).toDoubleOrNull() != null)

    private fun buildItem(sortOrder: Int): SubstanceGroupItem {
        val dose = doseText.replace(',', '.').toDoubleOrNull()
        val stdDev = estimatedStdDevText.replace(',', '.').toDoubleOrNull()
        return SubstanceGroupItem(
            sortOrder = sortOrder,
            substanceName = substanceName,
            isCustomSubstance = isCustomSubstance,
            administrationRoute = administrationRoute,
            dose = dose,
            units = unitsText.ifBlank { null },
            isEstimate = isEstimate,
            estimatedDoseStandardDeviation = if (isEstimate) stdDev else null,
            customUnitId = null,
        )
    }

    fun saveAndDismiss(onDone: () -> Unit) {
        if (!canSave) return
        viewModelScope.launch {
            val existing = groupRepo.getWithItems(groupId)
            val nextSort = (existing?.items?.maxOfOrNull { it.sortOrder } ?: -1) + 1
            groupRepo.appendItem(groupId, buildItem(nextSort))
            onDone()
        }
    }
}
