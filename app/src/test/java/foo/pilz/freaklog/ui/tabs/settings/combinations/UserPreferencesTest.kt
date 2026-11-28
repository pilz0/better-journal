package foo.pilz.freaklog.ui.tabs.settings.combinations

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import java.io.File
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserPreferencesTest {

  @get:Rule val tempFolder = TemporaryFolder()

  private fun createPreferences(): UserPreferences {
    val file = File(tempFolder.root, "test_prefs.preferences_pb")
    val dataStore = PreferenceDataStoreFactory.create { file }
    return UserPreferences(dataStore)
  }

  @Test
  fun `distinctUntilChanged prevents re-emission on unrelated key changes`() = runTest {
    val prefs = createPreferences()

    prefs.areDosageDotsHiddenFlow.test {
      assertThat(awaitItem()).isFalse()

      prefs.saveIsTimelineHidden(true)
      prefs.saveActivateSafer(true)
      expectNoEvents()

      prefs.saveDosageDotsAreHidden(true)
      assertThat(awaitItem()).isTrue()

      prefs.saveDosageDotsAreHidden(true)
      expectNoEvents()

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `atomic ingestion time helpers update and clear both timestamp keys`() = runTest {
    val prefs = createPreferences()
    val first = Instant.ofEpochSecond(1_700_000_000L)
    val cloned = Instant.ofEpochSecond(1_700_001_000L)

    prefs.saveClonedIngestionTime(cloned)
    prefs.saveLastIngestionTimeAndClearCloned(first)

    prefs.lastIngestionTimeOfExperienceFlow.test {
      assertThat(awaitItem()).isEqualTo(first)
      cancelAndIgnoreRemainingEvents()
    }
    prefs.clonedIngestionTimeFlow.test {
      assertThat(awaitItem()).isNull()
      cancelAndIgnoreRemainingEvents()
    }

    prefs.resetAddIngestionTimes()
    prefs.lastIngestionTimeOfExperienceFlow.test {
      assertThat(awaitItem()).isNull()
      cancelAndIgnoreRemainingEvents()
    }
  }
}
