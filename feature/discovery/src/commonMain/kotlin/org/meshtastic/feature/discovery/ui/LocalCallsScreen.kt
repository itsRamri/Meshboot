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
package org.meshtastic.feature.discovery.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.CallState
import org.meshtastic.core.model.LocalCallPeer
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.back
import org.meshtastic.core.resources.ic_arrow_back
import org.meshtastic.core.resources.ic_call
import org.meshtastic.core.resources.ic_call_end
import org.meshtastic.core.resources.ic_graphic_eq
import org.meshtastic.core.resources.ic_volume_mute
import org.meshtastic.core.resources.ic_volume_up
import org.meshtastic.core.resources.local_calls
import org.meshtastic.core.resources.local_calls_busy
import org.meshtastic.core.resources.local_calls_calling
import org.meshtastic.core.resources.local_calls_connected
import org.meshtastic.core.resources.local_calls_declined
import org.meshtastic.core.resources.local_calls_desc
import org.meshtastic.core.resources.local_calls_earpiece
import org.meshtastic.core.resources.local_calls_enable
import org.meshtastic.core.resources.local_calls_enable_desc
import org.meshtastic.core.resources.local_calls_end_call
import org.meshtastic.core.resources.local_calls_ended
import org.meshtastic.core.resources.local_calls_incoming
import org.meshtastic.core.resources.local_calls_mute
import org.meshtastic.core.resources.local_calls_no_peers
import org.meshtastic.core.resources.local_calls_permission_mic
import org.meshtastic.core.resources.local_calls_ringing
import org.meshtastic.core.resources.local_calls_same_wifi_note
import org.meshtastic.core.resources.local_calls_scanning
import org.meshtastic.core.resources.local_calls_speaker
import org.meshtastic.core.resources.local_calls_status_disabled
import org.meshtastic.core.resources.local_calls_status_enabled
import org.meshtastic.core.resources.local_calls_timeout
import org.meshtastic.core.resources.local_calls_unmute
import org.meshtastic.core.resources.local_calls_unreachable
import org.meshtastic.core.ui.util.PermissionStatus
import org.meshtastic.core.ui.util.rememberRecordAudioPermissionState
import org.meshtastic.feature.discovery.LocalCallsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalCallsScreen(
    viewModel: LocalCallsViewModel,
    onNavigateUp: () -> Unit,
) {
    val isEnabled by viewModel.isEnabled.collectAsStateWithLifecycle()
    val displayName by viewModel.displayName.collectAsStateWithLifecycle()
    val myPeerId by viewModel.myPeerId.collectAsStateWithLifecycle()
    val peers by viewModel.discoveredPeers.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()

    val micPermissionState = rememberRecordAudioPermissionState()

    var editingName by remember { mutableStateOf(false) }
    var tempName by remember(displayName) { mutableStateOf(displayName) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.local_calls)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(painter = painterResource(Res.drawable.ic_arrow_back), contentDescription = stringResource(Res.string.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    // Feature Enable Toggle
                    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = stringResource(Res.string.local_calls_enable),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = stringResource(Res.string.local_calls_enable_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { checked ->
                                    if (checked && micPermissionState.status != PermissionStatus.GRANTED) {
                                        micPermissionState.request()
                                    }
                                    viewModel.setEnabled(checked)
                                },
                            )
                        }
                    }
                }

                if (isEnabled) {
                    item {
                        // User Profile on Local Call Network
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Your Local ID: $myPeerId",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                if (editingName) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        OutlinedTextField(
                                            value = tempName,
                                            onValueChange = { tempName = it },
                                            label = { Text("Display Name") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(onClick = {
                                            viewModel.setDisplayName(tempName)
                                            editingName = false
                                        }) {
                                            Text("Save")
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "Name: $displayName",
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Button(
                                            onClick = { editingName = true },
                                            colors = ButtonDefaults.outlinedButtonColors(),
                                        ) {
                                            Text("Edit")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (micPermissionState.status != PermissionStatus.GRANTED) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = stringResource(Res.string.local_calls_permission_mic),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = { micPermissionState.request() }) {
                                        Text("Grant Microphone")
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = stringResource(Res.string.local_calls_same_wifi_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }

                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            Text(
                                text = "Available Local Users (${peers.size})",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }

                    if (peers.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(Res.string.local_calls_no_peers),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    } else {
                        items(peers, key = { it.peerId }) { peer ->
                            PeerItem(
                                peer = peer,
                                onCall = {
                                    if (micPermissionState.status != PermissionStatus.GRANTED) {
                                        micPermissionState.request()
                                    } else {
                                        viewModel.startCall(peer)
                                    }
                                },
                            )
                        }
                    }
                } else {
                    item {
                        Text(
                            text = stringResource(Res.string.local_calls_status_disabled),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }

            // In-Call Overlay or Incoming Call Banner
            activeSession?.let { session ->
                CallOverlay(
                    session = session,
                    onAccept = viewModel::acceptCall,
                    onDecline = viewModel::declineCall,
                    onEnd = viewModel::endCall,
                    onToggleMute = viewModel::toggleMute,
                    onToggleSpeaker = viewModel::toggleSpeaker,
                )
            }
        }
    }
}

@Composable
private fun PeerItem(
    peer: LocalCallPeer,
    onCall: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = peer.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${peer.hostAddress} (${peer.peerId})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(onClick = onCall) {
                Icon(
                    painter = painterResource(Res.drawable.ic_call),
                    contentDescription = "Call ${peer.displayName}",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CallOverlay(
    session: org.meshtastic.core.model.ActiveCallSession,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_call),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = session.peer.displayName,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(8.dp))
            val statusText = when (session.state) {
                CallState.OUTGOING_RINGING -> stringResource(Res.string.local_calls_calling)
                CallState.INCOMING_RINGING -> stringResource(Res.string.local_calls_incoming)
                CallState.CONNECTED -> {
                    val m = session.durationSeconds / 60
                    val s = session.durationSeconds % 60
                    "%02d:%02d".format(m, s)
                }
                CallState.ENDED_BUSY -> stringResource(Res.string.local_calls_busy)
                CallState.ENDED_DECLINED -> stringResource(Res.string.local_calls_declined)
                CallState.ENDED_TIMEOUT -> stringResource(Res.string.local_calls_timeout)
                CallState.ENDED_UNREACHABLE -> stringResource(Res.string.local_calls_unreachable)
                CallState.ENDED_NORMAL -> stringResource(Res.string.local_calls_ended)
                else -> ""
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = Color.LightGray,
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (session.state == CallState.INCOMING_RINGING) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onDecline,
                        modifier = Modifier.size(64.dp).background(Color(0xFFD32F2F), CircleShape),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_call_end),
                            contentDescription = "Decline",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    IconButton(
                        onClick = onAccept,
                        modifier = Modifier.size(64.dp).background(Color(0xFF388E3C), CircleShape),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_call),
                            contentDescription = "Accept",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            } else if (session.state == CallState.CONNECTED) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier.size(56.dp).background(
                            if (session.isMicMuted) Color.Gray else Color.DarkGray,
                            CircleShape,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(if (session.isMicMuted) Res.drawable.ic_volume_mute else Res.drawable.ic_graphic_eq),
                            contentDescription = if (session.isMicMuted) "Unmute" else "Mute",
                            tint = Color.White,
                        )
                    }

                    IconButton(
                        onClick = onEnd,
                        modifier = Modifier.size(64.dp).background(Color(0xFFD32F2F), CircleShape),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_call_end),
                            contentDescription = "End Call",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }

                    IconButton(
                        onClick = onToggleSpeaker,
                        modifier = Modifier.size(56.dp).background(
                            if (session.isSpeakerOn) MaterialTheme.colorScheme.primary else Color.DarkGray,
                            CircleShape,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_volume_up),
                            contentDescription = "Speaker",
                            tint = Color.White,
                        )
                    }
                }
            } else if (session.state == CallState.OUTGOING_RINGING) {
                IconButton(
                    onClick = onEnd,
                    modifier = Modifier.size(64.dp).background(Color(0xFFD32F2F), CircleShape),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_call_end),
                        contentDescription = "Cancel",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}
