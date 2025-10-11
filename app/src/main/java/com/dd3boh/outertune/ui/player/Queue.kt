/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 O⁠ute⁠rTu⁠ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.ui.player

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_ENDED
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalMenuState
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.InsetsSafeE
import com.dd3boh.outertune.constants.InsetsSafeS
import com.dd3boh.outertune.constants.InsetsSafeSE
import com.dd3boh.outertune.constants.InsetsSafeSTE
import com.dd3boh.outertune.constants.InsetsSafeT
import com.dd3boh.outertune.constants.ListItemHeight
import com.dd3boh.outertune.constants.LockQueueKey
import com.dd3boh.outertune.constants.MiniPlayerHeight
import com.dd3boh.outertune.constants.PlayerHorizontalPadding
import com.dd3boh.outertune.constants.SeekIncrement
import com.dd3boh.outertune.constants.SeekIncrementKey
import com.dd3boh.outertune.extensions.metadata
import com.dd3boh.outertune.extensions.move
import com.dd3boh.outertune.extensions.supportsWideScreen
import com.dd3boh.outertune.extensions.tabMode
import com.dd3boh.outertune.extensions.togglePlayPause
import com.dd3boh.outertune.extensions.toggleRepeatMode
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.models.MultiQueueObject
import com.dd3boh.outertune.ui.component.BottomSheet
import com.dd3boh.outertune.ui.component.BottomSheetState
import com.dd3boh.outertune.ui.component.EmptyPlaceholder
import com.dd3boh.outertune.ui.component.LazyColumnScrollbar
import com.dd3boh.outertune.ui.component.SelectHeader
import com.dd3boh.outertune.ui.component.button.IconButton
import com.dd3boh.outertune.ui.component.button.ResizableIconButton
import com.dd3boh.outertune.ui.component.items.MediaMetadataListItem
import com.dd3boh.outertune.ui.menu.PlayerMenu
import com.dd3boh.outertune.ui.menu.QueueMenu
import com.dd3boh.outertune.utils.makeTimeString
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun QueueSheet(
    state: BottomSheetState,
    onTerminate: () -> Unit,
    playerBottomSheetState: BottomSheetState,
    onBackgroundColor: Color,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    BottomSheet(
        state = state,
        background = {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(NavigationBarDefaults.Elevation))
                    .fillMaxSize()
            )
        },
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
        QueueContent(
            queueState = state,
            onTerminate = onTerminate,
            playerState = playerBottomSheetState,
            navController = navController
        )
    }
}

