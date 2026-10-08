package foo.pilz.freaklog.data.room.experiences.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

enum class IntakeLimitType { DOSE, COUNT }

@Entity(
    indices = [
        Index(value = ["substanceName"]),
    ],
)
data class IntakeLimit(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val substanceName: String,
    val creationDate: Instant = Instant.now(),
    var limitType: IntakeLimitType,
    var maxDose: Double?,
    var unit: String?,
    var maxCount: Int?,
    var windowSeconds: Long,
    var warningPercent: Int,
    @ColumnInfo(defaultValue = "1")
    var isEnabled: Boolean = true,
) {
    companion object {
        const val DEFAULT_WARNING_PERCENT = 80

        val caffeineSample = IntakeLimit(
            substanceName = "Caffeine",
            limitType = IntakeLimitType.DOSE,
            maxDose = 400.0,
            unit = "mg",
            maxCount = null,
            windowSeconds = 86_400,
            warningPercent = 80,
        )

        val nicotineSample = IntakeLimit(
            substanceName = "Nicotine",
            limitType = IntakeLimitType.COUNT,
            maxDose = null,
            unit = null,
            maxCount = 5,
            windowSeconds = 2_592_000,
            warningPercent = 80,
        )
    }
}
