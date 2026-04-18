/*
 * Copyright (c) 2026. FreakLog contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package foo.pilz.freaklog.ui.tabs.settings.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.reminders.RemindersRepository
import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import foo.pilz.freaklog.scheduled.NotificationScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val remindersRepository: RemindersRepository,
    private val notificationScheduler: NotificationScheduler,
) : ViewModel() {

    val reminders = remindersRepository.getAllRemindersFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    fun canScheduleExactAlarms(): Boolean = notificationScheduler.canScheduleExactAlarms()

    fun toggleReminder(reminder: Reminder) {
        viewModelScope.launch {
            val updated = reminder.copy(isEnabled = !reminder.isEnabled)
            remindersRepository.update(updated)
            if (updated.isEnabled) {
                notificationScheduler.scheduleReminder(updated)
            } else {
                notificationScheduler.cancelReminder(updated)
            }
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            notificationScheduler.cancelReminder(reminder)
            remindersRepository.delete(reminder)
        }
    }

    /** Creates a copy of [reminder] with the same schedule, then schedules it. */
    fun duplicateReminder(reminder: Reminder) {
        viewModelScope.launch {
            val copy = reminder.copy(
                id = 0,
                title = "${reminder.title} (copy)",
            )
            val newId = remindersRepository.insert(copy).toInt()
            notificationScheduler.scheduleReminder(copy.copy(id = newId))
        }
    }
}
