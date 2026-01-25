package foo.pilz.freaklog.data.room.reminders.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminder")
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean,
    @ColumnInfo(defaultValue = "1")
    val intervalValue: Int = 1,
    @ColumnInfo(defaultValue = "DAYS")
    val intervalUnit: String = "DAYS"
)
