package foo.pilz.freaklog.ui.tabs.search.custom.customdurations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoa
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDose
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDuration
import foo.pilz.freaklog.data.substances.classes.roa.Bioavailability
import foo.pilz.freaklog.ui.main.navigation.graphs.CustomDurationEditorRoute
import foo.pilz.freaklog.ui.tabs.journal.experience.TimelineDisplayOption
import foo.pilz.freaklog.ui.tabs.journal.experience.components.DataForOneEffectLine
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.AllTimelinesModel
import foo.pilz.freaklog.ui.utils.getInstant
import foo.pilz.freaklog.ui.utils.stateInVm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class CustomDurationEditorViewModel @Inject constructor(
    val experienceRepository: ExperienceRepository,
    val state: SavedStateHandle,
) : ViewModel() {
    private val navRoute = state.toRoute<CustomDurationEditorRoute>()
    val substanceId = navRoute.substanceId
    val substanceName = navRoute.substanceName
    val route = navRoute.route
    var roaDurationFlow = MutableStateFlow<CustomRoaDuration?>(null)
    var roaDoseFlow = MutableStateFlow<CustomRoaDose?>(null)
    var roaFlow = MutableStateFlow<CustomRoa?>(null)
    var substanceUnitFlow = MutableStateFlow("")
    val ingestionPreviewTimeFlow = MutableStateFlow(LocalDateTime.now())

    val timelineDisplayOptionFlow = combine(
        roaDurationFlow,
        ingestionPreviewTimeFlow,
    ) { roaDuration, ingestionTime ->
        val substanceCompanion = experienceRepository.getSubstanceCompanion(substanceName)

        val dataForEffectLine = DataForOneEffectLine(
            substanceName = substanceName,
            route = route,
            roaDuration = roaDuration?.toRoaDuration(),
            height = 1f,
            horizontalWeight = 0.5f,
            color = route.color,
            startTime = ingestionTime.getInstant(),
            endTime = null,
        )

        val model = AllTimelinesModel(
            dataForLines = listOf(dataForEffectLine),
            dataForRatings = emptyList(),
            timedNotes = emptyList(),
            areSubstanceHeightsIndependent = false
        )
        return@combine TimelineDisplayOption.Shown(model)
    }.flowOn(Dispatchers.Default)
        .stateInVm(viewModelScope, TimelineDisplayOption.Loading)

    init {
        viewModelScope.launch {
            val unit = experienceRepository.getCustomSubstance(substanceName)?.units ?: ""
            substanceUnitFlow.value = unit

            roaDurationFlow.value = experienceRepository.getCustomRoaDurations(substanceId)
                .find { it.route == route }
                ?: CustomRoaDuration(
                    route = route,
                    customSubstanceId = substanceId,
                    onset = null,
                    comeup = null,
                    peak = null,
                    offset = null,
                    total = null,
                )

            roaDoseFlow.value = experienceRepository.getCustomRoaDose(substanceId, route)
                ?: CustomRoaDose(
                    customSubstanceId = substanceId,
                    route = route,
                    units = unit,
                )

            roaFlow.value = experienceRepository.getCustomRoa(substanceId, route)
                ?: CustomRoa(
                    customSubstanceId = substanceId,
                    route = route,
                    bioavailability = null,
                )
        }
    }

    fun updateBioavailability(min: Double?, max: Double?) {
        val current = roaFlow.value ?: return
        roaFlow.value = current.copy(
            bioavailability = if (min == null && max == null) null
            else Bioavailability(min = min, max = max),
        )
    }

    fun onDone() {
        viewModelScope.launch {
            roaDurationFlow.value?.let { experienceRepository.insert(it) }
            roaDoseFlow.value?.let { dose ->
                val hasDose = dose.lightMin != null || dose.commonMin != null ||
                        dose.strongMin != null || dose.heavyMin != null
                if (hasDose) {
                    experienceRepository.upsertCustomRoaDose(substanceId, dose)
                } else {
                    experienceRepository.delete(dose)
                }
            }
            roaFlow.value?.let { roa ->
                if (roa.bioavailability != null) {
                    experienceRepository.insert(roa)
                } else {
                    experienceRepository.delete(roa)
                }
            }
        }
    }

    fun onDelete() {
        viewModelScope.launch {
            roaDurationFlow.value?.let { experienceRepository.delete(it) }
            roaDoseFlow.value?.let { experienceRepository.delete(it) }
            roaFlow.value?.let { experienceRepository.delete(it) }
        }
    }
}
