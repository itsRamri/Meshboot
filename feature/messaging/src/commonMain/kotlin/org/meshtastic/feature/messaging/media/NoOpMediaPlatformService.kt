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

/** No-op fallback implementation of [MediaPlatformService] for environments without native media capabilities. */
class NoOpMediaPlatformService : MediaPlatformService {
    override suspend fun startVoiceRecording(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Voice recording not supported on this platform"))

    override suspend fun stopVoiceRecording(): Result<RecordedAudioResult> =
        Result.failure(UnsupportedOperationException("Voice recording not supported on this platform"))

    override suspend fun cancelVoiceRecording() {}

    override fun isRecording(): Boolean = false

    override suspend fun playAudio(filePath: String, onCompletion: () -> Unit): Result<Unit> =
        Result.failure(UnsupportedOperationException("Audio playback not supported on this platform"))

    override fun pauseAudio() {}

    override fun stopAudio() {}

    override fun isAudioPlaying(): Boolean = false

    override suspend fun compressImage(
        uriString: String,
        maxDimension: Int,
        quality: Int,
    ): Result<CompressedImageResult> =
        Result.failure(UnsupportedOperationException("Image compression not supported on this platform"))

    override suspend fun inspectAndOptimizePdf(
        uriString: String,
        allowLossyDownsampling: Boolean,
    ): Result<ProcessedPdfResult> =
        Result.failure(UnsupportedOperationException("PDF processing not supported on this platform"))

    override suspend fun saveCompletedMedia(
        transferId: String,
        fileName: String,
        bytes: ByteArray,
    ): String = ""

    override suspend fun cleanupTemporaryFiles() {}
}
