/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.ui.component

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import com.dd3boh.outertune.LocalMenuState
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.LyricClickable
import com.dd3boh.outertune.constants.LyricFontSizeKey
import com.dd3boh.outertune.constants.LyricKaraokeEnable
import com.dd3boh.outertune.constants.LyricUpdateSpeed
import com.dd3boh.outertune.constants.LyricsPosition
import com.dd3boh.outertune.constants.LyricsTextPositionKey
import com.dd3boh.outertune.constants.ShowLyricsKey
import com.dd3boh.outertune.constants.Speed
import com.dd3boh.outertune.db.entities.LyricsEntity
import com.dd3boh.outertune.db.entities.LyricsEntity.Companion.uninitializedLyric
import com.dd3boh.outertune.extensions.isPowerSaver
import com.dd3boh.outertune.ui.component.button.IconButton
import com.dd3boh.outertune.ui.component.shimmer.ShimmerHost
import com.dd3boh.outertune.ui.component.shimmer.TextPlaceholder
import com.dd3boh.outertune.ui.menu.LyricsMenu
import com.dd3boh.outertune.ui.utils.fadingEdge
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.akanework.gramophone.logic.utils.LrcUtils
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.logic.utils.SemanticLyrics.LyricLine
import java.io.File
import kotlin.time.Duration.Companion.seconds

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun Lyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val density = LocalDensity.current
    var (showLyrics, onShowLyricsChange) = rememberPreference(ShowLyricsKey, false)
    val landscapeOffset = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val lyricsTextPosition by rememberEnumPreference(LyricsTextPositionKey, LyricsPosition.CENTER)
    val lyricsFontSize by rememberPreference(LyricFontSizeKey, 20)

    val lyricsClickable by rememberPreference(LyricClickable, true)
    val lyricsFancy by rememberPreference(LyricKaraokeEnable, false)
    val lyricsUpdateSpeed by rememberEnumPreference(LyricUpdateSpeed, Speed.MEDIUM)
    var lyricRefreshRate = lyricsUpdateSpeed.toLrcRefreshMillis()

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    // NOTE: lyricsModel is the current display lyrics that is updated by playerLyrics AND/OR manually
    val playerLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
    var lyricsModel by remember { mutableStateOf(playerLyrics) }

    val lines: SnapshotStateList<LyricLine> = remember { mutableStateListOf<LyricLine>() }

    val isSynced = remember(lyricsModel) {
        lyricsModel is SemanticLyrics.SyncedLyrics
    }

    LaunchedEffect(playerLyrics) {
        lyricsModel = playerLyrics
    }

    LaunchedEffect(lyricsModel) {
        lines.clear()
        lyricsModel?.let { model ->
            if (isSynced) {
                val lyrics = lyricsModel as SemanticLyrics.SyncedLyrics
                lines.addAll(lyrics.text)

                if (lyricsFancy && lyrics.text.fastAny { it.words != null }) {
                    lyricRefreshRate = lyricsUpdateSpeed.toLrcRefreshMillis()
                } else {
                    lyricRefreshRate = Speed.SLOW.toLrcRefreshMillis()
                }
            } else {
                lines.add(
                    LyricLine(
                        model.unsyncedText.joinToString { "${it.first}\n" }, 0L.toULong(), 0L.toULong(),
                        null, null, false
                    )
                )
                lyricRefreshRate = Speed.SLOW.toLrcRefreshMillis()
            }
        }
    }

    val textColor = MaterialTheme.colorScheme.secondary
    val prevTextColor = MaterialTheme.colorScheme.primary

    var currentLineIndex by remember {
        mutableIntStateOf(-1)
    }
    // Because LaunchedEffect has delay, which leads to inconsistent with current line color and scroll animation,
    // we use deferredCurrentLineIndex when user is scrolling
    var deferredCurrentLineIndex by rememberSaveable {
        mutableIntStateOf(0)
    }

    var lastPreviewTime by rememberSaveable {
        mutableLongStateOf(0L)
    }
    var isSeeking by remember {
        mutableStateOf(false)
    }
    var currentPos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(lyricsModel) {
        if (lyricsModel == null || !isSynced || (lyricsModel as SemanticLyrics.SyncedLyrics).text.isEmpty()) {
            currentLineIndex = -1
            return@LaunchedEffect
        }
        while (isActive) {
            // TODO: likely can improve power usage by disabling lyric refresh
            delay(lyricRefreshRate)
            if (!playerConnection.isPlaying.value) continue
            val sliderPosition = sliderPositionProvider()
            isSeeking = sliderPosition != null
            currentLineIndex = findCurrentLineIndex(lines, sliderPosition ?: playerConnection.player.currentPosition)
            currentPos = sliderPosition ?: playerConnection.player.currentPosition
        }
    }

    LaunchedEffect(isSeeking, lastPreviewTime) {
        if (isSeeking) {
            lastPreviewTime = 0L
        } else if (lastPreviewTime != 0L) {
            delay(LyricsPreviewTime)
            lastPreviewTime = 0L
        }
    }

    val lazyListState = rememberLazyListState()

    LaunchedEffect(currentLineIndex, lastPreviewTime) {
        /**
         * Count number of new lines in a lyric
         */
        fun countNewLine(str: String) = str.count { it == '\n' }

        /**
         * Calculate the lyric offset Based on how many lines (\n chars)
         */
        fun calculateOffset() = with(density) {
            if (landscapeOffset) {
                16.dp.toPx().toInt() * countNewLine(lines[currentLineIndex].text) // landscape sits higher by default
            } else {
                20.dp.toPx().toInt() * countNewLine(lines[currentLineIndex].text)
            }
        }

        if (!isSynced) return@LaunchedEffect
        if (currentLineIndex != -1) {
            deferredCurrentLineIndex = currentLineIndex
            if (lastPreviewTime == 0L) {
                if (isSeeking) {
                    lazyListState.scrollToItem(
                        currentLineIndex,
                        with(density) { 36.dp.toPx().toInt() } + calculateOffset())
                } else {
                    lazyListState.animateScrollToItem(
                        currentLineIndex,
                        with(density) { 36.dp.toPx().toInt() } + calculateOffset())
                }
            }
        }
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 12.dp)
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = WindowInsets.systemBars
                .only(WindowInsetsSides.Top)
                .add(WindowInsets(top = maxHeight / 2, bottom = maxHeight / 2))
                .asPaddingValues(),
            modifier = Modifier
                .fadingEdge(vertical = 64.dp)
                .nestedScroll(remember {
                    object : NestedScrollConnection {
                        override fun onPostScroll(
                            consumed: Offset,
                            available: Offset,
                            source: NestedScrollSource
                        ): Offset {
                            lastPreviewTime = System.currentTimeMillis()
                            return super.onPostScroll(consumed, available, source)
                        }

                        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                            lastPreviewTime = System.currentTimeMillis()
                            return super.onPostFling(consumed, available)
                        }
                    }
                })
        ) {
            val displayedCurrentLineIndex = if (isSeeking) deferredCurrentLineIndex else currentLineIndex

            if (lyricsModel == null) {
                item {
                    ShimmerHost {
                        repeat(10) {
                            Box(
                                contentAlignment = when (lyricsTextPosition) {
                                    LyricsPosition.LEFT -> Alignment.CenterStart
                                    LyricsPosition.CENTER -> Alignment.Center
                                    LyricsPosition.RIGHT -> Alignment.CenterEnd
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 4.dp)
                            ) {
                                TextPlaceholder()
                            }
                        }
                    }
                }
            } else if (lyricsModel != uninitializedLyric) {
                val maxW = maxWidth - 48.dp
                itemsIndexed(
                    items = lines
                ) { index, item ->
                    var lyricFontSizeAdjusted = lyricsFontSize
                    if (item.speaker?.isBackground == true) {
                        lyricFontSizeAdjusted = (lyricFontSizeAdjusted * 0.75).toInt()
                    }
                    if (item.isTranslated) {
                        lyricFontSizeAdjusted = (lyricFontSizeAdjusted * 0.75).toInt()
                    }

                    Column(
                        horizontalAlignment = when (lyricsTextPosition) {
                            LyricsPosition.LEFT -> Alignment.Start
                            LyricsPosition.CENTER -> Alignment.CenterHorizontally
                            LyricsPosition.RIGHT -> Alignment.End
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 24.dp,
                                top = if (item.isTranslated) 0.dp else 8.dp,
                                end = 24.dp,
                                bottom = if (item.isTranslated) 16.dp else 8.dp,
                            )
                            // we allow clicking on blank lyrics, ignore item.isClickable
                            .clickable(enabled = isSynced && lyricsClickable) {
                                playerConnection.player.seekTo(item.start.toLong())
                                currentLineIndex = index
                                currentPos = item.start.toLong()
                                lastPreviewTime = 0L
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            }
                    ) {
                        if (currentPos.toULong() in item.start..item.end + 100.toULong() && lyricsFancy
                            && item.words != null && !context.isPowerSaver()
                        ) { // word by word
                            // now do eye bleach to make lyric line babies
                            val style = LocalTextStyle.current.copy(
                                fontSize = lyricsFontSize.sp,
                                color = textColor,
                                fontWeight = FontWeight.Bold
                            )
                            val rawSplitLines = splitTextToLines(item.text, style, maxW)
                            val lyricLines = ArrayList<LyricLine>()
                            if (rawSplitLines.size > 1) {
                                var from = 0
                                for (i in rawSplitLines) {
                                    val to = from + i.split(' ').size
                                    val words = item.words.subList(from, to.coerceIn(from, item.words.size))
                                    lyricLines.add(
                                        item.copy(
                                            text = i,
                                            start = words.first().timeRange.start,
                                            end = words.last().timeRange.endInclusive,
                                            words = words
                                        )
                                    )
                                    from = to
                                }
                            } else {
                                lyricLines.add(item)
                            }


                            lyricLines.forEach {
                                HorizontalReveal(
                                    progress = calculateLineProgress(it, currentPos),
                                    modifier = Modifier
                                ) {
                                    Text(
                                        text = it.text,
                                        fontSize = lyricFontSizeAdjusted.sp,
                                        color = textColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }


                        } else { // regular
                            val isConsumed = currentPos.toULong() > (item.end + 100.toULong())
                            val isHighlighted =
                                ((index == displayedCurrentLineIndex || (index == displayedCurrentLineIndex + 1 && item.isTranslated)))
                            Text(
                                text = item.text,
                                fontSize = lyricFontSizeAdjusted.sp,
                                color = if (isConsumed && !isHighlighted) prevTextColor else textColor,
                                textAlign = when (lyricsTextPosition) {
                                    LyricsPosition.LEFT -> TextAlign.Left
                                    LyricsPosition.CENTER -> TextAlign.Center
                                    LyricsPosition.RIGHT -> TextAlign.Right
                                },
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.alpha(
                                    if (!isSynced || isHighlighted) {
                                        1f
                                    } else if (isConsumed) {
                                        0.6f
                                    } else {
                                        0.5f
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }

        if (lyricsModel == uninitializedLyric) {
            Text(
                text = stringResource(R.string.lyrics_not_found),
                fontSize = lyricsFontSize.sp,
                color = textColor,
                textAlign = when (lyricsTextPosition) {
                    LyricsPosition.LEFT -> TextAlign.Left
                    LyricsPosition.CENTER -> TextAlign.Center
                    LyricsPosition.RIGHT -> TextAlign.Right
                },
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        mediaMetadata?.let { mediaMetadata ->
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp)
            ) {
                IconButton(
                    onClick = { onShowLyricsChange(false) }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = textColor
                    )
                }
                IconButton(
                    onClick = {
                        menuState.show {
                            LyricsMenu(
                                lyricsProvider = {
                                    var dbLyric = runBlocking(Dispatchers.IO) {
                                        playerConnection.service.database.lyrics(mediaMetadata.id).first()
                                    }

                                    // eye bleach to try to load local file for editor
                                    if (dbLyric == null && mediaMetadata.localPath != null) {
                                        LrcUtils.loadLyricsFile(File(mediaMetadata.localPath))?.let {
                                            dbLyric = LyricsEntity(mediaMetadata.id, it)
                                        }
                                    }

                                    dbLyric
                                },
                                mediaMetadataProvider = { mediaMetadata },
                                onRefreshRequest = { lyricsModel = it },
                                onDismiss = menuState::dismiss,
                            )
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = null,
                        tint = textColor
                    )
                }
            }
        }
    }
}

@Composable
fun HorizontalReveal(
    progress: Float,
    modifier: Modifier = Modifier,
    backgroundAlpha: Float = 0.5f,
    rtl: Boolean = false,
    content: @Composable () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 100, easing = LinearEasing)
    )

    Box(modifier = modifier.padding(start = 1.dp)) {
        Box(modifier = Modifier.alpha(backgroundAlpha)) {
            content()
        }

        Box(
            modifier = Modifier
                .graphicsLayer {
                    clip = true
                    shape = RectangleShape
                }
                .drawWithContent {
                    val clipWidth = size.width * animatedProgress
                    val left = if (!rtl) 0f else size.width - clipWidth
                    val right = if (!rtl) clipWidth else size.width
                    clipRect(left, 0f, right, size.height) {
                        this@drawWithContent.drawContent()
                    }
                }
        ) {
            content()
        }
    }
}

@Composable
fun splitTextToLines(
    text: String,
    style: TextStyle,
    maxWidth: Dp
): List<String> {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var currentLine = ""

    for (word in words) {
        val tentativeLine = if (currentLine.isEmpty()) word else "$currentLine $word"
        val tentativeWidth = with(density) {
            textMeasurer.measure(
                tentativeLine, style,
            ).size.width.toDp()
        }

        if (tentativeWidth < maxWidth) {
            currentLine = tentativeLine
        } else {
            lines.add(currentLine)
            currentLine = word
        }
    }

    if (currentLine.isNotEmpty()) {
        lines.add(currentLine)
    }

    return lines
}

/**
 * Get current position in lyric line list
 */
fun findCurrentLineIndex(lines: List<LyricLine>, position: Long): Int {
    for (index in lines.indices) {
        if (lines[index].start > (position).toUInt()) {
            return if (index > 0 && lines[index - 1].isTranslated) index - 2 else index - 1
        }
    }
    return if (lines[lines.lastIndex].isTranslated) lines.lastIndex - 1 else lines.lastIndex
}

/**
 * Get current position in lyric line. Used for word by word lyrics
 */
fun calculateLineProgress(line: LyricLine, currentPositionMs: Long): Float {
    val words = line.words
    val startMs = line.start.toLong()
    val endMs = line.end.toLong()

    // by line if no words are available
    if (words.isNullOrEmpty()) {
        return when {
            currentPositionMs < startMs -> 0f
            currentPositionMs > endMs -> 1f // add buffer so lyric line animation completes
            else -> (currentPositionMs - startMs).toFloat() / (endMs - startMs).toFloat()
        }
    }

    // progress based on words
    val currentMs = currentPositionMs.toULong()
    var completedWords = 0
    var partialProgress = 0f

    return when {
        currentPositionMs < startMs -> 0f
        currentPositionMs > endMs -> 1f // add buffer so lyric line animation completes
        else -> {
            for (i in words.indices) {
                val word = words[i]
                val start = word.timeRange.first
                val end = word.timeRange.last

                if (currentMs < start) {
                    break // we're before this word
                } else if (currentMs in word.timeRange) {
                    val wordDuration = (end - start).coerceAtLeast(1u).toFloat()
                    partialProgress = (currentMs - start).toFloat() / wordDuration
                    completedWords = i
                    break
                } else {
                    completedWords++
                }
            }

            val totalWords = words.size.toFloat()
            var progress = (completedWords + partialProgress) / totalWords
            progress.coerceIn(0f, 1f)
        }
    }
}

const val animateScrollDuration = 300L
val LyricsPreviewTime = 7.seconds
