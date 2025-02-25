/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 O⁠ute⁠rTu⁠ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.ui.player

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachReversed
import androidx.media3.common.C
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Timeline
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.constants.ListItemHeight
import com.dd3boh.outertune.constants.LockQueueKey
import com.dd3boh.outertune.constants.PlayerHorizontalPadding
import com.dd3boh.outertune.extensions.metadata
import com.dd3boh.outertune.extensions.move
import com.dd3boh.outertune.extensions.togglePlayPause
import com.dd3boh.outertune.extensions.toggleRepeatMode
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.models.MultiQueueObject
import com.dd3boh.outertune.models.isShuffleEnabled
import com.dd3boh.outertune.playback.PlayerConnection.Companion.queueBoard
import com.dd3boh.outertune.R
import com.dd3boh.outertune.ui.component.BottomSheet
import com.dd3boh.outertune.ui.component.BottomSheetState
import com.dd3boh.outertune.ui.component.LocalMenuState
import com.dd3boh.outertune.ui.component.MediaMetadataListItem
import com.dd3boh.outertune.ui.component.ResizableIconButton
import com.dd3boh.outertune.ui.component.SelectHeader
import com.dd3boh.outertune.ui.menu.PlayerMenu
import com.dd3boh.outertune.ui.menu.QueueMenu
import com.dd3boh.outertune.utils.makeTimeString
import com.dd3boh.outertune.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@SuppressLint("UnrememberedMutableState")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun Queue(
    state: BottomSheetState,
    onTerminate: () -> Unit,
    playerBottomSheetState: BottomSheetState,
    onBackgroundColor: Color,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    var lockQueue by rememberPreference(LockQueueKey, defaultValue = false)

    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current

    // player vars
    val playerConnection = LocalPlayerConnection.current ?: return

    val shuffleModeEnabled by isShuffleEnabled.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    val playbackState by playerConnection.playbackState.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()

    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()

    // multiselect vars
    var inSelectMode by remember {
        mutableStateOf(false)
    }
    val selectedItems = remember { mutableStateListOf<Int>() }
    val onExitSelectionMode = {
        inSelectMode = false
        selectedItems.clear()
    }

    if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    BottomSheet(
        state = state,
        backgroundColor = MaterialTheme.colorScheme.surfaceColorAtElevation(NavigationBarDefaults.Elevation),
        modifier = modifier,
        collapsedContent = {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.systemBars
                            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                    )
            ) {
                IconButton(onClick = {
                    state.expandSoft()
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                }) {
                    Icon(
                        imageVector = Icons.Rounded.ExpandLess,
                        tint = onBackgroundColor,
                        contentDescription = null,
                    )
                }
            }
        },
    ) {
        val coroutineScope = rememberCoroutineScope()
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

        // current queue vars
        val queueTitle by playerConnection.queueTitle.collectAsState()
        val queueWindows by playerConnection.queueWindows.collectAsState()
        val mutableQueueWindows = remember { mutableStateListOf<Timeline.Window>() }
        val queueLength = remember(queueWindows) {
            queueWindows.sumOf { it.mediaItem.metadata!!.duration }
        }

        // multi queue vars
        var multiqueueExpand by remember { mutableStateOf(false) }
        val mutableQueues = remember { mutableStateListOf<MultiQueueObject>() }
        var playingQueue by remember { mutableIntStateOf(queueBoard.getMasterIndex()) }
        var detachedHead by remember { mutableStateOf(queueBoard.detachedHead) }
        val detachedQueue = remember { mutableStateListOf<MediaMetadata>() }
        var detachedQueueIndex by remember { mutableIntStateOf(-1) }
        var detachedQueuePos by remember { mutableIntStateOf(-1) }
        var detachedQueueTitle by remember { mutableStateOf("") }
        val detachedQueueListState = rememberLazyListState()

        // for main songs list
        val lazySongsListState = rememberLazyListState()
        var dragInfo by remember {
            mutableStateOf<Pair<Int, Int>?>(null)
        }
        val reorderableState = rememberReorderableLazyListState(
            lazyListState = lazySongsListState,
            scrollThresholdPadding = WindowInsets.systemBars.add(
                WindowInsets(
                    top = ListItemHeight,
                    bottom = ListItemHeight
                )
            ).asPaddingValues()
        ) { from, to ->
            val currentDragInfo = dragInfo
            dragInfo = if (currentDragInfo == null) {
                from.index to to.index
            } else {
                currentDragInfo.first to to.index
            }
            mutableQueueWindows.move(from.index, to.index)
        }
        LaunchedEffect(reorderableState.isAnyItemDragging) {
            if (!reorderableState.isAnyItemDragging) {
                dragInfo?.let { (from, to) ->
                    if (from == to) {
                        return@LaunchedEffect
                    }

                    queueBoard.moveSong(
                        from,
                        to,
                        playerConnection.player.currentMediaItemIndex,
                        playerConnection.service
                    )
                    playerConnection.player.moveMediaItem(from, to)
                    dragInfo = null
                }
            }
        }

        /**
         * This reloads the queue in the UI. This exists because for some stupid reason reassigning a remember-ed var
         * does not trigger LaunchedEffect, even though that is the point of remember.
         */
        fun updateQueues() {
            if (detachedHead) {
                coroutineScope.launch {
                    delay(300) // needed for scrolling to queue when switching to new queue
                    detachedQueueListState.animateScrollToItem(detachedQueuePos)
                }
                return
            }

            mutableQueues.apply {
                clear()
                addAll(queueBoard.getAllQueues())
            }
            playingQueue = queueBoard.getMasterIndex()
            coroutineScope.launch {
                delay(300) // needed for scrolling to queue when switching to new queue
                lazySongsListState.animateScrollToItem(playerConnection.player.currentMediaItemIndex)
            }
        }

        // for multiqueue
        val lazyQueuesListState = rememberLazyListState()
        var dragInfoEx by remember {
            mutableStateOf<Pair<Int, Int>?>(null)
        }
        val reorderableStateEx = rememberReorderableLazyListState(
            lazyListState = lazyQueuesListState,
            scrollThresholdPadding = WindowInsets.systemBars.add(
                WindowInsets(
                    top = ListItemHeight,
                    bottom = ListItemHeight
                )
            ).asPaddingValues()
        ) { from, to ->
            val currentDragInfo = dragInfoEx
            dragInfoEx = if (currentDragInfo == null) {
                from.index to to.index
            } else {
                currentDragInfo.first to to.index
            }
            mutableQueues.move(from.index, to.index)
        }
        LaunchedEffect(reorderableStateEx.isAnyItemDragging) {
            if (!reorderableStateEx.isAnyItemDragging) {
                dragInfoEx?.let { (from, to) ->
                    queueBoard.move(from, to, playerConnection.service)
                coroutineScope.launch {
                    updateQueues()
                }
                    dragInfoEx = null
                }
            }
        }

        LaunchedEffect(queueWindows) { // add to queue windows
            mutableQueueWindows.apply {
                clear()
                addAll(queueWindows)
            }

            selectedItems.fastForEachReversed { uidHash ->
                if (queueWindows.find { it.uid.hashCode() == uidHash } == null) {
                    selectedItems.remove(uidHash)
                }
            }
        }

        LaunchedEffect(mutableQueueWindows) { // scroll to song
            if (currentWindowIndex != -1)
                lazySongsListState.scrollToItem(currentWindowIndex)
        }

        LaunchedEffect(mutableQueues) { // scroll to queue
            if (currentWindowIndex != -1)
                lazyQueuesListState.scrollToItem(playingQueue)
        }

        LaunchedEffect(Unit) {
            updateQueues() // initiate queues
        }


        val queueList: @Composable ColumnScope.() -> Unit = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.queues_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            lockQueue = !lockQueue
                        }
                    ) {
                        Icon(
                            imageVector = if (lockQueue) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            contentDescription = null,
                        )
                    }
                    if (!landscape) {
                        ResizableIconButton(
                            icon = Icons.Rounded.Close,
                            onClick = {
                                multiqueueExpand = false
                            },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
            }

            if (mutableQueues.isEmpty()) {
                Text(text = stringResource(R.string.queues_empty))
            }

            LazyColumn(
                state = lazyQueuesListState,
                modifier = Modifier.nestedScroll(state.preUpPostDownNestedScrollConnection)
            ) {
                itemsIndexed(
                    items = mutableQueues,
                    key = { _, item -> item.hashCode() }
                ) { index, mq ->
                    ReorderableItem(
                        state = reorderableStateEx,
                        key = mq.hashCode()
                    ) {
                        Row( // wrapper
                            modifier = Modifier
                                .background(
                                    if (playingQueue == index) {
                                        MaterialTheme.colorScheme.tertiary.copy(0.3f)
                                    } else if (detachedHead && detachedQueueIndex == index) {
                                        MaterialTheme.colorScheme.tertiary.copy(0.1f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .combinedClickable(
                                    onClick = {
                                        // clicking on queue shows it in the ui
                                        if (playingQueue == index) {
                                            detachedHead = false
                                        } else {
                                            detachedHead = true
                                            detachedQueue.clear()
                                            detachedQueue.addAll(mq.getCurrentQueueShuffled())
                                            detachedQueueIndex = index
                                            detachedQueuePos = mq.queuePos
                                            detachedQueueTitle = mq.title
                                        }

                                        updateQueues()
                                    },
                                    onLongClick = {
                                        menuState.show {
                                            QueueMenu(
                                                mq = mq,
                                                onDismiss = menuState::dismiss,
                                                refreshUi = { updateQueues() }
                                            )
                                        }
                                    }
                                )
                        ) {
                            Row( // row contents (wrapper is needed for margin)
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(horizontal = 40.dp, vertical = 8.dp)
                                    .fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f, false)
                                ) {
                                    if (!lockQueue) {
                                        ResizableIconButton(
                                            icon = Icons.Rounded.Close,
                                            onClick = {
                                                val remainingQueues =
                                                    queueBoard.deleteQueue(mq, playerConnection.service)
                                                if (playingQueue == index) {
                                                    queueBoard.setCurrQueue(playerConnection)
                                                }
                                                detachedHead = false
                                                updateQueues()
                                                if (remainingQueues < 1) {
                                                    onTerminate.invoke()
                                                } else {
                                                    coroutineScope.launch {
                                                        lazyQueuesListState.animateScrollToItem(
                                                            playerConnection.player.currentMediaItemIndex
                                                        )
                                                    }
                                                }
                                            },
                                        )
                                    }
                                    Text(
                                        text = "${index + 1}. ${mq.title}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 0.dp)
                                    )
                                }

                                if (!lockQueue) {
                                    Icon(
                                        imageVector = Icons.Rounded.DragHandle,
                                        contentDescription = null,
                                        modifier = Modifier.draggableHandle()
                                    )
                                }
                            }
                        }
                    } // ReorderableItem
                }
            }
        }

        val songHeader: @Composable ColumnScope.() -> Unit = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.songs),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (detachedHead) {
                        Text(
                            text = detachedQueueTitle,
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // play the detached queue
                if (detachedHead) {
                    ResizableIconButton(
                        icon = Icons.Rounded.PlayArrow,
                        onClick = {
                            coroutineScope.launch(Dispatchers.Main) {
                                // change to this queue, seek to the item clicked on
                                queueBoard.setCurrQueue(detachedQueueIndex, playerConnection, false)
                                playerConnection.player.seekTo(detachedQueuePos, C.TIME_UNSET)
                                playerConnection.player.playWhenReady = true
                                detachedHead = false
                                updateQueues()
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        val songList: @Composable ColumnScope.() -> Unit = {
            if (detachedHead) { // detached head queue
                /**
                 * TODO: Probably integrate this with the main queue. Currently it is read only
                 * Not sure if we even want all the extra complexity both in code and for the user
                 */
                LazyColumn(
                    state = detachedQueueListState,
                    modifier = Modifier.nestedScroll(state.preUpPostDownNestedScrollConnection)
                ) {
                    itemsIndexed(
                        items = detachedQueue,
//                        key = { _, item -> item.} // duplicate key crash
                    ) { index, window ->
                        Row(
                            horizontalArrangement = Arrangement.Center
                        ) {
                            MediaMetadataListItem(
                                mediaMetadata = window,
                                isActive = index == detachedQueuePos,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            coroutineScope.launch(Dispatchers.Main) {
                                                // change to this queue, seek to the item clicked on
                                                queueBoard.setCurrQueue(detachedQueueIndex, playerConnection, false)
                                                playerConnection.player.seekTo(index, C.TIME_UNSET)
                                                detachedHead = false
                                                updateQueues()
                                            }
                                        },
                                        onLongClick = { }
                                    )
                            )
                        }
                    }
                }
            } else { // actual playing queue
                LazyColumn(
                    state = lazySongsListState,
                    contentPadding = if (multiqueueExpand && !landscape) PaddingValues(0.dp)
                                     else PaddingValues(0.dp, 16.dp), // header may cut off first song
                    modifier = Modifier
                        .nestedScroll(state.preUpPostDownNestedScrollConnection)
                ) {
                    itemsIndexed(
                        items = mutableQueueWindows,
                        key = { _, item -> item.uid.hashCode() }
                    ) { index, window ->
                        ReorderableItem(
                            state = reorderableState,
                            key = window.uid.hashCode()
                        ) {
                            val currentItem by rememberUpdatedState(window)
                            val dismissState = rememberSwipeToDismissBoxState(
                                positionalThreshold = { totalDistance ->
                                    totalDistance
                                },
                                confirmValueChange = { dismissValue ->
                                    when (dismissValue) {
                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            playerConnection.player.removeMediaItem(currentItem.firstPeriodIndex)
                                            queueBoard.removeCurrentQueueSong(currentItem.firstPeriodIndex, playerConnection.service)
                                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                            return@rememberSwipeToDismissBoxState true
                                        }

                                        SwipeToDismissBoxValue.EndToStart -> {
                                            playerConnection.player.removeMediaItem(currentItem.firstPeriodIndex)
                                            queueBoard.removeCurrentQueueSong(currentItem.firstPeriodIndex, playerConnection.service)
                                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                            return@rememberSwipeToDismissBoxState true
                                        }

                                        SwipeToDismissBoxValue.Settled -> {
                                            return@rememberSwipeToDismissBoxState false
                                        }
                                    }
                                }
                            )

                            val onCheckedChange: (Boolean) -> Unit = {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                if (it) {
                                    selectedItems.add(window.uid.hashCode())
                                } else {
                                    selectedItems.remove(window.uid.hashCode())
                                }
                            }

                            val content = @Composable {
                                MediaMetadataListItem(
                                    mediaMetadata = window.mediaItem.metadata!!,
                                    isActive = index == currentWindowIndex,
                                    isPlaying = isPlaying,
                                    trailingContent = {
                                        if (inSelectMode) {
                                            Checkbox(
                                                checked = window.uid.hashCode() in selectedItems,
                                                onCheckedChange = onCheckedChange
                                            )
                                        } else {
                                            IconButton(
                                                onClick = {
                                                    menuState.show {
                                                        PlayerMenu(
                                                            mediaMetadata = window.mediaItem.metadata,
                                                            navController = navController,
                                                            playerBottomSheetState = playerBottomSheetState,
                                                            onDismiss = {
                                                                menuState.dismiss()
                                                                state.collapseSoft()
                                                            },
                                                        )
                                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.MoreVert,
                                                    contentDescription = null
                                                )
                                            }
                                            if (!lockQueue) {
                                                IconButton(
                                                    onClick = { },
                                                    modifier = Modifier.draggableHandle()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.DragHandle,
                                                        contentDescription = null
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    isSelected = inSelectMode && window.uid.hashCode() in selectedItems,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (inSelectMode) {
                                                    onCheckedChange(window.uid.hashCode() !in selectedItems)
                                                } else {
                                                    coroutineScope.launch(Dispatchers.Main) {
                                                        if (index == currentWindowIndex) {
                                                            playerConnection.player.togglePlayPause()
                                                        } else {
                                                            playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
                                                            playerConnection.player.prepare() // else cannot click to play after auto-skip onError stop
                                                            playerConnection.player.playWhenReady = true
                                                        }

                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                if (!inSelectMode) {
                                                    inSelectMode = true
                                                    selectedItems.add(window.uid.hashCode())
                                                }
                                            }
                                        )
                                        .longPressDraggableHandle()
                                )
                            }

                            if (!lockQueue && !inSelectMode) {
                                SwipeToDismissBox(
                                    state = dismissState,
                                    backgroundContent = {},
                                    content = { content() }
                                )
                            } else {
                                content()
                            }
                        }
                    }
                }
            }
        }

        // queue info + player controls
        val bottomNav: @Composable ColumnScope.() -> Unit = {
            // queue info
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                // handle selection mode
                if (inSelectMode) {
                   SelectHeader(
                       selectedItems = selectedItems.mapNotNull { uidHash ->
                           mutableQueueWindows.find { it.uid.hashCode() == uidHash }
                       }.mapNotNull { it.mediaItem.metadata },
                       totalItemCount = queueWindows.size,
                       onSelectAll = {
                           selectedItems.clear()
                           selectedItems.addAll(queueWindows.map { it.uid.hashCode() })
                       },
                       onDeselectAll = { selectedItems.clear() },
                       menuState = menuState,
                       onDismiss = { inSelectMode = false }
                   )
                } else {
                    // queue title and show multiqueue button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(12.dp))
                            .padding(2.dp)
                            .weight(1f)
                            .clickable {
                                if (!landscape) {
                                    multiqueueExpand = !multiqueueExpand
                                }
                            }
                    ) {
                        Text(
                            text = queueTitle.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        ResizableIconButton(
                            icon = if (multiqueueExpand) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            enabled = !landscape,
                            onClick = {
                                multiqueueExpand = !multiqueueExpand
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            },
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = "${currentWindowIndex + 1} / ${queueWindows.size}",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            text = makeTimeString(queueLength * 1000L),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // player controls
            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding)
                ) {

                    Box(modifier = Modifier.weight(1f)) {
                        ResizableIconButton(
                            icon = Icons.Rounded.Shuffle,
                            modifier = Modifier
                                .size(32.dp)
                                .align(Alignment.Center)
                                .padding(4.dp)
                                .alpha(if (shuffleModeEnabled) 1f else 0.3f),
                            color = onBackgroundColor,
                            onClick = {
                                playerConnection.triggerShuffle()
                                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            }
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        ResizableIconButton(
                            icon = Icons.Rounded.SkipPrevious,
                            enabled = canSkipPrevious,
                            modifier = Modifier
                                .size(32.dp)
                                .align(Alignment.Center),
                            color = onBackgroundColor,
                            onClick = {
                                playerConnection.player.seekToPrevious()
                                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            }
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        ResizableIconButton(
                            icon = if (playbackState == STATE_ENDED) Icons.Rounded.Replay else if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            modifier = Modifier
                                .size(36.dp)
                                .align(Alignment.Center),
                            color = onBackgroundColor,
                            onClick = {
                                if (playbackState == STATE_ENDED) {
                                    playerConnection.player.seekTo(0, 0)
                                    playerConnection.player.playWhenReady = true
                                } else {
                                    playerConnection.player.togglePlayPause()
                                }
                                // play/pause is slightly harder haptic
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            }
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        ResizableIconButton(
                            icon = Icons.Rounded.SkipNext,
                            enabled = canSkipNext,
                            modifier = Modifier
                                .size(32.dp)
                                .align(Alignment.Center),
                            color = onBackgroundColor,
                            onClick = {
                                playerConnection.player.seekToNext()
                                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            }
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        ResizableIconButton(
                            icon = when (repeatMode) {
                                REPEAT_MODE_OFF, REPEAT_MODE_ALL -> Icons.Rounded.Repeat
                                REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                                else -> throw IllegalStateException()
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .align(Alignment.Center)
                                .padding(4.dp)
                                .alpha(if (repeatMode == REPEAT_MODE_OFF) 0.3f else 1f),
                            color = onBackgroundColor,
                            onClick = {
                                playerConnection.player.toggleRepeatMode()
                                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        // finally render ui
        if (landscape) {
            Row(
                modifier = Modifier
                    .nestedScroll(state.preUpPostDownNestedScrollConnection)
                    .padding(WindowInsets.systemBars.asPaddingValues())
            ) {
                // song header & song list
                Column(
                    modifier = Modifier.fillMaxWidth(0.5f)
                ) {
                    songHeader()
                    songList()
                }

                Spacer(Modifier.width(8.dp))

                // multiqueue list & navbar
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .nestedScroll(state.preUpPostDownNestedScrollConnection)
                            .weight(1f, false)
                    ) {
                        queueList()
                    }

                    // nav bar
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .fillMaxWidth()
                            .clickable {
                                state.collapseSoft()
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            }
                            .windowInsetsPadding(
                                WindowInsets.systemBars
                                    .only(WindowInsetsSides.Bottom)
                            )
                            .padding(horizontal = 12.dp)
                    ) {
                        bottomNav()
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        } else {
            // queue contents
            Column(
                modifier = Modifier
                    .nestedScroll(state.preUpPostDownNestedScrollConnection)
                    .padding(
                        WindowInsets.systemBars
                            .add(WindowInsets(bottom = ListItemHeight * 2))
                            .asPaddingValues()
                    )
            ) {
                // multiqueue list
                if (multiqueueExpand) {
                    Column(
                        modifier = Modifier.fillMaxHeight(0.4f)
                    ) {
                        queueList()
                    }

                    Spacer(Modifier.height(8.dp))
                    songHeader() // song header
                }

                songList() // song list
            }

            // nav bar
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .clickable {
                        state.collapseSoft()
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    }
                    .windowInsetsPadding(
                        WindowInsets.systemBars
                            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                    )
                    .padding(horizontal = 12.dp)
            ) {
                bottomNav()
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
