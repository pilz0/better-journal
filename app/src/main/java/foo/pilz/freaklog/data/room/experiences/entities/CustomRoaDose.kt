package foo.pilz.freaklog.data.room.experiences.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.RoaDose
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Entity(
    primaryKeys = ["customSubstanceId", "route"],
    indices = [
        Index(value = ["customSubstanceId"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = CustomSubstance::class,
            parentColumns = ["id"],
            childColumns = ["customSubstanceId"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
@Serializable
data class CustomRoaDose(
    @Transient
    var customSubstanceId: Int = 0,

    val route: AdministrationRoute,

    val units: String? = null,
    val lightMin: Double? = null,
    val commonMin: Double? = null,
    val strongMin: Double? = null,
    val heavyMin: Double? = null,
) {
    fun toRoaDose(): RoaDose {
        return RoaDose(
            units = units ?: "",
            lightMin = lightMin,
            commonMin = commonMin,
            strongMin = strongMin,
            heavyMin = heavyMin,
        )
    }
}
