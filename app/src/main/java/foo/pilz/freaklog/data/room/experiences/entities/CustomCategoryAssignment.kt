package foo.pilz.freaklog.data.room.experiences.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Entity(
    primaryKeys = ["customSubstanceId", "categoryName"],
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
data class CustomCategoryAssignment(
    @Transient
    var customSubstanceId: Int = 0,
    val categoryName: String,
)
