/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 O﻿ute﻿rTu﻿ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.ui.player

import android.annotation.SuppressLint
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.ListThumbnailSize
import com.dd3boh.outertune.constants.MiniPlayerHeight
import com.dd3boh.outertune.constants.ThumbnailCornerRadius
import com.dd3boh.outertune.extensions.togglePlayPause
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.ui.component.button.IconButton
import kotlin.math.roundToInt

@Composable
fun MiniPlayer(
    position: Long,
    duration: Long,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val playbackState by playerConnection.playbackState.collectAsState()
    val error by playerConnection.error.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()

    val px = (ListThumbnailSize.value * density.density).roundToInt()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
//            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp))
    ) {
        LinearProgressIndicator(
            progress = { (position.toFloat() / duration).coerceIn(0f, 1f) },
            drawStopIndicator = { },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.BottomCenter),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier

                .fillMaxSize(),
        ) {
            val iconButtonColor = MaterialTheme.colorScheme.onSecondaryContainer
            Box(Modifier.weight(1f)) {
                mediaMetadata?.let {
                    MiniMediaInfo(
                        mediaMetadata = it,
                        error = error,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }

            IconButton(
                onClick = {
                    if (playerConnection.player.currentMediaItem == null) {
                        playerConnection.service.queueBoard.setCurrQueue()
                        playerConnection.player.togglePlayPause()
                    } else if (playbackState == Player.STATE_ENDED) {
                        playerConnection.player.seekTo(0, 0)
                        playerConnection.player.playWhenReady = true
                    } else {
                        playerConnection.player.togglePlayPause()
                    }
                }
            ) {
                Icon(
                    imageVector = if (playbackState == Player.STATE_ENDED) Icons.Rounded.Replay else if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    tint = iconButtonColor,
                    contentDescription = null
                )
            }

            IconButton(
                enabled = canSkipNext,
                onClick = {
                    if (playerConnection.player.currentMediaItem == null) {
                        playerConnection.service.queueBoard.setCurrQueue()
                        playerConnection.player.playWhenReady = true
                    }
                    playerConnection.player.seekToNext()
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.skip_next),
                    tint = iconButtonColor.copy(alpha = (if (canSkipNext) 1f else 0.5f)),
                    contentDescription = null
                )
            }
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun MiniMediaInfo(
    mediaMetadata: MediaMetadata,
    error: PlaybackException?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val playerConnection = LocalPlayerConnection.current
    val isWaitingForNetwork by playerConnection?.waitingForNetworkConnection?.collectAsState(initial = false)
        ?: remember { mutableStateOf(false) }

    val px = (ListThumbnailSize.value * density.density).roundToInt()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .padding(6.dp)
                .size(48.dp)
        ) {
            AsyncImage(
                model = mediaMetadata.getThumbnailModel(px, px),
                contentDescription = null,
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = error != null || isWaitingForNetwork,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    Modifier
                        .background(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(ThumbnailCornerRadius)
                        )
                ) {
                    if (isWaitingForNetwork) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(24.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.Center)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
        ) {
            Text(
                text = mediaMetadata.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = mediaMetadata.artists.joinToString { it.name },
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
