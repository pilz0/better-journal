package foo.pilz.freaklog.data.ai

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import foo.pilz.freaklog.data.ai.anodyne.AiAnodyneTools
import foo.pilz.freaklog.data.ai.profiles.AiKeyCipher
import foo.pilz.freaklog.data.ai.profiles.AiProfile
import foo.pilz.freaklog.data.ai.profiles.AiProfileStore
import foo.pilz.freaklog.data.ai.profiles.AiProviderPreset
import foo.pilz.freaklog.data.room.AppDatabase
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import foo.pilz.freaklog.ui.tabs.settings.lock.BiometricAuthManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AiChatbotRepositoryPreferenceTest {
    private object PlainCipher : AiKeyCipher {
        override fun encrypt(plain: String) = plain
        override fun decrypt(sealed: String) = sealed
    }

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun TestScope.setUp(): Triple<UserPreferences, AiProfileStore, AiChatbotRepository> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(context.cacheDir, "ai-repo-${System.nanoTime()}.preferences_pb") },
        )
        val preferences = UserPreferences(dataStore)
        val store = AiProfileStore(dataStore, PlainCipher)
        val tools = AiTools(
            db.experienceDao(),
            mockk<AiSubstanceTools>(relaxed = true),
            mockk<AiAnodyneTools>(relaxed = true),
            mockk<BiometricAuthManager> { every { shouldLock } returns MutableStateFlow(false) },
        )
        val repository = AiChatbotRepository(db.experienceDao(), preferences, tools, store)
        return Triple(preferences, store, repository)
    }

    private fun gemini(model: String = "gemini-test") = AiProfile(
        id = "g",
        name = "Gemini",
        presetId = AiProviderPreset.GEMINI.name,
        standard = AiProviderPreset.GEMINI.standard,
        baseUrl = AiProviderPreset.GEMINI.baseUrl,
        model = model,
    )

    @Test
    fun `createChatSession returns disabled when assistant is disabled even with a profile`() = runTest {
        val (preferences, store, repository) = setUp()
        store.save(gemini(), apiKey = "fake-key")
        preferences.saveAiAssistantEnabled(false)
        assertThat(repository.createChatSession(experienceId = null)).isEqualTo(AiChatSessionResult.Disabled)
    }

    @Test
    fun `createChatSession asks for setup without a profile`() = runTest {
        val (preferences, _, repository) = setUp()
        preferences.saveAiAssistantEnabled(true)
        assertThat(repository.createChatSession(experienceId = null))
            .isInstanceOf(AiChatSessionResult.NeedsSetup::class)
    }

    @Test
    fun `hosted provider without key needs setup`() = runTest {
        val (preferences, store, repository) = setUp()
        preferences.saveAiAssistantEnabled(true)
        store.save(gemini(), apiKey = null)
        assertThat(repository.createChatSession(experienceId = null))
            .isInstanceOf(AiChatSessionResult.NeedsSetup::class)
            .prop(AiChatSessionResult.NeedsSetup::message)
            .isEqualTo("The profile \"Gemini\" has no API key.")
    }

    @Test
    fun `self-hosted provider works without key and uses the whole journal as context`() = runTest {
        val (preferences, store, repository) = setUp()
        preferences.saveAiAssistantEnabled(true)
        val local = gemini().copy(
            presetId = AiProviderPreset.OLLAMA.name,
            standard = AiProviderPreset.OLLAMA.standard,
            baseUrl = "http://localhost:11434/v1",
            model = "llama3.2",
        )
        store.save(local, apiKey = null)
        val result = repository.createChatSession(experienceId = null)
        assertThat(result).isInstanceOf(AiChatSessionResult.Ready::class).all {
            prop(AiChatSessionResult.Ready::contextLabel).isEqualTo("Whole journal")
            prop(AiChatSessionResult.Ready::profile).prop(AiProfile::model).isEqualTo("llama3.2")
        }
    }
}
