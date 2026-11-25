package foo.pilz.freaklog.data.ai.web

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import foo.pilz.freaklog.data.ai.profiles.AiKeyCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AiWebSettingsStoreTest {
  @get:Rule val tmp = TemporaryFolder()

  private object FakeCipher : AiKeyCipher {
    override fun encrypt(plain: String) = "sealed:" + plain.reversed()

    override fun decrypt(sealed: String) = sealed.removePrefix("sealed:").reversed()
  }

  private fun TestScope.dataStore(): DataStore<Preferences> =
      PreferenceDataStoreFactory.create(
          scope = backgroundScope,
          produceFile = { tmp.newFile("web-settings.preferences_pb") },
      )

  @Test
  fun defaultsAreDisabledAndDuckDuckGo() = runTest {
    val store = AiWebSettingsStore(dataStore(), FakeCipher)
    assertThat(store.isEnabled()).isFalse()
    assertThat(store.searchEngine()).isEqualTo(AiWebSearchEngine.DUCKDUCKGO)
    assertThat(store.searxngUrl()).isEqualTo("")
    assertThat(store.hasBraveApiKeyFlow.first()).isFalse()
    assertThat(store.braveApiKey()).isEqualTo("")
  }

  @Test
  fun braveApiKeyIsEncryptedInDataStoreAndRemovable() = runTest {
    val ds = dataStore()
    val store = AiWebSettingsStore(ds, FakeCipher)
    store.setBraveApiKey("  my-brave-secret  ")

    assertThat(store.hasBraveApiKeyFlow.first()).isTrue()
    assertThat(store.braveApiKey()).isEqualTo("my-brave-secret")
    val rawValues = ds.data.first().asMap().values
    assertThat(rawValues.any { it.toString().contains("my-brave-secret") }).isFalse()

    store.setBraveApiKey("")
    assertThat(store.hasBraveApiKeyFlow.first()).isFalse()
    assertThat(store.braveApiKey()).isEqualTo("")
  }

  @Test
  fun searxngUrlNormalizesAndClearsWhenInvalidOrBlank() = runTest {
    val store = AiWebSettingsStore(dataStore(), FakeCipher)
    store.setEnabled(true)
    store.setSearchEngine(AiWebSearchEngine.SEARXNG)
    store.setSearxngUrl("searx.example.org/")

    assertThat(store.isEnabled()).isTrue()
    assertThat(store.searchEngine()).isEqualTo(AiWebSearchEngine.SEARXNG)
    assertThat(store.searxngUrl()).isEqualTo("https://searx.example.org")

    store.setSearxngUrl("  ")
    assertThat(store.searxngUrl()).isEqualTo("")
  }
}
