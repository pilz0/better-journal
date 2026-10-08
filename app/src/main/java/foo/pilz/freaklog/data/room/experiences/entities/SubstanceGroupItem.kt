package foo.pilz.freaklog.data.room.experiences.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import foo.pilz.freaklog.data.substances.AdministrationRoute
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = SubstanceGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CustomUnit::class,
            parentColumns = ["id"],
            childColumns = ["customUnitId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["groupId"]),
        Index(value = ["customUnitId"]),
    ],
)
@Serializable
data class SubstanceGroupItem(
    @Transient
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @Transient
    var groupId: Int = 0,
    val sortOrder: Int,
    val substanceName: String,
    val isCustomSubstance: Boolean = false,
    val administrationRoute: AdministrationRoute,
    val dose: Double? = null,
    val units: String? = null,
    val isEstimate: Boolean = false,
    val estimatedDoseStandardDeviation: Double? = null,
    var customUnitId: Int? = null,
)
