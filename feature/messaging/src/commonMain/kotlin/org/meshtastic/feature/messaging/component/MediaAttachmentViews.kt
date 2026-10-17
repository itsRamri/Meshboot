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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.rememberAsyncImagePainter
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.close
import org.meshtastic.core.resources.media_cancel_transfer
import org.meshtastic.core.resources.media_compressed_image
import org.meshtastic.core.resources.media_download
import org.meshtastic.core.resources.media_not_available_on_device
import org.meshtastic.core.resources.media_open_file
import org.meshtastic.core.resources.media_pause
import org.meshtastic.core.resources.media_pdf_document
import org.meshtastic.core.resources.media_play
import org.meshtastic.core.resources.media_status_cancelled
import org.meshtastic.core.resources.media_status_completed
import org.meshtastic.core.resources.media_status_failed
import org.meshtastic.core.resources.media_status_preparing
import org.meshtastic.core.resources.media_status_receiving
import org.meshtastic.core.resources.media_status_sending
import org.meshtastic.core.resources.media_status_timed_out
import org.meshtastic.core.resources.media_status_verifying
import org.meshtastic.core.resources.media_status_waiting_receiver
import org.meshtastic.core.resources.media_view_image
import org.meshtastic.core.resources.media_voice_message
import org.meshtastic.core.ui.icon.Camera
import org.meshtastic.core.ui.icon.Close
import org.meshtastic.core.ui.icon.Download
import org.meshtastic.core.ui.icon.FolderOpen
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Pause
import org.meshtastic.core.ui.icon.PlayArrow
import org.meshtastic.feature.messaging.media.MediaTransferState
import org.meshtastic.feature.messaging.media.TransferStatus

