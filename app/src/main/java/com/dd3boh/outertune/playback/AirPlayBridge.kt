/*
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.playback

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * Bridge to the native AirPlay 2 library.
 * Provides device discovery (via Android NSD), connection management, and audio streaming to AirPlay devices.
 */
object AirPlayBridge {
    private const val TAG = "AirPlayBridge"

    // Native library loaded state
    private var nativeLibLoaded = false

    // Android NSD-based discovery manager (more reliable on Android than native mDNS)
    private var nsdDiscoveryManager: NsdDiscoveryManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Connection state
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // Discovered devices
    private val _devices = MutableStateFlow<List<AirPlayDevice>>(emptyList())
    val devices: StateFlow<List<AirPlayDevice>> = _devices.asStateFlow()

    // Currently connected device
    private val _connectedDevice = MutableStateFlow<AirPlayDevice?>(null)
    val connectedDevice: StateFlow<AirPlayDevice?> = _connectedDevice.asStateFlow()

    // Discovery state
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    init {
        try {
            System.loadLibrary("airplay_bridge")
            nativeLibLoaded = true
            nativeInit()
            Log.i(TAG, "AirPlay native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load AirPlay native library: ${e.message}")
            nativeLibLoaded = false
        }
    }

    /**
     * Check if AirPlay functionality is available
     */
    fun isAvailable(): Boolean = nativeLibLoaded

    /**
     * Initialize the AirPlay bridge with application context.
     * Must be called before starting discovery.
     */
    fun initialize(context: Context) {
        if (nsdDiscoveryManager == null) {
            nsdDiscoveryManager = NsdDiscoveryManager(context)
            Log.i(TAG, "AirPlayBridge initialized with NSD discovery manager")

            // Observe NSD discovery state
            scope.launch {
                nsdDiscoveryManager?.isDiscovering?.collect { discovering ->
                    _isDiscovering.value = discovering
                }
            }

            // Observe discovered devices from NSD
            scope.launch {
                nsdDiscoveryManager?.devices?.collect { deviceMap ->
                    _devices.value = deviceMap.values.toList()
                    Log.d(TAG, "Devices updated: ${deviceMap.size} found")
                }
            }
        }
    }

    /**
     * Start discovering AirPlay devices on the network using Android NSD
     */
    suspend fun startDiscovery() {
        if (nsdDiscoveryManager == null) {
            Log.w(TAG, "NSD discovery manager not initialized. Call initialize() first.")
            return
        }

        withContext(Dispatchers.Main) {
            nsdDiscoveryManager?.startDiscovery()
        }
    }

    /**
     * Stop device discovery
     */
    fun stopDiscovery() {
        nsdDiscoveryManager?.stopDiscovery()
    }

    /**
     * Refresh the list of discovered devices (no-op for NSD, devices are updated automatically)
     */
    suspend fun refreshDevices() {
        // NSD automatically updates devices via flow, no manual refresh needed
        // But we can log the current state for debugging
        Log.d(TAG, "Current devices: ${_devices.value.size}")
    }

    /**
     * Connect to an AirPlay device
     */
    suspend fun connect(device: AirPlayDevice): Boolean {
        if (!nativeLibLoaded) {
            Log.w(TAG, "Native library not loaded, cannot connect")
            return false
        }

        return withContext(Dispatchers.IO) {
            // Pass device info to native layer for connection
            val success = nativeConnectWithInfo(
                device.id,
                device.name,
                device.address,
                device.port,
                device.supports_airplay2
            )
            if (success) {
                _isConnected.value = true
                _connectedDevice.value = device
                Log.i(TAG, "Connected to ${device.name}")
            } else {
                Log.e(TAG, "Failed to connect to ${device.name}")
            }
            success
        }
    }

    /**
     * Disconnect from the current AirPlay device
     */
    fun disconnect() {
        if (!nativeLibLoaded) return

        nativeDisconnect()
        _isConnected.value = false
        _connectedDevice.value = null
        Log.i(TAG, "Disconnected from AirPlay device")
    }

    /**
     * Send audio data to the connected AirPlay device
     * @param audioData PCM audio data (16-bit signed, interleaved)
     * @param sampleRate Sample rate in Hz (e.g., 44100)
     * @param channels Number of audio channels (e.g., 2 for stereo)
     */
    fun sendAudio(audioData: ByteArray, sampleRate: Int, channels: Int): Boolean {
        if (!nativeLibLoaded || !_isConnected.value) return false

        return nativeSendAudio(audioData, sampleRate, channels)
    }

    /**
     * Set the volume on the AirPlay device
     * @param volume Volume level 0-100
     */
    fun setVolume(volume: Int): Boolean {
        if (!nativeLibLoaded || !_isConnected.value) return false

        return nativeSetVolume(volume.coerceIn(0, 100))
    }

    /**
     * Cleanup resources
     */
    fun destroy() {
        stopDiscovery()
        nsdDiscoveryManager?.destroy()
        nsdDiscoveryManager = null

        if (nativeLibLoaded) {
            disconnect()
            nativeDestroy()
        }
    }

    // Native methods - discovery is handled by Android NSD, native is only for connection/streaming
    private external fun nativeInit(): Boolean
    private external fun nativeConnectWithInfo(
        deviceId: String,
        deviceName: String,
        address: String,
        port: Int,
        supportsAirplay2: Boolean
    ): Boolean
    private external fun nativeDisconnect()
    private external fun nativeIsConnected(): Boolean
    private external fun nativeSendAudio(audioData: ByteArray, sampleRate: Int, channels: Int): Boolean
    private external fun nativeSetVolume(volume: Int): Boolean
    private external fun nativeDestroy()
}

/**
 * Represents a discovered AirPlay device
 */
@Serializable
data class AirPlayDevice(
    val id: String,
    val name: String,
    val address: String,
    val port: Int,
    val model: String? = null,
    val features: String? = null,
    val supports_airplay2: Boolean = false,
    val flags: String? = null
) {
    /**
     * Get a display-friendly name for the device
     */
    fun displayName(): String {
        return name.removeSuffix("._airplay._tcp.local.")
            .trim()
            .ifEmpty { address }
    }
}
