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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

@Suppress("TooManyFunctions", "TooGenericExceptionCaught", "LargeClass")
@Single(binds = [MediaPlatformService::class])
class AndroidMediaPlatformService(private val context: Context) : MediaPlatformService {
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var recordingStartTimeMs: Long = 0L

    private var mediaPlayer: MediaPlayer? = null
    private var isPlayingAudio: Boolean = false

    // ── Voice Recording ──

    override suspend fun startVoiceRecording(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            cancelVoiceRecording()

            val tempDir = File(context.cacheDir, TEMP_DIR_NAME).apply { mkdirs() }
            val isOpusSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            val ext = if (isOpusSupported) "ogg" else "mp4"
            val outputFile = File(tempDir, "rec_${System.currentTimeMillis()}.$ext")
            currentRecordingFile = outputFile

            @Suppress("DEPRECATION")
            val recorder =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    MediaRecorder()
                }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setOutputFormat(MediaRecorder.OutputFormat.OGG)
                    setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                } else {
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                }
                setAudioChannels(VOICE_CHANNELS)
                setAudioSamplingRate(VOICE_SAMPLING_RATE_HZ)
                setAudioEncodingBitRate(VOICE_ENCODING_BITRATE_BPS)
                setOutputFile(outputFile.absolutePath)
                setMaxDuration(MAX_VOICE_DURATION_MS)
                prepare()
                start()
            }

            mediaRecorder = recorder
            recordingStartTimeMs = System.currentTimeMillis()
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to start voice recording" }
            cleanupCurrentRecordingFile()
            Result.failure(e)
        }
    }

    override suspend fun stopVoiceRecording(): Result<RecordedAudioResult> = withContext(Dispatchers.IO) {
        val recorder = mediaRecorder ?: return@withContext Result.failure(IllegalStateException("No active recording"))
        val file = currentRecordingFile ?: return@withContext Result.failure(IllegalStateException("Missing recording file"))

        try {
            val durationMs = (System.currentTimeMillis() - recordingStartTimeMs).coerceAtLeast(0)
            val durationSeconds = ((durationMs + 500) / 1000).toInt().coerceAtLeast(1)

            try {
                recorder.stop()
            } catch (e: Exception) {
                Logger.w(e) { "Error stopping recorder (likely too short)" }
            }
            recorder.release()
            mediaRecorder = null

            if (!file.exists() || file.length() == 0L) {
                cleanupCurrentRecordingFile()
                return@withContext Result.failure(IllegalStateException("Empty recording file"))
            }

            val isOpus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            val rawBytes = file.readBytes()
            val result =
                RecordedAudioResult(
                    filePath = file.absolutePath,
                    durationSeconds = durationSeconds,
                    sizeBytes = file.length(),
                    rawBytes = rawBytes,
                    codec = if (isOpus) "Opus" else "AAC",
                    mimeType = if (isOpus) "audio/ogg" else "audio/mp4",
                )
            Result.success(result)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to stop recording" }
            cleanupCurrentRecordingFile()
            Result.failure(e)
        }
    }

    override suspend fun cancelVoiceRecording() = withContext(Dispatchers.IO) {
        try {
            mediaRecorder?.run {
                try {
                    stop()
                } catch (_: Exception) {}
                release()
            }
        } catch (e: Exception) {
            Logger.w(e) { "Error releasing media recorder" }
        } finally {
            mediaRecorder = null
            cleanupCurrentRecordingFile()
        }
    }

    override fun isRecording(): Boolean = mediaRecorder != null

    private fun cleanupCurrentRecordingFile() {
        currentRecordingFile?.let {
            if (it.exists()) it.delete()
        }
        currentRecordingFile = null
    }

    // ── Audio Playback ──

    override suspend fun playAudio(filePath: String, onCompletion: () -> Unit): Result<Unit> = withContext(Dispatchers.Main) {
        try {
            stopAudio()
            val file = File(filePath)
            if (!file.exists()) return@withContext Result.failure(IllegalArgumentException("Audio file does not exist"))

            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    isPlayingAudio = false
                    onCompletion()
                }
                start()
            }
            mediaPlayer = player
            isPlayingAudio = true
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to play audio" }
            stopAudio()
            Result.failure(e)
        }
    }

    override fun pauseAudio() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPlayingAudio = false
            }
        }
    }

    override fun stopAudio() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (_: Exception) {}
        }
        mediaPlayer = null
        isPlayingAudio = false
    }

    override fun isAudioPlaying(): Boolean = isPlayingAudio

    // ── Image Compression ──

    override suspend fun compressImage(
        uriString: String,
        maxDimension: Int,
        quality: Int,
    ): Result<CompressedImageResult> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            val originalSize = getFileSizeFromUri(uri)

            // Step 1: Decode bounds only (prevent OOM)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

            val rawWidth = options.outWidth
            val rawHeight = options.outHeight
            if (rawWidth <= 0 || rawHeight <= 0) {
                return@withContext Result.failure(IllegalArgumentException("Unable to decode image dimensions"))
            }

            // Step 2: Compute sample size
            var sampleSize = 1
            while (rawWidth / (sampleSize * 2) >= maxDimension && rawHeight / (sampleSize * 2) >= maxDimension) {
                sampleSize *= 2
            }

            // Step 3: Decode sampled bitmap
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val sampledBitmap =
                openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
                    ?: return@withContext Result.failure(IllegalStateException("Failed to decode sampled image"))

            // Step 4: Correct EXIF rotation
            val rotation = getExifOrientation(uri)
            val orientedBitmap =
                if (rotation != 0) {
                    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                    Bitmap.createBitmap(sampledBitmap, 0, 0, sampledBitmap.width, sampledBitmap.height, matrix, true)
                } else {
                    sampledBitmap
                }

            // Step 5: Scale to exact target bounds preserving aspect ratio
            val currentMax = maxOf(orientedBitmap.width, orientedBitmap.height)
            val scaledBitmap =
                if (currentMax > maxDimension) {
                    val scale = maxDimension.toFloat() / currentMax
                    val targetW = (orientedBitmap.width * scale).toInt().coerceAtLeast(1)
                    val targetH = (orientedBitmap.height * scale).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(orientedBitmap, targetW, targetH, true)
                } else {
                    orientedBitmap
                }

            // Step 6: Compress to WebP (or JPEG fallback)
            val outputStream = ByteArrayOutputStream()
            val format =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }

            scaledBitmap.compress(format, quality.coerceIn(10, 100), outputStream)
            val compressedBytes = outputStream.toByteArray()

            // Save compressed copy in temporary directory
            val tempDir = File(context.cacheDir, TEMP_DIR_NAME).apply { mkdirs() }
            val tempFile = File(tempDir, "img_${System.currentTimeMillis()}.webp")
            FileOutputStream(tempFile).use { it.write(compressedBytes) }

            Result.success(
                CompressedImageResult(
                    filePath = tempFile.absolutePath,
                    originalSize = originalSize,
                    compressedSize = compressedBytes.size.toLong(),
                    width = scaledBitmap.width,
                    height = scaledBitmap.height,
                    mimeType = "image/webp",
                    rawBytes = compressedBytes,
                ),
            )
        } catch (e: Exception) {
            Logger.e(e) { "Failed to compress image" }
            Result.failure(e)
        }
    }

    private fun getExifOrientation(uri: Uri): Int = try {
        openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (_: Exception) {
        0
    }

    // ── PDF Inspection & Optimization ──

    override suspend fun inspectAndOptimizePdf(
        uriString: String,
        allowLossyDownsampling: Boolean,
    ): Result<ProcessedPdfResult> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            val bytes = openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(IllegalArgumentException("Cannot read PDF"))

            if (bytes.size < 4 || bytes[0] != '%'.code.toByte() || bytes[1] != 'P'.code.toByte() ||
                bytes[2] != 'D'.code.toByte() || bytes[3] != 'F'.code.toByte()
            ) {
                return@withContext Result.failure(IllegalArgumentException("Invalid PDF header"))
            }

            if (bytes.size > MediaChunker.MAX_ALLOWED_MEDIA_SIZE_BYTES) {
                return@withContext Result.failure(
                    IllegalArgumentException("PDF size (${bytes.size} B) exceeds mesh limit (${MediaChunker.MAX_ALLOWED_MEDIA_SIZE_BYTES} B)"),
                )
            }

            // Count pages via standard PDF structure search
            var pageCount = 1
            val contentStr = bytes.decodeToString(throwOnInvalidSequence = false)
            val typePageCount = Regex("/Type\\s*/Page[^s]").findAll(contentStr).count()
            if (typePageCount > 0) pageCount = typePageCount

            // Safe lossless approach: keep original bytes intact to guarantee signature and structure preservation
            val tempDir = File(context.cacheDir, TEMP_DIR_NAME).apply { mkdirs() }
            val tempFile = File(tempDir, "doc_${System.currentTimeMillis()}.pdf")
            FileOutputStream(tempFile).use { it.write(bytes) }

            Result.success(
                ProcessedPdfResult(
                    filePath = tempFile.absolutePath,
                    originalSize = bytes.size.toLong(),
                    compressedSize = bytes.size.toLong(),
                    pageCount = pageCount,
                    fileName = "document.pdf",
                    rawBytes = bytes,
                ),
            )
        } catch (e: Exception) {
            Logger.e(e) { "Failed to inspect PDF" }
            Result.failure(e)
        }
    }

    // ── Storage Operations ──

    override suspend fun saveCompletedMedia(
        transferId: String,
        fileName: String,
        bytes: ByteArray,
    ): String = withContext(Dispatchers.IO) {
        val safeName = MediaMetadata.sanitizeFileName(fileName)
        val mediaDir = File(context.filesDir, MEDIA_STORAGE_DIR_NAME).apply { mkdirs() }
        val targetFile = File(mediaDir, "${transferId}_$safeName")
        FileOutputStream(targetFile).use { it.write(bytes) }
        targetFile.absolutePath
    }

    override suspend fun cleanupTemporaryFiles() = withContext(Dispatchers.IO) {
        try {
            val tempDir = File(context.cacheDir, TEMP_DIR_NAME)
            if (tempDir.exists() && tempDir.isDirectory) {
                tempDir.listFiles()?.forEach { file ->
                    if (System.currentTimeMillis() - file.lastModified() > TEMP_FILE_EXPIRY_MS) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Logger.w(e) { "Error cleaning up temporary media files" }
        }
    }

    private fun openInputStream(uri: Uri): InputStream? = if (uri.scheme == "file") {
        File(uri.path ?: "").inputStream()
    } else {
        context.contentResolver.openInputStream(uri)
    }

    private fun getFileSizeFromUri(uri: Uri): Long = try {
        if (uri.scheme == "file") {
            File(uri.path ?: "").length()
        } else {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        }
    } catch (_: Exception) {
        0L
    }

    companion object {
        private const val TEMP_DIR_NAME = "meshtastic_media_temp"
        private const val MEDIA_STORAGE_DIR_NAME = "meshtastic_media"
        private const val MAX_VOICE_DURATION_MS = 10_000 // 10 seconds max
        private const val VOICE_CHANNELS = 1 // Mono
        private const val VOICE_SAMPLING_RATE_HZ = 16_000
        private const val VOICE_ENCODING_BITRATE_BPS = 8_000 // 8 kbps
        private const val TEMP_FILE_EXPIRY_MS = 24 * 60 * 60 * 1000L // 24 hours
    }
}
