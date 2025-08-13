/*
 * Copyright (C) 2025 O⁠ute⁠rTu⁠ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */
package com.dd3boh.outertune.ui.component.items

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dd3boh.outertune.constants.ListThumbnailSize
import com.dd3boh.outertune.db.entities.Artist
import com.dd3boh.outertune.ui.utils.getNSongsString

@Composable
fun ArtistListItem(
    artist: Artist,
    modifier: Modifier = Modifier.Companion,
    badges: @Composable RowScope.() -> Unit = {
        if (artist.artist.bookmarkedAt != null) {
            Icon.Favorite()
        }

        // assume if they have a non local artist ID, they are not local
        if (artist.artist.isLocal) {
            Icon(
                Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.Companion
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }

        if (artist.downloadCount > 0) {
            Icon(
                imageVector = Icons.Rounded.OfflinePin,
                contentDescription = null,
                modifier = Modifier.Companion
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }
    },
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = artist.artist.name,
    subtitle = getNSongsString(artist.songCount, artist.downloadCount),
    badges = badges,
    thumbnailContent = {
        AsyncImage(
            model = artist.artist.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.Companion
                .size(ListThumbnailSize)
                .clip(CircleShape)
        )
    },
    trailingContent = trailingContent,
    modifier = modifier
)

@Composable
fun ArtistGridItem(
    artist: Artist,
    modifier: Modifier = Modifier.Companion,
    badges: @Composable RowScope.() -> Unit = {
        if (artist.artist.bookmarkedAt != null) {
            Icon.Favorite()
        }

        // assume if they have a non local artist ID, they are not local
        if (artist.artist.isLocal) {
            Icon(
                Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.Companion
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }

        if (artist.downloadCount > 0) {
            Icon(
                imageVector = Icons.Rounded.OfflinePin,
                contentDescription = null,
                modifier = Modifier.Companion
                    .size(18.dp)
                    .padding(end = 2.dp)
            )
        }
    },
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = artist.artist.name,
    subtitle = getNSongsString(artist.songCount, artist.downloadCount),
    badges = badges,
    thumbnailContent = {
        AsyncImage(
            model = artist.artist.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Companion.Crop,
            modifier = Modifier.Companion
                .fillMaxSize()
                .clip(CircleShape)
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

