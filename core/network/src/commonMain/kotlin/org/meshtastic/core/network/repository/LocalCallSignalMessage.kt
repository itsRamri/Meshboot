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

import kotlinx.serialization.Serializable

/**
 * Signaling protocol messages exchanged over direct TCP connection on local Wi-Fi.
 */
@Serializable
sealed class LocalCallSignalMessage {
    @Serializable
    data class Invite(
        val callId: String,
        val callerId: String,
        val callerName: String,
        val audioPort: Int,
        val sessionKey: String,
    ) : LocalCallSignalMessage()

    @Serializable
    data class Ringing(
        val callId: String,
    ) : LocalCallSignalMessage()

    @Serializable
    data class Accept(
        val callId: String,
        val calleeId: String,
        val calleeName: String,
        val audioPort: Int,
        val sessionKey: String,
    ) : LocalCallSignalMessage()

    @Serializable
    data class Decline(
        val callId: String,
        val reason: String = "declined",
    ) : LocalCallSignalMessage()

    @Serializable
    data class Busy(
        val callId: String,
    ) : LocalCallSignalMessage()

    @Serializable
    data class Hangup(
        val callId: String,
    ) : LocalCallSignalMessage()
}