@Composable
fun QueueScreen(
    onTerminate: () -> Unit,
    playerBottomSheetState: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
                    .add(WindowInsets(bottom = MiniPlayerHeight))
            )
    ) {
        QueueContent(
            onTerminate = onTerminate,
            playerState = playerBottomSheetState,
            navController = navController
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
fun BoxScope.QueueContent(
    queueState: BottomSheetState? = null,
    playerState: BottomSheetState,
    onTerminate: () -> Unit,
    navController: NavController,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val qb = playerConnection.service.queueBoard

    // preferences
    var lockQueue by rememberPreference(LockQueueKey, defaultValue = false)

    // player
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val playbackState by playerConnection.playbackState.collectAsState()

    // player controls
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val seekIncrement by rememberEnumPreference(
        key = SeekIncrementKey,
        defaultValue = SeekIncrement.OFF
    )

    // ui
    val tabMode = context.tabMode()
    val wideScreen = context.supportsWideScreen()
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE && !tabMode && wideScreen

    // multi queue vars
    var mqExpand by remember { mutableStateOf(false) }
    var detachedHead by remember { mutableStateOf(false) }
    var detachedQueue by remember { mutableStateOf<MultiQueueObject?>(null) }
    val mutableQueues = remember { mutableStateListOf<MultiQueueObject>() }
    var playingQueue by remember { mutableIntStateOf(-1) }

    // current queue vars
    val queueWindows by playerConnection.queueWindows.collectAsState()

    /**
     * SONG LIST
     */
    val mutableSongs = remember { mutableStateListOf<MediaMetadata>() }
    val lazySongsListState = rememberLazyListState()

    // multiselect
    var inSelectMode by remember {
        mutableStateOf(false)
    }
    val selectedItems = remember { mutableStateListOf<Int>() }
    val onExitSelectionMode = {
        inSelectMode = false
        selectedItems.clear()
    }

    // search
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var searchQuery by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    val filteredSongs = remember(mutableSongs, searchQuery) {
        if (searchQuery.text.isEmpty()) mutableSongs
        else mutableSongs.filter { song ->
            song.title.contains(searchQuery.text, ignoreCase = true)
                    || song.artists.fastAny { it.name.contains(searchQuery.text, ignoreCase = true) }
        }
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching && (queueState == null || queueState.isExpanded)) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(query) {
        snapshotFlow { searchQuery }.debounce { 300L }.collectLatest {
            searchQuery = query
        }
    }

    if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    } else if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    }


    // reorder
    var dragInfo by remember {
        mutableStateOf<Pair<Int, Int>?>(null)
    }
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazySongsListState,
        scrollThresholdPadding = WindowInsets.systemBars.add(
            WindowInsets(top = ListItemHeight, bottom = ListItemHeight)
        ).asPaddingValues()
    ) { from, to ->
        val currentDragInfo = dragInfo
        dragInfo = if (currentDragInfo == null) {
            from.index to to.index
        } else {
            currentDragInfo.first to to.index
        }
        mutableSongs.move(from.index, to.index)
    }
    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            dragInfo?.let { (from, to) ->
                if (from == to) return@LaunchedEffect
                qb.moveSong(from, to)
                playerConnection.player.moveMediaItem(from, to)
                dragInfo = null
            }
        }
    }

    val lazyQueuesListState = rememberLazyListState()
    var dragInfoEx by remember {
        mutableStateOf<Pair<Int, Int>?>(null)
    }
    val reorderableStateEx = rememberReorderableLazyListState(
        lazyListState = lazyQueuesListState,
        scrollThresholdPadding = WindowInsets.systemBars.add(
            WindowInsets(top = ListItemHeight, bottom = ListItemHeight)
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
                qb.move(from, to)
                dragInfoEx = null
            }
        }
    }

    // Helpers
    LaunchedEffect(detachedHead) {
        if (!detachedHead) {
            detachedQueue = null // detachedQueue should only exist in detached mode
        } else {
            isSearching = false // no searching in detach mode
        }
        onExitSelectionMode() // select supported in both modes, but we disallow cross contamination of items
    }

    LaunchedEffect(mqExpand) {
        if (detachedHead) {
            detachedHead = false
        }
    }


    LaunchedEffect(queueWindows, detachedQueue) { // add to songs list & scroll
        if (isSearching) return@LaunchedEffect
        if (detachedQueue != null) {
            mutableSongs.apply {
                clear()
                addAll(detachedQueue!!.getCurrentQueueShuffled())
            }
            detachedQueue?.let {
                lazySongsListState.scrollToItem(it.getQueuePosShuffled())
            }
            return@LaunchedEffect
        }
        val fallBackQueue = qb.getCurrentQueue()
        if (queueWindows.isEmpty() && fallBackQueue != null) {
            detachedQueue = fallBackQueue
            detachedHead = true
            mqExpand = true
            return@LaunchedEffect
        }

        mutableSongs.apply {
            clear()
            addAll(queueWindows.mapIndexedNotNull { index, w -> w.mediaItem.metadata?.copy(composeUidWorkaround = index.toDouble()) })
        }

        if (currentWindowIndex != -1 && !isSearching) {
            lazySongsListState.scrollToItem(currentWindowIndex)
        }

        selectedItems.clear()
    }

    LaunchedEffect(mqExpand) { // scroll to queue
        if (mqExpand && playingQueue >= 0) {
            lazyQueuesListState.animateScrollToItem(playingQueue)
            if (currentWindowIndex != -1) {
                lazySongsListState.scrollToItem(currentWindowIndex)
            }
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { qb.masterQueues.toList() }
            .collect { updatedList ->
                // Handle the updated list
                mutableQueues.clear()
                mutableQueues.addAll(qb.getAllQueues())
                playingQueue = updatedList.indexOf(qb.getCurrentQueue())
            }
    }

    val queueHeader: @Composable ColumnScope.(Modifier) -> Unit = { modifier ->
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
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
                ResizableIconButton(
                    icon = if (lockQueue) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    onClick = {
                        lockQueue = !lockQueue
                    },
                )

                if (!landscape) {
                    ResizableIconButton(
                        icon = Icons.Rounded.Close,
                        onClick = {
                            mqExpand = false
                        },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
        }
    }

    val queueList: @Composable ColumnScope.(PaddingValues) -> Unit = { contentPadding ->
        if (mutableQueues.isEmpty()) {
            Text(text = stringResource(R.string.queues_empty))
        }

        LazyColumn(
            state = lazyQueuesListState,
            contentPadding = contentPadding,
            modifier = if (queueState != null) Modifier.nestedScroll(queueState.preUpPostDownNestedScrollConnection) else Modifier
        ) {
            itemsIndexed(
                items = mutableQueues,
                key = { _, item -> item.id }
            ) { index, mq ->
                ReorderableItem(
                    state = reorderableStateEx,
                    key = mq.hashCode()
                ) {
                    Row( // wrapper
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (playingQueue == index) {
                                    MaterialTheme.colorScheme.tertiary.copy(0.3f)
                                } else if (detachedHead && detachedQueue == mq) {
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
                                        detachedQueue = mq
                                        onExitSelectionMode()
                                    }
                                },
                                onLongClick = {
                                    menuState.show {
                                        QueueMenu(
                                            navController = navController,
                                            mq = mq,
                                            onDismiss = menuState::dismiss
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
                                                qb.deleteQueue(mq)
                                            if (playingQueue == index) {
                                                qb.setCurrQueue()
                                            }
                                            detachedHead = false
                                            if (remainingQueues < 1) {
                                                onTerminate.invoke()
                                            }
                                        },
                                    )
                                }
                                Text(
                                    text = "${index + 1}. ${mq.title}",
                                    maxLines = 1,
                                    overflow = TextOverflow.MiddleEllipsis,
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
                }
            }
        }
        LazyColumnScrollbar(
            state = lazyQueuesListState,
        )
    }

    val songHeader: @Composable ColumnScope.(Modifier) -> Unit = { modifier ->
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
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
            }

            // play the detached queue
            if (detachedHead) {
                ResizableIconButton(
                    icon = Icons.Rounded.PlayArrow,
                    onClick = {
                        coroutineScope.launch(Dispatchers.Main) {
                            // change to this queue, seek to the item clicked on
                            qb.setCurrQueue(detachedQueue)
                            playerConnection.player.prepare() // else cannot click to play after auto-skip onError stop
                            playerConnection.player.playWhenReady = true
                            detachedHead = false
                        }
                    }
                )
            } else if (!isSearching) {
                ResizableIconButton(
                    icon = Icons.Rounded.Search,
                    onClick = {
                        isSearching = true
                    }
                )
            }
        }
    }

    val songList: @Composable ColumnScope.(PaddingValues) -> Unit = { contentPadding ->
        LazyColumn(
            state = lazySongsListState,
            contentPadding = contentPadding,
            modifier = if (queueState != null) Modifier.nestedScroll(queueState.preUpPostDownNestedScrollConnection) else Modifier
        ) {
            if ((if (isSearching) filteredSongs else mutableSongs).isEmpty()) {
                item {
                    EmptyPlaceholder(
                        icon = Icons.Rounded.MusicNote,
                        text = stringResource(if (isSearching) R.string.no_results_found else R.string.queues_empty),
                        modifier = Modifier.animateItem()
                    )
                }
            }

            itemsIndexed(
                items = if (isSearching) filteredSongs else mutableSongs,
                key = { _, item -> item.hashCode() }
            ) { index, window ->
                ReorderableItem(
                    state = reorderableState,
                    key = window.hashCode()
                ) {
                    val dismissState = rememberSwipeToDismissBoxState(
                        positionalThreshold = { totalDistance ->
                            totalDistance
                        },
                        confirmValueChange = { dismissValue ->
                            when (dismissValue) {
                                SwipeToDismissBoxValue.StartToEnd -> {
                                    if (qb.removeCurrentQueueSong(index)) {
                                        playerConnection.player.removeMediaItem(index)
                                        mutableSongs.removeAt(index)
                                    }
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    return@rememberSwipeToDismissBoxState true
                                }

                                SwipeToDismissBoxValue.EndToStart -> {
                                    if (qb.removeCurrentQueueSong(index)) {
                                        playerConnection.player.removeMediaItem(index)
                                        mutableSongs.removeAt(index)
                                    }
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
                            selectedItems.add(window.hashCode())
                        } else {
                            selectedItems.remove(window.hashCode())
                        }
                    }

                    val content = @Composable {
                        MediaMetadataListItem(
                            mediaMetadata = window,
                            isActive = (index == currentWindowIndex && !detachedHead) || index == detachedQueue?.getQueuePosShuffled(),
                            isPlaying = isPlaying && !detachedHead,
                            trailingContent = {
                                if (inSelectMode) {
                                    Checkbox(
                                        checked = window.hashCode() in selectedItems,
                                        onCheckedChange = onCheckedChange
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                PlayerMenu(
                                                    mediaMetadata = window,
                                                    navController = navController,
                                                    playerBottomSheetState = playerState,
                                                    onDismiss = {
                                                        menuState.dismiss()
                                                    },
                                                )
                                            }
                                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MoreVert,
                                            contentDescription = null
                                        )
                                    }
                                    if (!lockQueue && !detachedHead) {
                                        Icon(
                                            imageVector = Icons.Rounded.DragHandle,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .padding(end = 16.dp)
                                                .draggableHandle()
                                        )
                                    }
                                }
                            },
                            isSelected = inSelectMode && window.hashCode() in selectedItems,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (inSelectMode) {
                                            onCheckedChange(window.hashCode() !in selectedItems)
                                        } else {
                                            coroutineScope.launch(Dispatchers.Main) {
                                                if (index == currentWindowIndex && !detachedHead) {
                                                    playerConnection.player.togglePlayPause()
                                                } else {
                                                    val index = index // race condition...?
                                                    if (detachedHead) {
                                                        detachedQueue?.setCurrentQueuePos(index)
                                                        qb.setCurrQueue(detachedQueue, false)
                                                    } else {
                                                        playerConnection.player.seekToDefaultPosition(index)
                                                    }
                                                    playerConnection.player.prepare() // else cannot click to play after auto-skip onError stop
                                                    playerConnection.player.playWhenReady = true
                                                    detachedHead = false
                                                }
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (!inSelectMode) {
                                            inSelectMode = true
                                            selectedItems.add(window.hashCode())
                                        }
                                    }
                                )
                        )
                    }

                    if (!lockQueue && !inSelectMode && !detachedHead) {
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
        LazyColumnScrollbar(
            state = lazySongsListState,
        )
    }

    val searchBar: @Composable ColumnScope.() -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isSearching && !detachedHead) {
                        isSearching = false
                        query = TextFieldValue()
                    } else {
                        navController.navigateUp()
                    }
                },
                modifier = Modifier.padding(start = 16.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null
                )
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.search),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }
    }

