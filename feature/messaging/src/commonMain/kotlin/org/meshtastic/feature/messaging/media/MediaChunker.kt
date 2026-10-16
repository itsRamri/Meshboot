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

import okio.ByteString.Companion.toByteString
import kotlin.random.Random

/** Plan containing all chunks prepared for transmission. */
data class MediaChunkPlan(
    val metadata: MediaMetadata,
    val envelopes: List<MediaEnvelope>,
) {
    val totalChunks: Int
        get() = envelopes.size

    val transferId: String
        get() = metadata.transferId
}

/** Utility to split media payloads into transmission envelopes. */
object MediaChunker {
    const val DEFAULT_DATA_CHUNK_PAYLOAD_BYTES = 84
    const val MAX_ALLOWED_MEDIA_SIZE_BYTES = 32 * 1024 // 32 KB safety threshold for LoRa

    /** Generates an 8-character random hex string for transfer ID. */
    fun generateTransferId(): String {
        val bytes = Random.nextBytes(4)
        return bytes.toByteString().hex().lowercase()
    }

    /**
     * Splits compressed media bytes into a [MediaChunkPlan].
     *
     * @param mediaType Category of the media.
     * @param compressedBytes The compressed binary data.
     * @param originalSize Uncompressed size in bytes.
     * @param fileName Descriptive name for the file.
     * @param mimeType MIME type of the compressed stream.
     * @param transferId Optional 8-hex transfer ID (randomly generated if omitted).
     * @param chunkPayloadBytes Raw byte capacity per data chunk.
     */
    fun chunk(
        mediaType: MediaType,
        compressedBytes: ByteArray,
        originalSize: Long,
        fileName: String,
        mimeType: String = mediaType.defaultMimeType,
        transferId: String = generateTransferId(),
        chunkPayloadBytes: Int = DEFAULT_DATA_CHUNK_PAYLOAD_BYTES,
    ): MediaChunkPlan {
        require(compressedBytes.size <= MAX_ALLOWED_MEDIA_SIZE_BYTES) {
            "Media size (${compressedBytes.size} bytes) exceeds limit ($MAX_ALLOWED_MEDIA_SIZE_BYTES bytes)"
        }
        require(transferId.length == 8) { "Transfer ID must be 8 hex characters" }

        val fullSha256 = compressedBytes.toByteString().sha256().hex().lowercase()
        val hash8 = fullSha256.take(8)

        val metadata =
            MediaMetadata(
                transferId = transferId,
                mediaType = mediaType,
                originalSize = originalSize,
                compressedSize = compressedBytes.size.toLong(),
                sha256 = fullSha256,
                fileName = fileName,
                mimeType = mimeType,
            )

        val metadataBytes = metadata.encode()
        require(metadataBytes.size <= MediaEnvelope.MAX_RAW_CHUNK_PAYLOAD_BYTES) {
            "Encoded metadata size ${metadataBytes.size} exceeds maximum chunk payload limit"
        }

        // Slice compressed payload into data chunks
        val dataChunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < compressedBytes.size) {
            val length = minOf(chunkPayloadBytes, compressedBytes.size - offset)
            val chunk = compressedBytes.copyOfRange(offset, offset + length)
            dataChunks.add(chunk)
            offset += length
        }

        val totalChunks = 1 + dataChunks.size
        require(totalChunks <= MediaEnvelope.MAX_CHUNKS) {
            "Resulting total chunks ($totalChunks) exceeds maximum limit (${MediaEnvelope.MAX_CHUNKS})"
        }

        val envelopes = mutableListOf<MediaEnvelope>()

        // Chunk 1: Metadata chunk
        envelopes.add(
            MediaEnvelope(
                version = MediaEnvelope.PROTOCOL_VERSION,
                transferId = transferId,
                mediaType = mediaType,
                chunkIndex = 1,
                totalChunks = totalChunks,
                hash8 = hash8,
                payload = metadataBytes,
            ),
        )

        // Chunks 2..N: Data payload chunks
        for (i in dataChunks.indices) {
            val chunkIndex = i + 2
            envelopes.add(
                MediaEnvelope(
                    version = MediaEnvelope.PROTOCOL_VERSION,
                    transferId = transferId,
                    mediaType = mediaType,
                    chunkIndex = chunkIndex,
                    totalChunks = totalChunks,
                    hash8 = hash8,
                    payload = dataChunks[i],
                ),
            )
        }

        return MediaChunkPlan(metadata = metadata, envelopes = envelopes)
    }
}
