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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.util.nowMillis

/** Status of a media transfer session. */
enum class TransferStatus {
    PREPARING,
    IN_PROGRESS, // Sending or Receiving
    WAITING_FOR_RECEIVER,
    VERIFYING,
    COMPLETED,
    CORRUPTED,
    FAILED,
    CANCELLED,
    TIMED_OUT,
}

/** Outcome returned when a chunk is ingested into the engine. */
sealed interface ReassemblyOutcome {
    data class Accepted(
        val transferId: String,
        val chunkIndex: Int,
        val totalChunks: Int,
        val receivedCount: Int,
        val isComplete: Boolean,
    ) : ReassemblyOutcome

    data class Duplicate(val transferId: String, val chunkIndex: Int) : ReassemblyOutcome

    data class Completed(
        val transferId: String,
        val totalChunks: Int,
        val metadata: MediaMetadata,
        val assembledBytes: ByteArray,
    ) : ReassemblyOutcome {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Completed) return false
            if (transferId != other.transferId) return false
            if (metadata != other.metadata) return false
            if (!assembledBytes.contentEquals(other.assembledBytes)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = transferId.hashCode()
            result = 31 * result + metadata.hashCode()
            result = 31 * result + assembledBytes.contentHashCode()
            return result
        }
    }

    data class ChecksumMismatch(
        val transferId: String,
        val expectedSha: String,
        val actualSha: String,
    ) : ReassemblyOutcome

    data class Rejected(val transferId: String, val reason: String) : ReassemblyOutcome
}

/** Active reassembly session for a specific transfer ID. */
class ReassemblySession(
    val transferId: String,
    val mediaType: MediaType,
    val totalChunks: Int,
    val hash8: String,
    val senderId: String?,
    val recipientId: String?,
    val createdAtMillis: Long = nowMillis,
) {
    var lastUpdateMillis: Long = createdAtMillis
        private set

    var metadata: MediaMetadata? = null
        private set

    var status: TransferStatus = TransferStatus.IN_PROGRESS
        private set

    // Maps chunkIndex (1..totalChunks) to raw chunk bytes
    private val chunks = mutableMapOf<Int, ByteArray>()

    val receivedCount: Int
        get() = chunks.size

    val progress: Float
        get() = if (totalChunks > 0) chunks.size.toFloat() / totalChunks else 0f

    val isComplete: Boolean
        get() = metadata != null && chunks.size == totalChunks

    fun missingChunkIndices(): List<Int> = (1..totalChunks).filter { it !in chunks }

    /** Ingests a chunk into this session. */
    @Suppress("ReturnCount")
    internal fun ingest(envelope: MediaEnvelope): ReassemblyOutcome {
        if (status == TransferStatus.CANCELLED || status == TransferStatus.TIMED_OUT) {
            return ReassemblyOutcome.Rejected(transferId, "Session is $status")
        }
        if (envelope.transferId != transferId) {
            return ReassemblyOutcome.Rejected(transferId, "Transfer ID mismatch")
        }
        if (envelope.totalChunks != totalChunks || envelope.hash8 != hash8) {
            return ReassemblyOutcome.Rejected(transferId, "Envelope parameters mismatch")
        }

        lastUpdateMillis = nowMillis

        // Duplicate check
        if (chunks.containsKey(envelope.chunkIndex)) {
            return ReassemblyOutcome.Duplicate(transferId, envelope.chunkIndex)
        }

        // Handle chunk 1: metadata chunk
        if (envelope.chunkIndex == 1) {
            val decodedMeta = MediaMetadata.decodeOrNull(transferId, mediaType, envelope.payload)
            if (decodedMeta == null) {
                status = TransferStatus.CORRUPTED
                return ReassemblyOutcome.Rejected(transferId, "Malformed metadata in chunk 1")
            }
            if (decodedMeta.sha256.take(8) != hash8) {
                status = TransferStatus.CORRUPTED
                return ReassemblyOutcome.Rejected(transferId, "Metadata hash does not match chunk hash8")
            }
            metadata = decodedMeta
        }

        chunks[envelope.chunkIndex] = envelope.payload

        if (isComplete) {
            val assembled = assemblePayload()
            val meta = metadata ?: return ReassemblyOutcome.Rejected(transferId, "Missing metadata")

            // Verify full SHA-256 integrity
            val actualSha = assembled.toByteString().sha256().hex().lowercase()
            if (actualSha != meta.sha256) {
                status = TransferStatus.CORRUPTED
                return ReassemblyOutcome.ChecksumMismatch(transferId, meta.sha256, actualSha)
            }

            status = TransferStatus.COMPLETED
            return ReassemblyOutcome.Completed(transferId, totalChunks, meta, assembled)
        }

        return ReassemblyOutcome.Accepted(
            transferId = transferId,
            chunkIndex = envelope.chunkIndex,
            totalChunks = totalChunks,
            receivedCount = chunks.size,
            isComplete = false,
        )
    }

    private fun assemblePayload(): ByteArray {
        val totalBytes = (2..totalChunks).sumOf { chunks[it]?.size ?: 0 }
        val output = ByteArray(totalBytes)
        var offset = 0
        for (i in 2..totalChunks) {
            val slice = chunks[i] ?: continue
            slice.copyInto(output, destinationOffset = offset)
            offset += slice.size
        }
        return output
    }

    fun markCancelled() {
        status = TransferStatus.CANCELLED
        chunks.clear()
    }

    fun markTimedOut() {
        status = TransferStatus.TIMED_OUT
        chunks.clear()
    }
}

