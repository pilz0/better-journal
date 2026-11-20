package foo.pilz.freaklog.ui.tabs.journal.outlookskin

import foo.pilz.freaklog.ui.tabs.settings.funny.UltraFunSkin

data class OutlookSkinPayload(
    val rows: List<OutlookMailRow>,
) : UltraFunSkin
