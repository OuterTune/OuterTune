/*
 * Copyright (C) 2025 O﻿ute﻿rTu﻿ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.ui.component

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.SwipeToQueueKey
import com.dd3boh.outertune.utils.rememberPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeToQueueBox(
    modifier: Modifier = Modifier,
    item: MediaItem,
    content: @Composable BoxScope.() -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current
    val coroutineScope = rememberCoroutineScope()

    val defaultActionSize = 150.dp

    val swipeOffset = remember { mutableFloatStateOf(0f) }
    var progress = remember { mutableIntStateOf(0) } // swipeOffset but to track haptics and opacity
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val firstThreshold = (screenWidth * 0.4f).value
    val secondThreshold = (screenWidth * 0.8f).value
    val draggableState = rememberDraggableState { delta ->
        swipeOffset.floatValue = (swipeOffset.floatValue + delta).coerceIn(0f, screenWidth.value)
    }

    val swipeToQueueEnabled by rememberPreference(SwipeToQueueKey, true)

    if (!enabled || !swipeToQueueEnabled) {
        Box { content() }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = draggableState,
                    onDragStopped = {
                        when {
                            swipeOffset.floatValue >= secondThreshold -> {
                                playerConnection?.enqueueEnd(item)

                                coroutineScope.launch {
                                    snackbarHostState?.showSnackbar(
                                        message = context.getString(
                                            R.string.song_added_to_queue_end,
                                            item.mediaMetadata.title
                                        ),
                                        duration = SnackbarDuration.Short
                                    )
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                resetDrag(coroutineScope, swipeOffset)
                            }

                            swipeOffset.floatValue >= firstThreshold -> {
                                playerConnection?.enqueueNext(item)

                                coroutineScope.launch {
                                    snackbarHostState?.showSnackbar(
                                        message = context.getString(
                                            R.string.song_added_to_queue,
                                            item.mediaMetadata.title
                                        ),
                                        duration = SnackbarDuration.Short
                                    )
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                resetDrag(coroutineScope, swipeOffset)
                            }

                            else -> resetDrag(coroutineScope, swipeOffset)
                        }
                    }
                )
        ) {
            // Background for the swipe actions
            if (swipeOffset.floatValue >= firstThreshold) {
                if (progress.intValue != 1) {
                    if (swipeOffset.floatValue < secondThreshold) {
                        haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        progress.intValue = 1
                    }
                }
            }
            if (swipeOffset.floatValue > 0f) {
                if (swipeOffset.floatValue >= secondThreshold) {
                    if (progress.intValue < 2) {
                        haptic.performHapticFeedback(HapticFeedbackType.Reject)
                    }
                    progress.intValue = 2
                }
                if (swipeOffset.floatValue < firstThreshold) {
                    progress.intValue = 0
                }


                DragActionIcon(
                    color = MaterialTheme.colorScheme.primary,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    modifier = Modifier
                        .alpha(if (progress.intValue == 1) 1f else 0.6f)
                        .width(defaultActionSize)
                        .fillMaxHeight()
                        .align(Alignment.CenterStart)
                        .offset {
                            IntOffset((-screenWidth.value + swipeOffset.floatValue).roundToInt(), 0)
                        }
                )

                DragActionIcon(
                    color = MaterialTheme.colorScheme.secondary,
                    tint = MaterialTheme.colorScheme.onSecondary,
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    modifier = Modifier
                        .alpha(if (progress.intValue == 2) 1f else 0.6f)
                        .width(defaultActionSize)
                        .fillMaxHeight()
                        .align(Alignment.CenterStart)
                        .offset {
                            IntOffset(
                                (-screenWidth.value - (defaultActionSize.value * 6f) + (swipeOffset.floatValue * 3.0f))
                                    .roundToInt(), 0
                            )
                        }
                )
            }

            // Foreground draggable content
            Box(
                modifier = Modifier
                    .offset { IntOffset(swipeOffset.floatValue.roundToInt(), 0) }
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
                content = content
            )
        }
    }
}

private fun resetDrag(scope: CoroutineScope, offset: MutableState<Float>) {
    scope.launch {
        animate(
            initialValue = offset.value,
            targetValue = 0f,
            animationSpec = tween<Float>(durationMillis = 500) // slowSnapAnimationSpec
        ) { value, _ ->
            offset.value = value
        }
    }
}


@Composable
fun DragActionIcon(
    modifier: Modifier,
    color: Color,
    tint: Color,
    icon: ImageVector
) {
    Box(
        modifier = modifier.background(color),
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.End,
        ) {
            Icon(
                modifier = Modifier
                    .padding(vertical = 5.dp)
                    .padding(horizontal = 10.dp)
                    .size(50.dp),
                imageVector = icon,
                contentDescription = null,
                tint = tint
            )
        }
    }
}
