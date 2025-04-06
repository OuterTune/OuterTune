/*
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */
package com.dd3boh.outertune.ui.screens.settings.fragments

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dd3boh.outertune.LocalSyncUtils
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.InnerTubeCookieKey
import com.dd3boh.outertune.constants.LikedAutoDownloadKey
import com.dd3boh.outertune.constants.LikedAutodownloadMode
import com.dd3boh.outertune.constants.PauseListenHistoryKey
import com.dd3boh.outertune.constants.PauseRemoteListenHistoryKey
import com.dd3boh.outertune.constants.YtmSyncKey
import com.dd3boh.outertune.ui.component.ListPreference
import com.dd3boh.outertune.ui.component.PreferenceEntry
import com.dd3boh.outertune.ui.component.SwitchPreference
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import com.zionhuang.innertube.utils.parseCookieString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.SyncFrag() {
    val context = LocalContext.current
    val syncUtils = LocalSyncUtils.current
    val coroutineScope = rememberCoroutineScope()


    val (innerTubeCookie, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val (ytmSync, onYtmSyncChange) = rememberPreference(YtmSyncKey, defaultValue = true)
    val pauseListenHistory by rememberPreference(key = PauseListenHistoryKey, defaultValue = false)
    val (pauseRemoteListenHistory, onPauseRemoteListenHistoryChange) = rememberPreference(
        key = PauseRemoteListenHistoryKey,
        defaultValue = false
    )
    val (likedAutoDownload, onLikedAutoDownload) = rememberEnumPreference(
        LikedAutoDownloadKey,
        LikedAutodownloadMode.OFF
    )

    val isSyncingRemotePlaylists by syncUtils.isSyncingRemotePlaylists.collectAsState()
    val isSyncingRemoteAlbums by syncUtils.isSyncingRemoteAlbums.collectAsState()
    val isSyncingRemoteArtists by syncUtils.isSyncingRemoteArtists.collectAsState()
    val isSyncingRemoteSongs by syncUtils.isSyncingRemoteSongs.collectAsState()
    val isSyncingRemoteLikedSongs by syncUtils.isSyncingRemoteLikedSongs.collectAsState()
    // TODO: move to home screen as button?
    // TODO: rename scanner_manual_btn to sync_manual_btn

    PreferenceEntry(
        title = { Text(stringResource(R.string.scanner_manual_btn)) },
        icon = { Icon(Icons.Rounded.Sync, null) },
        onClick = {
            Toast.makeText(context, context.getString(R.string.sync_progress_active), Toast.LENGTH_SHORT).show()
            coroutineScope.launch(Dispatchers.Main) {
                syncUtils.syncAll()
                Toast.makeText(context, context.getString(R.string.sync_progress_active), Toast.LENGTH_SHORT).show()
            }
        },
        isEnabled = isLoggedIn
    )

    SyncProgressItem(stringResource(R.string.songs), isSyncingRemoteSongs)
    SyncProgressItem(stringResource(R.string.liked_songs), isSyncingRemoteLikedSongs)
    SyncProgressItem(stringResource(R.string.artists), isSyncingRemoteArtists)
    SyncProgressItem(stringResource(R.string.albums), isSyncingRemoteAlbums)
    SyncProgressItem(stringResource(R.string.playlists), isSyncingRemotePlaylists)

    SwitchPreference(
        title = { Text(stringResource(R.string.ytm_sync)) },
        icon = { Icon(Icons.Rounded.Sync, null) },
        checked = ytmSync,
        onCheckedChange = onYtmSyncChange,
        isEnabled = isLoggedIn
    )
    SwitchPreference(
        title = { Text(stringResource(R.string.pause_remote_listen_history)) },
        icon = { Icon(Icons.Rounded.History, null) },
        checked = pauseRemoteListenHistory,
        onCheckedChange = onPauseRemoteListenHistoryChange,
        isEnabled = !pauseListenHistory && isLoggedIn
    )
    ListPreference(
        title = { Text(stringResource(R.string.like_autodownload)) },
        icon = { Icon(Icons.Rounded.Favorite, null) },
        values = listOf(LikedAutodownloadMode.OFF, LikedAutodownloadMode.ON, LikedAutodownloadMode.WIFI_ONLY),
        selectedValue = likedAutoDownload,
        valueText = {
            when (it) {
                LikedAutodownloadMode.OFF -> stringResource(androidx.compose.ui.R.string.state_off)
                LikedAutodownloadMode.ON -> stringResource(androidx.compose.ui.R.string.state_on)
                LikedAutodownloadMode.WIFI_ONLY -> stringResource(R.string.wifi_only)
            }
        },
        onValueSelected = onLikedAutoDownload,
    )
}

@Composable
fun SyncProgressItem(text: String, isSyncing: Boolean) {
    AnimatedVisibility(isSyncing) {
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
