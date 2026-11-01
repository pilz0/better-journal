package foo.pilz.freaklog.ui.tabs.journal.experience.components

import foo.pilz.freaklog.data.substances.AdministrationRoute

data class IngestionDoseKey(
    val substance: String,
    val route: AdministrationRoute,
    val unit: String,
)
