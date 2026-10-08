package foo.pilz.freaklog.data.room.experiences.relations

import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.Bioavailability
import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import kotlinx.serialization.Serializable

@Serializable
data class CustomRoaDurationSerializable(
    val route: AdministrationRoute,
    val onset: DurationRange? = null,
    val comeup: DurationRange? = null,
    val peak: DurationRange? = null,
    val offset: DurationRange? = null,
    val total: DurationRange? = null,
    val lightDoseMin: Double? = null,
    val commonDoseMin: Double? = null,
    val strongDoseMin: Double? = null,
    val heavyDoseMin: Double? = null,
    val bioavailability: Bioavailability? = null,
)
