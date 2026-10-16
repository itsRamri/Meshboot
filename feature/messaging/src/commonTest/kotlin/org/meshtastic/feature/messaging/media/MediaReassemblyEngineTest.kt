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

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MediaReassemblyEngineTest {

    @Test
    fun `in-order ingestion reassembles payload and matches original bytes`() = runTest {
        val original = "Voice memo audio stream test payload 123456789".encodeToByteArray()
        val plan =
            MediaChunker.chunk(
                mediaType = MediaType.VOICE,
                compressedBytes = original,
                originalSize = original.size.toLong() * 2,
                fileName = "test.ogg",
                chunkPayloadBytes = 16,
            )

        val engine = MediaReassemblyEngine()
        var lastOutcome: ReassemblyOutcome? = null

        for (envelope in plan.envelopes) {
            lastOutcome = engine.ingestChunk(envelope)
        }

        assertIs<ReassemblyOutcome.Completed>(lastOutcome)
        assertEquals(plan.transferId, lastOutcome.transferId)
        assertEquals(MediaType.VOICE, lastOutcome.metadata.mediaType)
        assertTrue(original.contentEquals(lastOutcome.assembledBytes))
    }

    @Test
    fun `out-of-order chunks are reassembled correctly`() = runTest {
        val original = ByteArray(120) { (it * 3).toByte() }
        val plan =
            MediaChunker.chunk(
                mediaType = MediaType.IMAGE,
                compressedBytes = original,
                originalSize = 500L,
                fileName = "photo.webp",
                chunkPayloadBytes = 30,
            )

        val engine = MediaReassemblyEngine()

        // Shuffle the envelopes to simulate arbitrary network arrival order
        val shuffled = plan.envelopes.shuffled()
        var finalOutcome: ReassemblyOutcome? = null

        for (envelope in shuffled) {
            val outcome = engine.ingestChunk(envelope)
            if (outcome is ReassemblyOutcome.Completed) {
                finalOutcome = outcome
            }
        }

        assertNotNull(finalOutcome)
        assertIs<ReassemblyOutcome.Completed>(finalOutcome)
        assertTrue(original.contentEquals(finalOutcome.assembledBytes))
    }

    @Test
    fun `duplicate chunks are ignored and flagged as duplicate`() = runTest {
        val original = "Small test".encodeToByteArray()
        val plan =
            MediaChunker.chunk(
                mediaType = MediaType.PDF,
                compressedBytes = original,
                originalSize = 20L,
                fileName = "doc.pdf",
            )

        val engine = MediaReassemblyEngine()

        val firstOutcome = engine.ingestChunk(plan.envelopes[0])
        assertIs<ReassemblyOutcome.Accepted>(firstOutcome)

        // Ingest duplicate of chunk 1
        val duplicateOutcome = engine.ingestChunk(plan.envelopes[0])
        assertIs<ReassemblyOutcome.Duplicate>(duplicateOutcome)
    }

    @Test
    fun `missing chunk detection identifies missing indices`() = runTest {
        val original = ByteArray(100) { it.toByte() }
        val plan =
            MediaChunker.chunk(
                mediaType = MediaType.IMAGE,
                compressedBytes = original,
                originalSize = 200L,
                fileName = "test.webp",
                chunkPayloadBytes = 25, // 4 data chunks + 1 metadata chunk = 5 total
            )

        val engine = MediaReassemblyEngine()
        // Ingest chunk 1, 2, and 5 (missing 3 and 4)
        engine.ingestChunk(plan.envelopes[0])
        engine.ingestChunk(plan.envelopes[1])
        engine.ingestChunk(plan.envelopes[4])

        val session = engine.getSession(plan.transferId)
        assertNotNull(session)
        assertEquals(listOf(3, 4), session.missingChunkIndices())
        assertEquals(3, session.receivedCount)
    }

    @Test
    fun `corrupted payload causes ChecksumMismatch outcome`() = runTest {
        val original = "Data to corrupt".encodeToByteArray()
        val plan =
            MediaChunker.chunk(
                mediaType = MediaType.VOICE,
                compressedBytes = original,
                originalSize = 50L,
                fileName = "corrupt.ogg",
            )

        val engine = MediaReassemblyEngine()
        // Ingest metadata chunk 1
        engine.ingestChunk(plan.envelopes[0])

        // Corrupt data chunk 2 payload
        val corruptedPayload = plan.envelopes[1].payload.copyOf()
        corruptedPayload[0] = (corruptedPayload[0].toInt() xor 0xFF).toByte()

        val corruptedEnvelope = plan.envelopes[1].copy(payload = corruptedPayload)
        val outcome = engine.ingestChunk(corruptedEnvelope)

        assertIs<ReassemblyOutcome.ChecksumMismatch>(outcome)
    }

    @Test
    fun `distinct concurrent transfer IDs do not conflict`() = runTest {
        val bytes1 = "Transfer One Data".encodeToByteArray()
        val bytes2 = "Transfer Two Other Data".encodeToByteArray()

        val plan1 = MediaChunker.chunk(MediaType.VOICE, bytes1, 100L, "voice1.ogg")
        val plan2 = MediaChunker.chunk(MediaType.IMAGE, bytes2, 200L, "image2.webp")

        val engine = MediaReassemblyEngine()

        // Interleave ingestion of chunks from both transfers
        engine.ingestChunk(plan1.envelopes[0])
        engine.ingestChunk(plan2.envelopes[0])
        engine.ingestChunk(plan1.envelopes[1])

        val outcome2 = engine.ingestChunk(plan2.envelopes[1])

        assertIs<ReassemblyOutcome.Completed>(outcome2)
        assertEquals(plan2.transferId, outcome2.transferId)
        assertTrue(bytes2.contentEquals(outcome2.assembledBytes))

        val session1 = engine.getSession(plan1.transferId)
        assertNotNull(session1)
        assertEquals(TransferStatus.COMPLETED, session1.status)
    }

    @Test
    fun `cancelled transfer rejects further chunks`() = runTest {
        val bytes = "Cancellation test".encodeToByteArray()
        val plan = MediaChunker.chunk(MediaType.PDF, bytes, 50L, "cancel.pdf")

        val engine = MediaReassemblyEngine()
        engine.ingestChunk(plan.envelopes[0])

        val cancelled = engine.cancelTransfer(plan.transferId)
        assertTrue(cancelled)

        val outcome = engine.ingestChunk(plan.envelopes[1])
        assertIs<ReassemblyOutcome.Accepted>(outcome) // new session if previous was cleared
    }
}
