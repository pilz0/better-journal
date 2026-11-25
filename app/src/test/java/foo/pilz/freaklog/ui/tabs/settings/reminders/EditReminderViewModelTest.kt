package foo.pilz.freaklog.ui.tabs.settings.reminders

import androidx.lifecycle.SavedStateHandle
import foo.pilz.freaklog.data.room.reminders.RemindersRepository
import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import foo.pilz.freaklog.scheduled.NotificationScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class EditReminderViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun existingReminder(id: Int = 42, dose: Double? = 125.5) =
      Reminder(
          id = id,
          title = "Morning dose",
          hour = 8,
          minute = 30,
          isEnabled = true,
          timesOfDay = "08:30",
          substanceName = "Caffeine",
          administrationRoute = "ORAL",
          dose = dose,
          units = "mg",
      )

  @Test
  fun `save ignores taps before existing reminder finishes loading then awaits update`() = runTest {
    val loadDeferred = CompletableDeferred<Reminder?>()
    val updateDeferred = CompletableDeferred<Unit>()
    val repo =
        mockk<RemindersRepository>(relaxed = true) {
          coEvery { getReminderById(42) } coAnswers { loadDeferred.await() }
          coEvery { update(any()) } coAnswers { updateDeferred.await() }
        }
    val scheduler = mockk<NotificationScheduler>(relaxed = true)

    val viewModel =
        EditReminderViewModel(
            savedStateHandle = SavedStateHandle(mapOf("reminderId" to 42)),
            remindersRepository = repo,
            notificationScheduler = scheduler,
        )

    var doneCount = 0
    viewModel.save { doneCount++ }
    advanceUntilIdle()
    coVerify(exactly = 0) { repo.update(any()) }
    assertEquals(0, doneCount)

    loadDeferred.complete(existingReminder())
    advanceUntilIdle()
    assertEquals("125.5", viewModel.state.value.dose)

    viewModel.save { doneCount++ }
    viewModel.save { doneCount++ }
    advanceUntilIdle()
    assertEquals(0, doneCount)

    updateDeferred.complete(Unit)
    advanceUntilIdle()
    assertEquals(1, doneCount)
    coVerify(exactly = 1) { repo.update(any()) }
    verify(exactly = 1) { scheduler.scheduleReminder(match { it.id == 42 }, any()) }
  }

  @Test
  fun `new reminder evaluates dose expression and rejects non-positive doses`() = runTest {
    val repo =
        mockk<RemindersRepository>(relaxed = true) {
          coEvery { insert(any()) } returns 99L
        }
    val scheduler = mockk<NotificationScheduler>(relaxed = true)

    val viewModel =
        EditReminderViewModel(
            savedStateHandle = SavedStateHandle(mapOf("reminderId" to -1)),
            remindersRepository = repo,
            notificationScheduler = scheduler,
        )

    viewModel.setTitle("Split")
    viewModel.setDose("50 + 25")
    viewModel.save()
    advanceUntilIdle()
    coVerify(exactly = 1) { repo.insert(match { it.dose == 75.0 }) }
    verify(exactly = 1) { scheduler.scheduleReminder(match { it.id == 99 }, any()) }

    viewModel.setDose("0")
    viewModel.save()
    advanceUntilIdle()
    coVerify(exactly = 1) { repo.insert(match { it.dose == null }) }
  }

  @Test
  fun `removeTime preserves at least one time of day`() {
    val repo = mockk<RemindersRepository>(relaxed = true)
    val scheduler = mockk<NotificationScheduler>(relaxed = true)
    val viewModel =
        EditReminderViewModel(
            savedStateHandle = SavedStateHandle(mapOf("reminderId" to -1)),
            remindersRepository = repo,
            notificationScheduler = scheduler,
        )

    val initialTime = LocalTime.of(8, 0)
    val secondTime = LocalTime.of(20, 0)
    viewModel.addTime(secondTime)
    assertEquals(listOf(initialTime, secondTime), viewModel.state.value.timesOfDay)

    viewModel.removeTime(initialTime)
    assertEquals(listOf(secondTime), viewModel.state.value.timesOfDay)

    viewModel.removeTime(secondTime)
    assertEquals(listOf(secondTime), viewModel.state.value.timesOfDay)
  }
}
