package foo.pilz.freaklog.ui.tabs.journal.experience.teamsskin

import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import foo.pilz.freaklog.ui.tabs.journal.experience.components.TimeDisplayOption
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.AllTimelinesModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class TeamsSkinPayload(
    val ingestions: List<IngestionWithCompanionAndCustomUnit>,
    val timelineModel: AllTimelinesModel?,
    val timeDisplayOption: TimeDisplayOption,
    val contactName: String,
)

@Singleton
class TeamsSkinController @Inject constructor() {
    private val _state = MutableStateFlow<TeamsSkinPayload?>(null)
    val state: StateFlow<TeamsSkinPayload?> = _state.asStateFlow()
    fun show(payload: TeamsSkinPayload) {
        _state.value = payload
    }

    fun dismiss() {
        _state.value = null
    }
}
