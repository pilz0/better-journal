package foo.pilz.freaklog.data.substances.classes

import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.RoaDose
import foo.pilz.freaklog.data.substances.classes.roa.RoaDuration

data class Formulation(
    val name: String,
    val route: AdministrationRoute,
    val dose: RoaDose?,
    val duration: RoaDuration?
)
