package foo.pilz.freaklog.data.room.reminders.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

import kotlinx.serialization.Serializable

/**
 * A medication-tracker style reminder.
 *
 * Two scheduling modes are supported via [scheduleType]:
 *  - "DAILY_AT_TIMES" — fires at each `HH:mm` listed in [timesOfDay] on each day matching
 *    [daysOfWeekMask]. This is the default for new reminders.
 *  - "INTERVAL" — fires every [intervalValue] [intervalUnit] starting from [startEpochMillis]
 *    (or, for legacy rows, anchored on [hour]:[minute] today). Day-of-week filtering still
 *    applies.
 *
 * The substance/dose/route/notes fields, when present, are used to pre-fill the ingestion
 * created when the user taps the "Take" notification action.
 */
@Entity(tableName = "reminder")
@Serializable
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    /** Display name. Kept as `title` for backwards-compat with existing data and exports. */
    val title: String,
    /** Legacy: anchor hour for INTERVAL mode. */
    val hour: Int,
    /** Legacy: anchor minute for INTERVAL mode. */
    val minute: Int,
    val isEnabled: Boolean,
    @ColumnInfo(defaultValue = "1")
    val intervalValue: Int = 1,
    @ColumnInfo(defaultValue = "DAYS")
    val intervalUnit: String = "Days",
    @ColumnInfo(defaultValue = "DAILY_AT_TIMES")
    val scheduleType: String = "DAILY_AT_TIMES",
    /** Comma-separated `HH:mm` values, e.g. `"08:00,20:00"`. Used when [scheduleType] is `DAILY_AT_TIMES`. */
    @ColumnInfo(defaultValue = "")
    val timesOfDay: String = "",
    /** 7-bit mask, bit 0 = Monday … bit 6 = Sunday. 127 = every day (default). */
    @ColumnInfo(defaultValue = "127")
    val daysOfWeekMask: Int = 127,
    /** Anchor for INTERVAL mode; 0 means "use [hour]:[minute] today". */
    @ColumnInfo(defaultValue = "0")
    val startEpochMillis: Long = 0L,
    /** Optional stop time. Null = no end. */
    @ColumnInfo(defaultValue = "NULL")
    val endEpochMillis: Long? = null,
    @ColumnInfo(defaultValue = "NULL")
    val substanceName: String? = null,
    /** Stored as [foo.pilz.freaklog.data.substances.AdministrationRoute] enum name. */
    @ColumnInfo(defaultValue = "NULL")
    val administrationRoute: String? = null,
    @ColumnInfo(defaultValue = "NULL")
    val dose: Double? = null,
    @ColumnInfo(defaultValue = "NULL")
    val units: String? = null,
    @ColumnInfo(defaultValue = "")
    val notes: String = ""
)
