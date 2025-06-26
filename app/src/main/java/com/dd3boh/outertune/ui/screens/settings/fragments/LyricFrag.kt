/*
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
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
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.EnableKugouKey
import com.dd3boh.outertune.constants.EnableLrcLibKey
import com.dd3boh.outertune.constants.LyricFontSizeKey
import com.dd3boh.outertune.constants.LyricSourcePrefKey
import com.dd3boh.outertune.constants.LyricTrimKey
import com.dd3boh.outertune.constants.LyricsPosition
import com.dd3boh.outertune.constants.LyricsTextPositionKey
import com.dd3boh.outertune.constants.MultilineLrcKey
import com.dd3boh.outertune.ui.component.CounterDialog
import com.dd3boh.outertune.ui.component.EnumListPreference
import com.dd3boh.outertune.ui.component.PreferenceEntry
import com.dd3boh.outertune.ui.component.SwitchPreference
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference

@Composable
fun ColumnScope.LyricFormatFrag() {
    val (lyricsPosition, onLyricsPositionChange) = rememberEnumPreference(
        LyricsTextPositionKey,
        defaultValue = LyricsPosition.CENTER
    )

    val (lyricFontSize, onLyricFontSizeChange) = rememberPreference(LyricFontSizeKey, defaultValue = 20)

    var showFontSizeDialog by remember {
        mutableStateOf(false)
    }

    EnumListPreference(
        title = { Text(stringResource(R.string.lyrics_text_position)) },
        icon = { Icon(Icons.Rounded.Lyrics, null) },
        selectedValue = lyricsPosition,
        onValueSelected = onLyricsPositionChange,
        valueText = {
            when (it) {
                LyricsPosition.LEFT -> stringResource(R.string.left)
                LyricsPosition.CENTER -> stringResource(R.string.center)
                LyricsPosition.RIGHT -> stringResource(R.string.right)
            }
        }
    )
    PreferenceEntry(
        title = { Text(stringResource(R.string.lyrics_font_Size)) },
        description = "$lyricFontSize sp",
        icon = { Icon(Icons.Rounded.TextFields, null) },
        onClick = { showFontSizeDialog = true }
    )


    /**
     * ---------------------------
     * Dialogs
     * ---------------------------
     */


    if (showFontSizeDialog) {
        CounterDialog(
            title = stringResource(R.string.lyrics_font_Size),
            initialValue = lyricFontSize,
            upperBound = 32,
            lowerBound = 8,
            unitDisplay = " pt",
            onDismiss = { showFontSizeDialog = false },
            onConfirm = {
                onLyricFontSizeChange(it)
                showFontSizeDialog = false
            },
            onReset = { onLyricFontSizeChange(20) },
            onCancel = { showFontSizeDialog = false }
        )
    }
}


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