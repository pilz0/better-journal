package foo.pilz.freaklog.data.ai.profiles

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import foo.pilz.freaklog.data.ai.provider.AiApiStandard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AiProfileStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** Reversible stand-in for the Keystore cipher, which Robolectric doesn't provide. */
    private object FakeCipher : AiKeyCipher {
        override fun encrypt(plain: String) = "sealed:" + plain.reversed()
        override fun decrypt(sealed: String) = sealed.removePrefix("sealed:").reversed()
    }

    private fun TestScope.dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmp.newFile("p.preferences_pb") })

    private fun profile(id: String, name: String = id) = AiProfile(
        id = id,
        name = name,
        presetId = AiProviderPreset.CUSTOM_OPENAI.name,
        standard = AiApiStandard.OPENAI_CHAT,
        baseUrl = "http://10.0.0.2:11434/v1",
        model = "llama",
    )

    @Test
    fun legacyGeminiSettingsBecomeAnEncryptedProfile() = runTest {
        val ds = dataStore()
        ds.edit {
            it[stringPreferencesKey("ai_api_key")] = "legacy-key"
            it[stringPreferencesKey("ai_model_name")] = "gemini-x"
        }
        val store = AiProfileStore(ds, FakeCipher)
        store.migrateLegacySettings()

        val active = store.activeProfile()
        assertThat(active).isNotNull()
        assertThat(active!!.standard).isEqualTo(AiApiStandard.GEMINI)
        assertThat(active.model).isEqualTo("gemini-x")
        assertThat(store.apiKey(active.id)).isEqualTo("legacy-key")
        val prefs = ds.data.first()
        assertThat(prefs[stringPreferencesKey("ai_api_key")]).isNull()
        assertThat(prefs[stringPreferencesKey("ai_model_name")]).isNull()
        // The key is never stored in plain text.
        assertThat(prefs.asMap().values.any { it == "legacy-key" }).isFalse()
    }

    @Test
    fun migrationWithoutLegacySettingsCreatesNothingAndRunsOnce() = runTest {
        val ds = dataStore()
        val store = AiProfileStore(ds, FakeCipher)
        store.migrateLegacySettings()
        ds.edit { it[stringPreferencesKey("ai_api_key")] = "late" }
        store.migrateLegacySettings()
        assertThat(store.profilesFlow.first()).isEmpty()
    }

    @Test
    fun firstSavedProfileIsActiveAndKeysCanBeKeptOrRemoved() = runTest {
        val store = AiProfileStore(dataStore(), FakeCipher)
        store.save(profile("a"), apiKey = "k1")
        store.save(profile("b"), apiKey = null)
        assertThat(store.activeProfile()?.id).isEqualTo("a")
        assertThat(store.profilesWithKeyFlow.first()).isEqualTo(setOf("a"))

        store.save(profile("a", name = "renamed"), apiKey = null)
        assertThat(store.apiKey("a")).isEqualTo("k1")
        assertThat(store.profile("a")?.name).isEqualTo("renamed")

        store.save(profile("a"), apiKey = "")
        assertThat(store.apiKey("a")).isEqualTo("")
    }

    @Test
    fun deletingTheActiveProfileActivatesAnotherAndDropsItsKey() = runTest {
        val store = AiProfileStore(dataStore(), FakeCipher)
        store.save(profile("a"), apiKey = "k1")
        store.save(profile("b"), apiKey = "k2")
        store.setActive("a")
        store.delete("a")
        assertThat(store.activeProfile()?.id).isEqualTo("b")
        assertThat(store.apiKey("a")).isEqualTo("")
        assertThat(store.profilesFlow.first().map { it.id }).containsExactly("b")
    }

    @Test
    fun insecureRemoteDetection() {
        assertThat(isInsecureRemoteUrl("http://192.168.1.5:11434/v1")).isTrue()
        assertThat(isInsecureRemoteUrl("http://localhost:11434/v1")).isFalse()
        assertThat(isInsecureRemoteUrl("http://127.0.0.1:11434/v1")).isFalse()
        assertThat(isInsecureRemoteUrl("http://[::1]:11434/v1")).isFalse()
        assertThat(isInsecureRemoteUrl("HTTP://LOCALHOST:11434/v1")).isFalse()
        assertThat(isInsecureRemoteUrl("http://localhost.example.com/v1")).isTrue()
        assertThat(isInsecureRemoteUrl("http://localhost@remote.example/v1")).isTrue()
        assertThat(isInsecureRemoteUrl("https://192.168.1.5/v1")).isFalse()
        assertThat(isInsecureRemoteUrl("https://api.openai.com/v1")).isFalse()
    }
}
