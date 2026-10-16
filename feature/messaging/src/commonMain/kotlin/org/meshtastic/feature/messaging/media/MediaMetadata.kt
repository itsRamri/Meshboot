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

/**
 * Metadata describing a media file being transferred over the mesh.
 *
 * @property version Metadata schema version.
 * @property transferId Unique transfer identifier (8-character hex).
 * @property mediaType Type of media being transferred.
 * @property originalSize Size in bytes before compression.
 * @property compressedSize Size in bytes of the compressed payload.
 * @property sha256 Full SHA-256 hex digest (64 characters) of the compressed payload.
 * @property fileName Sanitized filename.
 * @property mimeType MIME type of the compressed media.
 */
data class MediaMetadata(
    val version: Int = CURRENT_VERSION,
    val transferId: String,
    val mediaType: MediaType,
    val originalSize: Long,
    val compressedSize: Long,
    val sha256: String,
    val fileName: String,
    val mimeType: String,
) {
    init {
        require(transferId.length == 8) { "Transfer ID must be 8 hex characters" }
        require(sha256.length == 64) { "SHA-256 must be 64 hex characters" }
        require(originalSize >= 0) { "Original size must be non-negative" }
        require(compressedSize > 0) { "Compressed size must be positive" }
    }

    /**
     * Serializes metadata into a compact string suitable for base64 encapsulation in chunk 1:
     * `version|origSize|compSize|sha256|mimeType|sanitizedName`
     */
    fun encode(): ByteArray {
        val safeName = sanitizeFileName(fileName)
        val payload = "$version|$originalSize|$compressedSize|$sha256|$mimeType|$safeName"
        return payload.encodeToByteArray()
    }

    companion object {
        const val CURRENT_VERSION = 1
        private const val MAX_FILENAME_LENGTH = 64
        private val UNSAFE_FILENAME_REGEX = Regex("[^a-zA-Z0-9._-]")

        /** Sanitizes file names to prevent path traversal or unsafe characters. */
        fun sanitizeFileName(name: String): String {
            val baseName = name.substringAfterLast('/').substringAfterLast('\\')
            val cleaned = baseName.replace(UNSAFE_FILENAME_REGEX, "_").take(MAX_FILENAME_LENGTH)
            return cleaned.ifEmpty { "attachment.bin" }
        }

        /** Decodes metadata from compact byte representation. Returns null if invalid or untrusted. */
        @Suppress("ReturnCount")
        fun decodeOrNull(
            transferId: String,
            mediaType: MediaType,
            bytes: ByteArray,
            maxAllowedSize: Long = 64 * 1024,
        ): MediaMetadata? {
            try {
                val str = bytes.decodeToString()
                val parts = str.split('|')
                if (parts.size < 6) return null

                val version = parts[0].toIntOrNull() ?: return null
                if (version != CURRENT_VERSION) return null

                val origSize = parts[1].toLongOrNull() ?: return null
                val compSize = parts[2].toLongOrNull() ?: return null
                val sha = parts[3].trim().lowercase()
                val mime = parts[4].trim()
                val rawName = parts.subList(5, parts.size).joinToString("|")
                val safeName = sanitizeFileName(rawName)

                // Security bounds checks: never allocate or allow oversized transfers
                if (origSize < 0 || compSize <= 0 || compSize > maxAllowedSize) return null
                if (sha.length != 64 || !sha.all { it in "0123456789abcdef" }) return null

                return MediaMetadata(
                    version = version,
                    transferId = transferId,
                    mediaType = mediaType,
                    originalSize = origSize,
                    compressedSize = compSize,
                    sha256 = sha,
                    fileName = safeName,
                    mimeType = mime,
                )
            } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                return null
            }
        }
    }
}
