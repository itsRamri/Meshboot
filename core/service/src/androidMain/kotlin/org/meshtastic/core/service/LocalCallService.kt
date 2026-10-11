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
package org.meshtastic.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.meshtastic.core.model.CallState
import org.meshtastic.core.network.repository.CallSessionManager

private const val CHANNEL_ID = "mesh_local_voice_call_channel"
private const val NOTIFICATION_ID = 42435

class LocalCallService : Service() {

    private val callSessionManager: CallSessionManager by inject()
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var observerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observerJob = serviceScope.launch {
            callSessionManager.activeSession.collect { session ->
                if (session == null || session.state == CallState.IDLE || session.state.name.startsWith("ENDED")) {
                    stopSelf()
                } else if (session.state == CallState.CONNECTED) {
                    val notification = buildOngoingCallNotification(
                        peerName = session.peer.displayName,
                        durationSec = session.durationSeconds,
                    )
                    startForegroundWithMicType(notification)
                }
            }
        }
    }

    private fun startForegroundWithMicType(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to start local call foreground service" }
        }
    }

    private fun buildOngoingCallNotification(peerName: String, durationSec: Long): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val minutes = durationSec / 60
        val seconds = durationSec % 60
        val formattedTime = "%02d:%02d".format(minutes, seconds)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Active Local Wi-Fi Call")
            .setContentText("Speaking with $peerName ($formattedTime)")
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Local Wi-Fi Voice Calls",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Shows notifications for ongoing local voice calls"
                setSound(null, null)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        observerJob?.cancel()
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Logger.i { "LocalCallService destroyed" }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, LocalCallService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocalCallService::class.java)
            context.stopService(intent)
        }
    }
}
