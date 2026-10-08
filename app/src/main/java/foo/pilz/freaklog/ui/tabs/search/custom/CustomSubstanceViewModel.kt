package foo.pilz.freaklog.ui.tabs.search.custom

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceRepository
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithEverything
import foo.pilz.freaklog.data.substances.classes.Category
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.data.substanceshare.shareCustomSubstance
import foo.pilz.freaklog.ui.main.navigation.graphs.CustomSubstanceRoute
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
class CustomSubstanceViewModel @Inject constructor(
    substanceRepo: SubstanceRepository,
    private val customRepo: CustomSubstanceRepository,
    private val experienceRepo: ExperienceRepository,
    state: SavedStateHandle,
) : ViewModel() {
    val customSubstanceId = state.toRoute<CustomSubstanceRoute>().customSubstanceId

    val categoriesByName: Map<String, Category> = substanceRepo.getAllCategories()
        .associateBy { it.name }

    val substanceFlow = customRepo.getWithEverythingFlow(customSubstanceId)
        .stateInVm(viewModelScope, null as CustomSubstanceWithEverything?)

    val ingestionTimeFlow = MutableStateFlow(LocalDateTime.now())

    fun changeIngestionTime(newTime: LocalDateTime) {
        viewModelScope.launch { ingestionTimeFlow.emit(newTime) }
    }

    fun share(context: Context) {
        viewModelScope.launch {
            val current = customRepo.getWithEverything(customSubstanceId) ?: return@launch
            shareCustomSubstance(context, current)
        }
    }

    val timelineDisplayOptionFlow = combine(
        substanceFlow,
        ingestionTimeFlow,
    ) { substance, ingestionTime ->
        substance ?: return@combine TimelineDisplayOption.Loading
        val infosWithDurations = substance.roaInfos.filter { info ->
            val d = info.duration
            d != null && (d.onset != null || d.comeup != null || d.peak != null ||
                    d.offset != null || d.total != null)
        }
        if (infosWithDurations.isEmpty()) return@combine TimelineDisplayOption.NotWorthDrawing

        val companion = experienceRepo.getSubstanceCompanion(substance.substance.name)
        val dataForLines = infosWithDurations.mapIndexed { index, info ->
            DataForOneEffectLine(
                substanceName = "name$index",
                route = info.route,
                roaDuration = info.duration?.toRoaDuration(),
                height = 1f,
                horizontalWeight = 0.5f,
                color = info.route.color,
                startTime = ingestionTime.getInstant(),
                endTime = null,
            )
        }
        TimelineDisplayOption.Shown(
            AllTimelinesModel(
                dataForLines = dataForLines,
                dataForRatings = emptyList(),
                timedNotes = emptyList(),
                areSubstanceHeightsIndependent = false,
            )
        )
    }.flowOn(Dispatchers.Default)
        .stateInVm(viewModelScope, TimelineDisplayOption.Loading)
}
