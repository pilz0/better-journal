package foo.pilz.freaklog.data.room.experiences.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Entity(indices = [Index(value = ["name"])])
@Serializable
data class CustomSubstance(
    @Transient
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    var units: String,
    var description: String,
    var summary: String? = null,
    var toleranceFull: String? = null,
    var toleranceHalf: String? = null,
    var toleranceZero: String? = null,
    var effectsText: String? = null,
    var generalRisks: String? = null,
    var longTermRisks: String? = null,
)