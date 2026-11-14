/*
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */
package io.github.yuuichi_s.astertune.ui.screens.settings.fragments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TextRotationAngledown
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.EnableBetterLyricsKey
import io.github.yuuichi_s.astertune.constants.EnableKugouKey
import io.github.yuuichi_s.astertune.constants.EnableLrcLibKey
import io.github.yuuichi_s.astertune.constants.EnableLyricsPrefetchKey
import io.github.yuuichi_s.astertune.constants.LyricClickable
import io.github.yuuichi_s.astertune.constants.LyricFontSizeKey
import io.github.yuuichi_s.astertune.constants.LyricKaraokeEnable
import io.github.yuuichi_s.astertune.constants.LyricSourcePrefKey
import io.github.yuuichi_s.astertune.constants.LyricTrimKey
import io.github.yuuichi_s.astertune.constants.LyricUpdateSpeed
import io.github.yuuichi_s.astertune.constants.LyricsPosition
import io.github.yuuichi_s.astertune.constants.LyricsPrefetchCountKey
import io.github.yuuichi_s.astertune.constants.LyricsTextPositionKey
import io.github.yuuichi_s.astertune.constants.MultilineLrcKey
import io.github.yuuichi_s.astertune.constants.Speed
import io.github.yuuichi_s.astertune.ui.component.EnumListPreference
import io.github.yuuichi_s.astertune.ui.component.ListPreference
import io.github.yuuichi_s.astertune.ui.component.PreferenceEntry
import io.github.yuuichi_s.astertune.ui.component.SwitchPreference
import io.github.yuuichi_s.astertune.ui.dialog.CounterDialog
import io.github.yuuichi_s.astertune.utils.rememberEnumPreference
import io.github.yuuichi_s.astertune.utils.rememberPreference

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
    val (enableBetterLyrics, onEnableBetterLyricsChange) = rememberPreference(key = EnableBetterLyricsKey, defaultValue = false)
    val (preferLocalLyric, onPreferLocalLyric) = rememberPreference(LyricSourcePrefKey, defaultValue = true)
    val (enablePrefetch, onEnablePrefetchChange) = rememberPreference(EnableLyricsPrefetchKey, defaultValue = true)
    val (prefetchCount, onPrefetchCountChange) = rememberPreference(LyricsPrefetchCountKey, defaultValue = 3)

    var showPrefetchCountDialog by remember {
        mutableStateOf(false)
    }

    SwitchPreference(
        title = { Text(stringResource(R.string.enable_betterlyrics)) },
        icon = { Icon(Icons.Rounded.Lyrics, null) },
        checked = enableBetterLyrics,
        onCheckedChange = onEnableBetterLyricsChange
    )
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
    SwitchPreference(
        title = { Text(stringResource(R.string.lyrics_prefetch_title)) },
        description = stringResource(R.string.lyrics_prefetch_description),
        icon = { Icon(Icons.Rounded.Lyrics, null) },
        checked = enablePrefetch,
        onCheckedChange = onEnablePrefetchChange
    )
    PreferenceEntry(
        title = { Text(stringResource(R.string.lyrics_prefetch_count_title)) },
        description = prefetchCount.toString(),
        icon = { Icon(Icons.Rounded.Lyrics, null) },
        isEnabled = enablePrefetch,
        onClick = { showPrefetchCountDialog = true }
    )

    if (showPrefetchCountDialog) {
        CounterDialog(
            title = stringResource(R.string.lyrics_prefetch_count_title),
            initialValue = prefetchCount,
            upperBound = 10,
            lowerBound = 1,
            unitDisplay = "",
            onDismiss = { showPrefetchCountDialog = false },
            onConfirm = {
                onPrefetchCountChange(it)
                showPrefetchCountDialog = false
            },
            onReset = { onPrefetchCountChange(3) },
            onCancel = { showPrefetchCountDialog = false }
        )
    }
}

@Composable
fun ColumnScope.LyricAdvancedFrag() {
    val (lyricUpdateSpeed, onLyricsUpdateSpeedChange) = rememberEnumPreference(LyricUpdateSpeed, Speed.MEDIUM)
    val (lyricsFancy, onLyricsFancyChange) = rememberPreference(LyricKaraokeEnable, false)
    val (syncedLyricsClickable, onSyncedLyricsClickable) = rememberPreference(LyricClickable, defaultValue = true)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        // clickable lyrics
        SwitchPreference(
            title = { Text(stringResource(R.string.lyrics_synced_clickable)) },
            icon = { Icon(Icons.Rounded.TouchApp, null) },
            checked = syncedLyricsClickable,
            onCheckedChange = onSyncedLyricsClickable
        )
    }
    Spacer(modifier = Modifier.height(16.dp))

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        SwitchPreference(
            title = { Text(stringResource(R.string.lyrics_karaoke_title)) },
            description = stringResource(R.string.lyrics_karaoke_description),
            icon = { Icon(Icons.Rounded.TextRotationAngledown, null) },
            checked = lyricsFancy,
            onCheckedChange = onLyricsFancyChange
        )

        ListPreference(
            title = { Text(stringResource(R.string.lyrics_karaoke_hz_title)) },
            icon = { Icon(Icons.Rounded.Speed, null) },
            selectedValue = lyricUpdateSpeed,
            onValueSelected = onLyricsUpdateSpeedChange,
            values = Speed.entries,
            valueText = {
                when (it) {
                    Speed.SLOW -> stringResource(R.string.speed_slow)
                    Speed.MEDIUM -> stringResource(R.string.speed_medium)
                    Speed.FAST -> stringResource(R.string.speed_fast)
                }
            },
            isEnabled = lyricsFancy
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LyricFormatFragPreview() {
    Column {
        LyricFormatFrag()
    }
}

@Preview(showBackground = true)
@Composable
private fun LyricParserFragPreview() {
    Column {
        LyricParserFrag()
    }
}

@Preview(showBackground = true)
@Composable
private fun LyricSourceFragPreview() {
    Column {
        LyricSourceFrag()
    }
}

@Preview(showBackground = true)
@Composable
private fun LyricAdvancedFragPreview() {
    Column {
        LyricAdvancedFrag()
    }
}