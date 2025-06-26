/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.ui.player

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.media3.common.C
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_READY
import androidx.navigation.NavController
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.DarkMode
import com.dd3boh.outertune.constants.DarkModeKey
import com.dd3boh.outertune.constants.PlayerBackgroundStyle
import com.dd3boh.outertune.constants.PlayerBackgroundStyleKey
import com.dd3boh.outertune.constants.PlayerHorizontalPadding
import com.dd3boh.outertune.constants.QueuePeekHeight
import com.dd3boh.outertune.constants.ShowLyricsKey
import com.dd3boh.outertune.constants.SwipeToSkip
import com.dd3boh.outertune.extensions.isPowerSaver
import com.dd3boh.outertune.extensions.metadata
import com.dd3boh.outertune.extensions.tabMode
import com.dd3boh.outertune.extensions.togglePlayPause
import com.dd3boh.outertune.extensions.toggleRepeatMode
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.ui.component.AsyncImageLocal
import com.dd3boh.outertune.ui.component.BottomSheet
import com.dd3boh.outertune.ui.component.BottomSheetState
import com.dd3boh.outertune.ui.component.LocalMenuState
import com.dd3boh.outertune.ui.component.PlayerSliderTrack
import com.dd3boh.outertune.ui.component.ResizableIconButton
import com.dd3boh.outertune.ui.component.rememberBottomSheetState
import com.dd3boh.outertune.ui.menu.PlayerMenu
import com.dd3boh.outertune.ui.theme.extractGradientColors
import com.dd3boh.outertune.ui.utils.SnapLayoutInfoProvider
import com.dd3boh.outertune.ui.utils.imageCache
import com.dd3boh.outertune.utils.makeTimeString
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BottomSheetPlayer(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val context = LocalContext.current

    val playbackState by playerConnection.playbackState.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)

    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()

    val thumbnailLazyGridState = rememberLazyGridState()

    val swipeToSkip by rememberPreference(SwipeToSkip, defaultValue = true)
    val previousMediaMetadata = if (swipeToSkip && playerConnection.player.hasPreviousMediaItem()) {
        val previousIndex = playerConnection.player.previousMediaItemIndex
        playerConnection.player.getMediaItemAt(previousIndex).metadata
    } else null

    val nextMediaMetadata = if (swipeToSkip && playerConnection.player.hasNextMediaItem()) {
        val nextIndex = playerConnection.player.nextMediaItemIndex
        playerConnection.player.getMediaItemAt(nextIndex).metadata
    } else null

    val mediaItems = listOfNotNull(previousMediaMetadata, mediaMetadata, nextMediaMetadata)
    val currentMediaIndex = mediaItems.indexOf(mediaMetadata)

    val currentItem by remember { derivedStateOf { thumbnailLazyGridState.firstVisibleItemIndex } }
    val itemScrollOffset by remember { derivedStateOf { thumbnailLazyGridState.firstVisibleItemScrollOffset } }

    LaunchedEffect(itemScrollOffset) {
        if (!thumbnailLazyGridState.isScrollInProgress || !swipeToSkip || itemScrollOffset != 0) return@LaunchedEffect

        if (currentItem > currentMediaIndex)
            playerConnection.player.seekToNext()
        else if (currentItem < currentMediaIndex)
            playerConnection.player.seekToPreviousMediaItem()
    }

    LaunchedEffect(mediaMetadata, canSkipPrevious, canSkipNext) {
        // When the media item changes, scroll to it
        val index = maxOf(0, currentMediaIndex)

        // Only animate scroll when player expanded, otherwise animated scroll won't work
        if (state.isExpanded)
            thumbnailLazyGridState.animateScrollToItem(index)
        else
            thumbnailLazyGridState.scrollToItem(index)
    }

    val horizontalLazyGridItemWidthFactor = 1f
    val thumbnailSnapLayoutInfoProvider = remember(thumbnailLazyGridState) {
        SnapLayoutInfoProvider(
            lazyGridState = thumbnailLazyGridState,
            positionInLayout = { layoutSize, itemSize ->
                (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
            }
        )
    }

    val playerBackground by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = PlayerBackgroundStyle.DEFAULT
    )

    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
        if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
    }

    val onBackgroundColor = when (playerBackground) {
        PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.secondary
        else ->
            if (useDarkTheme)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onPrimary
    }

    val showLyrics by rememberPreference(ShowLyricsKey, defaultValue = false)

    var position by rememberSaveable(playbackState) {
        mutableLongStateOf(playerConnection.player.currentPosition)
    }
    var duration by rememberSaveable(playbackState) {
        mutableLongStateOf(playerConnection.player.duration)
    }
    var sliderPosition by remember {
        mutableStateOf<Long?>(null)
    }

    var gradientColors by remember {
        mutableStateOf<List<Color>>(emptyList())
    }


    // gradient colours
    LaunchedEffect(mediaMetadata) {
        if (playerBackground != PlayerBackgroundStyle.GRADIENT || context.isPowerSaver()) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            if (mediaMetadata?.isLocal == true) {
                imageCache.getLocalThumbnail(mediaMetadata?.localPath)?.extractGradientColors()?.let {
                    gradientColors = it
                }
            } else {
                val result = (ImageLoader(context).execute(
                    ImageRequest.Builder(context)
                        .data(mediaMetadata?.thumbnailUrl)
                        .allowHardware(false)
                        .build()
                ).drawable as? BitmapDrawable)?.bitmap?.extractGradientColors()

                result?.let {
                    gradientColors = it
                }
            }
        }
    }

    LaunchedEffect(playbackState) {
        if (playbackState == STATE_READY) {
            while (isActive) {
                delay(500)
                position = playerConnection.player.currentPosition
                duration = playerConnection.player.duration
            }
        }
    }

    val queueSheetState = rememberBottomSheetState(
        dismissedBound = QueuePeekHeight + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        expandedBound = state.expandedBound,
    )


    BottomSheet(
        state = state,
        modifier = modifier,
        backgroundColor = if (useDarkTheme || playerBackground == PlayerBackgroundStyle.DEFAULT) {
            MaterialTheme.colorScheme.surfaceColorAtElevation(NavigationBarDefaults.Elevation)
        } else MaterialTheme.colorScheme.onSurfaceVariant,
        collapsedBackgroundColor = MaterialTheme.colorScheme.surfaceColorAtElevation(NavigationBarDefaults.Elevation),
        onDismiss = {
            playerConnection.player.stop()
            playerConnection.player.clearMediaItems()
            playerConnection.service.deInitQueue()
        },
        collapsedContent = {
            MiniPlayer(
                position = position,
                duration = duration
            )
        }
    ) {
        val tabMode = context.tabMode()

        val actionButtons: @Composable RowScope.() -> Unit = {
            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .offset(y = 5.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                ResizableIconButton(
                    icon = if (currentSong?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp),
                    onClick = playerConnection::toggleLike
                )
            }

            Spacer(modifier = Modifier.width(7.dp))

            Box(
                modifier = Modifier
                    .offset(y = 5.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                ResizableIconButton(
                    icon = Icons.Rounded.MoreVert,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Center),
                    onClick = {
                        menuState.show {
                            PlayerMenu(
                                mediaMetadata = mediaMetadata,
                                navController = navController,
                                playerBottomSheetState = state,
                                onDismiss = menuState::dismiss
                            )
                        }
                    }
                )
            }
        }

        val controlsContent: @Composable ColumnScope.(MediaMetadata) -> Unit = { mediaMetadata ->
            val playPauseRoundness by animateDpAsState(
                targetValue = if (isPlaying) 24.dp else 36.dp,
                animationSpec = tween(durationMillis = 100, easing = LinearEasing),
                label = "playPauseRoundness"
            )

            // action buttons for landscape (above title)
            if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE && !tabMode) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = PlayerHorizontalPadding, end = PlayerHorizontalPadding, bottom = 16.dp)
                ) {
                    actionButtons()
                }
            }

            Row(
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerHorizontalPadding)
            ) {
                Row {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mediaMetadata.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = onBackgroundColor,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .basicMarquee(
                                    iterations = 1,
                                    initialDelayMillis = 3000
                                )
                                .clickable(enabled = mediaMetadata.album != null) {
                                    navController.navigate("album/${mediaMetadata.album!!.id}")
                                    state.collapseSoft()
                                }
                        )

                        Row {
                            mediaMetadata.artists.fastForEachIndexed { index, artist ->
                                Text(
                                    text = artist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = onBackgroundColor,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .basicMarquee(
                                            iterations = 1,
                                            initialDelayMillis = 5000
                                        )
                                        .clickable(enabled = artist.id != null) {
                                            navController.navigate("artist/${artist.id}")
                                            state.collapseSoft()
                                        }
                                )

                                if (index != mediaMetadata.artists.lastIndex) {
                                    Text(
                                        text = ", ",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = onBackgroundColor
                                    )
                                }
                            }
                        }
                    }

                    // action buttons for portrait (inline with title)
                    if (LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE && !tabMode) {
                        actionButtons()
                    }
                }
            }

            Slider(
                value = (sliderPosition ?: position).toFloat(),
                valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                onValueChange = {
                    sliderPosition = it.toLong()
                    // slider too granular for this haptic to feel right
//                    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                },
                onValueChangeFinished = {
                    sliderPosition?.let {
                        playerConnection.player.seekTo(it)
                        position = it
                    }
                    sliderPosition = null
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                },
                thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                track = { sliderState ->
                    PlayerSliderTrack(
                        sliderState = sliderState,
                        colors = SliderDefaults.colors()
                    )
                },
                modifier = Modifier.padding(horizontal = PlayerHorizontalPadding)
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerHorizontalPadding + 4.dp)
            ) {
                Text(
                    text = makeTimeString(sliderPosition ?: position),
                    style = MaterialTheme.typography.labelMedium,
                    color = onBackgroundColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = if (duration != C.TIME_UNSET) makeTimeString(duration) else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = onBackgroundColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerHorizontalPadding)
            ) {
                val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()

                Box(modifier = Modifier.weight(1f)) {
                    ResizableIconButton(
                        icon = Icons.Rounded.Shuffle,
                        modifier = Modifier
                            .size(32.dp)
                            .padding(4.dp)
                            .align(Alignment.Center)
                            .alpha(if (shuffleModeEnabled) 1f else 0.5f),
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

                Box(
                    modifier = Modifier
                        .size(if (showLyrics) 56.dp else 72.dp)
                        .animateContentSize()
                        .clip(RoundedCornerShape(playPauseRoundness))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            if (playbackState == STATE_ENDED) {
                                playerConnection.player.seekTo(0, 0)
                                playerConnection.player.playWhenReady = true
                            } else {
                                playerConnection.player.togglePlayPause()
                            }
                            // play/pause is slightly harder haptic
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        }
                ) {
                    Image(
                        imageVector = if (playbackState == STATE_ENDED) Icons.Rounded.Replay else if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(36.dp)
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
                            .padding(4.dp)
                            .align(Alignment.Center)
                            .alpha(if (repeatMode == REPEAT_MODE_OFF) 0.5f else 1f),
                        color = onBackgroundColor,
                        onClick = {
                            playerConnection.player.toggleRepeatMode()
                            haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !context.isPowerSaver() && state.isExpanded,
            enter = fadeIn(tween(500)),
            exit = fadeOut(tween(500))
        ) {
            AnimatedContent(
                targetState = mediaMetadata,
                transitionSpec = {
                    fadeIn(tween(1000)).togetherWith(fadeOut(tween(1000)))
                }
            ) { metadata ->
                if (playerBackground == PlayerBackgroundStyle.BLUR) {
                    if (metadata?.isLocal == true) {
                        metadata.let {
                            AsyncImageLocal(
                                image = { imageCache.getLocalThumbnail(it.localPath) },
                                contentDescription = null,
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(200.dp)
                            )
                        }
                    } else {
                        AsyncImage(
                            model = metadata?.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(200.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                    )
                }
            }

            AnimatedContent(
                targetState = gradientColors,
                transitionSpec = {
                    fadeIn(tween(1000)).togetherWith(fadeOut(tween(1000)))
                }
            ) { colors ->
                if (playerBackground == PlayerBackgroundStyle.GRADIENT && colors.size >= 2) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(colors), alpha = 0.8f)
                    )
                }
            }

            if (playerBackground != PlayerBackgroundStyle.DEFAULT && showLyrics) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                )
            }
        }

        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE && !tabMode) {
            Row(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                    .padding(bottom = queueSheetState.collapsedBound)
                    .fillMaxSize()
            ) {
                BoxWithConstraints(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .nestedScroll(state.preUpPostDownNestedScrollConnection)
                ) {
                    val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor

                    LazyHorizontalGrid(
                        state = thumbnailLazyGridState,
                        rows = GridCells.Fixed(1),
                        flingBehavior = rememberSnapFlingBehavior(thumbnailSnapLayoutInfoProvider),
                        userScrollEnabled = state.isExpanded && swipeToSkip
                    ) {
                        items(
                            items = mediaItems,
                            key = { it.id }
                        ) {
                            Thumbnail(
                                sliderPositionProvider = { sliderPosition },
                                modifier = Modifier
                                    .width(horizontalLazyGridItemWidth)
                                    .animateContentSize(),
                                contentScale = ContentScale.Crop,
                                showLyricsOnClick = true,
                                customMediaMetadata = it
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(if (showLyrics) 0.4f else 1f, false)
                        .animateContentSize()
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                ) {
                    Spacer(Modifier.weight(1f))

                    mediaMetadata?.let {
                        controlsContent(it)
                    }

                    Spacer(Modifier.weight(1f))
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                    .padding(bottom = queueSheetState.collapsedBound)
            ) {
                BoxWithConstraints(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .nestedScroll(state.preUpPostDownNestedScrollConnection)
                ) {
                    val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor

                    LazyHorizontalGrid(
                        state = thumbnailLazyGridState,
                        rows = GridCells.Fixed(1),
                        flingBehavior = rememberSnapFlingBehavior(thumbnailSnapLayoutInfoProvider),
                        userScrollEnabled = swipeToSkip && state.isExpanded
                    ) {
                        items(
                            items = mediaItems,
                            key = { it.id }
                        ) {
                            Thumbnail(
                                modifier = Modifier
                                    .width(horizontalLazyGridItemWidth)
                                    .animateContentSize(),
                                contentScale = ContentScale.Crop,
                                sliderPositionProvider = { sliderPosition },
                                showLyricsOnClick = true,
                                customMediaMetadata = it
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                mediaMetadata?.let {
                    controlsContent(it)
                }

                Spacer(Modifier.height(24.dp))
            }
        }


        QueueSheet(
            state = queueSheetState,
            playerBottomSheetState = state,
            onTerminate = {
                state.dismiss()
                playerConnection.service.queueBoard.detachedHead = false
            },
            onBackgroundColor = onBackgroundColor,
            navController = navController
        )
    }
}
