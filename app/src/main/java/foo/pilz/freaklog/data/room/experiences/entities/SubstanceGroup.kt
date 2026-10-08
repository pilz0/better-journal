package foo.pilz.freaklog.data.room.experiences.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Entity
@Serializable
data class SubstanceGroup(
    @Transient
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    var name: String,
)
