/*
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.playback

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress

/**
 * Handles AirPlay device discovery using Android's Network Service Discovery (NSD) API.
 * This is more reliable on Android than trying to do mDNS in native code.
 */
class NsdDiscoveryManager(private val context: Context) {
    companion object {
        private const val TAG = "NsdDiscoveryManager"
        private const val AIRPLAY_SERVICE_TYPE = "_airplay._tcp."
    }

    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private val _devices = MutableStateFlow<Map<String, AirPlayDevice>>(emptyMap())
    val devices: StateFlow<Map<String, AirPlayDevice>> = _devices.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    // Track pending resolutions to avoid duplicates
    private val pendingResolutions = mutableSetOf<String>()

    init {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    /**
     * Start discovering AirPlay devices
     */
    fun startDiscovery() {
        if (_isDiscovering.value) {
            Log.w(TAG, "Discovery already running")
            return
        }

        if (nsdManager == null) {
            Log.e(TAG, "NsdManager not available")
            return
        }

        // Acquire multicast lock
        acquireMulticastLock()

        // Don't clear existing devices - they'll be updated as services are found/lost
        // Only clear pending resolutions to allow re-resolution
        pendingResolutions.clear()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.i(TAG, "Discovery started for $serviceType")
                _isDiscovering.value = true
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "Discovery stopped for $serviceType")
                _isDiscovering.value = false
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "Service found: ${serviceInfo.serviceName}")

                // Avoid duplicate resolutions
                val key = serviceInfo.serviceName
                synchronized(pendingResolutions) {
                    if (pendingResolutions.contains(key)) {
                        Log.d(TAG, "Already resolving $key")
                        return
                    }
                    pendingResolutions.add(key)
                }

                // Resolve the service to get IP and port
                resolveService(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "Service lost: ${serviceInfo.serviceName}")
                removeDevice(serviceInfo.serviceName)
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
                _isDiscovering.value = false
                releaseMulticastLock()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
            }
        }

        try {
            nsdManager?.discoverServices(
                AIRPLAY_SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery: ${e.message}")
            _isDiscovering.value = false
            releaseMulticastLock()
        }
    }

    /**
     * Stop discovering AirPlay devices
     */
    fun stopDiscovery() {
        if (!_isDiscovering.value) return

        discoveryListener?.let {
            try {
                nsdManager?.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop discovery: ${e.message}")
            }
        }

        discoveryListener = null
        _isDiscovering.value = false
        pendingResolutions.clear()
        releaseMulticastLock()
    }

    /**
     * Resolve a discovered service to get its IP address and port
     */
    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Resolve failed for ${serviceInfo.serviceName}: error $errorCode")
                synchronized(pendingResolutions) {
                    pendingResolutions.remove(serviceInfo.serviceName)
                }
            }

            override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                Log.i(TAG, "Service resolved: ${resolvedInfo.serviceName} at ${resolvedInfo.host}:${resolvedInfo.port}")

                synchronized(pendingResolutions) {
                    pendingResolutions.remove(resolvedInfo.serviceName)
                }

                val host = resolvedInfo.host
                if (host != null) {
                    addDevice(resolvedInfo, host)
                } else {
                    Log.w(TAG, "Resolved service has no host: ${resolvedInfo.serviceName}")
                }
            }
        }

        try {
            nsdManager?.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve service: ${e.message}")
            synchronized(pendingResolutions) {
                pendingResolutions.remove(serviceInfo.serviceName)
            }
        }
    }

    /**
     * Add a discovered device to the list
     */
    private fun addDevice(serviceInfo: NsdServiceInfo, host: InetAddress) {
        val name = serviceInfo.serviceName
        val address = host.hostAddress ?: return
        val port = serviceInfo.port

        // Generate a unique ID from the name
        val id = name.hashCode().toString(16)

        // Try to extract attributes (available on API 34+)
        val attributes = try {
            serviceInfo.attributes
        } catch (e: Exception) {
            emptyMap<String, ByteArray>()
        }

        val model = attributes["model"]?.decodeToString()
        val features = attributes["features"]?.decodeToString()
        val flags = attributes["flags"]?.decodeToString()
        val deviceId = attributes["deviceid"]?.decodeToString() ?: id

        // Check for AirPlay 2 support
        val supportsAirplay2 = features?.let { feat ->
            try {
                val hexValue = if (feat.startsWith("0x")) {
                    feat.substring(2).toLong(16)
                } else {
                    feat.toLong()
                }
                // Bit 48 indicates AirPlay 2
                (hexValue and 0x1000000000000L) != 0L
            } catch (e: Exception) {
                false
            }
        } ?: false

        val device = AirPlayDevice(
            id = deviceId,
            name = name,
            address = address,
            port = port,
            model = model,
            features = features,
            supports_airplay2 = supportsAirplay2,
            flags = flags
        )

        Log.i(TAG, "Adding device: $name at $address:$port (AirPlay 2: $supportsAirplay2)")

        _devices.value = _devices.value + (deviceId to device)
    }

    /**
     * Remove a device from the list
     */
    private fun removeDevice(serviceName: String) {
        val currentDevices = _devices.value.toMutableMap()
        val key = currentDevices.entries.find { it.value.name == serviceName }?.key
        if (key != null) {
            currentDevices.remove(key)
            _devices.value = currentDevices
            Log.i(TAG, "Removed device: $serviceName")
        }
    }

    /**
     * Acquire multicast lock for mDNS reception
     */
    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return

        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("AirPlayNsdDiscovery")?.apply {
                setReferenceCounted(true)
                acquire()
            }
            Log.i(TAG, "Multicast lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire multicast lock: ${e.message}")
        }
    }

    /**
     * Release multicast lock
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
     * Clean up resources
     */
    fun destroy() {
        stopDiscovery()
        releaseMulticastLock()
        nsdManager = null
    }
}
