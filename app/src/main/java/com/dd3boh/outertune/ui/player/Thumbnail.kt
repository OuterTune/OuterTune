package com.dd3boh.outertune.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.constants.PlayerHorizontalPadding
import com.dd3boh.outertune.constants.ShowLyricsKey
import com.dd3boh.outertune.constants.ThumbnailCornerRadius
import com.dd3boh.outertune.ui.component.AsyncLocalImage
import com.dd3boh.outertune.ui.component.Lyrics
import com.dd3boh.outertune.ui.utils.getLocalThumbnail
import com.dd3boh.outertune.utils.rememberPreference

@Composable
fun Thumbnail(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
    showLyricsOnClick: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val currentView = LocalView.current
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val error by playerConnection.error.collectAsState()

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
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = PlayerHorizontalPadding)
            ) {
                if (mediaMetadata?.isLocal == true) {
                    // local thumbnail arts
                    mediaMetadata?.let { // required to re render when song changes
                        AsyncLocalImage(
                            image = { getLocalThumbnail(it.localPath) },
                            contentDescription = null,
                            modifier = Modifier
                                .weight(1f, false)
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
                        modifier = Modifier
                            .weight(1f, false)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(ThumbnailCornerRadius * 2))
                            .clickable(enabled = showLyricsOnClick) { showLyrics = !showLyrics }
                    )
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