/**
 * Renders a unified WhatsApp-style voice message bubble.
 *
 * Supports:
 * - Sender mode: Immediately playable with waveform, duration, size, and sending progress overlay with cancel X.
 * - WaitingForReceiver: Shows clear waiting status until receiver ACK arrives.
 * - Receiver mode: Shows Download or circular progress during transfer, real play/pause once reconstructed.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun VoiceAttachmentBubble(
    durationSeconds: Int,
    sizeBytes: Long,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    modifier: Modifier = Modifier,
    transferState: MediaTransferState? = null,
    isLocalAvailable: Boolean = true,
    onDownload: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val status = transferState?.status
            val isTransferring = status == TransferStatus.IN_PROGRESS || status == TransferStatus.PREPARING
            val progress = transferState?.progress ?: 0f

            // Action button (Play/Pause, Download, or Circular Progress with Cancel X)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(44.dp),
            ) {
                if (isTransferring) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(44.dp),
                        color = Color(0xFF25D366).takeIf { false } ?: MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                    )
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = MeshtasticIcons.Close,
                            contentDescription = stringResource(Res.string.media_cancel_transfer),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else if (!isLocalAvailable) {
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = MeshtasticIcons.Download,
                            contentDescription = stringResource(Res.string.media_download),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    IconButton(
                        onClick = onPlayToggle,
                        enabled = isLocalAvailable,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) MeshtasticIcons.Pause else MeshtasticIcons.PlayArrow,
                            contentDescription = if (isPlaying) stringResource(Res.string.media_pause) else stringResource(Res.string.media_play),
                            tint = if (isLocalAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }

            // Body: Title/Waveform and Status Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().height(16.dp),
                ) {
                    // Stylized voice waveform bars (WhatsApp style)
                    val barCount = 20
                    for (i in 0 until barCount) {
                        val normalizedHeight = 0.3f + 0.7f * kotlin.math.abs(kotlin.math.sin(i * 0.45f)).toFloat()
                        val isPlayed = isPlaying && (i < (barCount / 2))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height((14 * normalizedHeight).dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(
                                    if (isPlayed) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    },
                                ),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val minutes = durationSeconds / 60
                    val seconds = durationSeconds % 60
                    val durationFormatted = "$minutes:${if (seconds < 10) "0$seconds" else "$seconds"}"

                    Text(
                        text = "$durationFormatted • ${formatByteSize(sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    TransferStatusLabel(transferState = transferState, isLocalAvailable = isLocalAvailable)
                }
            }
        }
    }
}

/**
 * Renders a WhatsApp-style photo bubble.
 *
 * Shows the actual thumbnail preview immediately for the sender, with an overlaid blue circular
 * progress indicator and a cancel "X" button during transmission.
 * When tapping the photo, opens the full-screen photo viewer dialog.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun ImageAttachmentBubble(
    filePath: String?,
    sizeBytes: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    transferState: MediaTransferState? = null,
    isLocalAvailable: Boolean = true,
    onDownload: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    val status = transferState?.status
    val isTransferring = status == TransferStatus.IN_PROGRESS || status == TransferStatus.PREPARING
    val progress = transferState?.progress ?: 0f

    var showFullScreenViewer by remember { mutableStateOf(false) }

    if (showFullScreenViewer && !filePath.isNullOrEmpty()) {
        FullScreenImageViewerDialog(
            filePath = filePath,
            onDismiss = { showFullScreenViewer = false },
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = isLocalAvailable && !isTransferring) {
                if (!filePath.isNullOrEmpty()) {
                    showFullScreenViewer = true
                } else {
                    onClick()
                }
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(MaterialTheme.colorScheme.surfaceDim),
                contentAlignment = Alignment.Center,
            ) {
                if (!filePath.isNullOrEmpty()) {
                    val painter = rememberAsyncImagePainter(model = "file://$filePath")
                    Image(
                        painter = painter,
                        contentDescription = stringResource(Res.string.media_compressed_image),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = MeshtasticIcons.Camera,
                        contentDescription = stringResource(Res.string.media_compressed_image),
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }

                // WhatsApp-style circular progress overlay with cancel "X" button
                if (isTransferring) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier.size(54.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                CircularProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.size(48.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 3.5.dp,
                                )
                                IconButton(
                                    onClick = onCancel,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = MeshtasticIcons.Close,
                                        contentDescription = stringResource(Res.string.media_cancel_transfer),
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                } else if (!isLocalAvailable) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp),
                        ) {
                            IconButton(onClick = onDownload, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = MeshtasticIcons.Download,
                                    contentDescription = stringResource(Res.string.media_download),
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Bottom info bar (size & transfer status)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatByteSize(sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                TransferStatusLabel(transferState = transferState, isLocalAvailable = isLocalAvailable)
            }
        }
    }
}

/** Renders a PDF or document attachment card with filename, size, circular progress, and cancel X. */
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun PdfAttachmentBubble(
    fileName: String,
    sizeBytes: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    transferState: MediaTransferState? = null,
    isLocalAvailable: Boolean = true,
    onDownload: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    val status = transferState?.status
    val isTransferring = status == TransferStatus.IN_PROGRESS || status == TransferStatus.PREPARING
    val progress = transferState?.progress ?: 0f

    Card(
        modifier = modifier.fillMaxWidth().clickable(enabled = isLocalAvailable && !isTransferring, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(44.dp),
            ) {
                if (isTransferring) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(44.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                    )
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = MeshtasticIcons.Close,
                            contentDescription = stringResource(Res.string.media_cancel_transfer),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else if (!isLocalAvailable) {
                    IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = MeshtasticIcons.Download,
                            contentDescription = stringResource(Res.string.media_download),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Icon(
                        imageVector = MeshtasticIcons.FolderOpen,
                        contentDescription = stringResource(Res.string.media_pdf_document),
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatByteSize(sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    TransferStatusLabel(transferState = transferState, isLocalAvailable = isLocalAvailable)
                }
            }
        }
    }
}

@Composable
private fun TransferStatusLabel(
    transferState: MediaTransferState?,
    isLocalAvailable: Boolean,
) {
    if (transferState == null) {
        if (!isLocalAvailable) {
            Text(
                text = stringResource(Res.string.media_download),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        return
    }

    when (transferState.status) {
        TransferStatus.PREPARING -> {
            Text(
                text = stringResource(Res.string.media_status_preparing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        TransferStatus.IN_PROGRESS -> {
            val percent = (transferState.progress * 100).toInt()
            val label = if (transferState.isOutgoing) {
                stringResource(Res.string.media_status_sending, transferState.currentChunk, transferState.totalChunks)
            } else {
                stringResource(Res.string.media_status_receiving, transferState.currentChunk, transferState.totalChunks)
            }
            Text(
                text = "$label ($percent%)",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        TransferStatus.WAITING_FOR_RECEIVER -> {
            Text(
                text = stringResource(Res.string.media_status_waiting_receiver),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        TransferStatus.VERIFYING -> {
            Text(
                text = stringResource(Res.string.media_status_verifying),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        TransferStatus.COMPLETED -> {
            Text(
                text = stringResource(Res.string.media_status_completed),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        TransferStatus.CANCELLED -> {
            Text(
                text = stringResource(Res.string.media_status_cancelled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        TransferStatus.TIMED_OUT -> {
            Text(
                text = stringResource(Res.string.media_status_timed_out),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        TransferStatus.FAILED, TransferStatus.CORRUPTED -> {
            Text(
                text = stringResource(Res.string.media_status_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Full-screen photo viewer dialog with close button and high-res image view. */
@Composable
fun FullScreenImageViewerDialog(
    filePath: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            val painter = rememberAsyncImagePainter(model = "file://$filePath")
            Image(
                painter = painter,
                contentDescription = stringResource(Res.string.media_view_image),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )

            // Top close bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.media_view_image),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = MeshtasticIcons.Close,
                        contentDescription = stringResource(Res.string.close),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

private fun formatByteSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "${NumberFormatter.format(bytes.toFloat() / (1024 * 1024), 1)} MB"
    bytes >= 1024 -> "${NumberFormatter.format(bytes.toFloat() / 1024, 1)} KB"
    else -> "$bytes B"
}