/** Thread-safe reassembly engine managing concurrent incoming transfers. */
class MediaReassemblyEngine(
    private val sessionTimeoutMillis: Long = DEFAULT_SESSION_TIMEOUT_MILLIS,
    private val maxConcurrentSessions: Int = MAX_CONCURRENT_SESSIONS,
) {
    private val mutex = Mutex()
    private val sessions = mutableMapOf<String, ReassemblySession>()

    /** Ingests a media envelope. */
    suspend fun ingestChunk(
        envelope: MediaEnvelope,
        senderId: String? = null,
        recipientId: String? = null,
    ): ReassemblyOutcome = mutex.withLock {
        evictExpiredSessionsLocked()

        var session = sessions[envelope.transferId]
        if (session == null) {
            if (sessions.size >= maxConcurrentSessions) {
                return ReassemblyOutcome.Rejected(envelope.transferId, "Too many concurrent media transfers")
            }
            session =
                ReassemblySession(
                    transferId = envelope.transferId,
                    mediaType = envelope.mediaType,
                    totalChunks = envelope.totalChunks,
                    hash8 = envelope.hash8,
                    senderId = senderId,
                    recipientId = recipientId,
                )
            sessions[envelope.transferId] = session
        }

        val outcome = session.ingest(envelope)
        if (outcome is ReassemblyOutcome.Completed || outcome is ReassemblyOutcome.ChecksumMismatch) {
            // Completed or corrupted session is retained for query, but will expire
        }
        return outcome
    }

    suspend fun getSession(transferId: String): ReassemblySession? = mutex.withLock { sessions[transferId] }

    suspend fun getAllActiveSessions(): List<ReassemblySession> = mutex.withLock {
        evictExpiredSessionsLocked()
        sessions.values.toList()
    }

    suspend fun cancelTransfer(transferId: String): Boolean = mutex.withLock {
        val session = sessions[transferId] ?: return false
        session.markCancelled()
        sessions.remove(transferId)
        true
    }

    suspend fun clear() = mutex.withLock { sessions.clear() }

    private fun evictExpiredSessionsLocked() {
        val now = nowMillis
        val iterator = sessions.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.lastUpdateMillis > sessionTimeoutMillis) {
                entry.value.markTimedOut()
                iterator.remove()
            }
        }
    }

    companion object {
        const val DEFAULT_SESSION_TIMEOUT_MILLIS = 10 * 60 * 1000L // 10 minutes
        const val MAX_CONCURRENT_SESSIONS = 16
    }
}
