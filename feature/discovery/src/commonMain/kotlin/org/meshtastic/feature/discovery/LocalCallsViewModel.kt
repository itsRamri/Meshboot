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
package org.meshtastic.feature.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.model.ActiveCallSession
import org.meshtastic.core.model.LocalCallPeer
import org.meshtastic.core.network.repository.CallSessionManager
import org.meshtastic.core.network.repository.LocalCallDiscoveryService
import org.meshtastic.core.repository.LocalCallPrefs

@KoinViewModel
class LocalCallsViewModel(
    private val localCallPrefs: LocalCallPrefs,
    private val discoveryService: LocalCallDiscoveryService,
    private val callSessionManager: CallSessionManager,
) : ViewModel() {

    val isEnabled: StateFlow<Boolean> = localCallPrefs.isLocalCallsEnabled
    val displayName: StateFlow<String> = localCallPrefs.localCallDisplayName
    val myPeerId: StateFlow<String> = localCallPrefs.localCallPeerId

    val discoveredPeers: StateFlow<List<LocalCallPeer>> = discoveryService.discoveredPeers
    val activeSession: StateFlow<ActiveCallSession?> = callSessionManager.activeSession

    init {
        viewModelScope.launch {
            isEnabled.collect { enabled ->
                if (enabled) {
                    discoveryService.startDiscovery()
                } else {
                    discoveryService.stopDiscovery()
                }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        localCallPrefs.setLocalCallsEnabled(enabled)
    }

    fun setDisplayName(name: String) {
        localCallPrefs.setLocalCallDisplayName(name)
    }

    fun startCall(peer: LocalCallPeer) {
        callSessionManager.startCall(peer)
    }

    fun acceptCall() {
        callSessionManager.acceptCall()
    }

    fun declineCall() {
        callSessionManager.declineCall()
    }

    fun endCall() {
        callSessionManager.endCall()
    }

    fun toggleMute() {
        callSessionManager.toggleMute()
    }

    fun toggleSpeaker() {
        callSessionManager.toggleSpeaker()
    }
}
