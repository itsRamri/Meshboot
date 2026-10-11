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
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ActiveCallSession
import org.meshtastic.core.model.CallState
import org.meshtastic.core.model.LocalCallPeer
import org.meshtastic.core.repository.LocalCallPrefs
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import kotlin.random.Random

private const val SIGNAL_PORT = 42430
private const val LOCAL_AUDIO_PORT = 42432
private const val CALL_RING_TIMEOUT_MS = 30000L

@Single
class CallSessionManagerImpl(
    private val localCallPrefs: LocalCallPrefs,
    private val audioCallEngine: AudioCallEngine,
    private val dispatchers: CoroutineDispatchers,
) : CallSessionManager {

    private val scope = CoroutineScope(dispatchers.default)
    private val _activeSession = MutableStateFlow<ActiveCallSession?>(null)
    override val activeSession: StateFlow<ActiveCallSession?> = _activeSession.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var listenerJob: Job? = null
    private var durationJob: Job? = null
    private var timeoutJob: Job? = null
    private var activeSocket: Socket? = null
    private var writer: PrintWriter? = null

    private val json = Json { ignoreUnknownKeys = true }

    init {
        scope.launch(dispatchers.io) {
            localCallPrefs.isLocalCallsEnabled.collect { enabled ->
                if (enabled) {
                    startSignalingServer()
                } else {
                    stopSignalingServer()
                    endCall()
                }
            }
        }
    }

    private fun startSignalingServer() {
        stopSignalingServer()
        listenerJob = scope.launch(dispatchers.io) {
            try {
                val server = ServerSocket(SIGNAL_PORT)
                serverSocket = server
                Logger.i { "Call signaling server listening on port $SIGNAL_PORT" }
                while (isActive && !server.isClosed) {
                    val client = server.accept()
                    launch(dispatchers.io) {
                        handleIncomingConnection(client)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Logger.w(e) { "Signaling server exception" }
                }
            }
        }
    }

    private fun stopSignalingServer() {
        listenerJob?.cancel()
        listenerJob = null
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Logger.w(e) { "Error closing signaling server socket" }
        }
        serverSocket = null
    }

    private suspend fun handleIncomingConnection(client: Socket) {
        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
        val clientWriter = PrintWriter(client.getOutputStream(), true)

        val line = reader.readLine() ?: return
        try {
            val message = json.decodeFromString<LocalCallSignalMessage>(line)
            when (message) {
                is LocalCallSignalMessage.Invite -> {
                    val current = _activeSession.value
                    if (current != null && current.state != CallState.IDLE) {
                        // Busy
                        val busyMsg = json.encodeToString<LocalCallSignalMessage>(
                            LocalCallSignalMessage.Busy(message.callId)
                        )
                        clientWriter.println(busyMsg)
                        client.close()
                        return
                    }

                    // Accepted incoming call invitation
                    activeSocket = client
                    writer = clientWriter

                    val peer = LocalCallPeer(
                        peerId = message.callerId,
                        displayName = message.callerName,
                        hostAddress = client.inetAddress.hostAddress ?: "",
                        port = message.audioPort,
                        lastSeenTimestamp = System.currentTimeMillis(),
                    )

                    _activeSession.value = ActiveCallSession(
                        callId = message.callId,
                        peer = peer,
                        state = CallState.INCOMING_RINGING,
                        isIncoming = true,
                        startTimeMillis = 0L,
                    )

                    // Send Ringing acknowledgment
                    val ringMsg = json.encodeToString<LocalCallSignalMessage>(
                        LocalCallSignalMessage.Ringing(message.callId)
                    )
                    clientWriter.println(ringMsg)

                    // Start timeout
                    startTimeoutTimer()

                    // Listen for subsequent messages on this socket
                    listenSocketMessages(reader, message.callId)
                }
                else -> {
                    client.close()
                }
            }
        } catch (e: Exception) {
            Logger.e(e) { "Error parsing signaling message" }
            client.close()
        }
    }

    override fun startCall(peer: LocalCallPeer) {
        val current = _activeSession.value
        if (current != null && current.state != CallState.IDLE) {
            Logger.w { "Call already in progress" }
            return
        }

        val callId = UUID.randomUUID().toString()
        _activeSession.value = ActiveCallSession(
            callId = callId,
            peer = peer,
            state = CallState.OUTGOING_RINGING,
            isIncoming = false,
        )

        scope.launch(dispatchers.io) {
            try {
                val socket = Socket(peer.hostAddress, peer.port)
                activeSocket = socket
                val socketWriter = PrintWriter(socket.getOutputStream(), true)
                writer = socketWriter
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                val sessionKey = generateSessionKey()
                val invite = LocalCallSignalMessage.Invite(
                    callId = callId,
                    callerId = localCallPrefs.localCallPeerId.value,
                    callerName = localCallPrefs.localCallDisplayName.value,
                    audioPort = LOCAL_AUDIO_PORT,
                    sessionKey = sessionKey,
                )

                socketWriter.println(json.encodeToString<LocalCallSignalMessage>(invite))
                startTimeoutTimer()

                listenSocketMessages(reader, callId)
            } catch (e: Exception) {
                Logger.e(e) { "Failed to connect to peer for calling" }
                _activeSession.value = _activeSession.value?.copy(state = CallState.ENDED_UNREACHABLE)
                cleanupCall()
            }
        }
    }

    private suspend fun listenSocketMessages(reader: BufferedReader, callId: String) {
        withContext(dispatchers.io) {
            try {
                while (isActive) {
                    val line = reader.readLine() ?: break
                    val msg = json.decodeFromString<LocalCallSignalMessage>(line)
                    when (msg) {
                        is LocalCallSignalMessage.Ringing -> {
                            // Peer is ringing
                        }
                        is LocalCallSignalMessage.Accept -> {
                            timeoutJob?.cancel()
                            _activeSession.value = _activeSession.value?.copy(
                                state = CallState.CONNECTED,
                                startTimeMillis = System.currentTimeMillis(),
                            )
                            startDurationCounter()
                            // Start audio streaming
                            val peer = _activeSession.value?.peer ?: return@withContext
                            audioCallEngine.startAudioStream(
                                remoteAddress = peer.hostAddress,
                                remotePort = msg.audioPort,
                                localPort = LOCAL_AUDIO_PORT,
                                sessionKey = msg.sessionKey.toByteArray(),
                            )
                        }
                        is LocalCallSignalMessage.Decline -> {
                            _activeSession.value = _activeSession.value?.copy(state = CallState.ENDED_DECLINED)
                            cleanupCall()
                            break
                        }
                        is LocalCallSignalMessage.Busy -> {
                            _activeSession.value = _activeSession.value?.copy(state = CallState.ENDED_BUSY)
                            cleanupCall()
                            break
                        }
                        is LocalCallSignalMessage.Hangup -> {
                            _activeSession.value = _activeSession.value?.copy(state = CallState.ENDED_NORMAL)
                            cleanupCall()
                            break
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                Logger.w(e) { "Connection lost with peer" }
                _activeSession.value = _activeSession.value?.copy(state = CallState.ENDED_NORMAL)
                cleanupCall()
            }
        }
    }

    override fun acceptCall() {
        val session = _activeSession.value ?: return
        if (session.state != CallState.INCOMING_RINGING) return

        timeoutJob?.cancel()
        val sessionKey = generateSessionKey()

        val acceptMsg = LocalCallSignalMessage.Accept(
            callId = session.callId,
            calleeId = localCallPrefs.localCallPeerId.value,
            calleeName = localCallPrefs.localCallDisplayName.value,
            audioPort = LOCAL_AUDIO_PORT,
            sessionKey = sessionKey,
        )

        scope.launch(dispatchers.io) {
            writer?.println(json.encodeToString<LocalCallSignalMessage>(acceptMsg))
            _activeSession.value = session.copy(
                state = CallState.CONNECTED,
                startTimeMillis = System.currentTimeMillis(),
            )
            startDurationCounter()
            audioCallEngine.startAudioStream(
                remoteAddress = session.peer.hostAddress,
                remotePort = session.peer.port,
                localPort = LOCAL_AUDIO_PORT,
                sessionKey = sessionKey.toByteArray(),
            )
        }
    }

    override fun declineCall() {
        val session = _activeSession.value ?: return
        val declineMsg = LocalCallSignalMessage.Decline(session.callId)
        scope.launch(dispatchers.io) {
            writer?.println(json.encodeToString<LocalCallSignalMessage>(declineMsg))
            _activeSession.value = session.copy(state = CallState.ENDED_DECLINED)
            cleanupCall()
        }
    }

    override fun endCall() {
        val session = _activeSession.value ?: return
        val hangupMsg = LocalCallSignalMessage.Hangup(session.callId)
        scope.launch(dispatchers.io) {
            try {
                writer?.println(json.encodeToString<LocalCallSignalMessage>(hangupMsg))
            } catch (e: Exception) {
                Logger.w(e) { "Error sending hangup" }
            }
            _activeSession.value = session.copy(state = CallState.ENDED_NORMAL)
            cleanupCall()
        }
    }

    override fun toggleMute() {
        val session = _activeSession.value ?: return
        val newMute = !session.isMicMuted
        audioCallEngine.setMicrophoneMute(newMute)
        _activeSession.value = session.copy(isMicMuted = newMute)
    }

    override fun toggleSpeaker() {
        val session = _activeSession.value ?: return
        val newSpeaker = !session.isSpeakerOn
        audioCallEngine.setSpeakerphoneOn(newSpeaker)
        _activeSession.value = session.copy(isSpeakerOn = newSpeaker)
    }

    private fun startTimeoutTimer() {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(CALL_RING_TIMEOUT_MS)
            val session = _activeSession.value
            if (session != null && (session.state == CallState.OUTGOING_RINGING || session.state == CallState.INCOMING_RINGING)) {
                _activeSession.value = session.copy(state = CallState.ENDED_TIMEOUT)
                cleanupCall()
            }
        }
    }

    private fun startDurationCounter() {
        durationJob?.cancel()
        durationJob = scope.launch {
            while (isActive && _activeSession.value?.state == CallState.CONNECTED) {
                delay(1000L)
                val current = _activeSession.value ?: break
                if (current.startTimeMillis > 0L) {
                    val elapsed = (System.currentTimeMillis() - current.startTimeMillis) / 1000L
                    _activeSession.value = current.copy(durationSeconds = elapsed)
                }
            }
        }
    }

    private fun cleanupCall() {
        timeoutJob?.cancel()
        timeoutJob = null
        durationJob?.cancel()
        durationJob = null

        audioCallEngine.stopAudioStream()

        try {
            activeSocket?.close()
        } catch (e: Exception) {
            Logger.w(e) { "Error closing active call socket" }
        }
        activeSocket = null
        writer = null

        scope.launch {
            delay(2000L) // Keep final ended state visible briefly for UI feedback
            if (_activeSession.value?.state?.name?.startsWith("ENDED") == true) {
                _activeSession.value = null
            }
        }
    }

    private fun generateSessionKey(): String {
        return Random.nextBytes(16).joinToString("") { "%02x".format(it) }
    }
}
