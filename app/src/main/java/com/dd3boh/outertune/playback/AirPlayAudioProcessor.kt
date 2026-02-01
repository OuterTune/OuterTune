/*
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.playback

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Audio processor that intercepts PCM audio and sends it to AirPlay devices.
 * When connected to an AirPlay device, audio is streamed to that device.
 * Local playback can optionally be muted when streaming.
 */
class AirPlayAudioProcessor : AudioProcessor {
    companion object {
        private const val TAG = "AirPlayAudioProcessor"

        // Standard AirPlay audio format
        private const val AIRPLAY_SAMPLE_RATE = 44100
        private const val AIRPLAY_CHANNEL_COUNT = 2
    }

    private var inputAudioFormat = AudioFormat.NOT_SET
    private var outputAudioFormat = AudioFormat.NOT_SET
    private var isActive = false
    private var inputBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    // Buffer for accumulating audio data before sending to AirPlay
    private var accumulatedData = ByteArray(0)
    private val frameSize = 352 * 2 * 2 // 352 samples * 2 channels * 2 bytes per sample

    // Whether to mute local playback when streaming to AirPlay
    var muteLocalWhenStreaming = false

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        this.inputAudioFormat = inputAudioFormat

        // Check if we can process this format
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            // We only handle 16-bit PCM for AirPlay
            this.outputAudioFormat = inputAudioFormat
            this.isActive = false
            return inputAudioFormat
        }

        this.outputAudioFormat = inputAudioFormat
        this.isActive = true

        Log.d(TAG, "Configured: sampleRate=${inputAudioFormat.sampleRate}, " +
                "channels=${inputAudioFormat.channelCount}, encoding=${inputAudioFormat.encoding}")

        return outputAudioFormat
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive || inputBuffer.remaining() == 0) {
            this.inputBuffer = inputBuffer
            return
        }

        // Check if AirPlay is connected
        val isAirPlayConnected = AirPlayBridge.isConnected.value

        if (isAirPlayConnected) {
            // Copy data for AirPlay streaming
            val remaining = inputBuffer.remaining()
            val data = ByteArray(remaining)
            val position = inputBuffer.position()
            inputBuffer.get(data)
            inputBuffer.position(position) // Reset position for local playback

            // Accumulate data until we have enough for a frame
            accumulatedData = accumulatedData + data

            // Send complete frames to AirPlay
            while (accumulatedData.size >= frameSize) {
                val frameData = accumulatedData.copyOfRange(0, frameSize)
                accumulatedData = accumulatedData.copyOfRange(frameSize, accumulatedData.size)

                // Send to AirPlay bridge
                AirPlayBridge.sendAudio(
                    frameData,
                    inputAudioFormat.sampleRate,
                    inputAudioFormat.channelCount
                )
            }

            // If muting local playback, zero out the buffer
            if (muteLocalWhenStreaming) {
                val zeroBuffer = ByteBuffer.allocateDirect(inputBuffer.remaining())
                    .order(ByteOrder.nativeOrder())
                this.outputBuffer = zeroBuffer
                this.inputBuffer = AudioProcessor.EMPTY_BUFFER
                return
            }
        }

        // Pass through to local playback
        this.inputBuffer = inputBuffer
    }

    override fun queueEndOfStream() {
        inputEnded = true
        // Flush any remaining accumulated data
        if (accumulatedData.isNotEmpty() && AirPlayBridge.isConnected.value) {
            AirPlayBridge.sendAudio(
                accumulatedData,
                inputAudioFormat.sampleRate,
                inputAudioFormat.channelCount
            )
            accumulatedData = ByteArray(0)
        }
    }

    override fun getOutput(): ByteBuffer {
        val output = if (outputBuffer !== AudioProcessor.EMPTY_BUFFER) {
            outputBuffer
        } else {
            inputBuffer
        }

        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputBuffer = AudioProcessor.EMPTY_BUFFER

        return output
    }

    override fun isEnded(): Boolean = inputEnded && inputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun flush() {
        inputBuffer = AudioProcessor.EMPTY_BUFFER
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        accumulatedData = ByteArray(0)
    }

    override fun reset() {
        flush()
        inputAudioFormat = AudioFormat.NOT_SET
        outputAudioFormat = AudioFormat.NOT_SET
        isActive = false
    }
}
