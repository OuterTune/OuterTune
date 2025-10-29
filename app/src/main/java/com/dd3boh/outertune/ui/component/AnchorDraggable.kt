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
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.SNACKBAR_VERY_SHORT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Swipe to perform an action. This supports one or two actions
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeToQueueBox(
    modifier: Modifier = Modifier,
    item: MediaItem,
    swipeEnabled: Boolean,
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current

    SwipeActionBox(
        firstAction = Pair(Icons.AutoMirrored.Rounded.PlaylistPlay, {
            playerConnection?.enqueueNext(item)
            coroutineScope.launch {
                snackbarHostState?.showSnackbar(
                    message = context.getString(
                        R.string.song_added_to_queue,
                        item.mediaMetadata.title
                    ),
                    withDismissAction = true,
                    duration = SnackbarDuration.Short
                )
            }
        }),
        secondAction = Pair(Icons.AutoMirrored.Rounded.PlaylistAdd, {
            playerConnection?.enqueueEnd(item)
            coroutineScope.launch {
                val job = launch {
                    snackbarHostState?.showSnackbar(
                        message = context.getString(
                            R.string.song_added_to_queue_end,
                            item.mediaMetadata.title
                        ),
                        withDismissAction = true,
                        duration = SnackbarDuration.Indefinite
                    )
                }
                delay(SNACKBAR_VERY_SHORT)
                job.cancel()
            }
        }),
        thirdAction = Pair(Icons.AutoMirrored.Rounded.PlaylistAdd, {
            playerConnection?.enqueuePriority(item, true)
            coroutineScope.launch {
                val job = launch {
                    snackbarHostState?.showSnackbar(
                        message = "ORCODDI",
                        withDismissAction = true,
                        duration = SnackbarDuration.Indefinite
                    )
                }
                delay(SNACKBAR_VERY_SHORT)
                job.cancel()
            }
        }),
        fourthAction = Pair(Icons.AutoMirrored.Rounded.PlaylistAdd, {
            playerConnection?.enqueuePriority(item, false)
            coroutineScope.launch {
                val job = launch {
                    snackbarHostState?.showSnackbar(
                        message = "ORCODDI",
                        withDismissAction = true,
                        duration = SnackbarDuration.Indefinite
                    )
                }
                delay(SNACKBAR_VERY_SHORT)
                job.cancel()
            }
        }),
        enabled = swipeEnabled,
        modifier = modifier,
        content = content
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeActionBox(
    firstAction: Pair<ImageVector, () -> Unit>,
    secondAction: Pair<ImageVector, () -> Unit>? = null,
    thirdAction: Pair<ImageVector, () -> Unit>? = null,
    fourthAction: Pair<ImageVector, () -> Unit>? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val defaultActionSize = 150.dp
    val tightnessFactor = 200f

    val swipeOffset = remember { mutableFloatStateOf(0f) }
    val progress = remember { mutableIntStateOf(0) } // For haptic and visual feedback
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val firstThreshold = (screenWidth * 0.4f).value
    val secondThreshold = (screenWidth * 0.8f).value

    val draggableState = rememberDraggableState { delta ->
        swipeOffset.floatValue = (swipeOffset.floatValue + delta)
            .coerceIn(-screenWidth.value, screenWidth.value)
    }

    if (!enabled) {
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
                            // Swipe right → second or first
                            swipeOffset.floatValue >= secondThreshold -> {
                                (secondAction ?: firstAction).second.invoke()
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                resetDrag(coroutineScope, swipeOffset)
                            }

                            swipeOffset.floatValue >= firstThreshold -> {
                                firstAction.second.invoke()
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                resetDrag(coroutineScope, swipeOffset)
                            }

                            // Swipe left → third or fourth
                            swipeOffset.floatValue <= -secondThreshold -> {
                                (fourthAction ?: thirdAction)?.second?.invoke()
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                resetDrag(coroutineScope, swipeOffset)
                            }

                            swipeOffset.floatValue <= -firstThreshold -> {
                                thirdAction?.second?.invoke()
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                resetDrag(coroutineScope, swipeOffset)
                            }

                            else -> resetDrag(coroutineScope, swipeOffset)
                        }
                    }
                )
        ) {
            // Background for right swipe
            if (swipeOffset.floatValue > 0f) {
                handleProgressRight(swipeOffset, firstThreshold, secondThreshold, progress, haptic)

                DragActionIcon(
                    color = MaterialTheme.colorScheme.primary,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    icon = firstAction.first,
                    contentAlignment = Alignment.CenterEnd,
                    modifier = Modifier
                        .alpha(if (progress.intValue == 1) 1f else 0.6f)
                        .width(defaultActionSize)
                        .fillMaxHeight()
                        .align(Alignment.CenterStart)
                        .offset {
                            IntOffset((-screenWidth.value + swipeOffset.floatValue).roundToInt(), 0)
                        }
                )

                secondAction?.let {
                    DragActionIcon(
                        color = MaterialTheme.colorScheme.secondary,
                        tint = MaterialTheme.colorScheme.onSecondary,
                        icon = it.first,
                        contentAlignment = Alignment.CenterEnd,
                        modifier = Modifier
                            .alpha(if (progress.intValue == 2) 1f else 0.6f)
                            .width(defaultActionSize)
                            .fillMaxHeight()
                            .align(Alignment.CenterStart)
                            .offset {
                                val x = -screenWidth.value + swipeOffset.floatValue
                                val size = defaultActionSize.value
                                IntOffset(
                                    ((x - (x * x / tightnessFactor)) - (size * 0.9)
                                        .coerceIn(0.0, size.toDouble())).roundToInt(), 0
                                )
                            }
                    )
                }
            }

            // Background for left swipe
            if (swipeOffset.floatValue < 0f) {
                handleProgressLeft(swipeOffset, firstThreshold, secondThreshold, progress, haptic)

                thirdAction?.let {
                    DragActionIcon(
                        color = MaterialTheme.colorScheme.tertiary,
                        tint = MaterialTheme.colorScheme.onTertiary,
                        icon = it.first,
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .alpha(if (progress.intValue == -1) 1f else 0.6f)
                            .width(defaultActionSize)
                            .fillMaxHeight()
                            .align(Alignment.CenterEnd)
                            .offset {
                                IntOffset(
                                    (screenWidth.value + swipeOffset.floatValue)
                                        .roundToInt(),
                                    0
                                )
                            }
                    )
                }

                fourthAction?.let {
                    DragActionIcon(
                        color = MaterialTheme.colorScheme.error,
                        tint = MaterialTheme.colorScheme.onError,
                        icon = it.first,
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .alpha(if (progress.intValue == -2) 1f else 0.6f)
                            .width(defaultActionSize)
                            .fillMaxHeight()
                            .align(Alignment.CenterEnd)
                            .offset {
                                val x = screenWidth.value + swipeOffset.floatValue
                                val size = defaultActionSize.value
                                IntOffset(
                                    ((x + (x * x / tightnessFactor)) + (size * 0.9)
                                        .coerceIn(0.0, size.toDouble())).roundToInt(),
                                    0
                                )
                            }
                    )
                }
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

private fun handleProgressRight(
    swipeOffset: MutableFloatState,
    firstThreshold: Float,
    secondThreshold: Float,
    progress: MutableIntState,
    haptic: HapticFeedback
) {
    if (swipeOffset.floatValue >= firstThreshold && progress.intValue != 1 && swipeOffset.floatValue < secondThreshold) {
        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        progress.intValue = 1
    } else if (swipeOffset.floatValue >= secondThreshold && progress.intValue != 2) {
        haptic.performHapticFeedback(HapticFeedbackType.Reject)
        progress.intValue = 2
    } else if (swipeOffset.floatValue < firstThreshold) {
        progress.intValue = 0
    }
}

private fun handleProgressLeft(
    swipeOffset: MutableFloatState,
    firstThreshold: Float,
    secondThreshold: Float,
    progress: MutableIntState,
    haptic: HapticFeedback
) {
    if (swipeOffset.floatValue <= -firstThreshold && progress.intValue != -1 && swipeOffset.floatValue > -secondThreshold) {
        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        progress.intValue = -1
    } else if (swipeOffset.floatValue <= -secondThreshold && progress.intValue != -2) {
        haptic.performHapticFeedback(HapticFeedbackType.Reject)
        progress.intValue = -2
    } else if (swipeOffset.floatValue > -firstThreshold) {
        progress.intValue = 0
    }
}

private fun resetDrag(scope: CoroutineScope, offset: MutableFloatState) {
    scope.launch {
        animate(offset.floatValue, 0f) { value, _ -> offset.floatValue = value }
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
    icon: ImageVector,
    contentAlignment: Alignment
) {
    Box(
        modifier = modifier.background(color),
        contentAlignment = contentAlignment
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
