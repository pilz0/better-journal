package foo.pilz.freaklog.scheduled

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import foo.pilz.freaklog.data.room.reminders.RemindersRepository
import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationSchedulerTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val remindersRepository = mockk<RemindersRepository>(relaxed = true)
  private val userPreferences = mockk<UserPreferences>()
  private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
  private val shadowAlarmManager: ShadowAlarmManager = shadowOf(alarmManager)

  private lateinit var scheduler: NotificationScheduler

  @Before
  fun setUp() {
    every { userPreferences.isLockEnabledFlow } returns flowOf(false)
    scheduler = NotificationScheduler(context, remindersRepository, userPreferences)
  }

  private fun reminder(
      id: Int = 10,
      title: String = "Test Reminder",
      scheduleType: String = ReminderSchedule.SCHEDULE_TYPE_DAILY_AT_TIMES,
      timesOfDay: String = "12:00",
      isEnabled: Boolean = true,
      startEpochMillis: Long = System.currentTimeMillis() - 10_000,
  ) =
      Reminder(
          id = id,
          title = title,
          hour = 12,
          minute = 0,
          isEnabled = isEnabled,
          scheduleType = scheduleType,
          timesOfDay = timesOfDay,
          intervalValue = 1,
          intervalUnit = "DAYS",
          daysOfWeekMask = 127,
          startEpochMillis = startEpochMillis,
          substanceName = "Coffee",
      )

  @Test
  fun `snooze sets alarm with snooze action without cancelling regular alarm`() {
    val rem = reminder(id = 1)
    val regularTime = System.currentTimeMillis() + 3600_000L
    val snoozeTime = System.currentTimeMillis() + 900_000L

    scheduler.scheduleAt(rem, regularTime)
    assertEquals(1, shadowAlarmManager.scheduledAlarms.size)

    scheduler.snooze(rem, snoozeTime)
    assertEquals(2, shadowAlarmManager.scheduledAlarms.size)

    val snoozeAlarm =
        shadowAlarmManager.scheduledAlarms.first {
          shadowOf(it.operation).savedIntent.action?.endsWith(".snooze") == true
        }
    assertEquals(snoozeTime, snoozeAlarm.triggerAtTime)

    val regularAlarm =
        shadowAlarmManager.scheduledAlarms.first {
          shadowOf(it.operation).savedIntent.action ==
              "${NotificationScheduler.ACTION_FIRE_REMINDER}.1"
        }
    assertEquals(regularTime, regularAlarm.triggerAtTime)
  }

  @Test
  fun `scheduleReminder keeps existing snooze alarm while updating regular alarm`() {
    val rem = reminder(id = 2)
    val snoozeTime = System.currentTimeMillis() + 600_000L
    scheduler.snooze(rem, snoozeTime)
    assertEquals(1, shadowAlarmManager.scheduledAlarms.size)

    scheduler.scheduleReminder(rem)
    val hasSnooze =
        shadowAlarmManager.scheduledAlarms.any {
          shadowOf(it.operation).savedIntent.action?.endsWith(".snooze") == true
        }
    assertTrue(hasSnooze)
  }

  @Test
  fun `cancelReminder with includeSnooze true cancels both regular and snooze alarms`() {
    val rem = reminder(id = 3)
    scheduler.scheduleAt(rem, System.currentTimeMillis() + 3600_000L)
    scheduler.snooze(rem, System.currentTimeMillis() + 600_000L)
    assertEquals(2, shadowAlarmManager.scheduledAlarms.size)

    scheduler.cancelReminder(rem.id, includeSnooze = true)
    assertEquals(0, shadowAlarmManager.scheduledAlarms.size)
  }

  @Test
  fun `cancelReminder with includeSnooze false leaves snooze alarm active`() {
    val rem = reminder(id = 4)
    scheduler.scheduleAt(rem, System.currentTimeMillis() + 3600_000L)
    scheduler.snooze(rem, System.currentTimeMillis() + 600_000L)
    assertEquals(2, shadowAlarmManager.scheduledAlarms.size)

    scheduler.cancelReminder(rem.id, includeSnooze = false)
    assertEquals(1, shadowAlarmManager.scheduledAlarms.size)
    assertTrue(
        shadowOf(shadowAlarmManager.scheduledAlarms.single().operation)
            .savedIntent
            .action
            ?.endsWith(".snooze") == true
    )
  }

  @Test
  fun `rescheduleAll backfills startEpochMillis for legacy interval reminders`() = runTest {
    val legacy =
        reminder(
            id = 5,
            scheduleType = ReminderSchedule.SCHEDULE_TYPE_INTERVAL,
            startEpochMillis = 0L,
            timesOfDay = "",
        )
    coEvery { remindersRepository.getAllReminders() } returns listOf(legacy)

    scheduler.rescheduleAll()

    coVerify {
      remindersRepository.update(match { it.id == 5 && it.startEpochMillis > 0L })
    }
    assertTrue(shadowAlarmManager.scheduledAlarms.isNotEmpty())
  }
}
