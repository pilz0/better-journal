package foo.pilz.freaklog.ui.tabs.search.custom

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import foo.pilz.freaklog.data.room.AppDatabase
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceRepository
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

  @Before
  fun setUp() {
    Dispatchers.setMain(Dispatchers.Unconfined)
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
  }

  @After
  fun tearDown() {
    db.close()
    Dispatchers.resetMain()
  }

  @Test
  fun fastTypingSavesTheWholeName() = runBlocking {
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
    val viewModel = AddCustomSubstanceViewModel(experienceRepo, customRepo)
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
}
