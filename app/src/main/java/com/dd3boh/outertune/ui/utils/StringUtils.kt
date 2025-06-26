package com.dd3boh.outertune.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import com.dd3boh.outertune.R

/*
IMPORTANT: Put any string utils that **DO NOT** require composable in outertu.ne/utils/StringUtils.kt
 */

@Composable
fun getNSongsString(songCount: Int, downloadCount: Int = 0): String {
    return if (downloadCount > 0)
        "$downloadCount / " + pluralStringResource(R.plurals.n_song, songCount, songCount)
    else
        pluralStringResource(R.plurals.n_song, songCount, songCount)
}