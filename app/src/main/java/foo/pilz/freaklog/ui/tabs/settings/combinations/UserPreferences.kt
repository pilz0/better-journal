/*
 * Copyright (c) 2023. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.ui.tabs.settings.combinations

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import foo.pilz.freaklog.ui.tabs.journal.experience.components.SavedTimeDisplayOption
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences.PreferencesKeys.WEBHOOK_URL
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferences @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private object PreferencesKeys {
        val KEY_TIME_DISPLAY_OPTION = stringPreferencesKey("key_time_display_option")

        // last ingestion time of experience is used when adding an ingestion from a past experience
        // cloned ingestion time is used to copy the time from another ingestion
        // those values need to be set/reset whenever an ingestion is added
        val KEY_LAST_INGESTION_OF_EXPERIENCE = longPreferencesKey("KEY_LAST_INGESTION_OF_EXPERIENCE")
        val KEY_CLONED_INGESTION_TIME = longPreferencesKey("KEY_CLONED_INGESTION_TIME")

        val KEY_HIDE_ORAL_DISCLAIMER = booleanPreferencesKey("key_hide_oral_disclaimer")
        val KEY_HIDE_DOSAGE_DOTS = booleanPreferencesKey("key_hide_dosage_dots")
        val KEY_ARE_SUBSTANCE_HEIGHTS_INDEPENDENT = booleanPreferencesKey("KEY_ARE_SUBSTANCE_HEIGHTS_INDEPENDENT")
        val KEY_IS_TIMELINE_HIDDEN = booleanPreferencesKey("KEY_IS_TIMELINE_HIDDEN")
        val KEY_HIDE_SAFER = booleanPreferencesKey("KEY_HIDE_SAFER")
        val KEY_HIDE_STATS = booleanPreferencesKey("KEY_HIDE_STATS")
        val KEY_HIDE_DRUGS = booleanPreferencesKey("KEY_HIDE_DRUGS")
        val KEY_HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("KEY_HAPTIC_FEEDBACK_ENABLED")

        val WEBHOOK_URL = stringPreferencesKey("webhook_url")

        val WEBHOOK_NAME = stringPreferencesKey("webhook_name")

        val WEBHOOK_TEMPLATE = stringPreferencesKey("webhook_template")

        // True once the legacy single-webhook preferences above have been migrated
        // into the `webhook` table. See WebhookSeeder.
        val WEBHOOK_SEEDED = booleanPreferencesKey("webhook_seeded")

        val AI_API_KEY = stringPreferencesKey("ai_api_key")
        val AI_MODEL_NAME = stringPreferencesKey("ai_model_name")

        val KEY_INVENTORY_ENABLED = booleanPreferencesKey("key_inventory_enabled")

        val KEY_REDOSE_ONSET_FRACTION = stringPreferencesKey("key_redose_onset_fraction")
        val KEY_REDOSE_COMEUP_FRACTION = stringPreferencesKey("key_redose_comeup_fraction")
        val KEY_REDOSE_PEAK_FRACTION = stringPreferencesKey("key_redose_peak_fraction")
        val KEY_REDOSE_SHOW = booleanPreferencesKey("key_redose_show")

        // App lock (biometric)
        val KEY_LOCK_ENABLED = booleanPreferencesKey("key_lock_enabled")
        val KEY_LOCK_TIME_OPTION = stringPreferencesKey("key_lock_time_option")
        val KEY_LOCK_LAST_ACTIVE = longPreferencesKey("key_lock_last_active_epoch_seconds")
    }

    suspend fun saveTimeDisplayOption(value: SavedTimeDisplayOption) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_TIME_DISPLAY_OPTION] = value.name
        }
    }

    val savedTimeDisplayOptionFlow: Flow<SavedTimeDisplayOption> = dataStore.data
        .map { preferences ->
            val name = preferences[PreferencesKeys.KEY_TIME_DISPLAY_OPTION] ?: SavedTimeDisplayOption.AUTO.name
            SavedTimeDisplayOption.valueOf(name)
        }

    suspend fun saveLastIngestionTimeOfExperience(value: Instant?) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_LAST_INGESTION_OF_EXPERIENCE] = value?.epochSecond ?: 0L
        }
    }

    val lastIngestionTimeOfExperienceFlow: Flow<Instant?> = dataStore.data
        .map { preferences ->
            val epochSecond = preferences[PreferencesKeys.KEY_LAST_INGESTION_OF_EXPERIENCE] ?: 0L
            if (epochSecond != 0L) {
                Instant.ofEpochSecond(epochSecond)
            } else {
                null
            }
        }

    suspend fun saveClonedIngestionTime(value: Instant?) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_CLONED_INGESTION_TIME] = value?.epochSecond ?: 0L
        }
    }

    val clonedIngestionTimeFlow: Flow<Instant?> = dataStore.data
        .map { preferences ->
            val epochSecond = preferences[PreferencesKeys.KEY_CLONED_INGESTION_TIME] ?: 0L
            if (epochSecond != 0L) {
                Instant.ofEpochSecond(epochSecond)
            } else {
                null
            }
        }

    suspend fun saveOralDisclaimerIsHidden(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_HIDE_ORAL_DISCLAIMER] = value
        }
    }

    val isOralDisclaimerHiddenFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.KEY_HIDE_ORAL_DISCLAIMER] ?: false
        }

    suspend fun saveDosageDotsAreHidden(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_HIDE_DOSAGE_DOTS] = value
        }
    }

    val areDosageDotsHiddenFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.KEY_HIDE_DOSAGE_DOTS] ?: false
        }

    suspend fun saveAreSubstanceHeightsIndependent(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_ARE_SUBSTANCE_HEIGHTS_INDEPENDENT] = value
        }
    }

    val areSubstanceHeightsIndependentFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.KEY_ARE_SUBSTANCE_HEIGHTS_INDEPENDENT] ?: false
        }

    val isTimelineHiddenFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.KEY_IS_TIMELINE_HIDDEN] ?: false
        }

    suspend fun saveIsTimelineHidden(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_IS_TIMELINE_HIDDEN] = value
        }
    }

    val activateSaferFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.KEY_HIDE_SAFER] ?: false
        }
    suspend fun saveActivateSafer(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_HIDE_SAFER] = value
        }
    }

    val isStatsHiddenFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.KEY_HIDE_STATS] ?: false
        }
    suspend fun saveIsStatsHidden(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_HIDE_STATS] = value
        }
    }

    val isDrugsHiddenFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.KEY_HIDE_DRUGS] ?: false
        }
    suspend fun saveIsDrugsHidden(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_HIDE_DRUGS] = value
        }
    }

    fun readWebhookURL(): Flow<String> = dataStore.data
        .map { preferences ->
            (preferences[WEBHOOK_URL] ?: "")
        }
    suspend fun writeWebhookURL(value: String) {
        dataStore.edit { preferences ->
            preferences[WEBHOOK_URL] = value
        }
    }

    fun readWebhookName(): Flow<String> = dataStore.data
        .map { preferences ->
            (preferences[PreferencesKeys.WEBHOOK_NAME] ?: "")
        }
    suspend fun writeWebhookName(value: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.WEBHOOK_NAME] = value
        }
    }

    fun readWebhookTemplate(): Flow<String> = dataStore.data
        .map { preferences ->
            (preferences[PreferencesKeys.WEBHOOK_TEMPLATE] ?: "")
        }
    suspend fun writeWebhookTemplate(value: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.WEBHOOK_TEMPLATE] = value
        }
    }

    /** Whether the legacy single-webhook preferences have already been migrated. */
    suspend fun isWebhookSeeded(): Boolean = dataStore.data
        .map { it[PreferencesKeys.WEBHOOK_SEEDED] ?: false }
        .first()

    suspend fun markWebhookSeeded() {
        dataStore.edit { it[PreferencesKeys.WEBHOOK_SEEDED] = true }
    }

    val isHapticFeedbackEnabledFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.KEY_HAPTIC_FEEDBACK_ENABLED] ?: true
        }

    suspend fun saveHapticFeedbackEnabled(value: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_HAPTIC_FEEDBACK_ENABLED] = value
        }
    }

    val aiApiKeyFlow: Flow<String> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.AI_API_KEY] ?: ""
        }

    suspend fun saveAiApiKey(value: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AI_API_KEY] = value
        }
    }

    val aiModelNameFlow: Flow<String> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.AI_MODEL_NAME] ?: "gemini-2.5-flash"
        }

    suspend fun saveAiModelName(value: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AI_MODEL_NAME] = value
        }
    }

    // ---- Inventory tab ----

    val isInventoryEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.KEY_INVENTORY_ENABLED] ?: false
    }

    suspend fun saveInventoryEnabled(value: Boolean) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.KEY_INVENTORY_ENABLED] = value }
    }

    // ---- Redose recommendation ----

    val isRedoseShownFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.KEY_REDOSE_SHOW] ?: true
    }

    suspend fun saveRedoseShown(value: Boolean) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.KEY_REDOSE_SHOW] = value }
    }

    val redoseOnsetFractionFlow: Flow<Float> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.KEY_REDOSE_ONSET_FRACTION]?.toFloatOrNull() ?: 1.0f
    }

    val redoseComeupFractionFlow: Flow<Float> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.KEY_REDOSE_COMEUP_FRACTION]?.toFloatOrNull() ?: 1.0f
    }

    val redosePeakFractionFlow: Flow<Float> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.KEY_REDOSE_PEAK_FRACTION]?.toFloatOrNull() ?: 0.5f
    }

    suspend fun saveRedoseFractions(onset: Float, comeup: Float, peak: Float) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.KEY_REDOSE_ONSET_FRACTION] = onset.toString()
            prefs[PreferencesKeys.KEY_REDOSE_COMEUP_FRACTION] = comeup.toString()
            prefs[PreferencesKeys.KEY_REDOSE_PEAK_FRACTION] = peak.toString()
        }
    }

    // ---- App lock (biometric) ----

    val isLockEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.KEY_LOCK_ENABLED] ?: false
    }

    suspend fun saveLockEnabled(value: Boolean) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.KEY_LOCK_ENABLED] = value }
    }

    val lockTimeOptionFlow: Flow<foo.pilz.freaklog.ui.tabs.settings.lock.LockTimeOption> =
        dataStore.data.map { prefs ->
            foo.pilz.freaklog.ui.tabs.settings.lock.LockTimeOption.fromName(
                prefs[PreferencesKeys.KEY_LOCK_TIME_OPTION]
            )
        }

    suspend fun saveLockTimeOption(value: foo.pilz.freaklog.ui.tabs.settings.lock.LockTimeOption) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.KEY_LOCK_TIME_OPTION] = value.name }
    }

    /**
     * Last time the app was foregrounded, expressed as epoch seconds. 0 means "unknown",
     * which is treated as "needs to lock" by [foo.pilz.freaklog.ui.tabs.settings.lock.shouldLockNow].
     */
    val lastActiveEpochSecondsFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.KEY_LOCK_LAST_ACTIVE] ?: 0L
    }

    suspend fun saveLastActiveEpochSeconds(value: Long) {
        dataStore.edit { prefs -> prefs[PreferencesKeys.KEY_LOCK_LAST_ACTIVE] = value }
    }
}