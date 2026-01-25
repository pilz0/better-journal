package foo.pilz.freaklog.data.room.reminders

import foo.pilz.freaklog.data.room.AppDatabase
import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemindersRepository @Inject constructor(
    private val db: AppDatabase
) {
    private val dao = db.reminderDao()

    fun getAllRemindersFlow(): Flow<List<Reminder>> = dao.getAllRemindersFlow()

    suspend fun getAllReminders(): List<Reminder> = dao.getAllReminders()

    suspend fun getReminderById(id: Int): Reminder? = dao.getReminderById(id)

    suspend fun insert(reminder: Reminder): Long = dao.insert(reminder)

    suspend fun update(reminder: Reminder) = dao.update(reminder)

    suspend fun delete(reminder: Reminder) = dao.delete(reminder)
}
