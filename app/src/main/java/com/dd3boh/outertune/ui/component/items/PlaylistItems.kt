/*
 * Copyright (C) 2025 O﻿ute﻿rTu﻿ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */
package com.dd3boh.outertune.ui.component.items

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditOff
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import coil3.compose.AsyncImage
import com.dd3boh.outertune.LocalImageCache
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.ListThumbnailSize
import com.dd3boh.outertune.constants.ThumbnailCornerRadius
import com.dd3boh.outertune.db.entities.Playlist
import com.dd3boh.outertune.db.entities.PlaylistEntity
import com.dd3boh.outertune.ui.component.AsyncImageLocal
import com.dd3boh.outertune.ui.utils.getNSongsString

@Composable
fun AutoPlaylistListItem(
    playlist: PlaylistEntity,
    thumbnail: ImageVector,
    modifier: Modifier = Modifier.Companion,
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = playlist.name,
    subtitle = stringResource(id = R.string.auto_playlist),
    thumbnailContent = {
        Box(
            modifier = Modifier.Companion
                .size(ListThumbnailSize)
                .background(
                    MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                    shape = RoundedCornerShape(ThumbnailCornerRadius)
                )
        ) {
            Icon(
                imageVector = thumbnail,
                contentDescription = null,
                modifier = Modifier.Companion
                    .size(ListThumbnailSize / 2 + 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp))
                    .align(Alignment.Companion.Center)
            )
        }
    },
    trailingContent = trailingContent,
    modifier = modifier
)

@Composable
fun AutoPlaylistGridItem(
    playlist: PlaylistEntity,
    thumbnail: ImageVector,
    modifier: Modifier = Modifier.Companion,
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = playlist.name,
    subtitle = stringResource(id = R.string.auto_playlist),
    thumbnailContent = {
        val width = maxWidth
        Box(
            modifier = Modifier.Companion
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(ThumbnailCornerRadius)
                )
        ) {
            Icon(
                imageVector = thumbnail,
                contentDescription = null,
                tint = LocalContentColor.current.copy(alpha = 0.8f),
                modifier = Modifier.Companion
                    .size(width / 2 + 10.dp)
                    .align(Alignment.Companion.Center)
            )
        }
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun PlaylistListItem(
    playlist: Playlist,
    modifier: Modifier = Modifier.Companion,
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = playlist.playlist.name,
    subtitle =
        if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null)
            getNSongsString(playlist.playlist.remoteSongCount)
        else
            getNSongsString(playlist.songCount, playlist.downloadCount),
    badges = {
        Icon(
            imageVector = if (playlist.playlist.isEditable) Icons.Rounded.Edit else Icons.Rounded.EditOff,
            contentDescription = null,
            modifier = Modifier.Companion
                .size(18.dp)
                .padding(end = 2.dp)
        )

        if (playlist.playlist.isLocal) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = null,
                modifier = Modifier.Companion
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }

        if (playlist.downloadCount > 0) {
            Icon(
                imageVector = Icons.Rounded.OfflinePin,
                contentDescription = null,
                modifier = Modifier.Companion
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }
    },
    thumbnailContent = {
        PlaylistThumbnail(
            thumbnails = playlist.thumbnails,
            size = ListThumbnailSize,
            placeHolder = {
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                    modifier = Modifier.Companion.size(ListThumbnailSize)
                )
            },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(ThumbnailCornerRadius)
        )
    },
    trailingContent = trailingContent,
    modifier = modifier
)

@Composable
fun PlaylistGridItem(
    playlist: Playlist,
    modifier: Modifier = Modifier.Companion,
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = playlist.playlist.name,
    subtitle =
        if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null)
            getNSongsString(playlist.playlist.remoteSongCount)
        else
            getNSongsString(playlist.songCount, playlist.downloadCount),
    badges = {
        if (playlist.downloadCount > 0) {
            Icon(
                imageVector = Icons.Rounded.OfflinePin,
                contentDescription = null,
                modifier = Modifier.Companion
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }
    },
    thumbnailContent = {
        val width = maxWidth
        PlaylistThumbnail(
            thumbnails = playlist.thumbnails,
            size = width,
            placeHolder = {
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                    tint = LocalContentColor.current.copy(alpha = 0.8f),
                    modifier = Modifier.Companion
                        .size(width / 2)
                        .align(Alignment.Companion.Center)
                )
            },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(ThumbnailCornerRadius)
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun PlaylistThumbnail(
    thumbnails: List<String>,
    size: Dp,
    placeHolder: @Composable () -> Unit,
    shape: Shape,
) {
    val imageCache = LocalImageCache.current
    when (thumbnails.size) {
        0 -> placeHolder()

        1 -> if (thumbnails[0].startsWith("/storage")) {
            AsyncImage(
                model = thumbnails[0],
                contentDescription = null,
                contentScale = ContentScale.Companion.Crop,
                modifier = Modifier.Companion
                    .size(size)
                    .clip(shape)
            )
        } else {
            AsyncImage(
                model = thumbnails[0],
                contentDescription = null,
                contentScale = ContentScale.Companion.Crop,
                modifier = Modifier.Companion
                    .size(size)
                    .clip(shape)
            )
        }

        else -> Box(
            modifier = Modifier.Companion
                .size(size)
                .clip(shape)
        ) {
            Box(
                modifier = Modifier.Companion
                    .fillMaxSize()
                    .clip(shape)
            ) {
                listOf(
                    Alignment.Companion.TopStart,
                    Alignment.Companion.TopEnd,
                    Alignment.Companion.BottomStart,
                    Alignment.Companion.BottomEnd
                ).fastForEachIndexed { index, alignment ->
                    if (thumbnails.getOrNull(index)?.startsWith("/storage") == true) {
                        AsyncImageLocal(
                            image = { imageCache.getLocalThumbnail(thumbnails[index], true) },
                            contentScale = ContentScale.Companion.Crop,
                            modifier = Modifier.Companion
                                .align(alignment)
                                .size(size / 2)
                        )
                    } else {
                        AsyncImage(
                            model = thumbnails.getOrNull(index),
                            contentDescription = null,
                            contentScale = ContentScale.Companion.Crop,
                            modifier = Modifier.Companion
                                .align(alignment)
                                .size(size / 2)
                        )
                    }
                }
            }
        }
    }
}