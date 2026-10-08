package foo.pilz.freaklog.ui.tabs.search.custom.customdurations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDose
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDuration
import foo.pilz.freaklog.ui.main.navigation.graphs.CustomDurationRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class CustomDurationViewModel @Inject constructor(
    val experienceRepository: ExperienceRepository,
    state: SavedStateHandle,
) : ViewModel() {
    private val navRoute = state.toRoute<CustomDurationRoute>()
    val substanceId = navRoute.substanceId
    val substanceName = navRoute.substanceName
    var durations = experienceRepository.getCustomRoaDurationsFlow(substanceId)
        .stateIn(
            initialValue = listOf<CustomRoaDuration>(),
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
        )
    var doses = experienceRepository.getCustomRoaDosesFlow(substanceId)
        .stateIn(
            initialValue = listOf<CustomRoaDose>(),
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
        )
}
