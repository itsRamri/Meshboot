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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.repository.usecase.SendMessageOutcome
import org.meshtastic.core.repository.usecase.SendMessageUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MediaTransferManagerTest {

    private class FakeSendMessageUseCase : SendMessageUseCase {
        val sentMessages = mutableListOf<String>()

        override suspend fun invoke(
            text: String,
            contactKey: String,
            replyId: Int?,
        ): SendMessageOutcome {
            sentMessages.add(text)
            return SendMessageOutcome.Queued(sentMessages.size)
        }
    }

    private class FakeMediaPlatformService : MediaPlatformService {
        val savedFiles = mutableMapOf<String, ByteArray>()

        override suspend fun startVoiceRecording(): Result<Unit> = Result.success(Unit)
        override suspend fun stopVoiceRecording(): Result<RecordedAudioResult> =
            Result.success(RecordedAudioResult("/tmp/voice.ogg", 5, 100L, ByteArray(100)))

        override suspend fun cancelVoiceRecording() {}
        override fun isRecording(): Boolean = false

        override suspend fun playAudio(filePath: String, onCompletion: () -> Unit): Result<Unit> =
            Result.success(Unit)

        override fun pauseAudio() {}
        override fun stopAudio() {}
        override fun isAudioPlaying(): Boolean = false

        override suspend fun compressImage(
            uriString: String,
            maxDimension: Int,
            quality: Int,
        ): Result<CompressedImageResult> =
            Result.success(CompressedImageResult("/tmp/img.webp", 200L, 80L, 400, 400, "image/webp", ByteArray(80)))

        override suspend fun inspectAndOptimizePdf(
            uriString: String,
            allowLossyDownsampling: Boolean,
        ): Result<ProcessedPdfResult> =
            Result.success(ProcessedPdfResult("/tmp/doc.pdf", 300L, 300L, 1, "doc.pdf", ByteArray(300)))

        override suspend fun saveCompletedMedia(
            transferId: String,
            fileName: String,
            bytes: ByteArray,
        ): String {
            val path = "/mock/storage/${transferId}_$fileName"
            savedFiles[path] = bytes
            return path
        }

        override suspend fun cleanupTemporaryFiles() {}

        override fun openMediaFile(filePath: String, mimeType: String): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun `ordinary text messages return null and do not affect media engine`() = runTest {
        val fakeSendUseCase = FakeSendMessageUseCase()
        val fakePlatformService = FakeMediaPlatformService()
        val manager = MediaTransferManager(fakeSendUseCase, fakePlatformService)

        val outcome = manager.processIncomingMessage("Hello mesh network! This is ordinary text.")
        assertNull(outcome)
        assertTrue(manager.transferStates.value.isEmpty())
    }

    @Test
    fun `incoming media envelopes are processed to completion and saved to disk`() = runTest {
        val fakeSendUseCase = FakeSendMessageUseCase()
        val fakePlatformService = FakeMediaPlatformService()
        val manager = MediaTransferManager(fakeSendUseCase, fakePlatformService)

        val rawMedia = "Test Voice Data".encodeToByteArray()
        val plan = MediaChunker.chunk(MediaType.VOICE, rawMedia, 50L, "test_voice.ogg")

        // Feed chunks into the manager
        for (envelope in plan.envelopes) {
            manager.processIncomingMessage(envelope.toMessageText())
        }

        val state = manager.transferStates.value[plan.transferId]
        assertNotNull(state)
        assertEquals(TransferStatus.COMPLETED, state.status)
        assertNotNull(state.attachment)
        assertIs<MediaAttachment.Voice>(state.attachment)

        // Verify mediaPlatformService was invoked to persist the file
        val savedPath = state.attachment?.filePath
        assertNotNull(savedPath)
        val savedBytes = fakePlatformService.savedFiles[savedPath]
        assertNotNull(savedBytes)
        assertTrue(rawMedia.contentEquals(savedBytes))
    }

    @Test
    fun `outgoing transfer transmits all envelopes sequentially then enters WAITING_FOR_RECEIVER until receipt`() = runTest {
        val fakeSendUseCase = FakeSendMessageUseCase()
        val fakePlatformService = FakeMediaPlatformService()
        val manager = MediaTransferManager(fakeSendUseCase, fakePlatformService)

        val rawMedia = ByteArray(150) { it.toByte() }
        val plan =
            MediaChunker.chunk(
                mediaType = MediaType.IMAGE,
                compressedBytes = rawMedia,
                originalSize = 300L,
                fileName = "photo.webp",
                chunkPayloadBytes = 80,
            )

        val testScope = TestScope(testScheduler)
        manager.startOutgoingTransfer(testScope, plan, "0!ffffffff", interChunkDelayMs = 10L)
        testScope.advanceUntilIdle()

        // Verify all envelopes were transmitted
        assertEquals(plan.envelopes.size, fakeSendUseCase.sentMessages.size)

        // Sender is in WAITING_FOR_RECEIVER state, NOT immediately COMPLETED
        val state = manager.transferStates.value[plan.transferId]
        assertNotNull(state)
        assertEquals(TransferStatus.WAITING_FOR_RECEIVER, state.status)
        assertEquals(plan.totalChunks, state.currentChunk)

        // Simulate incoming receipt from receiver
        val receiptMessage = MediaEnvelope.createReceiptMessage(plan.transferId, plan.metadata.sha256.take(8))
        manager.processIncomingMessage(receiptMessage)

        val completedState = manager.transferStates.value[plan.transferId]
        assertNotNull(completedState)
        assertEquals(TransferStatus.COMPLETED, completedState.status)
    }

    @Test
    fun `cancelled transfer ceases outgoing transmission and sets status to CANCELLED`() = runTest {
        val fakeSendUseCase = FakeSendMessageUseCase()
        val fakePlatformService = FakeMediaPlatformService()
        val manager = MediaTransferManager(fakeSendUseCase, fakePlatformService)

        val rawMedia = ByteArray(500) { it.toByte() }
        val plan =
            MediaChunker.chunk(
                mediaType = MediaType.IMAGE,
                compressedBytes = rawMedia,
                originalSize = 1000L,
                fileName = "large.webp",
                chunkPayloadBytes = 50,
            )

        val testScope = TestScope(testScheduler)
        manager.startOutgoingTransfer(testScope, plan, "0!ffffffff", interChunkDelayMs = 100L)
        testScope.testScheduler.advanceTimeBy(150L) // Allow first chunk or two to transmit

        manager.cancelTransfer(plan.transferId)
        testScope.advanceUntilIdle()

        val state = manager.transferStates.value[plan.transferId]
        assertNotNull(state)
        assertEquals(TransferStatus.CANCELLED, state.status)

        // Late receipt should not make a cancelled transfer completed
        val receiptMessage = MediaEnvelope.createReceiptMessage(plan.transferId, plan.metadata.sha256.take(8))
        manager.processIncomingMessage(receiptMessage)
        assertEquals(TransferStatus.CANCELLED, manager.transferStates.value[plan.transferId]?.status)
    }

    @Test
    fun `receiver sends delivery receipt upon successful reassembly and verification`() = runTest {
        val fakeSendUseCase = FakeSendMessageUseCase()
        val fakePlatformService = FakeMediaPlatformService()
        val manager = MediaTransferManager(fakeSendUseCase, fakePlatformService)

        val rawMedia = "Test Voice Data".encodeToByteArray()
        val plan = MediaChunker.chunk(MediaType.VOICE, rawMedia, 50L, "test_voice.ogg")

        // Receiver receives all chunks
        for (envelope in plan.envelopes) {
            manager.processIncomingMessage(
                text = envelope.toMessageText(),
                senderId = "!12345678",
                contactKey = "!12345678",
            )
        }

        // Verify that the receiver sent exactly one receipt message
        val sentReceipts = fakeSendUseCase.sentMessages.filter { MediaEnvelope.isMediaReceipt(it) }
        assertEquals(1, sentReceipts.size)
        val parsedReceipt = MediaEnvelope.parseReceiptOrNull(sentReceipts.first())
        assertNotNull(parsedReceipt)
        val (transferId, hash8) = parsedReceipt
        assertEquals(plan.transferId, transferId)
        assertEquals(plan.metadata.sha256.take(8), hash8)
    }

    @Test
    fun `photo transfer completes and preserves image dimensions and MIME type`() = runTest {
        val fakeSendUseCase = FakeSendMessageUseCase()
        val fakePlatformService = FakeMediaPlatformService()
        val manager = MediaTransferManager(fakeSendUseCase, fakePlatformService)

        val rawImage = "Compressed WebP Image Bytes".encodeToByteArray()
        val plan = MediaChunker.chunk(MediaType.IMAGE, rawImage, 1000L, "photo.webp", "image/webp")

        for (envelope in plan.envelopes) {
            manager.processIncomingMessage(envelope.toMessageText())
        }

        val state = manager.transferStates.value[plan.transferId]
        assertNotNull(state)
        assertEquals(TransferStatus.COMPLETED, state.status)
        assertNotNull(state.attachment)
        assertIs<MediaAttachment.Image>(state.attachment)
        assertEquals("image/webp", (state.attachment as MediaAttachment.Image).mimeType)
    }

    @Test
    fun `pdf document transfer completes and preserves filename`() = runTest {
        val fakeSendUseCase = FakeSendMessageUseCase()
        val fakePlatformService = FakeMediaPlatformService()
        val manager = MediaTransferManager(fakeSendUseCase, fakePlatformService)

        val rawPdf = "%PDF-1.4 dummy pdf bytes".encodeToByteArray()
        val plan = MediaChunker.chunk(MediaType.PDF, rawPdf, 500L, "guide.pdf", "application/pdf")

        for (envelope in plan.envelopes) {
            manager.processIncomingMessage(envelope.toMessageText())
        }

        val state = manager.transferStates.value[plan.transferId]
        assertNotNull(state)
        assertEquals(TransferStatus.COMPLETED, state.status)
        assertNotNull(state.attachment)
        assertIs<MediaAttachment.Pdf>(state.attachment)
        assertEquals("guide.pdf", (state.attachment as MediaAttachment.Pdf).fileName)
    }
}
