/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 O﻿ute﻿rTu﻿ne Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import io.github.yuuichi_s.astertune.LocalSnackbarHostState
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.SettingsGroupSpacing
import io.github.yuuichi_s.astertune.ui.component.AccountAvatar
import io.github.yuuichi_s.astertune.ui.component.PreferenceEntry

val SETTINGS_TAG = "Settings"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val uriHandler = LocalUriHandler.current

    SettingsScaffold(
        title = stringResource(R.string.settings),
        navController = navController,
        scrollBehavior = scrollBehavior,
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.grp_account_sync)) },
                icon = { AccountAvatar { Icon(Icons.Rounded.AccountCircle, null) } },
                onClick = { navController.navigate("settings/account_sync") }
            )
        }
        Spacer(modifier = Modifier.height(SettingsGroupSpacing))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.grp_appearance_controls)) },
                icon = { Icon(Icons.Rounded.Palette, null) },
                onClick = { navController.navigate("settings/appearance") }
            )
            PreferenceEntry(
                title = { Text(stringResource(R.string.player_and_audio)) },
                icon = { Icon(Icons.Rounded.PlayArrow, null) },
                onClick = { navController.navigate("settings/player") }
            )
        }
        Spacer(modifier = Modifier.height(SettingsGroupSpacing))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.grp_library_and_content)) },
                icon = { Icon(Icons.AutoMirrored.Rounded.LibraryBooks, null) },
                onClick = { navController.navigate("settings/library") }
            )
            PreferenceEntry(
                title = { Text(stringResource(R.string.local_player_settings_title)) },
                icon = { Icon(Icons.Rounded.SdCard, null) },
                onClick = { navController.navigate("settings/local") }
            )
            PreferenceEntry(
                title = { Text(stringResource(R.string.lyrics_settings_title)) },
                icon = { Icon(Icons.Rounded.Lyrics, null) },
                onClick = { navController.navigate("settings/lyrics") }
            )
            PreferenceEntry(
                title = { Text(stringResource(R.string.privacy)) },
                icon = { Icon(Icons.Rounded.PrivacyTip, null) },
                onClick = { navController.navigate("settings/privacy") }
            )
        }
        Spacer(modifier = Modifier.height(SettingsGroupSpacing))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.storage)) },
                icon = { Icon(Icons.Rounded.Storage, null) },
                onClick = { navController.navigate("settings/storage") }
            )
            PreferenceEntry(
                title = { Text(stringResource(R.string.backup_restore)) },
                icon = { Icon(Icons.Rounded.Restore, null) },
                onClick = { navController.navigate("settings/backup_restore") }
            )
        }
        Spacer(modifier = Modifier.height(SettingsGroupSpacing))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.experimental_settings_title)) },
                icon = { Icon(Icons.Rounded.WarningAmber, null) },
                onClick = { navController.navigate("settings/experimental") }
            )
        }
        Spacer(modifier = Modifier.height(SettingsGroupSpacing))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.about)) },
                icon = { Icon(Icons.Rounded.Info, null) },
                onClick = { navController.navigate("settings/about") }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    SettingsPreview {
        CompositionLocalProvider(
            LocalSnackbarHostState provides remember { SnackbarHostState() },
        ) {
            SettingsScreen(
                navController = rememberNavController(),
                scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            )
        }
    }
}
