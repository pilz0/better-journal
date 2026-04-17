/*
 * Copyright (c) 2026. FreakLog contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package foo.pilz.freaklog.ui.tabs.settings.funny

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists which achievement IDs the user has already seen, so that
 * "newly unlocked" popups don't re-fire across app restarts and we can
 * badge the settings entry with any unseen unlocks.
 */
@Singleton
class AchievementPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val SEEN = stringSetPreferencesKey("achievement_seen_ids")
    }

    val seenIdsFlow: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[Keys.SEEN] ?: emptySet()
    }

    suspend fun markSeen(ids: Set<String>) {
        if (ids.isEmpty()) return
        dataStore.edit { prefs ->
            val current = prefs[Keys.SEEN] ?: emptySet()
            prefs[Keys.SEEN] = current + ids
        }
    }
}
