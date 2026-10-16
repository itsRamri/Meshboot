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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaEnvelopeTest {

    @Test
    fun `valid envelope encodes and decodes roundtrip identically`() {
        val payload = "Hello Meshtastic Media Transfer!".encodeToByteArray()
        val envelope =
            MediaEnvelope(
                version = 1,
                transferId = "1a2b3c4d",
                mediaType = MediaType.VOICE,
                chunkIndex = 1,
                totalChunks = 5,
                hash8 = "9e107d9d",
                payload = payload,
            )

        val messageText = envelope.toMessageText()
        assertTrue(MediaEnvelope.isMediaEnvelope(messageText))

        val parsed = MediaEnvelope.parseOrNull(messageText)
        assertNotNull(parsed)
        assertEquals(envelope.version, parsed.version)
        assertEquals(envelope.transferId, parsed.transferId)
        assertEquals(envelope.mediaType, parsed.mediaType)
        assertEquals(envelope.chunkIndex, parsed.chunkIndex)
        assertEquals(envelope.totalChunks, parsed.totalChunks)
        assertEquals(envelope.hash8, parsed.hash8)
        assertTrue(envelope.payload.contentEquals(parsed.payload))
    }

    @Test
    fun `regular text messages are not parsed as media envelopes`() {
        assertFalse(MediaEnvelope.isMediaEnvelope("Hello there!"))
        assertFalse(MediaEnvelope.isMediaEnvelope("[Random Bracketed Text]"))
        assertFalse(MediaEnvelope.isMediaEnvelope("[MM:v2:12345678:A:1:1:12345678:AQID]")) // Unsupported version
        assertNull(MediaEnvelope.parseOrNull("Hello there!"))
        assertNull(MediaEnvelope.parseOrNull(""))
    }

    @Test
    fun `malformed envelope components return null`() {
        // Missing colon fields
        assertNull(MediaEnvelope.parseOrNull("[MM:v1:1a2b3c4d:A:1:5]"))
        // Invalid transfer ID length
        assertNull(MediaEnvelope.parseOrNull("[MM:v1:short:A:1:5:12345678:AQID]"))
        // Invalid media type code
        assertNull(MediaEnvelope.parseOrNull("[MM:v1:1a2b3c4d:Z:1:5:12345678:AQID]"))
        // Chunk index greater than total chunks
        assertNull(MediaEnvelope.parseOrNull("[MM:v1:1a2b3c4d:A:6:5:12345678:AQID]"))
        // Chunk index 0
        assertNull(MediaEnvelope.parseOrNull("[MM:v1:1a2b3c4d:A:0:5:12345678:AQID]"))
        // Total chunks 0
        assertNull(MediaEnvelope.parseOrNull("[MM:v1:1a2b3c4d:A:1:0:12345678:AQID]"))
        // Total chunks exceeding maximum limit
        assertNull(MediaEnvelope.parseOrNull("[MM:v1:1a2b3c4d:A:1:9999:12345678:AQID]"))
        // Invalid hex in hash8
        assertNull(MediaEnvelope.parseOrNull("[MM:v1:1a2b3c4d:A:1:5:not_hex!:AQID]"))
        // Invalid base64 payload
        assertNull(MediaEnvelope.parseOrNull("[MM:v1:1a2b3c4d:A:1:5:12345678:!@#$]"))
    }

    @Test
    fun `boundary payload sizes comply with message character limit`() {
        val maxPayload = ByteArray(MediaEnvelope.MAX_RAW_CHUNK_PAYLOAD_BYTES) { 0x42.toByte() }
        val envelope =
            MediaEnvelope(
                version = 1,
                transferId = "abcdef01",
                mediaType = MediaType.IMAGE,
                chunkIndex = 12,
                totalChunks = 120,
                hash8 = "12345678",
                payload = maxPayload,
            )

        val text = envelope.toMessageText()
        // Must stay under 200 bytes text message character limit
        assertTrue(text.length < 200, "Envelope text length (${text.length}) must be < 200 chars")

        val parsed = MediaEnvelope.parseOrNull(text)
        assertNotNull(parsed)
        assertTrue(maxPayload.contentEquals(parsed.payload))
    }
}
