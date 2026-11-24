package foo.pilz.freaklog.ui.tabs.settings.combinations

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.VitalKind
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
class UserPreferencesVitalsTest {
  @Test
  fun `extra vitals are all off by default and toggle individually`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dataStore =
        PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = {
              File(context.cacheDir, "vitals-prefs-${System.nanoTime()}.preferences_pb")
            },
        )
    val preferences = UserPreferences(dataStore)

    preferences.extraVitalsFlow.test {
      assertThat(awaitItem()).isEmpty()
      preferences.saveExtraVitalEnabled(VitalKind.OXYGEN_SATURATION, true)
      assertThat(awaitItem()).isEqualTo(setOf(VitalKind.OXYGEN_SATURATION))
      preferences.saveExtraVitalEnabled(VitalKind.BODY_TEMPERATURE, true)
      assertThat(awaitItem())
          .isEqualTo(setOf(VitalKind.OXYGEN_SATURATION, VitalKind.BODY_TEMPERATURE))
      preferences.saveExtraVitalEnabled(VitalKind.OXYGEN_SATURATION, false)
      assertThat(awaitItem()).isEqualTo(setOf(VitalKind.BODY_TEMPERATURE))
      cancelAndIgnoreRemainingEvents()
    }
  }
}
