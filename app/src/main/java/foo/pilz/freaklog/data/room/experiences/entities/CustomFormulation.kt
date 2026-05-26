package foo.pilz.freaklog.data.room.experiences.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import foo.pilz.freaklog.data.substances.AdministrationRoute

data class FormulationDurationOverrides(
    val onsetMinMinutes: Int? = null,
    val onsetMaxMinutes: Int? = null,
    val comeupMinMinutes: Int? = null,
    val comeupMaxMinutes: Int? = null,
    val peakMinMinutes: Int? = null,
    val peakMaxMinutes: Int? = null,
    val offsetMinMinutes: Int? = null,
    val offsetMaxMinutes: Int? = null,
    val totalMinMinutes: Int? = null,
    val totalMaxMinutes: Int? = null,
    val afterglowMinMinutes: Int? = null,
    val afterglowMaxMinutes: Int? = null
)

@Entity(tableName = "custom_formulation")
data class CustomFormulation(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val substanceName: String,
    val baseRoa: AdministrationRoute,
    val name: String,
    @Embedded val durationOverrides: FormulationDurationOverrides? = null
)
