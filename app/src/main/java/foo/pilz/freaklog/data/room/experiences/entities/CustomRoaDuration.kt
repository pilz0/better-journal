package foo.pilz.freaklog.data.room.experiences.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.RoaDuration
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Entity(
    primaryKeys = ["route", "customSubstanceId"],
    indices = [
        Index(value = ["route", "customSubstanceId"], unique = true),
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
@OptIn(ExperimentalSerializationApi::class)
data class CustomRoaDuration(
    val route: AdministrationRoute,

    @Transient
    var customSubstanceId: Int = 0,

    @Embedded(prefix = "onset_")
    val onset: DurationRange?,
    @Embedded(prefix = "comeup_")
    val comeup: DurationRange?,
    @Embedded(prefix = "peak_")
    val peak: DurationRange?,
    @Embedded(prefix = "offset_")
    val offset: DurationRange?,
    @Embedded(prefix = "total_")
    val total: DurationRange?,
) {
    fun toRoaDuration(): RoaDuration {
        return RoaDuration(
            onset = onset,
            comeup = comeup,
            peak = peak,
            offset = offset,
            total = total,
            afterglow = null,
        )
    }
}
