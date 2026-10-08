package foo.pilz.freaklog.data.room.experiences.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.Bioavailability
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
data class CustomRoa(
    @Transient
    var customSubstanceId: Int = 0,

    val route: AdministrationRoute,

    @Embedded(prefix = "bioavailability_")
    val bioavailability: Bioavailability? = null,
)
