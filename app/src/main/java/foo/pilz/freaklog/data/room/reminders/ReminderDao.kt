package foo.pilz.freaklog.data.room.reminders

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder")
    fun getAllRemindersFlow(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminder")
    suspend fun getAllReminders(): List<Reminder>

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun getReminderById(id: Int): Reminder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)

    @Query("DELETE FROM reminder")
    suspend fun deleteAll()
}
