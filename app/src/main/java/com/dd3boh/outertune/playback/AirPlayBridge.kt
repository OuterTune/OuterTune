/*
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.playback

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Bridge to the native AirPlay 2 library.
 * Provides device discovery, connection management, and audio streaming to AirPlay devices.
 */
object AirPlayBridge {
    private const val TAG = "AirPlayBridge"

    // Native library loaded state
    private var nativeLibLoaded = false

    // Multicast lock for mDNS discovery (Android filters multicast by default)
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wifiManager: WifiManager? = null

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
     * Must be called before starting discovery to enable multicast reception.
     */
    fun initialize(context: Context) {
        try {
            wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            Log.i(TAG, "AirPlayBridge initialized with context")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get WifiManager: ${e.message}")
        }
    }

    /**
     * Acquire multicast lock to receive mDNS packets.
     * Android filters out multicast by default to save battery.
     */
    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return

        try {
            multicastLock = wifiManager?.createMulticastLock("AirPlayDiscovery")?.apply {
                setReferenceCounted(true)
                acquire()
            }
            Log.i(TAG, "Multicast lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire multicast lock: ${e.message}")
        }
    }

    /**
     * Release multicast lock when discovery stops.
     */
    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
                Log.i(TAG, "Multicast lock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release multicast lock: ${e.message}")
        }
        multicastLock = null
    }

    /**
     * Start discovering AirPlay devices on the network
     */
    suspend fun startDiscovery() {
        if (!nativeLibLoaded) {
            Log.w(TAG, "Native library not loaded, cannot start discovery")
            return
        }

        withContext(Dispatchers.IO) {
            // Acquire multicast lock before discovery
            acquireMulticastLock()
            _isDiscovering.value = true
            nativeStartDiscovery()
        }
    }

    /**
     * Stop device discovery
     */
    fun stopDiscovery() {
        if (!nativeLibLoaded) return

        _isDiscovering.value = false
        nativeStopDiscovery()
        releaseMulticastLock()
    }

    /**
     * Refresh the list of discovered devices
     */
    suspend fun refreshDevices() {
        if (!nativeLibLoaded) return

        withContext(Dispatchers.IO) {
            val devicesJson = nativeGetDevices()
            try {
                val deviceList = Json.decodeFromString<List<AirPlayDevice>>(devicesJson)
                _devices.value = deviceList
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse devices JSON: ${e.message}")
            }
        }
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
            val success = nativeConnect(device.id)
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
        if (!nativeLibLoaded) return

        stopDiscovery()
        disconnect()
        releaseMulticastLock()
        nativeDestroy()
    }

    // Native methods
    private external fun nativeInit(): Boolean
    private external fun nativeStartDiscovery(): Boolean
    private external fun nativeStopDiscovery()
    private external fun nativeGetDevices(): String
    private external fun nativeConnect(deviceId: String): Boolean
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
