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

        // AirPlay frame size: 352 samples * 2 channels * 2 bytes per sample
        private const val FRAME_SIZE = 352 * 2 * 2

        // Buffer capacity: enough for multiple frames to handle variable input sizes
        private const val BUFFER_CAPACITY = FRAME_SIZE * 16
    }

    private var inputAudioFormat = AudioFormat.NOT_SET
    private var outputAudioFormat = AudioFormat.NOT_SET
    private var isActive = false
    private var inputBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    // Efficient ring buffer for accumulating audio data
    private val accumulationBuffer = ByteArray(BUFFER_CAPACITY)
    private var writePosition = 0

    // Reusable frame buffer to avoid allocation on every frame
    private val frameBuffer = ByteArray(FRAME_SIZE)

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
            val position = inputBuffer.position()

            // Calculate how much space we have
            val availableSpace = BUFFER_CAPACITY - writePosition

            if (remaining <= availableSpace) {
                // Normal case: we have enough space
                inputBuffer.get(accumulationBuffer, writePosition, remaining)
                writePosition += remaining
            } else {
                // Buffer overflow: copy what we can and log warning
                Log.w(TAG, "Buffer overflow: dropping ${remaining - availableSpace} bytes")
                inputBuffer.get(accumulationBuffer, writePosition, availableSpace)
                writePosition += availableSpace
                // Skip the rest
                inputBuffer.position(position + remaining)
            }

            // Reset position for local playback
            inputBuffer.position(position)

            // Send complete frames to AirPlay
            while (writePosition >= FRAME_SIZE) {
                // Copy frame to reusable buffer
                System.arraycopy(accumulationBuffer, 0, frameBuffer, 0, FRAME_SIZE)

                // Send the frame
                AirPlayBridge.sendAudio(
                    frameBuffer,
                    inputAudioFormat.sampleRate,
                    inputAudioFormat.channelCount
                )

                // Shift remaining data to the front of the buffer
                val remainingBytes = writePosition - FRAME_SIZE
                if (remainingBytes > 0) {
                    System.arraycopy(
                        accumulationBuffer, FRAME_SIZE,
                        accumulationBuffer, 0,
                        remainingBytes
                    )
                }
                writePosition = remainingBytes
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
        if (writePosition > 0 && AirPlayBridge.isConnected.value) {
            // Pad the remaining data to make a complete frame if needed
            if (writePosition < FRAME_SIZE) {
                // Pad with silence
                for (i in writePosition until FRAME_SIZE) {
                    accumulationBuffer[i] = 0
                }
            }

            // Copy to frame buffer and send
            System.arraycopy(accumulationBuffer, 0, frameBuffer, 0, FRAME_SIZE)
            AirPlayBridge.sendAudio(
                frameBuffer,
                inputAudioFormat.sampleRate,
                inputAudioFormat.channelCount
            )
            writePosition = 0
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
        writePosition = 0
    }

    override fun reset() {
        flush()
        inputAudioFormat = AudioFormat.NOT_SET
        outputAudioFormat = AudioFormat.NOT_SET
        isActive = false
    }
}
