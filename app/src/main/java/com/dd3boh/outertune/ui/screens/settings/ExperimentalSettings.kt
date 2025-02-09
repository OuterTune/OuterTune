/*
 * Copyright (C) 2025 O​u​t​er​Tu​ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.LocalSyncUtils
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.DevSettingsKey
import com.dd3boh.outertune.constants.FirstSetupPassed
import com.dd3boh.outertune.constants.ScannerImpl
import com.dd3boh.outertune.constants.ScannerImplKey
import com.dd3boh.outertune.ui.component.IconButton
import com.dd3boh.outertune.ui.component.PreferenceEntry
import com.dd3boh.outertune.ui.component.PreferenceGroupTitle
import com.dd3boh.outertune.ui.component.SwitchPreference
import com.dd3boh.outertune.ui.utils.backToMain
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.utils.scanners.LocalMediaScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentalSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val syncUtils = LocalSyncUtils.current
    val coroutineScope = rememberCoroutineScope()

    // state variables and such
    val (devSettings, onDevSettingsChange) = rememberPreference(DevSettingsKey, defaultValue = false)
    val (firstSetupPassed, onFirstSetupPassedChange) = rememberPreference(FirstSetupPassed, defaultValue = false)

    val isSyncingRemotePlaylists by syncUtils.isSyncingRemotePlaylists.collectAsState()
    val isSyncingRemoteAlbums by syncUtils.isSyncingRemoteAlbums.collectAsState()
    val isSyncingRemoteArtists by syncUtils.isSyncingRemoteArtists.collectAsState()
    val isSyncingRemoteSongs by syncUtils.isSyncingRemoteSongs.collectAsState()
    val isSyncingRemoteLikedSongs by syncUtils.isSyncingRemoteLikedSongs.collectAsState()

    val (scannerImpl) = rememberEnumPreference(
        key = ScannerImplKey,
        defaultValue = ScannerImpl.TAGLIB
    )

    var nukeEnabled by remember {
        mutableStateOf(false)
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
    ) {

        // dev settings
        SwitchPreference(
            title = { Text(stringResource(R.string.dev_settings_title)) },
            description = stringResource(R.string.dev_settings_description),
            icon = { Icon(Icons.Rounded.DeveloperMode, null) },
            checked = devSettings,
            onCheckedChange = onDevSettingsChange
        )

        // TODO: move to home screen as button?
        PreferenceEntry(
            title = { Text(stringResource(R.string.scanner_manual_btn)) },
            icon = { Icon(Icons.Rounded.Sync, null) },
            onClick = {
                Toast.makeText(context, context.getString(R.string.sync_progress_active), Toast.LENGTH_SHORT).show()
                coroutineScope.launch(Dispatchers.Main) {
                    syncUtils.syncAll()
                    Toast.makeText(context, context.getString(R.string.sync_progress_active), Toast.LENGTH_SHORT).show()
                }
            }
        )

        SyncProgressItem(stringResource(R.string.songs), isSyncingRemoteSongs)
        SyncProgressItem(stringResource(R.string.liked_songs), isSyncingRemoteLikedSongs)
        SyncProgressItem(stringResource(R.string.artists), isSyncingRemoteArtists)
        SyncProgressItem(stringResource(R.string.albums), isSyncingRemoteAlbums)
        SyncProgressItem(stringResource(R.string.playlists), isSyncingRemotePlaylists)

        if (devSettings) {
            PreferenceGroupTitle(
                title = stringResource(R.string.settings_debug)
            )
            PreferenceEntry(
                title = { Text("DEBUG: Force local to remote artist migration NOW") },
                icon = { Icon(Icons.Rounded.Backup, null) },
                onClick = {
                    Toast.makeText(context, context.getString(R.string.scanner_ytm_link_start), Toast.LENGTH_SHORT).show()
                    coroutineScope.launch(Dispatchers.IO) {
                        val scanner = LocalMediaScanner.getScanner(context, ScannerImpl.TAGLIB)
                        Timber.tag("Settings").d("Force Migrating local artists to YTM (MANUAL TRIGGERED)")
                        scanner.localToRemoteArtist(database)
                        Toast.makeText(context, context.getString(R.string.scanner_ytm_link_success), Toast.LENGTH_SHORT).show()
                    }
                }
            )


            PreferenceEntry(
                title = { Text("Enter configurator") },
                icon = { Icon(Icons.Rounded.ConfirmationNumber, null) },
                onClick = {
                    onFirstSetupPassedChange(false)
                    runBlocking { // hax. page loads before pref updates
                        delay(500)
                    }
                    navController.navigate("setup_wizard")
                }
            )


            Spacer(Modifier.height(20.dp))
            Text("Material colours test")


            Column {
                Row(Modifier.padding(10.dp).background(MaterialTheme.colorScheme.primary)) {
                    Text("Primary", color = MaterialTheme.colorScheme.onPrimary)
                }
                Row(Modifier.padding(10.dp).background(MaterialTheme.colorScheme.secondary)) {
                    Text("Secondary", color = MaterialTheme.colorScheme.onSecondary)
                }
                Row(Modifier.padding(10.dp).background(MaterialTheme.colorScheme.tertiary)) {
                    Text("Tertiary", color = MaterialTheme.colorScheme.onTertiary)
                }
                Row(Modifier.padding(10.dp).background(MaterialTheme.colorScheme.surface)) {
                    Text("Surface", color = MaterialTheme.colorScheme.onSurface)
                }
                Row(Modifier.padding(10.dp).background(MaterialTheme.colorScheme.inverseSurface)) {
                    Text("Inverse Surface", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.padding(10.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Text("Surface Variant", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.padding(10.dp).background(MaterialTheme.colorScheme.surfaceBright)) {
                    Text("Surface Bright", color = MaterialTheme.colorScheme.onSurface)
                }
                Row(Modifier.padding(10.dp).background(MaterialTheme.colorScheme.surfaceTint)) {
                    Text("Surface Tint", color = MaterialTheme.colorScheme.onSurface)
                }
                Row(Modifier.padding(10.dp).background(MaterialTheme.colorScheme.surfaceDim)) {
                    Text("Surface Dim", color = MaterialTheme.colorScheme.onSurface)
                }
                Row(Modifier.padding(10.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Text("Surface Container Highest", color = MaterialTheme.colorScheme.onSurface)
                }
                Row(Modifier.padding(10.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Text("Surface Container High", color = MaterialTheme.colorScheme.onSurface)
                }
                Row(Modifier.padding(10.dp).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Text("Surface Container Low", color = MaterialTheme.colorScheme.onSurface)
                }
                Row(Modifier.padding(10.dp).background(MaterialTheme.colorScheme.errorContainer)) {
                    Text("Error Container", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Haptics test")

            Column {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                ) {
                    Text("LongPress")
                }
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                ) {
                    Text("TextHandleMove")
                }
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    }
                ) {
                    Text("VirtualKey")
                }
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                    }
                ) {
                    Text("GestureEnd")
                }
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    }
                ) {
                    Text("GestureThresholdActivate")
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                ) {
                    Text("SegmentTick")
                }
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                ) {
                    Text("SegmentFrequentTick")
                }
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    }
                ) {
                    Text("ContextClick")
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    }
                ) {
                    Text("Confirm")
                }
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Reject)
                    }
                ) {
                    Text("Reject")
                }

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    }
                ) {
                    Text("ToggleOn")
                }
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ToggleOff)
                    }
                ) {
                    Text("ToggleOff")
                }
            }

            // nukes
            Spacer(Modifier.height(100.dp))
            PreferenceEntry(
                title = { Text("Tap to show nuke options") },
                icon = { Icon(Icons.Rounded.ErrorOutline, null) },
                onClick = {
                    nukeEnabled = true
                }
            )

            if (nukeEnabled) {
                PreferenceEntry(
                    title = { Text("DEBUG: Nuke local lib") },
                    icon = { Icon(Icons.Rounded.ErrorOutline, null) },
                    onClick = {
                        Toast.makeText(context, "Nuking local files from database...", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch(Dispatchers.IO) {
                            Timber.tag("Settings").d("Nuke database status:  ${database.nukeLocalData()}")
                        }
                    }
                )
                PreferenceEntry(
                    title = { Text("DEBUG: Nuke local artists") },
                    icon = { Icon(Icons.Rounded.WarningAmber, null) },
                    onClick = {
                        Toast.makeText(context, "Nuking local artists from database...", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch(Dispatchers.IO) {
                            Timber.tag("Settings").d("Nuke database status:  ${database.nukeLocalArtists()}")
                        }
                    }
                )
                PreferenceEntry(
                    title = { Text("DEBUG: Nuke format entities") },
                    icon = { Icon(Icons.Rounded.WarningAmber, null) },
                    onClick = {
                        Toast.makeText(context, "Nuking format entities from database...", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch(Dispatchers.IO) {
                            Timber.tag("Settings").d("Nuke database status:  ${database.nukeFormatEntities()}")
                        }
                    }
                )
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.experimental_settings_title)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
fun SyncProgressItem(text: String, isSyncing: Boolean) {
    if (isSyncing) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(text)
        }
    }
}
