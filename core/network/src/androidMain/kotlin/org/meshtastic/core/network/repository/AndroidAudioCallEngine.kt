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
package org.meshtastic.core.network.repository

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

private const val SAMPLE_RATE = 16000 // 16 kHz wideband speech
private const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
private const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
private const val BUFFER_SIZE_FACTOR = 2

@Single
class AndroidAudioCallEngine(
    private val context: Context,
) : AudioCallEngine {
    override val isSupported: Boolean = true

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var socket: DatagramSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var captureJob: Job? = null
    private var playbackJob: Job? = null
    private val engineScope = CoroutineScope(Dispatchers.IO)

    private val isMuted = AtomicBoolean(false)
    private var activeSessionKey: ByteArray? = null

    override fun startAudioStream(
        remoteAddress: String,
        remotePort: Int,
        localPort: Int,
        sessionKey: ByteArray,
    ): Boolean = try {
        stopAudioStream()
        activeSessionKey = sessionKey

        val minRecordBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT)
        val minTrackBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT)
        val recordBufferSize = (minRecordBuf * BUFFER_SIZE_FACTOR).coerceAtLeast(1024)
        val trackBufferSize = (minTrackBuf * BUFFER_SIZE_FACTOR).coerceAtLeast(1024)

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL_CONFIG_IN,
            AUDIO_FORMAT,
            recordBufferSize,
        )

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG_OUT)
                    .setEncoding(AUDIO_FORMAT)
                    .build(),
            )
            .setBufferSizeInBytes(trackBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val datagramSocket = DatagramSocket(localPort)
        socket = datagramSocket
        audioRecord = record
        audioTrack = track

        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION

        record.startRecording()
        track.play()

        // Capture loop (Microphone -> UDP socket)
        captureJob = engineScope.launch {
            val audioBuffer = ByteArray(640) // 20ms of 16kHz 16-bit mono PCM = 320 samples * 2 bytes = 640 bytes
            val destination = InetAddress.getByName(remoteAddress)
            while (isActive && !datagramSocket.isClosed) {
                val readBytes = record.read(audioBuffer, 0, audioBuffer.size)
                if (readBytes > 0) {
                    if (isMuted.get()) {
                        audioBuffer.fill(0)
                    }
                    val encryptedPayload = applyXorMask(audioBuffer, readBytes, sessionKey)
                    val packet = DatagramPacket(encryptedPayload, readBytes, destination, remotePort)
                    try {
                        datagramSocket.send(packet)
                    } catch (e: Exception) {
                        Logger.w(e) { "Error sending audio packet" }
                        break
                    }
                }
            }
        }

        // Playback loop (UDP socket -> Speaker/Earpiece)
        playbackJob = engineScope.launch {
            val receiveBuffer = ByteArray(1024)
            val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)
            while (isActive && !datagramSocket.isClosed) {
                try {
                    datagramSocket.receive(packet)
                    val decrypted = applyXorMask(packet.data, packet.length, sessionKey)
                    track.write(decrypted, 0, packet.length)
                } catch (e: Exception) {
                    if (isActive && !datagramSocket.isClosed) {
                        Logger.w(e) { "Error receiving audio packet" }
                    }
                    break
                }
            }
        }

        Logger.i { "Audio call stream started to $remoteAddress:$remotePort (local port $localPort)" }
        true
    } catch (e: Exception) {
        Logger.e(e) { "Failed to start audio call stream" }
        stopAudioStream()
        false
    }

    override fun stopAudioStream() {
        captureJob?.cancel()
        captureJob = null
        playbackJob?.cancel()
        playbackJob = null

        try {
            audioRecord?.let {
                if (it.state == AudioRecord.STATE_INITIALIZED) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Logger.w(e) { "Error releasing AudioRecord" }
        }
        audioRecord = null

        try {
            audioTrack?.let {
                if (it.state == AudioTrack.STATE_INITIALIZED) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Logger.w(e) { "Error releasing AudioTrack" }
        }
        audioTrack = null

        try {
            socket?.close()
        } catch (e: Exception) {
            Logger.w(e) { "Error closing DatagramSocket" }
        }
        socket = null

        audioManager?.mode = AudioManager.MODE_NORMAL
        Logger.i { "Audio call stream stopped and cleaned up" }
    }

    override fun setMicrophoneMute(muted: Boolean) {
        isMuted.set(muted)
    }

    override fun setSpeakerphoneOn(speakerOn: Boolean) {
        try {
            audioManager?.isSpeakerphoneOn = speakerOn
        } catch (e: Exception) {
            Logger.w(e) { "Error setting speakerphone state: $speakerOn" }
        }
    }

    /** Simple symmetric stream mask using shared sessionKey */
    private fun applyXorMask(data: ByteArray, length: Int, key: ByteArray): ByteArray {
        val out = ByteArray(length)
        if (key.isEmpty()) return out
        for (i in 0 until length) {
            out[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return out
    }
}
