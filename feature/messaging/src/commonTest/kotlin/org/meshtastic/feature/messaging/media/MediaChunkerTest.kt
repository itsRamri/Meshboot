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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MediaChunkerTest {

    @Test
    fun `chunking arbitrary payload produces valid sequential envelopes`() {
        val payload = ByteArray(250) { (it % 256).toByte() }
        val chunkPlan =
            MediaChunker.chunk(
                mediaType = MediaType.VOICE,
                compressedBytes = payload,
                originalSize = 1000L,
                fileName = "voice.ogg",
                chunkPayloadBytes = 84,
            )

        // 250 bytes / 84 bytes per chunk = 3 data chunks (84 + 84 + 82)
        // 1 metadata chunk + 3 data chunks = 4 total chunks
        assertEquals(4, chunkPlan.totalChunks)
        assertEquals(4, chunkPlan.envelopes.size)

        // Verify chunk indices and total chunks
        for (i in 0 until 4) {
            val envelope = chunkPlan.envelopes[i]
            assertEquals(i + 1, envelope.chunkIndex)
            assertEquals(4, envelope.totalChunks)
            assertEquals(chunkPlan.transferId, envelope.transferId)
            assertEquals(MediaType.VOICE, envelope.mediaType)
            assertEquals(chunkPlan.metadata.sha256.take(8), envelope.hash8)
        }

        // Chunk 1 payload is metadata
        val metaDecoded =
            MediaMetadata.decodeOrNull(
                chunkPlan.transferId,
                MediaType.VOICE,
                chunkPlan.envelopes[0].payload,
            )
        assertNotNull(metaDecoded)
        assertEquals(1000L, metaDecoded.originalSize)
        assertEquals(250L, metaDecoded.compressedSize)
        assertEquals(payload.toByteString().sha256().hex().lowercase(), metaDecoded.sha256)
    }

    @Test
    fun `small boundary payload creates 2 envelopes`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val chunkPlan =
            MediaChunker.chunk(
                mediaType = MediaType.IMAGE,
                compressedBytes = payload,
                originalSize = 10L,
                fileName = "thumb.webp",
            )

        assertEquals(2, chunkPlan.totalChunks) // 1 metadata + 1 data chunk
        assertEquals(1, chunkPlan.envelopes[0].chunkIndex)
        assertEquals(2, chunkPlan.envelopes[1].chunkIndex)
        assertTrue(payload.contentEquals(chunkPlan.envelopes[1].payload))
    }

    @Test
    fun `oversized payload throws IllegalArgumentException`() {
        val oversized = ByteArray(MediaChunker.MAX_ALLOWED_MEDIA_SIZE_BYTES + 10)
        assertFailsWith<IllegalArgumentException> {
            MediaChunker.chunk(
                mediaType = MediaType.PDF,
                compressedBytes = oversized,
                originalSize = 50000L,
                fileName = "huge.pdf",
            )
        }
    }
}
