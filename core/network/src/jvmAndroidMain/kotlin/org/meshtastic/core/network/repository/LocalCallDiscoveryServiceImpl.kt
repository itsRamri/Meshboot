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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.LocalCallPeer
import org.meshtastic.core.repository.LocalCallPrefs
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

private const val MULTICAST_GROUP = "239.255.42.99"
private const val MULTICAST_PORT = 42429
private const val SIGNAL_PORT_BASE = 42430
private const val PEER_TIMEOUT_MS = 15000L
private const val BROADCAST_INTERVAL_MS = 3000L

@Single
class LocalCallDiscoveryServiceImpl(
    private val localCallPrefs: LocalCallPrefs,
    dispatchers: CoroutineDispatchers,
) : LocalCallDiscoveryService {

    private val scope = CoroutineScope(dispatchers.io)
    private val _discoveredPeers = MutableStateFlow<List<LocalCallPeer>>(emptyList())
    override val discoveredPeers: StateFlow<List<LocalCallPeer>> = _discoveredPeers.asStateFlow()

    private var broadcastJob: Job? = null
    private var listenJob: Job? = null
    private var cleanupJob: Job? = null
    private var multicastSocket: MulticastSocket? = null

    private val json = Json { ignoreUnknownKeys = true }

    override fun startDiscovery() {
        stopDiscovery()
        if (!localCallPrefs.isLocalCallsEnabled.value) {
            Logger.d { "Local calls disabled, skipping discovery start" }
            return
        }

        Logger.i { "Starting LocalCallDiscoveryService on $MULTICAST_GROUP:$MULTICAST_PORT" }
        try {
            val group = InetAddress.getByName(MULTICAST_GROUP)
            val socket = MulticastSocket(MULTICAST_PORT).apply {
                timeToLive = 2 // Local subnet only
                reuseAddress = true
                joinGroup(group)
            }
            multicastSocket = socket

            // Broadcast periodic presence beacon
            broadcastJob = scope.launch {
                val buffer = ByteArray(1024)
                while (isActive) {
                    if (localCallPrefs.isLocalCallsEnabled.value) {
                        try {
                            val myId = localCallPrefs.localCallPeerId.value
                            val myName = localCallPrefs.localCallDisplayName.value
                            val payload = "MESHCALL_ANNOUNCE:$myId:$myName:$SIGNAL_PORT_BASE"
                            val bytes = payload.toByteArray(StandardCharsets.UTF_8)
                            val packet = DatagramPacket(bytes, bytes.size, group, MULTICAST_PORT)
                            socket.send(packet)
                        } catch (e: Exception) {
                            Logger.w(e) { "Error sending discovery beacon" }
                        }
                    }
                    delay(BROADCAST_INTERVAL_MS)
                }
            }

            // Listen for announcements
            listenJob = scope.launch {
                val recvBuf = ByteArray(2048)
                val packet = DatagramPacket(recvBuf, recvBuf.size)
                while (isActive && !socket.isClosed) {
                    try {
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)
                        val senderAddress = packet.address.hostAddress ?: ""
                        handleIncomingAnnouncement(text, senderAddress)
                    } catch (e: Exception) {
                        if (isActive && !socket.isClosed) {
                            Logger.w(e) { "Error in multicast receive loop" }
                        }
                    }
                }
            }

            // Cleanup stale peers
            cleanupJob = scope.launch {
                while (isActive) {
                    delay(PEER_TIMEOUT_MS / 2)
                    val now = System.currentTimeMillis()
                    val currentList = _discoveredPeers.value
                    val filtered = currentList.filter { now - it.lastSeenTimestamp < PEER_TIMEOUT_MS }
                    if (filtered.size != currentList.size) {
                        _discoveredPeers.value = filtered
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to bind multicast socket for call discovery" }
        }
    }

    private fun handleIncomingAnnouncement(text: String, hostAddress: String) {
        if (!text.startsWith("MESHCALL_ANNOUNCE:")) return
        val parts = text.split(":")
        if (parts.size < 4) return

        val peerId = parts[1]
        val displayName = parts[2]
        val port = parts[3].toIntOrNull() ?: SIGNAL_PORT_BASE

        // Ignore own announcement
        if (peerId == localCallPrefs.localCallPeerId.value) return

        val now = System.currentTimeMillis()
        val existing = _discoveredPeers.value.toMutableList()
        val index = existing.indexOfFirst { it.peerId == peerId }

        val peer = LocalCallPeer(
            peerId = peerId,
            displayName = displayName,
            hostAddress = hostAddress,
            port = port,
            lastSeenTimestamp = now,
            isOnline = true,
        )

        if (index >= 0) {
            existing[index] = peer
        } else {
            existing.add(peer)
        }
        _discoveredPeers.value = existing
    }

    override fun stopDiscovery() {
        broadcastJob?.cancel()
        broadcastJob = null
        listenJob?.cancel()
        listenJob = null
        cleanupJob?.cancel()
        cleanupJob = null

        try {
            multicastSocket?.let {
                if (!it.isClosed) {
                    it.leaveGroup(InetAddress.getByName(MULTICAST_GROUP))
                    it.close()
                }
            }
        } catch (e: Exception) {
            Logger.w(e) { "Error closing multicast socket" }
        }
        multicastSocket = null
        _discoveredPeers.value = emptyList()
        Logger.i { "LocalCallDiscoveryService stopped" }
    }
}
