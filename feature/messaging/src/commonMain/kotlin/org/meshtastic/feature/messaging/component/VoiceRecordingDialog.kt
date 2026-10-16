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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.media_compressed_size
import org.meshtastic.core.resources.media_duration
import org.meshtastic.core.resources.media_est_transfer_time
import org.meshtastic.core.resources.media_pause
import org.meshtastic.core.resources.media_play
import org.meshtastic.core.resources.media_record_voice
import org.meshtastic.core.resources.media_stop_recording
import org.meshtastic.core.resources.send
import org.meshtastic.core.ui.icon.Close
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Mic
import org.meshtastic.core.ui.icon.PlayArrow
import org.meshtastic.core.ui.icon.Send
import org.meshtastic.feature.messaging.media.MediaPlatformService
import org.meshtastic.feature.messaging.media.RecordedAudioResult

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun VoiceRecordingDialog(
    mediaPlatformService: MediaPlatformService,
    onDismiss: () -> Unit,
    onSend: (bytes: ByteArray, durationSeconds: Int) -> Unit = { _, _ -> },
    onSendRecording: ((RecordedAudioResult) -> Unit)? = null,
) {
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var recordedResult by remember { mutableStateOf<RecordedAudioResult?>(null) }
    var isPlayingPreview by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    // Timer loop during recording
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording && recordingSeconds < 10) {
                delay(1000L)
                if (isRecording) {
                    recordingSeconds++
                    if (recordingSeconds >= 10) {
                        // Max 10s auto-stop
                        scope.launch {
                            val res = mediaPlatformService.stopVoiceRecording()
                            isRecording = false
                            res.onSuccess { recordedResult = it }
                                .onFailure { errorMessage = it.message ?: "Recording failed" }
                        }
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (isRecording) {
                scope.launch { mediaPlatformService.cancelVoiceRecording() }
            }
            mediaPlatformService.stopAudio()
            onDismiss()
        },
        title = {
            Text(
                text = stringResource(Res.string.media_record_voice),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                // Recording / Preview Display
                if (recordedResult == null) {
                    // Recording State
                    Box(
                        modifier = Modifier.size(80.dp).clip(CircleShape)
                            .background(if (isRecording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    if (!isRecording) {
                                        errorMessage = null
                                        val startRes = mediaPlatformService.startVoiceRecording()
                                        startRes.onSuccess { isRecording = true }
                                            .onFailure { errorMessage = it.message ?: "Failed to start" }
                                    } else {
                                        val stopRes = mediaPlatformService.stopVoiceRecording()
                                        isRecording = false
                                        stopRes.onSuccess { recordedResult = it }
                                            .onFailure { errorMessage = it.message ?: "Failed to stop" }
                                    }
                                }
                            },
                            modifier = Modifier.size(64.dp),
                        ) {
                            Icon(
                                imageVector = MeshtasticIcons.Mic,
                                contentDescription = if (isRecording) stringResource(Res.string.media_stop_recording) else stringResource(Res.string.media_record_voice),
                                tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "00:${if (recordingSeconds < 10) "0$recordingSeconds" else "$recordingSeconds"} / 00:10",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (isRecording) stringResource(Res.string.media_stop_recording) else "Tap to start (10s max)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (recordedResult != null) {
                    // Preview State
                    val result = recordedResult!!
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            IconButton(
                                onClick = {
                                    if (isPlayingPreview) {
                                        mediaPlatformService.pauseAudio()
                                        isPlayingPreview = false
                                    } else {
                                        scope.launch {
                                            isPlayingPreview = true
                                            mediaPlatformService.playAudio(result.filePath) {
                                                isPlayingPreview = false
                                            }
                                        }
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = MeshtasticIcons.PlayArrow,
                                    contentDescription = if (isPlayingPreview) stringResource(Res.string.media_pause) else stringResource(Res.string.media_play),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(Res.string.media_duration, "${result.durationSeconds}s"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                val kbStr = NumberFormatter.format(result.sizeBytes.toFloat() / 1024, 1)
                                Text(
                                    text = "${stringResource(Res.string.media_compressed_size, "$kbStr KB")} • ${result.codec}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                val estSeconds = (result.sizeBytes / 80) * 2.5f
                                val estStr = NumberFormatter.format(estSeconds, 0)
                                Text(
                                    text = stringResource(Res.string.media_est_transfer_time, "~${estStr}s"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (recordedResult != null) {
                Button(
                    onClick = {
                        mediaPlatformService.stopAudio()
                        recordedResult?.let { res ->
                            onSend(res.rawBytes, res.durationSeconds)
                            onSendRecording?.invoke(res)
                        }
                        onDismiss()
                    },
                ) {
                    Icon(MeshtasticIcons.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(Res.string.send))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (isRecording) {
                        scope.launch { mediaPlatformService.cancelVoiceRecording() }
                    }
                    mediaPlatformService.stopAudio()
                    onDismiss()
                },
            ) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}
