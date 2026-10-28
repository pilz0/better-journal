package foo.pilz.freaklog.data.ai.profiles

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import foo.pilz.freaklog.data.ai.provider.AiApiStandard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saved AI provider profiles. The list and the active profile id live in the app's preferences
 * as JSON; each API key is stored under its own preference, encrypted with [AiKeyCipher].
 * Preferences are excluded from cloud backup and device transfer (`data_extraction_rules.xml`).
 */
@Singleton
class AiProfileStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val cipher: AiKeyCipher,
) {
    companion object {
        private val PROFILES = stringPreferencesKey("ai_profiles")
        private val ACTIVE_PROFILE_ID = stringPreferencesKey("ai_active_profile_id")
        private fun keyPref(id: String) = stringPreferencesKey("ai_profile_key_$id")

        // Single-provider settings of earlier versions (plain-text Gemini key and model).
        private val LEGACY_API_KEY = stringPreferencesKey("ai_api_key")
        private val LEGACY_MODEL_NAME = stringPreferencesKey("ai_model_name")

        private val json = Json { ignoreUnknownKeys = true }

        fun newId(): String = UUID.randomUUID().toString()
    }

    val profilesFlow: Flow<List<AiProfile>> = dataStore.data.map { decode(it[PROFILES]) }

    /** The selected profile's id, falling back to the first profile. */
    val activeProfileIdFlow: Flow<String?> = dataStore.data.map { activeId(it) }

    /** Ids of profiles that have a saved API key. */
    val profilesWithKeyFlow: Flow<Set<String>> = dataStore.data.map { prefs ->
        decode(prefs[PROFILES]).filter { prefs[keyPref(it.id)] != null }.map { it.id }.toSet()
    }

    suspend fun activeProfile(): AiProfile? {
        val prefs = dataStore.data.first()
        val id = activeId(prefs) ?: return null
        return decode(prefs[PROFILES]).firstOrNull { it.id == id }
    }

    suspend fun profile(id: String): AiProfile? = profilesFlow.first().firstOrNull { it.id == id }

    /** The decrypted API key, or "" when none is saved or it can no longer be decrypted. */
    suspend fun apiKey(id: String): String =
        dataStore.data.first()[keyPref(id)]?.let { cipher.decrypt(it) }.orEmpty()

    /**
     * Inserts or replaces [profile]. [apiKey] null keeps the saved key; blank removes it.
     * The first profile saved becomes the active one.
     */
    suspend fun save(profile: AiProfile, apiKey: String?) {
        dataStore.edit { prefs ->
            val list = decode(prefs[PROFILES])
            val updated = if (list.any { it.id == profile.id }) {
                list.map { if (it.id == profile.id) profile else it }
            } else {
                list + profile
            }
            prefs[PROFILES] = json.encodeToString(updated)
            when {
                apiKey == null -> Unit
                apiKey.isBlank() -> prefs.remove(keyPref(profile.id))
                else -> prefs[keyPref(profile.id)] = cipher.encrypt(apiKey.trim())
            }
            if (activeId(prefs) == null) prefs[ACTIVE_PROFILE_ID] = profile.id
        }
    }

    suspend fun delete(id: String) {
        dataStore.edit { prefs ->
            val remaining = decode(prefs[PROFILES]).filterNot { it.id == id }
            prefs[PROFILES] = json.encodeToString(remaining)
            prefs.remove(keyPref(id))
            if (prefs[ACTIVE_PROFILE_ID] == id) {
                remaining.firstOrNull()?.let { prefs[ACTIVE_PROFILE_ID] = it.id } ?: prefs.remove(ACTIVE_PROFILE_ID)
            }
        }
    }

    suspend fun setActive(id: String) {
        dataStore.edit { it[ACTIVE_PROFILE_ID] = id }
    }

    /**
     * Turns the single Gemini key and model of earlier versions into a "Gemini" profile and
     * removes the plain-text key. Runs once; does nothing when profiles already exist.
     */
    suspend fun migrateLegacySettings() {
        if (dataStore.data.first()[PROFILES] != null) return
        dataStore.edit { prefs -> migrate(prefs) }
    }

    private fun migrate(prefs: MutablePreferences) {
        if (prefs[PROFILES] != null) return
        val legacyKey = prefs[LEGACY_API_KEY].orEmpty().trim()
        val legacyModel = prefs[LEGACY_MODEL_NAME].orEmpty().trim()
        prefs.remove(LEGACY_API_KEY)
        prefs.remove(LEGACY_MODEL_NAME)
        if (legacyKey.isBlank() && legacyModel.isBlank()) {
            prefs[PROFILES] = "[]"
            return
        }
        val preset = AiProviderPreset.GEMINI
        val profile = AiProfile(
            id = newId(),
            name = preset.label,
            presetId = preset.name,
            standard = AiApiStandard.GEMINI,
            baseUrl = preset.baseUrl,
            model = legacyModel.ifBlank { preset.suggestedModel },
        )
        prefs[PROFILES] = json.encodeToString(listOf(profile))
        prefs[ACTIVE_PROFILE_ID] = profile.id
        if (legacyKey.isNotBlank()) prefs[keyPref(profile.id)] = cipher.encrypt(legacyKey)
    }

    private fun activeId(prefs: Preferences): String? {
        val list = decode(prefs[PROFILES])
        return prefs[ACTIVE_PROFILE_ID]?.takeIf { id -> list.any { it.id == id } } ?: list.firstOrNull()?.id
    }

    private fun decode(raw: String?): List<AiProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<AiProfile>>(raw)
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }
}
