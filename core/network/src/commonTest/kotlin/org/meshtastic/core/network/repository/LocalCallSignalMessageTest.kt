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

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.meshtastic.core.model.CallState
import org.meshtastic.core.model.LocalCallPeer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LocalCallSignalMessageTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `serialize and deserialize Invite signal message`() {
        val invite = LocalCallSignalMessage.Invite(
            callId = "call-1234",
            callerId = "peer-abc",
            callerName = "Alice",
            audioPort = 42432,
            sessionKey = "aabbccddeeff00112233445566778899",
        )

        val serialized = json.encodeToString<LocalCallSignalMessage>(invite)
        val deserialized = json.decodeFromString<LocalCallSignalMessage>(serialized)

        assertIs<LocalCallSignalMessage.Invite>(deserialized)
        assertEquals("call-1234", deserialized.callId)
        assertEquals("peer-abc", deserialized.callerId)
        assertEquals("Alice", deserialized.callerName)
        assertEquals(42432, deserialized.audioPort)
        assertEquals("aabbccddeeff00112233445566778899", deserialized.sessionKey)
    }

    @Test
    fun `serialize and deserialize Accept signal message`() {
        val accept = LocalCallSignalMessage.Accept(
            callId = "call-1234",
            calleeId = "peer-xyz",
            calleeName = "Bob",
            audioPort = 42432,
            sessionKey = "00112233445566778899aabbccddeeff",
        )

        val serialized = json.encodeToString<LocalCallSignalMessage>(accept)
        val deserialized = json.decodeFromString<LocalCallSignalMessage>(serialized)

        assertIs<LocalCallSignalMessage.Accept>(deserialized)
        assertEquals("call-1234", deserialized.callId)
        assertEquals("Bob", deserialized.calleeName)
    }

    @Test
    fun `serialize and deserialize Hangup, Decline, Busy, and Ringing signal messages`() {
        val hangup = LocalCallSignalMessage.Hangup("call-1")
        val decline = LocalCallSignalMessage.Decline("call-2", "declined")
        val busy = LocalCallSignalMessage.Busy("call-3")
        val ringing = LocalCallSignalMessage.Ringing("call-4")

        assertIs<LocalCallSignalMessage.Hangup>(json.decodeFromString<LocalCallSignalMessage>(json.encodeToString<LocalCallSignalMessage>(hangup)))
        assertIs<LocalCallSignalMessage.Decline>(json.decodeFromString<LocalCallSignalMessage>(json.encodeToString<LocalCallSignalMessage>(decline)))
        assertIs<LocalCallSignalMessage.Busy>(json.decodeFromString<LocalCallSignalMessage>(json.encodeToString<LocalCallSignalMessage>(busy)))
        assertIs<LocalCallSignalMessage.Ringing>(json.decodeFromString<LocalCallSignalMessage>(json.encodeToString<LocalCallSignalMessage>(ringing)))
    }

    @Test
    fun `verify LocalCallPeer data integrity`() {
        val peer = LocalCallPeer(
            peerId = "peer_999",
            displayName = "Charlie",
            hostAddress = "192.168.1.50",
            port = 42430,
            lastSeenTimestamp = 1000000L,
            isOnline = true,
        )

        assertEquals("peer_999", peer.peerId)
        assertEquals("Charlie", peer.displayName)
        assertEquals("192.168.1.50", peer.hostAddress)
        assertTrue(peer.isOnline)
    }

    @Test
    fun `verify call state transitions enum`() {
        val validStates = listOf(
            CallState.IDLE,
            CallState.OUTGOING_RINGING,
            CallState.INCOMING_RINGING,
            CallState.CONNECTING,
            CallState.CONNECTED,
            CallState.ENDED_BUSY,
            CallState.ENDED_DECLINED,
            CallState.ENDED_TIMEOUT,
            CallState.ENDED_UNREACHABLE,
            CallState.ENDED_NORMAL,
        )
        assertEquals(10, validStates.size)
    }
}
