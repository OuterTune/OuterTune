package com.dd3boh.outertune.ui.component

import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
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
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class DragAnchors {
    Start,
    Center,
    End,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DraggableItem(
    state: AnchoredDraggableState<DragAnchors>,
    content: @Composable BoxScope.() -> Unit,
    startAction: @Composable (BoxScope.() -> Unit)? = {},
    endAction: @Composable (BoxScope.() -> Unit)? = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RectangleShape)
    ) {

        endAction?.let {
            endAction()
        }

        startAction?.let {
            startAction()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterStart)
                .offset {
                    IntOffset(
                        x = -state
                            .requireOffset()
                            .roundToInt(),
                        y = 0,
                    )
                }
                .anchoredDraggable(
                    state = state,
                    orientation = Orientation.Horizontal,
                    reverseDirection = true
                ),
            content = content
        )

        LaunchedEffect(state.isAnimationRunning) {
            if (state.isAnimationRunning)
                state.animateTo(DragAnchors.Center)
        }
    }
}


@Composable
fun AddToQueueAction(modifier: Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                modifier = Modifier
                    .padding(vertical = 5.dp)
                    .padding(horizontal = 20.dp)
                    .size(50.dp),
                imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeToQueueBox(
    item: MediaItem,
    content: @Composable BoxScope.() -> Unit,
    snackbarHostState: SnackbarHostState,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val coroutineScope = rememberCoroutineScope()

    val defaultActionSize = 150.dp

    val density = LocalDensity.current
    var addedToQueue = false

    val decayAnimationSpec: DecayAnimationSpec<Float> = exponentialDecay(
        frictionMultiplier = 0.8f,
        absVelocityThreshold = 1f
    )
    
    val slowSnapAnimationSpec = tween<Float>(
        durationMillis = 500
    )

    val state = remember {
        AnchoredDraggableState(
            initialValue = DragAnchors.Center,
            anchors = DraggableAnchors {
                DragAnchors.Start at -with(density) { 200.dp.toPx() }
                DragAnchors.Center at 0f
            },
            positionalThreshold = { distance -> distance * 0.5f },
            velocityThreshold = { with(density) { 5000.dp.toPx() } }, 
            snapAnimationSpec = slowSnapAnimationSpec,
            decayAnimationSpec = decayAnimationSpec,
            confirmValueChange = { dragValue ->
                if (dragValue == DragAnchors.Start && !addedToQueue) {
                    addedToQueue = true
                    playerConnection?.enqueueNext(item)

                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = context.getString(
                                R.string.song_added_to_queue,
                                item.mediaMetadata.title
                            ),
                            duration = SnackbarDuration.Short
                        )
                    }
                } else if (dragValue == DragAnchors.Center && addedToQueue) {
                    addedToQueue = false
                }
                true
            }
        )
    }

    if (!enabled) {
        Box { content() }
    } 
    else {
         LaunchedEffect(state.currentValue) {
            if (state.currentValue == DragAnchors.Start && !state.isAnimationRunning) {
                state.animateTo(DragAnchors.Center)
            }
        }
        DraggableItem(
            state = state,
            content = {
                content()
            },

            startAction = {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .align(Alignment.CenterStart),
                ) {
                    AddToQueueAction(
                        Modifier
                            .width(defaultActionSize)
                            .fillMaxHeight()
                            .offset {
                                IntOffset(
                                    ((-state
                                        .requireOffset() - defaultActionSize.toPx()))
                                        .roundToInt(), 0
                                )
                            }
                    )
                }
            }
        )
    }
}