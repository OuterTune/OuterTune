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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Bridge to the native AirPlay 2 library.
 * Supports multi-device streaming - play to multiple AirPlay speakers simultaneously.
 */
object AirPlayBridge {
    private const val TAG = "AirPlayBridge"

    // Native library loaded state
    private var nativeLibLoaded = false

    // Android NSD-based discovery manager
    private var nsdDiscoveryManager: NsdDiscoveryManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Connection state - true if connected to ANY device
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // Discovered devices
    private val _devices = MutableStateFlow<List<AirPlayDevice>>(emptyList())
    val devices: StateFlow<List<AirPlayDevice>> = _devices.asStateFlow()

    // Set of connected device IDs (supports multiple)
    private val _connectedDeviceIds = MutableStateFlow<Set<String>>(emptySet())
    val connectedDeviceIds: StateFlow<Set<String>> = _connectedDeviceIds.asStateFlow()

    // Discovery state
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    // Connecting state per device (with timestamp for timeout)
    private val _connectingDeviceIds = MutableStateFlow<Set<String>>(emptySet())
    val connectingDeviceIds: StateFlow<Set<String>> = _connectingDeviceIds.asStateFlow()

    // Track when connection started for timeout (thread-safe)
    private val connectionStartTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private const val CONNECTION_TIMEOUT_MS = 10000L // 10 seconds timeout

    // Track failed connections
    private val _failedDeviceIds = MutableStateFlow<Set<String>>(emptySet())
    val failedDeviceIds: StateFlow<Set<String>> = _failedDeviceIds.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

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

            // Periodically poll connected devices from native
            scope.launch {
                while (true) {
                    delay(500)
                    refreshConnectedDevices()
                }
            }
        }
    }

    /**
     * Start discovering AirPlay devices on the network
     */
    suspend fun startDiscovery() {
        if (nsdDiscoveryManager == null) {
            Log.w(TAG, "NSD discovery manager not initialized")
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
     * Refresh devices list (NSD updates automatically)
     */
    suspend fun refreshDevices() {
        Log.d(TAG, "Current devices: ${_devices.value.size}")
    }

    /**
     * Refresh connected devices from native layer
     */
    private fun refreshConnectedDevices() {
        if (!nativeLibLoaded) return

        try {
            val connectedJson = nativeGetConnectedDevices()
            val connectedIds = json.decodeFromString<List<String>>(connectedJson).toSet()
            val currentTime = System.currentTimeMillis()

            // Check for connection timeouts
            val timedOutDevices = mutableSetOf<String>()
            connectionStartTimes.entries.removeAll { (deviceId, startTime) ->
                val elapsed = currentTime - startTime
                if (elapsed > CONNECTION_TIMEOUT_MS && !connectedIds.contains(deviceId)) {
                    Log.w(TAG, "Connection timeout for device: $deviceId")
                    timedOutDevices.add(deviceId)
                    true
                } else if (connectedIds.contains(deviceId)) {
                    // Successfully connected, remove from tracking and clear any failed state
                    _failedDeviceIds.value = _failedDeviceIds.value - deviceId
                    true
                } else {
                    false
                }
            }

            // Mark timed out devices as failed
            if (timedOutDevices.isNotEmpty()) {
                _failedDeviceIds.value = _failedDeviceIds.value + timedOutDevices
            }

            // Remove timed out and connected devices from connecting state
            val stillConnecting = _connectingDeviceIds.value - connectedIds - timedOutDevices
            _connectingDeviceIds.value = stillConnecting

            _connectedDeviceIds.value = connectedIds
            _isConnected.value = connectedIds.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh connected devices: ${e.message}")
        }
    }

    /**
     * Toggle connection to an AirPlay device.
     * If connected, disconnects. If disconnected, connects.
     */
    suspend fun toggleDevice(device: AirPlayDevice) {
        if (_connectedDeviceIds.value.contains(device.id)) {
            disconnectDevice(device.id)
        } else {
            connectDevice(device)
        }
    }

    /**
     * Connect to an AirPlay device (can connect to multiple)
     */
    suspend fun connectDevice(device: AirPlayDevice): Boolean {
        if (!nativeLibLoaded) {
            Log.w(TAG, "Native library not loaded")
            return false
        }

        // Clear any previous failed status and mark as connecting
        _failedDeviceIds.value = _failedDeviceIds.value - device.id
        _connectingDeviceIds.value = _connectingDeviceIds.value + device.id
        connectionStartTimes[device.id] = System.currentTimeMillis()

        return withContext(Dispatchers.IO) {
            val success = nativeConnectWithInfo(
                device.id,
                device.name,
                device.address,
                device.port,
                device.supports_airplay2
            )
            if (success) {
                Log.i(TAG, "Connection initiated to ${device.name}")
                // Will be confirmed via refreshConnectedDevices
            } else {
                Log.e(TAG, "Failed to initiate connection to ${device.name}")
                _connectingDeviceIds.value = _connectingDeviceIds.value - device.id
                connectionStartTimes.remove(device.id)
            }
            success
        }
    }

    /**
     * Disconnect from a specific device
     */
    fun disconnectDevice(deviceId: String) {
        if (!nativeLibLoaded) return
        nativeDisconnectDevice(deviceId)
        _connectedDeviceIds.value = _connectedDeviceIds.value - deviceId
        _connectingDeviceIds.value = _connectingDeviceIds.value - deviceId
        _isConnected.value = _connectedDeviceIds.value.isNotEmpty()
        Log.i(TAG, "Disconnected from device: $deviceId")
    }

    /**
     * Connect to all discovered devices
     */
    suspend fun connectAll() {
        _devices.value.forEach { device ->
            if (!_connectedDeviceIds.value.contains(device.id)) {
                connectDevice(device)
            }
        }
    }

    /**
     * Disconnect from all devices
     */
    fun disconnectAll() {
        if (!nativeLibLoaded) return
        nativeDisconnect()
        _connectedDeviceIds.value = emptySet()
        _connectingDeviceIds.value = emptySet()
        _isConnected.value = false
        Log.i(TAG, "Disconnected from all devices")
    }

    /**
     * Check if a specific device is connected
     */
    fun isDeviceConnected(deviceId: String): Boolean {
        return _connectedDeviceIds.value.contains(deviceId)
    }

    /**
     * Check if a specific device is currently connecting
     */
    fun isDeviceConnecting(deviceId: String): Boolean {
        return _connectingDeviceIds.value.contains(deviceId)
    }

    /**
     * Send audio data to all connected AirPlay devices
     */
    fun sendAudio(audioData: ByteArray, sampleRate: Int, channels: Int): Boolean {
        if (!nativeLibLoaded || !_isConnected.value) return false
        return nativeSendAudio(audioData, sampleRate, channels)
    }

    /**
     * Set volume on all connected devices
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
            disconnectAll()
            nativeDestroy()
        }
    }

    // Native methods
    private external fun nativeInit(): Boolean
    private external fun nativeConnectWithInfo(
        deviceId: String,
        deviceName: String,
        address: String,
        port: Int,
        supportsAirplay2: Boolean
    ): Boolean
    private external fun nativeDisconnectDevice(deviceId: String)
    private external fun nativeDisconnect()
    private external fun nativeGetConnectedDevices(): String
    private external fun nativeIsConnected(): Boolean
    private external fun nativeIsDeviceConnected(deviceId: String): Boolean
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
    fun displayName(): String {
        return name.removeSuffix("._airplay._tcp.local.")
            .trim()
            .ifEmpty { address }
    }
}
