package foo.pilz.freaklog.data.ai

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import foo.pilz.freaklog.data.room.AppDatabase
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AiChatbotRepositoryPreferenceTest {
    @Test
    fun `createChatSession returns disabled when assistant is disabled even with api key`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(context.cacheDir, "ai-repo-${System.nanoTime()}.preferences_pb") },
        )
        val preferences = UserPreferences(dataStore)
        preferences.saveAiApiKey("fake-key")
        preferences.saveAiModelName(AiChatbotRepository.DEFAULT_MODEL_NAME)
        preferences.saveAiAssistantEnabled(false)
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = AiChatbotRepository(db.experienceDao(), preferences)
            assertThat(repository.createChatSession(experienceId = null))
                .isEqualTo(AiChatSessionResult.Disabled)
        } finally {
            db.close()
        }
    }
}
