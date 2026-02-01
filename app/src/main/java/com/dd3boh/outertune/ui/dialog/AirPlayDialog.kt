/*
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.ui.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dd3boh.outertune.R
import com.dd3boh.outertune.playback.AirPlayBridge
import com.dd3boh.outertune.playback.AirPlayDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirPlayDialog(
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val devices by AirPlayBridge.devices.collectAsState()
    val isDiscovering by AirPlayBridge.isDiscovering.collectAsState()
    val isConnected by AirPlayBridge.isConnected.collectAsState()
    val connectedDevice by AirPlayBridge.connectedDevice.collectAsState()

    // Start discovery when dialog opens
    LaunchedEffect(Unit) {
        AirPlayBridge.startDiscovery()
        // Periodically refresh devices list
        while (true) {
            AirPlayBridge.refreshDevices()
            delay(2000)
        }
    }

    // Stop discovery when dialog closes
    DisposableEffect(Unit) {
        onDispose {
            AirPlayBridge.stopDiscovery()
        }
    }

    DefaultDialog(
        onDismiss = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Cast,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stringResource(R.string.airplay_title))
                Spacer(modifier = Modifier.width(8.dp))
                if (isDiscovering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        },
        buttons = {
            if (isConnected) {
                TextButton(
                    onClick = {
                        AirPlayBridge.disconnect()
                    }
                ) {
                    Text(text = stringResource(R.string.airplay_disconnect))
                }
            }
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    ) {
        if (!AirPlayBridge.isAvailable()) {
            Text(
                text = stringResource(R.string.airplay_not_available),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        } else if (devices.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.airplay_searching),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.size(16.dp))
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            AirPlayBridge.stopDiscovery()
                            AirPlayBridge.startDiscovery()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.airplay_refresh)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(devices) { device ->
                    AirPlayDeviceItem(
                        device = device,
                        isConnected = connectedDevice?.id == device.id,
                        onClick = {
                            coroutineScope.launch {
                                if (connectedDevice?.id == device.id) {
                                    AirPlayBridge.disconnect()
                                } else {
                                    AirPlayBridge.connect(device)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AirPlayDeviceItem(
    device: AirPlayDevice,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Speaker,
            contentDescription = null,
            tint = if (isConnected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.displayName(),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isConnected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (device.supports_airplay2)
                    stringResource(R.string.airplay_2_device)
                else
                    stringResource(R.string.airplay_device),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isConnected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
