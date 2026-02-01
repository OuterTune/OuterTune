/*
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.ui.dialog

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val devices by AirPlayBridge.devices.collectAsState()
    val isDiscovering by AirPlayBridge.isDiscovering.collectAsState()
    val isConnected by AirPlayBridge.isConnected.collectAsState()
    val connectedDeviceIds by AirPlayBridge.connectedDeviceIds.collectAsState()
    val connectingDeviceIds by AirPlayBridge.connectingDeviceIds.collectAsState()

    // Start discovery when dialog opens
    LaunchedEffect(Unit) {
        AirPlayBridge.initialize(context)
        AirPlayBridge.startDiscovery()
        // Periodically refresh
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
            // Select All button
            if (devices.isNotEmpty()) {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            if (connectedDeviceIds.size == devices.size) {
                                // All connected, disconnect all
                                AirPlayBridge.disconnectAll()
                            } else {
                                // Connect all
                                AirPlayBridge.connectAll()
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SelectAll,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (connectedDeviceIds.size == devices.size)
                            stringResource(R.string.airplay_deselect_all)
                        else
                            stringResource(R.string.airplay_select_all)
                    )
                }
            }

            // Disconnect All button (when connected)
            if (isConnected) {
                TextButton(
                    onClick = {
                        AirPlayBridge.disconnectAll()
                    }
                ) {
                    Text(text = stringResource(R.string.airplay_disconnect))
                }
            }

            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.done))
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
            // Connected count header
            if (connectedDeviceIds.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(
                                R.string.airplay_playing_on,
                                connectedDeviceIds.size
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(devices) { device ->
                    val isDeviceConnected = connectedDeviceIds.contains(device.id)
                    val isDeviceConnecting = connectingDeviceIds.contains(device.id)

                    AirPlayDeviceItem(
                        device = device,
                        isConnected = isDeviceConnected,
                        isConnecting = isDeviceConnecting,
                        onClick = {
                            coroutineScope.launch {
                                AirPlayBridge.toggleDevice(device)
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
    isConnecting: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isConnected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else
            MaterialTheme.colorScheme.surface,
        label = "backgroundColor"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Checkbox for multi-select
        Checkbox(
            checked = isConnected,
            onCheckedChange = { onClick() },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Speaker icon with status indicator
        Box {
            Icon(
                imageVector = Icons.Rounded.Speaker,
                contentDescription = null,
                tint = if (isConnected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )

            // Show playing indicator for connected devices
            if (isConnected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(8.dp)
                            .align(Alignment.Center)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.displayName(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isConnected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isConnected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = when {
                    isConnecting -> stringResource(R.string.airplay_connecting)
                    isConnected -> stringResource(R.string.airplay_playing)
                    device.supports_airplay2 -> stringResource(R.string.airplay_2_device)
                    else -> stringResource(R.string.airplay_device)
                },
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    isConnecting -> MaterialTheme.colorScheme.tertiary
                    isConnected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        // Status indicator on the right
        when {
            isConnecting -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            isConnected -> {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
