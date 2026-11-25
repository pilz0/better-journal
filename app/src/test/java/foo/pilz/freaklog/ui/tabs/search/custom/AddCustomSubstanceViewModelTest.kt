package foo.pilz.freaklog.ui.tabs.search.custom

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import foo.pilz.freaklog.data.room.AppDatabase
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceRepository
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AddCustomSubstanceViewModelTest {
  private lateinit var db: AppDatabase
  private val viewModels = mutableListOf<AddCustomSubstanceViewModel>()

  @Before
  fun setUp() {
    Dispatchers.setMain(Dispatchers.Unconfined)
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
  }

  @After
  fun tearDown() {
    runBlocking {
      withTimeout(5_000) {
        viewModels.forEach { vm ->
          vm.awaitPendingSaves()
          val job = vm.viewModelScope.coroutineContext[Job]
          job?.cancelChildren()
          job?.children?.toList()?.forEach { it.join() }
        }
      }
    }
    viewModels.clear()
    db.close()
    Dispatchers.resetMain()
  }

  private fun createViewModel(): Pair<CustomSubstanceRepository, AddCustomSubstanceViewModel> {
    val dao = db.experienceDao()
    val customRepo = CustomSubstanceRepository(dao)
    val experienceRepo =
        ExperienceRepository(
            db,
            dao,
            db.reminderDao(),
            db.webhookDao(),
            db.ingestionWebhookMessageDao(),
            ApplicationProvider.getApplicationContext(),
        )
    val vm = AddCustomSubstanceViewModel(experienceRepo, customRepo)
    viewModels += vm
    return customRepo to vm
  }

  @Test
  fun fastTypingSavesTheWholeName() = runBlocking {
    val (customRepo, viewModel) = createViewModel()
    val id = withTimeout(5_000) { viewModel.idFlow.first { it != null }!! }

    // Every keystroke starts a save; before the fix they raced on a stale copy.
    "Testamine".indices.forEach { viewModel.onNameChange("Testamine".substring(0, it + 1)) }

    val saved =
        withTimeout(5_000) {
          var name = customRepo.getWithEverything(id)?.substance?.name
          while (name != "Testamine") {
            delay(20)
            name = customRepo.getWithEverything(id)?.substance?.name
          }
          name
        }
    assertThat(saved).isEqualTo("Testamine")
  }

  @Test
  fun summaryTypedBeforeInitCompletesIsSaved() = runBlocking {
    val (customRepo, viewModel) = createViewModel()
    viewModel.onSummaryChange("Pre-init notes")

    val id = withTimeout(5_000) { viewModel.idFlow.first { it != null }!! }
    val saved =
        withTimeout(5_000) {
          var summary = customRepo.getWithEverything(id)?.substance?.summary
          while (summary != "Pre-init notes") {
            delay(20)
            summary = customRepo.getWithEverything(id)?.substance?.summary
          }
          summary
        }
    assertThat(saved).isEqualTo("Pre-init notes")
  }

  @Test
  fun deleteDraftRemovesCreatedRowAndInvokesCallbackOnce() = runBlocking {
    val (customRepo, viewModel) = createViewModel()
    viewModel.onNameChange("Ephemeral")

    val id = withTimeout(5_000) { viewModel.idFlow.first { it != null }!! }
    val deleted = CompletableDeferred<Unit>()
    var doneCount = 0
    viewModel.deleteDraft {
      doneCount++
      deleted.complete(Unit)
    }
    viewModel.deleteDraft { doneCount++ }

    withTimeout(5_000) { deleted.await() }
    viewModel.onNameChange("Should not revive")
    delay(50)

    assertThat(doneCount).isEqualTo(1)
    assertThat(customRepo.getWithEverything(id)).isEqualTo(null)
  }
}
