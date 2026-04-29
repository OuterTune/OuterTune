/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.ui.player

import android.annotation.SuppressLint
import android.content.ClipData
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackException
import com.dd3boh.outertune.BuildConfig
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.DEFAULT_PLAYER_BACKGROUND
import com.dd3boh.outertune.constants.DarkMode
import com.dd3boh.outertune.constants.DarkModeKey
import com.dd3boh.outertune.constants.PlayerBackgroundStyle
import com.dd3boh.outertune.constants.PlayerBackgroundStyleKey
import com.dd3boh.outertune.ui.utils.fadingEdge
import com.dd3boh.outertune.utils.YTPlayerDebugInfo
import com.dd3boh.outertune.utils.rememberEnumPreference

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun ThumbnailPlaybackError(
    error: PlaybackException,
    retry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboard.current

    val playerBackground by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = DEFAULT_PLAYER_BACKGROUND
    )
    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
        if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
    }

    val textColor = when (playerBackground) {
        PlayerBackgroundStyle.FOLLOW_THEME -> MaterialTheme.colorScheme.secondary
        else ->
            if (useDarkTheme)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onPrimary
    }

    var showStackTrace by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .fadingEdge(vertical = 64.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(64.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "${error.message} (${error.errorCode}): ${
                    error.cause?.message ?: error.cause?.cause?.message ?: stringResource(
                        R.string.error_unknown
                    )
                }",
                color = textColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        AnimatedVisibility(!showStackTrace) {
            TextButton(
                onClick = { showStackTrace = true }
            ) {
                Text(
                    text = stringResource(R.string.tap_show_more),
                    color = textColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        AnimatedVisibility(showStackTrace) {
            val debugInfo = YTPlayerDebugInfo.format()
            val systemInfo = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) | ${BuildConfig.FLAVOR}\n" +
                    "${BuildConfig.APPLICATION_ID} | ${BuildConfig.BUILD_TYPE}\n" +
                    "${Build.BRAND} ${Build.DEVICE} (${Build.MODEL})\n" +
                    "${Build.VERSION.SDK_INT} (${Build.ID})\n\n"
            val fullText = debugInfo + "\n" + systemInfo + error.stackTraceToString()

            Text(
                text = fullText,
                color = textColor,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                val clipData = ClipData.newPlainText(
                                    "OuterTune player error",
                                    AnnotatedString(fullText)
                                )
                                clipboardManager.nativeClipboard.setPrimaryClip(clipData)
                            }
                        )
                    },
            )
        }
        Spacer(Modifier.height(64.dp))
    }
}
