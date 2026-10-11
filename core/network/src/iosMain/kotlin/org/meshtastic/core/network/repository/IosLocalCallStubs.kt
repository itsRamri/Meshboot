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
package org.meshtastic.core.network.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single
import org.meshtastic.core.model.ActiveCallSession
import org.meshtastic.core.model.LocalCallPeer

@Single
class IosCallSessionManager : CallSessionManager {
    private val _activeSession = MutableStateFlow<ActiveCallSession?>(null)
    override val activeSession: StateFlow<ActiveCallSession?> = _activeSession.asStateFlow()

    override fun startCall(peer: LocalCallPeer) {}
    override fun acceptCall() {}
    override fun declineCall() {}
    override fun endCall() {}
    override fun toggleMute() {}
    override fun toggleSpeaker() {}
}

@Single
class IosLocalCallDiscoveryService : LocalCallDiscoveryService {
    private val _discoveredPeers = MutableStateFlow<List<LocalCallPeer>>(emptyList())
    override val discoveredPeers: StateFlow<List<LocalCallPeer>> = _discoveredPeers.asStateFlow()

    override fun startDiscovery() {}
    override fun stopDiscovery() {}
}

@Single
class IosAudioCallEngine : AudioCallEngine {
    override val isSupported: Boolean = false

    override fun startAudioStream(
        remoteAddress: String,
        remotePort: Int,
        localPort: Int,
        sessionKey: ByteArray,
    ): Boolean = false

    override fun stopAudioStream() {}
    override fun setMicrophoneMute(muted: Boolean) {}
    override fun setSpeakerphoneOn(speakerOn: Boolean) {}
}
