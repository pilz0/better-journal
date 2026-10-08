package foo.pilz.freaklog.ui.tabs.settings.intakelimits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.IntakeLimit
import foo.pilz.freaklog.ui.utils.stateInVm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

data class IntakeLimitListItem(
    val limit: IntakeLimit,
    val status: IntakeLimitStatus,
)

@HiltViewModel
class IntakeLimitsViewModel @Inject constructor(
    private val experienceRepository: ExperienceRepository,
) : ViewModel() {

    val itemsFlow = experienceRepository.getIntakeLimitsFlow()
        .map { limits -> limits.map { computeItem(it) } }
        .flowOn(Dispatchers.IO)
        .stateInVm(viewModelScope, emptyList())

    private suspend fun computeItem(limit: IntakeLimit): IntakeLimitListItem {
        val now = Instant.now()
        val since = now.minusSeconds(limit.windowSeconds)
        val ingestions =
            experienceRepository.getIngestionsWithCustomUnitsForSubstanceSince(
                limit.substanceName,
                since
            )
        val past = ingestions.map {
            LimitIngestion(
                effectiveDose = it.pureDose,
                unit = it.originalUnit,
                time = it.ingestion.time,
            )
        }
        return IntakeLimitListItem(limit, evaluateIntakeLimit(limit, now, past, pending = null))
    }
}
