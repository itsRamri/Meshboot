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
package org.meshtastic.feature.messaging.media

import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

/**
 * Versioned text message envelope carrying a chunk of media over the mesh.
 *
 * Format:
 * `[MM:v1:<transferId>:<mediaType>:<chunkIndex>:<totalChunks>:<hash8>:<base64Payload>]`
 *
 * @property version Envelope protocol version (currently 1).
 * @property transferId 8-character hex string identifying the transfer session.
 * @property mediaType Media category (VOICE, IMAGE, PDF).
 * @property chunkIndex 1-based index of this chunk.
 * @property totalChunks Total number of chunks in this transfer.
 * @property hash8 First 8 hex characters of the complete file's SHA-256 digest.
 * @property payload Decoded raw binary payload of this chunk.
 */
data class MediaEnvelope(
    val version: Int = PROTOCOL_VERSION,
    val transferId: String,
    val mediaType: MediaType,
    val chunkIndex: Int,
    val totalChunks: Int,
    val hash8: String,
    val payload: ByteArray,
) {
    init {
        require(version == PROTOCOL_VERSION) { "Unsupported envelope version: $version" }
        require(transferId.length == 8) { "Transfer ID must be 8 hex characters" }
        require(chunkIndex in 1..totalChunks) { "Chunk index $chunkIndex out of bounds for total $totalChunks" }
        require(totalChunks in 1..MAX_CHUNKS) { "Total chunks $totalChunks exceeds limit $MAX_CHUNKS" }
        require(hash8.length == 8) { "Checksum prefix must be 8 hex characters" }
        require(payload.size <= MAX_RAW_CHUNK_PAYLOAD_BYTES) { "Payload size ${payload.size} exceeds max" }
    }

    /** Serializes the envelope into a standard text message. */
    fun toMessageText(): String {
        val base64Payload = payload.toByteString().base64()
        return "[$ENVELOPE_PREFIX:v$version:$transferId:${mediaType.code}:$chunkIndex:$totalChunks:$hash8:$base64Payload]"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaEnvelope) return false
        if (version != other.version) return false
        if (transferId != other.transferId) return false
        if (mediaType != other.mediaType) return false
        if (chunkIndex != other.chunkIndex) return false
        if (totalChunks != other.totalChunks) return false
        if (hash8 != other.hash8) return false
        if (!payload.contentEquals(other.payload)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + transferId.hashCode()
        result = 31 * result + mediaType.hashCode()
        result = 31 * result + chunkIndex
        result = 31 * result + totalChunks
        result = 31 * result + hash8.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        const val PROTOCOL_VERSION = 1
        const val ENVELOPE_PREFIX = "MM"
        const val RECEIPT_PREFIX = "MM:RCV"
        const val MAX_CHUNKS = 250
        const val MAX_RAW_CHUNK_PAYLOAD_BYTES = 120

        /** Fast preliminary check before running full parsing. */
        fun isMediaEnvelope(text: String): Boolean {
            val trimmed = text.trim()
            return (trimmed.startsWith("[$ENVELOPE_PREFIX:v1:") || trimmed.startsWith("[$RECEIPT_PREFIX:v1:")) &&
                trimmed.endsWith("]")
        }

        /** Checks if a message text is a receiver receipt. */
        fun isMediaReceipt(text: String): Boolean {
            val trimmed = text.trim()
            return trimmed.startsWith("[$RECEIPT_PREFIX:v1:") && trimmed.endsWith("]")
        }

        /** Formats a delivery receipt message for a verified media transfer: `[MM:RCV:v1:<transferId>:<hash8>]`. */
        fun createReceiptMessage(transferId: String, hash8: String): String =
            "[$RECEIPT_PREFIX:v$PROTOCOL_VERSION:${transferId.lowercase()}:${hash8.lowercase()}]"

        /** Parses a receiver receipt message into Pair(transferId, hash8) or null. */
        fun parseReceiptOrNull(text: String): Pair<String, String>? {
            val trimmed = text.trim()
            if (!isMediaReceipt(trimmed)) return null
            val inner = trimmed.substring(1, trimmed.length - 1)
            val parts = inner.split(':')
            // Expected parts: ["MM", "RCV", "v1", transferId, hash8]
            if (parts.size != 5) return null
            if (parts[0] != "MM" || parts[1] != "RCV" || parts[2] != "v$PROTOCOL_VERSION") return null
            val transferId = parts[3].lowercase()
            val hash8 = parts[4].lowercase()
            if (transferId.length != 8 || !transferId.all { it in "0123456789abcdef" }) return null
            if (hash8.length != 8 || !hash8.all { it in "0123456789abcdef" }) return null
            return transferId to hash8
        }

        /**
         * Parses a text message into a [MediaEnvelope].
         *
         * Returns `null` if the text does not match the envelope format or contains invalid/untrusted values.
         */
        @Suppress("ReturnCount")
        fun parseOrNull(text: String): MediaEnvelope? {
            val trimmed = text.trim()
            if (!isMediaEnvelope(trimmed) || isMediaReceipt(trimmed)) return null

            // Strip enclosing brackets '[' and ']'
            val inner = trimmed.substring(1, trimmed.length - 1)
            val parts = inner.split(':')
            // Expected parts: ["MM", "v1", transferId, typeCode, chunkIndex, totalChunks, hash8, base64Payload]
            if (parts.size != 8) return null

            if (parts[0] != ENVELOPE_PREFIX) return null
            if (parts[1] != "v$PROTOCOL_VERSION") return null

            val transferId = parts[2].lowercase()
            if (transferId.length != 8 || !transferId.all { it in "0123456789abcdef" }) return null

            val typeCode = parts[3].singleOrNull() ?: return null
            val mediaType = MediaType.fromCode(typeCode) ?: return null

            val chunkIndex = parts[4].toIntOrNull() ?: return null
            val totalChunks = parts[5].toIntOrNull() ?: return null
            if (totalChunks !in 1..MAX_CHUNKS || chunkIndex !in 1..totalChunks) return null

            val hash8 = parts[6].lowercase()
            if (hash8.length != 8 || !hash8.all { it in "0123456789abcdef" }) return null

            val base64Payload = parts[7]
            val byteString = base64Payload.decodeBase64() ?: return null
            val payloadBytes = byteString.toByteArray()
            if (payloadBytes.size > MAX_RAW_CHUNK_PAYLOAD_BYTES) return null

            return try {
                MediaEnvelope(
                    version = PROTOCOL_VERSION,
                    transferId = transferId,
                    mediaType = mediaType,
                    chunkIndex = chunkIndex,
                    totalChunks = totalChunks,
                    hash8 = hash8,
                    payload = payloadBytes,
                )
            } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                null
            }
        }
    }
}
