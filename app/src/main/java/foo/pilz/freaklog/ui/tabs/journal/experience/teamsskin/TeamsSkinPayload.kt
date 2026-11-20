package foo.pilz.freaklog.ui.tabs.journal.experience.teamsskin

import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import foo.pilz.freaklog.ui.tabs.journal.experience.components.TimeDisplayOption
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.AllTimelinesModel
import foo.pilz.freaklog.ui.tabs.settings.funny.UltraFunSkin

data class TeamsSkinPayload(
    val ingestions: List<IngestionWithCompanionAndCustomUnit>,
    val timelineModel: AllTimelinesModel?,
    val timeDisplayOption: TimeDisplayOption,
    val contactName: String,
) : UltraFunSkin
