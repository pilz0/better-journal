package foo.pilz.freaklog.data.health

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Sharing choices stay on this device; they are intentionally absent from journal exports. */
@Singleton
class HealthSharingPreferences @Inject constructor(private val store: DataStore<Preferences>) {
    private val medicationsKey = stringSetPreferencesKey("health_shared_medications")
    private val aiKey = booleanPreferencesKey("health_ai_vitals")

    val medications = store.data.map { it[medicationsKey] ?: emptySet() }
    val aiVitals = store.data.map { it[aiKey] ?: false }

    suspend fun selectMedications(names: Set<String>) {
        store.edit { it[medicationsKey] = names }
    }

    suspend fun allowAiVitals(allow: Boolean) {
        store.edit { it[aiKey] = allow }
    }
}
