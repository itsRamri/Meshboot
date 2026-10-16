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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.meshtastic.core.repository.usecase.SendMessageUseCase

/**
 * Manager that coordinates media chunk transmission, pacing, and reassembly.
 *
 * Uses the existing supported text messaging transport to reliably send and receive media chunks
 * without requiring any firmware changes.
 */
@Suppress("TooManyFunctions")
@Single
class MediaTransferManager(
    private val sendMessageUseCase: SendMessageUseCase,
    val mediaPlatformService: MediaPlatformService,
    val reassemblyEngine: MediaReassemblyEngine = MediaReassemblyEngine(),
) {
    private val _transferStates = MutableStateFlow<Map<String, MediaTransferState>>(emptyMap())
    val transferStates: StateFlow<Map<String, MediaTransferState>> = _transferStates.asStateFlow()

    private val outgoingJobs = mutableMapOf<String, Job>()
    private val completedAttachments = mutableMapOf<String, MediaAttachment>()

    /** Ingests an incoming text message, parsing it as a media envelope if applicable. */
    suspend fun processIncomingMessage(
        text: String,
        senderId: String? = null,
        recipientId: String? = null,
    ): ReassemblyOutcome? {
        val envelope = MediaEnvelope.parseOrNull(text) ?: return null
        val outcome = reassemblyEngine.ingestChunk(envelope, senderId, recipientId)

        when (outcome) {
            is ReassemblyOutcome.Accepted -> {
                updateTransferState(
                    MediaTransferState(
                        transferId = outcome.transferId,
                        mediaType = envelope.mediaType,
                        isOutgoing = false,
                        currentChunk = outcome.receivedCount,
                        totalChunks = outcome.totalChunks,
                        status = TransferStatus.IN_PROGRESS,
                    ),
                )
            }

            is ReassemblyOutcome.Completed -> {
                try {
                    val savedPath =
                        mediaPlatformService.saveCompletedMedia(
                            transferId = outcome.transferId,
                            fileName = outcome.metadata.fileName,
                            bytes = outcome.assembledBytes,
                        )

                    val attachment: MediaAttachment =
                        when (outcome.metadata.mediaType) {
                            MediaType.VOICE ->
                                MediaAttachment.Voice(
                                    filePath = savedPath,
                                    sizeBytes = outcome.assembledBytes.size.toLong(),
                                    durationSeconds = (outcome.assembledBytes.size / 1000).coerceIn(1, 10),
                                )

                            MediaType.IMAGE ->
                                MediaAttachment.Image(
                                    filePath = savedPath,
                                    sizeBytes = outcome.assembledBytes.size.toLong(),
                                    width = 480,
                                    height = 480,
                                    mimeType = outcome.metadata.mimeType,
                                )

                            MediaType.PDF ->
                                MediaAttachment.Pdf(
                                    filePath = savedPath,
                                    sizeBytes = outcome.assembledBytes.size.toLong(),
                                    fileName = outcome.metadata.fileName,
                                    pageCount = 1,
                                )
                        }

                    completedAttachments[outcome.transferId] = attachment

                    updateTransferState(
                        MediaTransferState(
                            transferId = outcome.transferId,
                            mediaType = outcome.metadata.mediaType,
                            isOutgoing = false,
                            currentChunk = outcome.totalChunks,
                            totalChunks = outcome.totalChunks,
                            status = TransferStatus.COMPLETED,
                            attachment = attachment,
                        ),
                    )
                } catch (e: Exception) {
                    Logger.e(e) { "Failed to save completed media" }
                }
            }

            is ReassemblyOutcome.ChecksumMismatch -> {
                updateTransferState(
                    MediaTransferState(
                        transferId = outcome.transferId,
                        mediaType = envelope.mediaType,
                        isOutgoing = false,
                        currentChunk = 0,
                        totalChunks = envelope.totalChunks,
                        status = TransferStatus.CORRUPTED,
                        errorMessage = "Checksum mismatch: expected ${outcome.expectedSha.take(8)}, got ${outcome.actualSha.take(8)}",
                    ),
                )
            }

            is ReassemblyOutcome.Rejected -> {
                Logger.w { "Media chunk rejected: ${outcome.reason}" }
            }

            is ReassemblyOutcome.Duplicate -> {}
        }

        return outcome
    }

    /**
     * Starts transferring a prepared [MediaChunkPlan] over the mesh to [contactKey].
     *
     * @param scope CoroutineScope to launch the transmission loop in.
     * @param plan The chunk plan to transmit.
     * @param contactKey Destination contact key or channel index.
     * @param interChunkDelayMs Delay between chunks to prevent flooding the mesh (default 2000ms).
     */
    fun startOutgoingTransfer(
        scope: CoroutineScope,
        plan: MediaChunkPlan,
        contactKey: String,
        interChunkDelayMs: Long = DEFAULT_INTER_CHUNK_DELAY_MS,
    ): Job {
        val transferId = plan.transferId
        updateTransferState(
            MediaTransferState(
                transferId = transferId,
                mediaType = plan.metadata.mediaType,
                isOutgoing = true,
                currentChunk = 0,
                totalChunks = plan.totalChunks,
                status = TransferStatus.IN_PROGRESS,
            ),
        )

        val job =
            scope.launch {
                try {
                    for ((index, envelope) in plan.envelopes.withIndex()) {
                        val messageText = envelope.toMessageText()
                        sendMessageUseCase.invoke(text = messageText, contactKey = contactKey)

                        val currentChunkNum = index + 1
                        updateTransferState(
                            MediaTransferState(
                                transferId = transferId,
                                mediaType = plan.metadata.mediaType,
                                isOutgoing = true,
                                currentChunk = currentChunkNum,
                                totalChunks = plan.totalChunks,
                                status =
                                if (currentChunkNum == plan.totalChunks) {
                                    TransferStatus.COMPLETED
                                } else {
                                    TransferStatus.IN_PROGRESS
                                },
                            ),
                        )

                        if (index < plan.envelopes.size - 1) {
                            delay(interChunkDelayMs)
                        }
                    }
                } catch (e: CancellationException) {
                    updateTransferState(
                        MediaTransferState(
                            transferId = transferId,
                            mediaType = plan.metadata.mediaType,
                            isOutgoing = true,
                            currentChunk = 0,
                            totalChunks = plan.totalChunks,
                            status = TransferStatus.CANCELLED,
                        ),
                    )
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    Logger.e(e) { "Outgoing media transfer failed: $transferId" }
                    updateTransferState(
                        MediaTransferState(
                            transferId = transferId,
                            mediaType = plan.metadata.mediaType,
                            isOutgoing = true,
                            currentChunk = 0,
                            totalChunks = plan.totalChunks,
                            status = TransferStatus.CORRUPTED,
                            errorMessage = e.message ?: "Transfer error",
                        ),
                    )
                } finally {
                    outgoingJobs.remove(transferId)
                }
            }

        outgoingJobs[transferId] = job
        return job
    }

    fun cancelTransfer(transferId: String) {
        outgoingJobs[transferId]?.cancel()
        outgoingJobs.remove(transferId)
        updateTransferState(
            _transferStates.value[transferId]?.copy(status = TransferStatus.CANCELLED)
                ?: MediaTransferState(
                    transferId = transferId,
                    mediaType = MediaType.VOICE,
                    isOutgoing = true,
                    currentChunk = 0,
                    totalChunks = 1,
                    status = TransferStatus.CANCELLED,
                ),
        )
    }

    fun getAttachment(transferId: String): MediaAttachment? = completedAttachments[transferId]

    fun registerLocalAttachment(transferId: String, attachment: MediaAttachment) {
        completedAttachments[transferId] = attachment
    }

    private fun updateTransferState(state: MediaTransferState) {
        _transferStates.update { current -> current + (state.transferId to state) }
    }

    companion object {
        const val DEFAULT_INTER_CHUNK_DELAY_MS = 2500L // 2.5s pacing to satisfy LoRa airtime etiquette
    }
}
