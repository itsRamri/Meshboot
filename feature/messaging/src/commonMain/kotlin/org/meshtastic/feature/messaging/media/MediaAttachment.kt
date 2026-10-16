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

/** Representing a media attachment associated with a message. */
sealed interface MediaAttachment {
    val mediaType: MediaType
    val filePath: String
    val sizeBytes: Long

    data class Voice(
        override val filePath: String,
        override val sizeBytes: Long,
        val durationSeconds: Int,
    ) : MediaAttachment {
        override val mediaType: MediaType = MediaType.VOICE
    }

    data class Image(
        override val filePath: String,
        override val sizeBytes: Long,
        val width: Int,
        val height: Int,
        val mimeType: String = "image/webp",
    ) : MediaAttachment {
        override val mediaType: MediaType = MediaType.IMAGE
    }

    data class Pdf(
        override val filePath: String,
        override val sizeBytes: Long,
        val fileName: String,
        val pageCount: Int = 1,
    ) : MediaAttachment {
        override val mediaType: MediaType = MediaType.PDF
    }
}

/** State of an active or completed media transfer. */
data class MediaTransferState(
    val transferId: String,
    val mediaType: MediaType,
    val isOutgoing: Boolean,
    val currentChunk: Int,
    val totalChunks: Int,
    val status: TransferStatus,
    val attachment: MediaAttachment? = null,
    val errorMessage: String? = null,
) {
    val progress: Float
        get() = if (totalChunks > 0) currentChunk.toFloat() / totalChunks else 0f

    val isComplete: Boolean
        get() = status == TransferStatus.COMPLETED
}
