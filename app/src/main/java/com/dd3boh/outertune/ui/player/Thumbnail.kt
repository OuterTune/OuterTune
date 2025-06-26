/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.constants.PlayerHorizontalPadding
import com.dd3boh.outertune.constants.ShowLyricsKey
import com.dd3boh.outertune.constants.ThumbnailCornerRadius
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.ui.component.AsyncImageLocal
import com.dd3boh.outertune.ui.component.Lyrics
import com.dd3boh.outertune.ui.utils.imageCache
import com.dd3boh.outertune.utils.rememberPreference

@Composable
fun Thumbnail(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
    showLyricsOnClick: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit,
    customMediaMetadata: MediaMetadata? = null
) {
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val currentView = LocalView.current
    val playerMediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val error by playerConnection.error.collectAsState()

    val mediaMetadata = customMediaMetadata ?: playerMediaMetadata

    var showLyrics by rememberPreference(ShowLyricsKey, defaultValue = false)

    DisposableEffect(showLyrics) {
        currentView.keepScreenOn = showLyrics
        onDispose {
            currentView.keepScreenOn = false
        }
    }

    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = !showLyrics && error == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            var isRectangularImage by remember { mutableStateOf(false) }

            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = PlayerHorizontalPadding)
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f, false)
                ) {
                    if (mediaMetadata?.isLocal == true) {
                        // local thumbnail arts
                        mediaMetadata.let { // required to re render when song changes
                            AsyncImageLocal(
                                image = { imageCache.getLocalThumbnail(it.localPath, false) },
                                contentDescription = null,
                                contentScale = contentScale,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(ThumbnailCornerRadius * 2))
                                    .aspectRatio(ratio = 1f)
                                    .clickable(enabled = showLyricsOnClick) {
                                        showLyrics = !showLyrics
                                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    }
                            )
                        }
                    } else {
                        // YTM thumbnail arts
                        AsyncImage(
                            model = mediaMetadata?.thumbnailUrl,
                            contentDescription = null,
                            contentScale = contentScale,
                            onSuccess = { success ->
                                val width = success.result.drawable.intrinsicWidth
                                val height = success.result.drawable.intrinsicHeight

                                isRectangularImage = width.toFloat() / height != 1f
                            },
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(ThumbnailCornerRadius * 2))
                                .clickable(enabled = showLyricsOnClick) { showLyrics = !showLyrics }
                        )
                    }

                    if (isRectangularImage) {
                        val radial = Brush.radialGradient(
                            0.0f to Color.Black.copy(alpha = 0.5f),
                            0.8f to Color.Black.copy(alpha = 0.05f),
                            1.0f to Color.Transparent,
                        )

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(maxHeight / 8)
                                .offset(x = -maxHeight / 75)
                                .background(brush = radial, shape = CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.OndemandVideo,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showLyrics && error == null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Lyrics(sliderPositionProvider = sliderPositionProvider)
        }

        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .padding(32.dp)
                .align(Alignment.Center)
                .fillMaxSize()
        ) {
            error?.let { error ->
                PlaybackError(
                    error = error,
                    retry = playerConnection.player::prepare
                )
            }
        }
    }
}
