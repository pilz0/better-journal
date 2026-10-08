package foo.pilz.freaklog.data.room.experiences.relations

import foo.pilz.freaklog.data.room.experiences.entities.CustomRoa
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDose
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDuration
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.Roa

data class CustomRoaInfo(
    val route: AdministrationRoute,
    val roa: CustomRoa? = null,
    val dose: CustomRoaDose? = null,
    val duration: CustomRoaDuration? = null,
) {
    fun toRoa(): Roa = Roa(
        route = route,
        roaDose = dose?.toRoaDose(),
        roaDuration = duration?.toRoaDuration(),
        bioavailability = roa?.bioavailability,
    )
}
