/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.core.prefs.call

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.UiDataStore
import org.meshtastic.core.repository.LocalCallPrefs
import kotlin.random.Random

@Single
class LocalCallPrefsImpl(
    private val dataStore: UiDataStore,
    dispatchers: CoroutineDispatchers,
) : LocalCallPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val isLocalCallsEnabled: StateFlow<Boolean> =
        dataStore.data
            .map { it[KEY_LOCAL_CALLS_ENABLED] ?: false }
            .stateIn(scope, SharingStarted.Eagerly, false)

    override fun setLocalCallsEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_LOCAL_CALLS_ENABLED] = enabled } }
    }

    override val localCallDisplayName: StateFlow<String> =
        dataStore.data
            .map { it[KEY_LOCAL_CALL_DISPLAY_NAME] ?: "Local Caller" }
            .stateIn(scope, SharingStarted.Eagerly, "Local Caller")

    override fun setLocalCallDisplayName(name: String) {
        scope.launch { dataStore.edit { it[KEY_LOCAL_CALL_DISPLAY_NAME] = name } }
    }

    override val localCallPeerId: StateFlow<String> =
        dataStore.data
            .map { prefs ->
                prefs[KEY_LOCAL_CALL_PEER_ID] ?: run {
                    val generated = generateRandomPeerId()
                    // Persist newly generated ID
                    scope.launch { dataStore.edit { it[KEY_LOCAL_CALL_PEER_ID] = generated } }
                    generated
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, generateRandomPeerId())

    override fun setLocalCallPeerId(peerId: String) {
        scope.launch { dataStore.edit { it[KEY_LOCAL_CALL_PEER_ID] = peerId } }
    }

    private fun generateRandomPeerId(): String {
        val hex = Random.nextInt(0x10000000, 0x7FFFFFFF).toString(16)
        return "peer_$hex"
    }

    companion object {
        private val KEY_LOCAL_CALLS_ENABLED = booleanPreferencesKey("local_calls_enabled")
        private val KEY_LOCAL_CALL_DISPLAY_NAME = stringPreferencesKey("local_call_display_name")
        private val KEY_LOCAL_CALL_PEER_ID = stringPreferencesKey("local_call_peer_id")
    }
}