// queue info + player controls
    val bottomNav: @Composable ColumnScope.() -> Unit = {


        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.End))
                .clickable {
                    queueState?.collapseSoft()
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                }
        ) {
            // queue info
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(24.dp, 12.dp)
            ) {
                // handle selection mode
                if (inSelectMode && !isSearching) {
                    SelectHeader(
                        navController = navController,
                        selectedItems = selectedItems.mapNotNull { uidHash ->
                            (detachedQueue?.getCurrentQueueShuffled() ?: mutableSongs).find { it.hashCode() == uidHash }
                        },
                        totalItemCount = (detachedQueue?.getCurrentQueueShuffled() ?: mutableSongs).size,
                        onSelectAll = {
                            selectedItems.clear()
                            selectedItems.addAll(mutableSongs.map { it.hashCode() })
                        },
                        onDeselectAll = { selectedItems.clear() },
                        menuState = menuState,
                        onDismiss = onExitSelectionMode
                    )
                } else {
                    // queue title and show multiqueue button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(12.dp))
                            .padding(2.dp)
                            .weight(1f)
                            .clickable(enabled = !landscape && !queueWindows.isEmpty()) {
                                mqExpand = !mqExpand
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            }
                    ) {
                        Text(
                            text = detachedQueue?.title ?: mutableQueues.getOrNull(playingQueue)?.title ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        ResizableIconButton(
                            icon = if (mqExpand) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            enabled = !landscape && !queueWindows.isEmpty(),
                            onClick = {
                                mqExpand = !mqExpand
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
                        fun getQueueLength(): Int {
                            return if (!detachedHead) {
                                queueWindows.sumOf { it.mediaItem.metadata!!.duration }
                            } else detachedQueue?.queue?.sumOf { it.duration } ?: 0
                        }

                        fun getQueuePositionStr(): String {
                            return if (!detachedHead) {
                                "${currentWindowIndex + 1} / ${queueWindows.size}"
                            } else {
                                detachedQueue?.let {
                                    "${it.getQueuePosShuffled() + 1} / ${it.getSize()}"
                                } ?: "–/–"
                            }
                        }
                        Text(
                            text = getQueuePositionStr(),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            text = makeTimeString(getQueueLength() * 1000L),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // player controls
            if (queueState != null) {
                val iconButtonColor = MaterialTheme.colorScheme.onSecondaryContainer
                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        ResizableIconButton(
                            if (shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle_off,
                            modifier = Modifier
                                .size(32.dp)
                                .padding(4.dp)
                                .align(Alignment.Center),
                            color = iconButtonColor,
                            enabled = !detachedHead,
                            onClick = {
                                playerConnection.triggerShuffle()
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            }
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        ResizableIconButton(
                            icon = Icons.Rounded.SkipPrevious,
                            modifier = Modifier
                                .size(32.dp)
                                .align(Alignment.Center),
                            color = iconButtonColor,
                            enabled = !detachedHead && canSkipPrevious,
                            onClick = {
                                playerConnection.player.seekToPrevious()
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            }
                        )
                    }

                    if (seekIncrement != SeekIncrement.OFF) {
                        Box(modifier = Modifier.weight(1f)) {
                            ResizableIconButton(
                                icon = Icons.Rounded.FastRewind,
                                modifier = Modifier
                                    .size(32.dp)
                                    .align(Alignment.Center),
                                onClick = {
                                    playerConnection.player.seekTo(playerConnection.player.currentPosition - seekIncrement.millisec)
                                }
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        ResizableIconButton(
                            icon = if (playbackState == STATE_ENDED) Icons.Rounded.Replay else if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            modifier = Modifier
                                .size(36.dp)
                                .align(Alignment.Center),
                            color = iconButtonColor,
                            enabled = !detachedHead,
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

                    if (seekIncrement != SeekIncrement.OFF) {
                        Box(modifier = Modifier.weight(1f)) {
                            ResizableIconButton(
                                icon = Icons.Rounded.FastForward,
                                modifier = Modifier
                                    .size(32.dp)
                                    .align(Alignment.Center),
                                onClick = {
                                    playerConnection.player.seekTo(playerConnection.player.currentPosition + seekIncrement.millisec)
                                }
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        ResizableIconButton(
                            icon = Icons.Rounded.SkipNext,
                            modifier = Modifier
                                .size(32.dp)
                                .align(Alignment.Center),
                            color = iconButtonColor,
                            enabled = !detachedHead && canSkipNext,
                            onClick = {
                                playerConnection.player.seekToNext()
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            }
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        ResizableIconButton(
                            icon = when (repeatMode) {
                                REPEAT_MODE_OFF -> R.drawable.repeat_off
                                REPEAT_MODE_ALL -> R.drawable.repeat_on
                                REPEAT_MODE_ONE -> R.drawable.repeat_one
                                else -> throw IllegalStateException()
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .padding(4.dp)
                                .align(Alignment.Center),
                            color = iconButtonColor,
                            enabled = !detachedHead,
                            onClick = {
                                playerConnection.player.toggleRepeatMode()
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }


// finally render ui
    if (landscape) {
        Row {
            // song header & song list
            Column(
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                songHeader(Modifier.windowInsetsPadding(InsetsSafeSTE))
                songList(InsetsSafeS.asPaddingValues())
            }

            Spacer(Modifier.width(8.dp))

            // multiqueue list & navbar
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxHeight()
            ) {
                Column(
                    modifier = (if (queueState != null) Modifier.nestedScroll(queueState.preUpPostDownNestedScrollConnection) else Modifier)
                        .fillMaxWidth()
                        .weight(1f, false)
                ) {
                    if (isSearching) {
                        Spacer(Modifier.windowInsetsPadding(InsetsSafeT))
                        searchBar()
                        if (inSelectMode) {
                            Row {
                                SelectHeader(
                                    navController = navController,
                                    selectedItems = selectedItems.mapNotNull { uidHash ->
                                        mutableSongs.find { it.hashCode() == uidHash }
                                    },
                                    totalItemCount = mutableSongs.size,
                                    onSelectAll = {
                                        selectedItems.clear()
                                        selectedItems.addAll(mutableSongs.map { it.hashCode() })
                                    },
                                    onDeselectAll = { selectedItems.clear() },
                                    menuState = menuState,
                                    onDismiss = onExitSelectionMode
                                )
                            }
                        }
                    } else {
                        queueHeader(Modifier.windowInsetsPadding(InsetsSafeSTE))
                        queueList(InsetsSafeE.asPaddingValues())
                    }
                }

                // nav bar
                if (!isSearching) {
                    bottomNav()
                }
            }
        }
    } else {
        // queue contents
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier.weight(1f, false)
            ) {
                // multiqueue list
                AnimatedVisibility(
                    visible = isSearching,
                    modifier = Modifier
                        .windowInsetsPadding(InsetsSafeT)
                ) {
                    Spacer(Modifier.windowInsetsPadding(InsetsSafeT))
                    searchBar()
                    if (inSelectMode) {
                        Row {
                            SelectHeader(
                                navController = navController,
                                selectedItems = selectedItems.mapNotNull { uidHash ->
                                    filteredSongs.find { it.hashCode() == uidHash }
                                },
                                totalItemCount = filteredSongs.size,
                                onSelectAll = {
                                    selectedItems.clear()
                                    selectedItems.addAll(filteredSongs.map { it.hashCode() })
                                },
                                onDeselectAll = { selectedItems.clear() },
                                menuState = menuState,
                                onDismiss = onExitSelectionMode
                            )
                        }
                    }
                }

                AnimatedVisibility(mqExpand && !isSearching) {
                    // why cant i just put everything in one column???
                    Column {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight(0.4f)
                        ) {
                            queueHeader(Modifier.windowInsetsPadding(InsetsSafeSTE))
                            queueList(InsetsSafeSE.asPaddingValues())
                        }
                        Spacer(Modifier.height(12.dp))
                        songHeader(Modifier.windowInsetsPadding(InsetsSafeSE)) // song header
                    }
                }

                val songListInsets = if (mqExpand) {
                    InsetsSafeSE
                } else {
                    InsetsSafeSTE
                }
                songList(songListInsets.asPaddingValues()) // song list
            }

            // nav bar
            if (!isSearching) {
                bottomNav()
            }
        }
    }
}
