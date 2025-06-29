/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 O﻿ute﻿rTu﻿ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.ui.screens.settings

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.ENABLE_UPDATE_CHECKER
import com.dd3boh.outertune.constants.LastVersionKey
import com.dd3boh.outertune.constants.TopBarInsets
import com.dd3boh.outertune.constants.UpdateAvailableKey
import com.dd3boh.outertune.ui.component.IconButton
import com.dd3boh.outertune.ui.component.PreferenceEntry
import com.dd3boh.outertune.ui.utils.Updater
import com.dd3boh.outertune.ui.utils.backToMain
import com.dd3boh.outertune.utils.compareVersion
import com.dd3boh.outertune.utils.rememberPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val SETTINGS_TAG = "Settings"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val lastVer by rememberPreference(LastVersionKey, defaultValue = "0.0.0")
    val (updateAvailable, onUpdateAvailableChange) = rememberPreference(UpdateAvailableKey, defaultValue = false)

    var newVersion by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.grp_account_sync)) },
                icon = { Icon(Icons.Rounded.AccountCircle, null) },
                onClick = { navController.navigate("settings/account_sync") }
            )
            PreferenceEntry(
                title = { Text(stringResource(R.string.grp_library_and_content)) },
                icon = { Icon(Icons.AutoMirrored.Rounded.LibraryBooks, null) },
                onClick = { navController.navigate("settings/library") }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.appearance)) },
                icon = { Icon(Icons.Rounded.Palette, null) },
                onClick = { navController.navigate("settings/appearance") }
            )
            PreferenceEntry(
                title = { Text(stringResource(R.string.grp_interface)) },
                icon = { Icon(Icons.Rounded.Interests, null) },
                onClick = { navController.navigate("settings/interface") }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.player_and_audio)) },
                icon = { Icon(Icons.Rounded.PlayArrow, null) },
                onClick = { navController.navigate("settings/player") }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.backup_restore)) },
                icon = { Icon(Icons.Rounded.Restore, null) },
                onClick = { navController.navigate("settings/backup_restore") }
            )
            PreferenceEntry(
                title = { Text(stringResource(R.string.storage)) },
                icon = { Icon(Icons.Rounded.Storage, null) },
                onClick = { navController.navigate("settings/storage") }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.experimental_settings_title)) },
                icon = { Icon(Icons.Rounded.WarningAmber, null) },
                onClick = { navController.navigate("settings/experimental") }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.about)) },
                icon = { Icon(Icons.Rounded.Info, null) },
                onClick = { navController.navigate("settings/about") }
            )

            if (ENABLE_UPDATE_CHECKER)
                PreferenceEntry(
                    title = {
                        Text(
                            text = stringResource(if (updateAvailable) R.string.new_version_available else R.string.check_for_update),
                        )
                    },
                    description = if (updateAvailable) lastVer else stringResource(R.string.no_updates_available),
                    icon = {
                        BadgedBox(
                            badge = { if (updateAvailable) Badge() }
                        ) {
                            Icon(Icons.Rounded.Update, null)
                        }
                    },
                    onClick = {
                        if (updateAvailable) {
                            uriHandler.openUri("https://github.com/OuterTune/OuterTune/releases/latest")
                        } else {
                            CoroutineScope(Dispatchers.IO).launch {
                                Updater.tryCheckUpdate(context, true)?.let {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.check_for_update),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    if (compareVersion(lastVer, it) < 0) {
                                        onUpdateAvailableChange(true)
                                        Log.d(SETTINGS_TAG, "Update available. UpdateAvailable set to true")
                                        newVersion = it
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.new_version_available),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Log.d(SETTINGS_TAG, "No new updates available")
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.no_updates_available),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.settings)) },
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
        windowInsets = TopBarInsets,
        scrollBehavior = scrollBehavior
    )
}
