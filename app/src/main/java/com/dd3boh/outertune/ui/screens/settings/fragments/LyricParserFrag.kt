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
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.LyricTrimKey
import com.dd3boh.outertune.constants.MultilineLrcKey
import com.dd3boh.outertune.ui.component.SwitchPreference
import com.dd3boh.outertune.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.LyricParserFrag() {
    val (multilineLrc, onMultilineLrcChange) = rememberPreference(MultilineLrcKey, defaultValue = true)
    val (lyricTrim, onLyricTrimChange) = rememberPreference(LyricTrimKey, defaultValue = false)

    // multiline lyrics
    SwitchPreference(
        title = { Text(stringResource(R.string.lyrics_multiline_title)) },
        description = stringResource(R.string.lyrics_multiline_description),
        icon = { Icon(Icons.AutoMirrored.Rounded.Sort, null) },
        checked = multilineLrc,
        onCheckedChange = onMultilineLrcChange
    )

    // trim (remove spaces around) lyrics
    SwitchPreference(
        title = { Text(stringResource(R.string.lyrics_trim_title)) },
        icon = { Icon(Icons.Rounded.ContentCut, null) },
        checked = lyricTrim,
        onCheckedChange = onLyricTrimChange
    )
}