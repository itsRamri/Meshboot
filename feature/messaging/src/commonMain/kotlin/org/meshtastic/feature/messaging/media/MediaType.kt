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
 * Supported media transfer types for mesh transmission.
 *
 * @property code Single-character ASCII code used in the wire envelope.
 * @property defaultExtension File extension for reconstructed media.
 * @property defaultMimeType Default MIME type for this media category.
 */
enum class MediaType(
    val code: Char,
    val defaultExtension: String,
    val defaultMimeType: String,
) {
    VOICE('A', "ogg", "audio/ogg"),
    IMAGE('I', "webp", "image/webp"),
    PDF('P', "pdf", "application/pdf");

    companion object {
        fun fromCode(code: Char): MediaType? = entries.firstOrNull { it.code == code }
    }
}
