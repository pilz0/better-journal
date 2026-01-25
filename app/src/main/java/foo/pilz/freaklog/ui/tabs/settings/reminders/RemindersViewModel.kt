package foo.pilz.freaklog.ui.tabs.settings.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import foo.pilz.freaklog.data.room.reminders.RemindersRepository
import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import foo.pilz.freaklog.scheduled.NotificationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val remindersRepository: RemindersRepository,
    private val notificationScheduler: NotificationScheduler
) : ViewModel() {

    val reminders = remindersRepository.getAllRemindersFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addReminder(title: String, intervalValue: Int, intervalUnit: String) {
        viewModelScope.launch {
            val now = java.util.Calendar.getInstance()
            val reminder = Reminder(
                title = title,
                hour = now.get(java.util.Calendar.HOUR_OF_DAY),
                minute = now.get(java.util.Calendar.MINUTE),
                isEnabled = true,
                intervalValue = intervalValue,
                intervalUnit = intervalUnit
            )
            val id = remindersRepository.insert(reminder)
            // Schedule
            notificationScheduler.scheduleReminder(requestExact = true, reminder = reminder.copy(id = id.toInt()))
        }
    }

    fun updateReminder(reminder: Reminder, title: String, intervalValue: Int, intervalUnit: String) {
        viewModelScope.launch {
            // When updating, we might want to reset the start time to now, OR keep original?
            // "i only want to give it a time in minutes/hours/days" implies resetting the interval anchor to "now" makes sense for new inputs.
            // But if just editing title, maybe not? 
            // The requirement "not with a time" suggests the user sees this as "Remind me in X minutes".
            // So resetting to 'now' seems safer to ensure the interval behaves as expected from this moment.
            
            val now = java.util.Calendar.getInstance()
            val updatedReminder = reminder.copy(
                title = title,
                hour = now.get(java.util.Calendar.HOUR_OF_DAY),
                minute = now.get(java.util.Calendar.MINUTE),
                intervalValue = intervalValue,
                intervalUnit = intervalUnit,
                isEnabled = true
            )
            remindersRepository.update(updatedReminder)
            notificationScheduler.scheduleReminder(requestExact = true, reminder = updatedReminder)
        }
    }

    fun testReminder(title: String) {
        viewModelScope.launch {
             val reminder = Reminder(
                id = -1, // Dummy ID
                title = title,
                hour = 0,
                minute = 0,
                isEnabled = true
            )
            notificationScheduler.testNotification(reminder)
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            notificationScheduler.cancelReminder(reminder)
            remindersRepository.delete(reminder)
        }
    }

    fun toggleReminder(reminder: Reminder) {
        viewModelScope.launch {
            val newStatus = !reminder.isEnabled
            val updatedReminder = reminder.copy(isEnabled = newStatus)
            remindersRepository.update(updatedReminder)
            
            if (newStatus) {
                notificationScheduler.scheduleReminder(requestExact = true, reminder = updatedReminder)
            } else {
                notificationScheduler.cancelReminder(reminder)
            }
        }
    }
}
