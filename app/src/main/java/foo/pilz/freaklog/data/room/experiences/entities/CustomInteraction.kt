package foo.pilz.freaklog.data.room.experiences.entities

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Keep
enum class CustomInteractionSeverity {
    DANGEROUS,
    UNSAFE,
    UNCERTAIN,
}

@Keep
enum class CustomInteractionTargetType {
    SUBSTANCE,
    CATEGORY,
    COMMON,
}

@Entity(
    indices = [
        Index(value = ["customSubstanceId", "severity", "targetName"], unique = true),
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
data class CustomInteraction(
    @Transient
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @Transient
    var customSubstanceId: Int = 0,

    val severity: CustomInteractionSeverity,
    val targetType: CustomInteractionTargetType,
    val targetName: String,
)
