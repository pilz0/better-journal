package foo.pilz.freaklog.ui.tabs.stats.excelskin

import foo.pilz.freaklog.ui.tabs.settings.funny.UltraFunSkin
import foo.pilz.freaklog.ui.tabs.stats.StatItem

data class ExcelSkinPayload(
    val statItems: List<StatItem>,
    val fileName: String,
) : UltraFunSkin
