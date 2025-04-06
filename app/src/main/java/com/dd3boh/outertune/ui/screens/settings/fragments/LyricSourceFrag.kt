/*
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */
package com.dd3boh.outertune.ui.screens.settings.fragments

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.EnableKugouKey
import com.dd3boh.outertune.constants.EnableLrcLibKey
import com.dd3boh.outertune.constants.LyricSourcePrefKey
import com.dd3boh.outertune.ui.component.SwitchPreference
import com.dd3boh.outertune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.LyricSourceFrag() {

    val (enableKugou, onEnableKugouChange) = rememberPreference(key = EnableKugouKey, defaultValue = true)
    val (enableLrcLib, onEnableLrcLibChange) = rememberPreference(key = EnableLrcLibKey, defaultValue = true)
    val (preferLocalLyric, onPreferLocalLyric) = rememberPreference(LyricSourcePrefKey, defaultValue = true)

    SwitchPreference(
        title = { Text(stringResource(R.string.enable_lrclib)) },
        icon = { Icon(Icons.Rounded.Lyrics, null) },
        checked = enableLrcLib,
        onCheckedChange = onEnableLrcLibChange
    )
    SwitchPreference(
        title = { Text(stringResource(R.string.enable_kugou)) },
        icon = { Icon(Icons.Rounded.Lyrics, null) },
        checked = enableKugou,
        onCheckedChange = onEnableKugouChange
    )
    // prioritize local lyric files over all cloud providers
    SwitchPreference(
        title = { Text(stringResource(R.string.lyrics_prefer_local)) },
        description = stringResource(R.string.lyrics_prefer_local_description),
        icon = { Icon(Icons.Rounded.ContentCut, null) },
        checked = preferLocalLyric,
        onCheckedChange = onPreferLocalLyric
    )
}