/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.ui.screens.settings

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dd3boh.outertune.App.Companion.forgetAccount
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.LocalSyncUtils
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.AccountChannelHandleKey
import com.dd3boh.outertune.constants.AccountEmailKey
import com.dd3boh.outertune.constants.AccountNameKey
import com.dd3boh.outertune.constants.DataSyncIdKey
import com.dd3boh.outertune.constants.InnerTubeCookieKey
import com.dd3boh.outertune.constants.LikedAutoDownloadKey
import com.dd3boh.outertune.constants.LikedAutodownloadMode
import com.dd3boh.outertune.constants.PauseListenHistoryKey
import com.dd3boh.outertune.constants.PauseRemoteListenHistoryKey
import com.dd3boh.outertune.constants.SettingsTopBarHeight
import com.dd3boh.outertune.constants.UseLoginForBrowse
import com.dd3boh.outertune.constants.VisitorDataKey
import com.dd3boh.outertune.constants.YtmSyncKey
import com.dd3boh.outertune.ui.component.IconButton
import com.dd3boh.outertune.ui.component.InfoLabel
import com.dd3boh.outertune.ui.component.ListPreference
import com.dd3boh.outertune.ui.component.PreferenceEntry
import com.dd3boh.outertune.ui.component.PreferenceGroupTitle
import com.dd3boh.outertune.ui.component.SwitchPreference
import com.dd3boh.outertune.ui.component.TextFieldDialog
import com.dd3boh.outertune.ui.utils.backToMain
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.utils.parseCookieString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@SuppressLint("PrivateResource")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSyncSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val syncUtils = LocalSyncUtils.current
    val coroutineScope = rememberCoroutineScope()

    val (accountName, onAccountNameChange) = rememberPreference(AccountNameKey, "")
    val (accountEmail, onAccountEmailChange) = rememberPreference(AccountEmailKey, "")
    val (accountChannelHandle, onAccountChannelHandleChange) = rememberPreference(AccountChannelHandleKey, "")
    val (innerTubeCookie, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")
    val (visitorData, onVisitorDataChange) = rememberPreference(VisitorDataKey, "")
    val (dataSyncId, onDataSyncIdChange) = rememberPreference(DataSyncIdKey, "")
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

    val (useLoginForBrowse, onUseLoginForBrowseChange) = rememberPreference(UseLoginForBrowse, true)

    // temp vars
    var showToken: Boolean by remember {
        mutableStateOf(false)
    }

    var showTokenEditor by remember {
        mutableStateOf(false)
    }

    val isSyncingRemotePlaylists by syncUtils.isSyncingRemotePlaylists.collectAsState()
    val isSyncingRemoteAlbums by syncUtils.isSyncingRemoteAlbums.collectAsState()
    val isSyncingRemoteArtists by syncUtils.isSyncingRemoteArtists.collectAsState()
    val isSyncingRemoteSongs by syncUtils.isSyncingRemoteSongs.collectAsState()
    val isSyncingRemoteLikedSongs by syncUtils.isSyncingRemoteLikedSongs.collectAsState()

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(SettingsTopBarHeight))
        PreferenceGroupTitle(
            title = stringResource(R.string.account)
        )
        PreferenceEntry(
            title = { Text(if (isLoggedIn) accountName else stringResource(R.string.login)) },
            description = if (isLoggedIn) {
                accountEmail.takeIf { it.isNotEmpty() }
                    ?: accountChannelHandle.takeIf { it.isNotEmpty() }
            } else null,
            icon = { Icon(Icons.Rounded.Person, null) },
            onClick = { navController.navigate("login") }
        )
        if (isLoggedIn) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.logout)) },
                icon = { Icon(Icons.AutoMirrored.Rounded.Logout, null) },
                onClick = {
                    forgetAccount(context)
                }
            )
        }

        PreferenceEntry(
            title = {
                if (showToken) {
                    Text(stringResource(R.string.token_shown))
                    Text(
                        text = if (isLoggedIn) innerTubeCookie else stringResource(R.string.not_logged_in),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Light,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1 // just give a preview so user knows it's at least there
                    )
                } else {
                    Text(stringResource(R.string.token_hidden))
                }
            },
            onClick = {
                if (showToken == false) {
                    showToken = true
                } else {
                    showTokenEditor = true
                }
            },
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.use_login_for_browse)) },
            description = stringResource(R.string.use_login_for_browse_desc),
            icon = { Icon(Icons.Rounded.Person, null) },
            checked = useLoginForBrowse,
            onCheckedChange = {
                YouTube.useLoginForBrowse = it
                onUseLoginForBrowseChange(it)
            }
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.sync)
        )
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


    /**
     * ---------------------------
     * Dialogs
     * ---------------------------
     */


    if (showTokenEditor) {
        val text =
            "***INNERTUBE COOKIE*** =${innerTubeCookie}\n\n***VISITOR DATA*** =${visitorData}\n\n***DATASYNC ID*** =${dataSyncId}\n\n***ACCOUNT NAME*** =${accountName}\n\n***ACCOUNT EMAIL*** =${accountEmail}\n\n***ACCOUNT CHANNEL HANDLE*** =${accountChannelHandle}"
        TextFieldDialog(
            modifier = Modifier,
            initialTextFieldValue = TextFieldValue(text),
            onDone = { data ->
                data.split("\n").forEach {
                    if (it.startsWith("***INNERTUBE COOKIE*** =")) {
                        onInnerTubeCookieChange(it.substringAfter("***INNERTUBE COOKIE*** ="))
                    } else if (it.startsWith("***VISITOR DATA*** =")) {
                        onVisitorDataChange(it.substringAfter("***VISITOR DATA*** ="))
                    } else if (it.startsWith("***DATASYNC ID*** =")) {
                        onDataSyncIdChange(it.substringAfter("***DATASYNC ID*** ="))
                    } else if (it.startsWith("***ACCOUNT NAME*** =")) {
                        onAccountNameChange(it.substringAfter("***ACCOUNT NAME*** ="))
                    } else if (it.startsWith("***ACCOUNT EMAIL*** =")) {
                        onAccountEmailChange(it.substringAfter("***ACCOUNT EMAIL*** ="))
                    } else if (it.startsWith("***ACCOUNT CHANNEL HANDLE*** =")) {
                        onAccountChannelHandleChange(it.substringAfter("***ACCOUNT CHANNEL HANDLE*** ="))
                    }
                }
            },
            onDismiss = { showTokenEditor = false },
            singleLine = false,
            maxLines = 20,
            isInputValid = {
                it.isNotEmpty() &&
                        try {
                            "SAPISID" in parseCookieString(it)
                            true
                        } catch (e: Exception) {
                            false
                        }
            },
            extraContent = {
                InfoLabel(text = stringResource(R.string.token_adv_login_description))
            }
        )
    }

    TopAppBar(
        title = { Text("Account & Sync") },
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
