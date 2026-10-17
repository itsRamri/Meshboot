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

data class RecordedAudioResult(
    val filePath: String,
    val durationSeconds: Int,
    val sizeBytes: Long,
    val rawBytes: ByteArray,
    val codec: String = "Opus",
    val mimeType: String = "audio/ogg",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RecordedAudioResult) return false
        if (filePath != other.filePath) return false
        if (durationSeconds != other.durationSeconds) return false
        if (sizeBytes != other.sizeBytes) return false
        if (!rawBytes.contentEquals(other.rawBytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = filePath.hashCode()
        result = 31 * result + durationSeconds
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + rawBytes.contentHashCode()
        return result
    }
}

data class CompressedImageResult(
    val filePath: String,
    val originalSize: Long,
    val compressedSize: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val rawBytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CompressedImageResult) return false
        if (filePath != other.filePath) return false
        if (originalSize != other.originalSize) return false
        if (compressedSize != other.compressedSize) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (mimeType != other.mimeType) return false
        if (!rawBytes.contentEquals(other.rawBytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = filePath.hashCode()
        result = 31 * result + originalSize.hashCode()
        result = 31 * result + compressedSize.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + rawBytes.contentHashCode()
        return result
    }
}

data class ProcessedPdfResult(
    val filePath: String,
    val originalSize: Long,
    val compressedSize: Long,
    val pageCount: Int,
    val fileName: String,
    val rawBytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProcessedPdfResult) return false
        if (filePath != other.filePath) return false
        if (originalSize != other.originalSize) return false
        if (compressedSize != other.compressedSize) return false
        if (pageCount != other.pageCount) return false
        if (fileName != other.fileName) return false
        if (!rawBytes.contentEquals(other.rawBytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = filePath.hashCode()
        result = 31 * result + originalSize.hashCode()
        result = 31 * result + compressedSize.hashCode()
        result = 31 * result + pageCount
        result = 31 * result + fileName.hashCode()
        result = 31 * result + rawBytes.contentHashCode()
        return result
    }
}

/** Platform interface providing audio, image, PDF, and local media storage services. */
interface MediaPlatformService {
    // ── Voice ──
    suspend fun startVoiceRecording(): Result<Unit>
    suspend fun stopVoiceRecording(): Result<RecordedAudioResult>
    suspend fun cancelVoiceRecording()
    fun isRecording(): Boolean

    suspend fun playAudio(filePath: String, onCompletion: () -> Unit = {}): Result<Unit>
    fun pauseAudio()
    fun stopAudio()
    fun isAudioPlaying(): Boolean

    // ── Image ──
    suspend fun compressImage(
        uriString: String,
        maxDimension: Int = 480,
        quality: Int = 45,
    ): Result<CompressedImageResult>

    // ── PDF ──
    suspend fun inspectAndOptimizePdf(
        uriString: String,
        allowLossyDownsampling: Boolean = false,
    ): Result<ProcessedPdfResult>

    // ── Storage ──
    suspend fun saveCompletedMedia(transferId: String, fileName: String, bytes: ByteArray): String
    suspend fun cleanupTemporaryFiles()
    fun openMediaFile(filePath: String, mimeType: String): Result<Unit>
}
