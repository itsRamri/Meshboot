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
package org.meshtastic.feature.messaging.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.media_compressed_image
import org.meshtastic.core.resources.media_compressed_size
import org.meshtastic.core.resources.media_compression_ratio
import org.meshtastic.core.resources.media_est_transfer_time
import org.meshtastic.core.resources.media_lossy_downsampling
import org.meshtastic.core.resources.media_original_size
import org.meshtastic.core.resources.media_pdf_document
import org.meshtastic.core.resources.media_preview
import org.meshtastic.core.resources.send
import org.meshtastic.core.ui.icon.Camera
import org.meshtastic.core.ui.icon.FolderOpen
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Send
import org.meshtastic.feature.messaging.media.CompressedImageResult
import org.meshtastic.feature.messaging.media.MediaType
import org.meshtastic.feature.messaging.media.ProcessedPdfResult

@Suppress("LongMethod")
@Composable
fun MediaPreviewDialog(
    mediaType: MediaType,
    imageResult: CompressedImageResult? = null,
    pdfResult: ProcessedPdfResult? = null,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onConfirmSend: () -> Unit,
    onToggleLossyPdf: ((Boolean) -> Unit)? = null,
) {
    var allowLossy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.media_preview),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                if (isLoading) {
                    BoxLoading()
                } else if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else when (mediaType) {
                    MediaType.IMAGE -> {
                        val img = imageResult
                        if (img != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            imageVector = MeshtasticIcons.Camera,
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = stringResource(Res.string.media_compressed_image),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "${img.width} × ${img.height} px (${img.mimeType})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    val origKb = NumberFormatter.format(img.originalSize.toFloat() / 1024, 1)
                                    Text(
                                        text = stringResource(Res.string.media_original_size, "$origKb KB"),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    val compKb = NumberFormatter.format(img.compressedSize.toFloat() / 1024, 1)
                                    Text(
                                        text = stringResource(Res.string.media_compressed_size, "$compKb KB"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (img.originalSize > 0) {
                                        val ratio = ((1.0 - (img.compressedSize.toDouble() / img.originalSize)) * 100).coerceAtLeast(0.0)
                                        val ratioStr = "${NumberFormatter.format(ratio.toFloat(), 1)}% reduction"
                                        Text(
                                            text = stringResource(Res.string.media_compression_ratio, ratioStr),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    val estSec = (img.compressedSize / 80) * 2.5f
                                    Text(
                                        text = stringResource(Res.string.media_est_transfer_time, "~${NumberFormatter.format(estSec, 0)}s"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    MediaType.PDF -> {
                        val pdf = pdfResult
                        if (pdf != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            imageVector = MeshtasticIcons.FolderOpen,
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = stringResource(Res.string.media_pdf_document),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "${pdf.fileName} (${pdf.pageCount} pages)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    val compKb = NumberFormatter.format(pdf.compressedSize.toFloat() / 1024, 1)
                                    Text(
                                        text = stringResource(Res.string.media_compressed_size, "$compKb KB"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    val estSec = (pdf.compressedSize / 80) * 2.5f
                                    Text(
                                        text = stringResource(Res.string.media_est_transfer_time, "~${NumberFormatter.format(estSec, 0)}s"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Checkbox(
                                    checked = allowLossy,
                                    onCheckedChange = { checked ->
                                        allowLossy = checked
                                        onToggleLossyPdf?.invoke(checked)
                                    },
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(Res.string.media_lossy_downsampling),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                    MediaType.VOICE -> {}
                }
            }
        },
        confirmButton = {
            if (!isLoading && errorMessage == null) {
                Button(onClick = onConfirmSend) {
                    Icon(MeshtasticIcons.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(Res.string.send))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun BoxLoading() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = "Processing...", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun MediaPreviewDialog(
    attachment: org.meshtastic.feature.messaging.media.MediaAttachment,
    onDismiss: () -> Unit,
    onSend: () -> Unit,
    onToggleLossyPdf: ((Boolean) -> Unit)? = null,
) {
    when (attachment) {
        is org.meshtastic.feature.messaging.media.MediaAttachment.Image -> {
            val img = CompressedImageResult(
                filePath = "",
                originalSize = attachment.originalSize,
                compressedSize = attachment.compressedSize,
                width = attachment.width,
                height = attachment.height,
                mimeType = "image/webp",
                rawBytes = attachment.rawBytes,
            )
            MediaPreviewDialog(
                mediaType = MediaType.IMAGE,
                imageResult = img,
                onDismiss = onDismiss,
                onConfirmSend = onSend,
            )
        }
        is org.meshtastic.feature.messaging.media.MediaAttachment.Pdf -> {
            val pdf = ProcessedPdfResult(
                filePath = "",
                originalSize = attachment.originalSize,
                compressedSize = attachment.compressedSize,
                pageCount = attachment.pageCount,
                fileName = attachment.fileName,
                rawBytes = attachment.rawBytes,
            )
            MediaPreviewDialog(
                mediaType = MediaType.PDF,
                pdfResult = pdf,
                onDismiss = onDismiss,
                onConfirmSend = onSend,
                onToggleLossyPdf = onToggleLossyPdf,
            )
        }
        else -> Unit
    }
}

