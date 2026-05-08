package foo.pilz.freaklog.ui.tabs.settings.combinations

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UserPreferencesAiTest {
    @Test
    fun `ai assistant is disabled by default and can be enabled`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(context.cacheDir, "ai-prefs-${System.nanoTime()}.preferences_pb") },
        )
        val preferences = UserPreferences(dataStore)

        preferences.aiAssistantEnabledFlow.test {
            assertThat(awaitItem()).isEqualTo(false)
            preferences.saveAiAssistantEnabled(true)
            assertThat(awaitItem()).isEqualTo(true)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
