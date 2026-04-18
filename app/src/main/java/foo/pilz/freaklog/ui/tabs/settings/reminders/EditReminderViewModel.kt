/*
 * Copyright (c) 2026. FreakLog contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package foo.pilz.freaklog.ui.tabs.settings.reminders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.reminders.RemindersRepository
import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import foo.pilz.freaklog.scheduled.NotificationScheduler
import foo.pilz.freaklog.scheduled.ReminderSchedule
import foo.pilz.freaklog.ui.main.navigation.graphs.EditReminderRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject

/** Form state for the reminder edit screen. Held as a single immutable value for clarity. */
data class ReminderFormState(
    val id: Int = 0,
    val title: String = "",
    val isEnabled: Boolean = true,
    val scheduleType: String = ReminderSchedule.SCHEDULE_TYPE_DAILY_AT_TIMES,
    val timesOfDay: List<LocalTime> = listOf(LocalTime.of(8, 0)),
    val daysOfWeekMask: Int = 127,
    val intervalValue: Int = 6,
    val intervalUnit: String = "Hours",
    val intervalAnchorMillis: Long = 0L,
    val endEpochMillis: Long? = null,
    val substanceName: String = "",
    val administrationRoute: String? = null,
    val dose: String = "",
    val units: String = "",
    val notes: String = "",
)

@HiltViewModel
class EditReminderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val remindersRepository: RemindersRepository,
    private val notificationScheduler: NotificationScheduler,
) : ViewModel() {

    private val reminderId: Int = savedStateHandle.toRoute<EditReminderRoute>().reminderId

    val isNew: Boolean = reminderId <= 0

    private val _state = MutableStateFlow(ReminderFormState())
    val state: StateFlow<ReminderFormState> = _state.asStateFlow()

    init {
        if (!isNew) {
            viewModelScope.launch {
                val existing = remindersRepository.getReminderById(reminderId) ?: return@launch
                _state.value = existing.toForm()
            }
        }
    }

    fun setTitle(value: String) = _state.update { it.copy(title = value) }
    fun setEnabled(value: Boolean) = _state.update { it.copy(isEnabled = value) }
    fun setScheduleType(value: String) = _state.update { it.copy(scheduleType = value) }
    fun setSubstanceName(value: String) = _state.update { it.copy(substanceName = value) }
    fun setAdministrationRoute(value: String?) = _state.update { it.copy(administrationRoute = value) }
    fun setDose(value: String) = _state.update { it.copy(dose = value) }
    fun setUnits(value: String) = _state.update { it.copy(units = value) }
    fun setNotes(value: String) = _state.update { it.copy(notes = value) }
    fun setEndDate(value: Long?) = _state.update { it.copy(endEpochMillis = value) }
    fun setIntervalValue(value: Int) = _state.update { it.copy(intervalValue = value.coerceAtLeast(1)) }
    fun setIntervalUnit(value: String) = _state.update { it.copy(intervalUnit = value) }
    fun setIntervalAnchor(value: Long) = _state.update { it.copy(intervalAnchorMillis = value) }

    fun addTime(time: LocalTime) = _state.update {
        val updated = (it.timesOfDay + time).distinct().sorted()
        it.copy(timesOfDay = updated)
    }

    fun removeTime(time: LocalTime) = _state.update {
        it.copy(timesOfDay = it.timesOfDay.filter { t -> t != time })
    }

    fun toggleDay(day: DayOfWeek) = _state.update {
        it.copy(daysOfWeekMask = ReminderSchedule.toggleDay(it.daysOfWeekMask, day))
    }

    fun canScheduleExactAlarms(): Boolean = notificationScheduler.canScheduleExactAlarms()

    /** Saves the form, schedules the reminder, then invokes [onDone]. */
    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val reminder = _state.value.toReminder()
            val savedId = if (isNew) {
                remindersRepository.insert(reminder).toInt()
            } else {
                remindersRepository.update(reminder)
                reminder.id
            }
            val toSchedule = reminder.copy(id = savedId)
            notificationScheduler.scheduleReminder(toSchedule)
            onDone()
        }
    }

    fun test() {
        viewModelScope.launch {
            val reminder = _state.value.toReminder().copy(id = if (isNew) -1 else _state.value.id)
            // For new reminders, we use a transient id of -1; persist quickly so the receiver
            // can look it up.
            if (isNew) {
                val id = remindersRepository.insert(reminder.copy(id = 0, title = reminder.title.ifBlank { "(Test)" })).toInt()
                _state.update { it.copy(id = id) }
                notificationScheduler.testNotification(reminder.copy(id = id))
            } else {
                remindersRepository.update(reminder)
                notificationScheduler.testNotification(reminder)
            }
        }
    }

    private fun ReminderFormState.toReminder(): Reminder {
        val anchorMs = if (scheduleType == ReminderSchedule.SCHEDULE_TYPE_INTERVAL && intervalAnchorMillis == 0L) {
            System.currentTimeMillis()
        } else {
            intervalAnchorMillis
        }
        val firstTime = timesOfDay.firstOrNull() ?: LocalTime.of(8, 0)
        return Reminder(
            id = id,
            title = title.trim(),
            hour = firstTime.hour,
            minute = firstTime.minute,
            isEnabled = isEnabled,
            intervalValue = intervalValue.coerceAtLeast(1),
            intervalUnit = intervalUnit,
            scheduleType = scheduleType,
            timesOfDay = ReminderSchedule.formatTimesOfDay(timesOfDay),
            daysOfWeekMask = daysOfWeekMask.takeIf { it != 0 } ?: 127,
            startEpochMillis = anchorMs,
            endEpochMillis = endEpochMillis,
            substanceName = substanceName.trim().takeIf { it.isNotEmpty() },
            administrationRoute = administrationRoute,
            dose = dose.toDoubleOrNull(),
            units = units.trim().takeIf { it.isNotEmpty() },
            notes = notes.trim(),
        )
    }

    private fun Reminder.toForm(): ReminderFormState = ReminderFormState(
        id = id,
        title = title,
        isEnabled = isEnabled,
        scheduleType = scheduleType,
        timesOfDay = ReminderSchedule.parseTimesOfDay(timesOfDay).ifEmpty {
            // Legacy rows without timesOfDay: seed with the legacy hour:minute.
            listOf(LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)))
        },
        daysOfWeekMask = if (daysOfWeekMask == 0) 127 else daysOfWeekMask,
        intervalValue = intervalValue.coerceAtLeast(1),
        intervalUnit = intervalUnit,
        intervalAnchorMillis = startEpochMillis,
        endEpochMillis = endEpochMillis,
        substanceName = substanceName.orEmpty(),
        administrationRoute = administrationRoute,
        dose = dose?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
            .orEmpty(),
        units = units.orEmpty(),
        notes = notes,
    )

    @Suppress("unused")
    private fun nowMs(): Long = Instant.now().toEpochMilli()
}
