package foo.pilz.freaklog.data.ai.web

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import foo.pilz.freaklog.data.ai.profiles.AiKeyCipher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Search backends the AI assistant can query when web tools are enabled. */
enum class AiWebSearchEngine(val label: String, val id: String) {
  DUCKDUCKGO("DuckDuckGo", "duckduckgo"),
  SEARXNG("SearXNG", "searxng"),
  BRAVE("Brave Search", "brave"),
  ;

  companion object {
    fun fromName(raw: String?): AiWebSearchEngine =
        entries.firstOrNull { it.name == raw } ?: DUCKDUCKGO
  }
}

/**
 * Preferences for the assistant's opt-in web tools. Web search and page reading default to off, and
 * the optional Brave Search API key is encrypted with [AiKeyCipher].
 */
@Singleton
class AiWebSettingsStore
@Inject
constructor(
    private val dataStore: DataStore<Preferences>,
    private val cipher: AiKeyCipher,
) {
  companion object {
    private val AI_WEB_ENABLED = booleanPreferencesKey("ai_web_enabled")
    private val AI_WEB_SEARCH_ENGINE = stringPreferencesKey("ai_web_search_engine")
    private val AI_WEB_SEARXNG_URL = stringPreferencesKey("ai_web_searxng_url")
    private val AI_WEB_BRAVE_API_KEY = stringPreferencesKey("ai_web_brave_api_key")
  }

  val isEnabledFlow: Flow<Boolean> = dataStore.data.map { it[AI_WEB_ENABLED] ?: false }

  val searchEngineFlow: Flow<AiWebSearchEngine> =
      dataStore.data.map { AiWebSearchEngine.fromName(it[AI_WEB_SEARCH_ENGINE]) }

  val searxngUrlFlow: Flow<String> = dataStore.data.map { it[AI_WEB_SEARXNG_URL].orEmpty() }

  val hasBraveApiKeyFlow: Flow<Boolean> =
      dataStore.data.map { !it[AI_WEB_BRAVE_API_KEY].isNullOrBlank() }

  suspend fun isEnabled(): Boolean = isEnabledFlow.first()

  suspend fun searchEngine(): AiWebSearchEngine = searchEngineFlow.first()

  suspend fun searxngUrl(): String = searxngUrlFlow.first()

  suspend fun braveApiKey(): String =
      dataStore.data.first()[AI_WEB_BRAVE_API_KEY]?.let { cipher.decrypt(it) }.orEmpty()

  suspend fun setEnabled(value: Boolean) {
    dataStore.edit { it[AI_WEB_ENABLED] = value }
  }

  suspend fun setSearchEngine(engine: AiWebSearchEngine) {
    dataStore.edit { it[AI_WEB_SEARCH_ENGINE] = engine.name }
  }

  suspend fun setSearxngUrl(url: String) {
    val normalized = WebUrlValidator.normalizeSearxngUrl(url)
    dataStore.edit { prefs ->
      if (normalized == null) {
        prefs.remove(AI_WEB_SEARXNG_URL)
      } else {
        prefs[AI_WEB_SEARXNG_URL] = normalized
      }
    }
  }

  suspend fun setBraveApiKey(apiKey: String) {
    val trimmed = apiKey.trim()
    dataStore.edit { prefs ->
      if (trimmed.isEmpty()) {
        prefs.remove(AI_WEB_BRAVE_API_KEY)
      } else {
        prefs[AI_WEB_BRAVE_API_KEY] = cipher.encrypt(trimmed)
      }
    }
  }
}
