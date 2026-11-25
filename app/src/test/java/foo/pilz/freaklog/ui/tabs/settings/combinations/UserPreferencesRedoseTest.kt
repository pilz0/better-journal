package foo.pilz.freaklog.ui.tabs.settings.combinations

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import foo.pilz.freaklog.ui.tabs.journal.experience.redose.RedoseChipState
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UserPreferencesRedoseTest {
  @Test
  fun `redose recommendation is disabled by default and can be toggled`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dataStore =
        PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = {
              File(context.cacheDir, "redose-prefs-${System.nanoTime()}.preferences_pb")
            },
        )
    val preferences = UserPreferences(dataStore)

    preferences.isRedoseShownFlow.test {
      assertThat(awaitItem()).isEqualTo(false)
      preferences.saveRedoseShown(true)
      assertThat(awaitItem()).isEqualTo(true)
      preferences.saveRedoseShown(false)
      assertThat(awaitItem()).isEqualTo(false)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `redose chip initial state is hidden by default`() {
    assertThat(RedoseChipState().show).isEqualTo(false)
  }
}
