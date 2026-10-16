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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.ic_close
import org.meshtastic.core.resources.ic_mic
import org.meshtastic.core.resources.ic_notes
import org.meshtastic.core.resources.ic_play_arrow
import org.meshtastic.core.resources.media_cancel_transfer
import org.meshtastic.core.resources.media_compressed_image
import org.meshtastic.core.resources.media_download
import org.meshtastic.core.resources.media_pause
import org.meshtastic.core.resources.media_pdf_document
import org.meshtastic.core.resources.media_play
import org.meshtastic.core.resources.media_transferring
import org.meshtastic.core.resources.media_voice_message
import org.meshtastic.core.ui.icon.Camera
import org.meshtastic.core.ui.icon.Close
import org.meshtastic.core.ui.icon.Download
import org.meshtastic.core.ui.icon.FolderOpen
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Pause
import org.meshtastic.core.ui.icon.PlayArrow
import org.meshtastic.feature.messaging.media.MediaAttachment
import org.meshtastic.feature.messaging.media.MediaTransferState

/**
 * Renders a unified WhatsApp-style voice message bubble.
 *
 * Supports:
 * - Sender mode: Always playable from local audio file with playback progress, duration, size,
 *   and sending transfer progress indicator when active.
 * - Receiver mode: Shows Download action if audio is not yet local/verified, transitioning to Play
 *   once completed. Shows circular progress while downloading/reassembling.
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
            val isTransferring = transferState != null && transferState.status == org.meshtastic.feature.messaging.media.TransferStatus.IN_PROGRESS
            val progress = transferState?.progress ?: 0f

            // Action button (Play/Pause, Download, or Circular Progress)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(44.dp),
            ) {
                if (isTransferring) {
                    androidx.compose.material3.CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(40.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                    )
                }

                if (!isLocalAvailable && !isTransferring) {
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

                    if (isTransferring) {
                        val percent = (progress * 100).toInt()
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (!isLocalAvailable) {
                        Text(
                            text = stringResource(Res.string.media_download),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** Renders an image attachment card with size, circular progress, and open/download action. */
@Composable
fun ImageAttachmentBubble(
    sizeBytes: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    transferState: MediaTransferState? = null,
    isLocalAvailable: Boolean = true,
    onDownload: () -> Unit = {},
) {
    val isTransferring = transferState != null && transferState.status == org.meshtastic.feature.messaging.media.TransferStatus.IN_PROGRESS
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
                    androidx.compose.material3.CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(40.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                    )
                }

                if (!isLocalAvailable && !isTransferring) {
                    IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = MeshtasticIcons.Download,
                            contentDescription = stringResource(Res.string.media_download),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Icon(
                        imageVector = MeshtasticIcons.Camera,
                        contentDescription = stringResource(Res.string.media_compressed_image),
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.media_compressed_image),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
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
                    if (isTransferring) {
                        val percent = (progress * 100).toInt()
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (!isLocalAvailable) {
                        Text(
                            text = stringResource(Res.string.media_download),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** Renders a PDF document card with filename, size, circular progress, and open/download action. */
@Composable
fun PdfAttachmentBubble(
    fileName: String,
    sizeBytes: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    transferState: MediaTransferState? = null,
    isLocalAvailable: Boolean = true,
    onDownload: () -> Unit = {},
) {
    val isTransferring = transferState != null && transferState.status == org.meshtastic.feature.messaging.media.TransferStatus.IN_PROGRESS
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
                    androidx.compose.material3.CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(40.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                    )
                }

                if (!isLocalAvailable && !isTransferring) {
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
                    if (isTransferring) {
                        val percent = (progress * 100).toInt()
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (!isLocalAvailable) {
                        Text(
                            text = stringResource(Res.string.media_download),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** Renders active transfer progress within a message bubble. */
@Composable
fun MediaTransferProgressBubble(
    state: MediaTransferState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val percentInt = (state.progress * 100).toInt()
                Text(
                    text = stringResource(Res.string.media_transferring, state.currentChunk, state.totalChunks, "$percentInt%"),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                IconButton(onClick = onCancel, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = MeshtasticIcons.Close,
                        contentDescription = stringResource(Res.string.media_cancel_transfer),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            )
        }
    }
}

private fun formatByteSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "${NumberFormatter.format(bytes.toFloat() / (1024 * 1024), 1)} MB"
    bytes >= 1024 -> "${NumberFormatter.format(bytes.toFloat() / 1024, 1)} KB"
    else -> "$bytes B"
}
