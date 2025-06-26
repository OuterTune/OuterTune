/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavController
import com.dd3boh.outertune.db.entities.Album
import com.dd3boh.outertune.db.entities.Artist
import com.dd3boh.outertune.db.entities.Playlist
import com.dd3boh.outertune.ui.menu.AlbumMenu
import com.dd3boh.outertune.ui.menu.ArtistMenu
import com.dd3boh.outertune.ui.menu.PlaylistMenu
import com.dd3boh.outertune.ui.menu.YouTubePlaylistMenu
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.WatchEndpoint
import kotlinx.coroutines.CoroutineScope

@Composable
fun LibraryArtistListItem(
    navController: NavController,
    menuState: MenuState,
    coroutineScope: CoroutineScope,
    artist: Artist,
    modifier: Modifier = Modifier
) = ArtistListItem(
    artist = artist,
    trailingContent = {
        val haptic = LocalHapticFeedback.current
        androidx.compose.material3.IconButton(
            onClick = {
                menuState.show {
                    ArtistMenu(
                        originalArtist = artist,
                        coroutineScope = coroutineScope,
                        onDismiss = menuState::dismiss
                    )
                }
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            }
        ) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = null
            )
        }
    },
    modifier = modifier
        .fillMaxWidth()
        .clickable {
            navController.navigate("artist/${artist.id}")
        }
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryArtistGridItem(
    navController: NavController,
    menuState: MenuState,
    coroutineScope: CoroutineScope,
    artist: Artist,
    modifier: Modifier = Modifier
) = ArtistGridItem(
    artist = artist,
    fillMaxWidth = true,
    modifier = modifier
        .fillMaxWidth()
        .combinedClickable(
            onClick = {
                navController.navigate("artist/${artist.id}")
            },
            onLongClick = {
                menuState.show {
                    ArtistMenu(
                        originalArtist = artist,
                        coroutineScope = coroutineScope,
                        onDismiss = menuState::dismiss
                    )
                }
            }
        )
)

@Composable
fun LibraryAlbumListItem(
    modifier: Modifier = Modifier,
    navController: NavController,
    menuState: MenuState,
    album: Album,
    isActive: Boolean = false,
    isPlaying: Boolean = false
) = AlbumListItem(
    album = album,
    isActive = isActive,
    isPlaying = isPlaying,
    trailingContent = {
        val haptic = LocalHapticFeedback.current
        androidx.compose.material3.IconButton(
            onClick = {
                menuState.show {
                    AlbumMenu(
                        originalAlbum = album,
                        navController = navController,
                        onDismiss = menuState::dismiss
                    )
                }
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            }
        ) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = null
            )
        }
    },
    modifier = modifier
        .fillMaxWidth()
        .clickable {
            navController.navigate("album/${album.id}")
        }
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryAlbumGridItem(
    modifier: Modifier = Modifier,
    navController: NavController,
    menuState: MenuState,
    coroutineScope: CoroutineScope,
    album: Album,
    isActive: Boolean = false,
    isPlaying: Boolean = false
) = AlbumGridItem(
    album = album,
    isActive = isActive,
    isPlaying = isPlaying,
    coroutineScope = coroutineScope,
    fillMaxWidth = true,
    modifier = modifier
        .fillMaxWidth()
        .combinedClickable(
            onClick = {
                navController.navigate("album/${album.id}")
            },
            onLongClick = {
                menuState.show {
                    AlbumMenu(
                        originalAlbum = album,
                        navController = navController,
                        onDismiss = menuState::dismiss
                    )
                }
            }
        )
)

@Composable
fun LibraryPlaylistListItem(
    navController: NavController,
    menuState: MenuState,
    coroutineScope: CoroutineScope,
    playlist: Playlist,
    modifier: Modifier = Modifier
) = PlaylistListItem(
    playlist = playlist,
    trailingContent = {
        val haptic = LocalHapticFeedback.current
        androidx.compose.material3.IconButton(
            onClick = {
                menuState.show {
                    // TODO: investigate why song count is needed. Remove if not needed
                    if (playlist.playlist.isEditable || playlist.playlist.isLocal || playlist.playlist.browseId == null || playlist.songCount != 0) {
                        PlaylistMenu(
                            navController = navController,
                            playlist = playlist,
                            coroutineScope = coroutineScope,
                            onDismiss = menuState::dismiss
                        )
                    } else {
                        val browseId = playlist.playlist.browseId
                        YouTubePlaylistMenu(
                            navController = navController,
                            playlist = PlaylistItem(
                                id = browseId,
                                title = playlist.playlist.name,
                                author = null,
                                songCountText = null,
                                thumbnail = playlist.thumbnails.getOrNull(0),
                                playEndpoint = WatchEndpoint(
                                    playlistId = browseId,
                                    params = playlist.playlist.playEndpointParams
                                ),
                                shuffleEndpoint = WatchEndpoint(
                                    playlistId = browseId,
                                    params = playlist.playlist.shuffleEndpointParams
                                ),
                                radioEndpoint = WatchEndpoint(
                                    playlistId = "RDAMPL$browseId",
                                    params = playlist.playlist.radioEndpointParams
                                ),
                                isEditable = false
                            ),
                            coroutineScope = coroutineScope,
                            onDismiss = menuState::dismiss
                        )
                    }
                }
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            }
        ) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = null
            )
        }
    },
    modifier = modifier
        .fillMaxWidth()
        .clickable {
            if (playlist.playlist.isEditable || playlist.playlist.isLocal || playlist.playlist.browseId == null || playlist.songCount != 0) {
                navController.navigate("local_playlist/${playlist.id}")
            } else {
                navController.navigate("online_playlist/${playlist.playlist.browseId}")
            }
        }
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPlaylistGridItem(
    navController: NavController,
    menuState: MenuState,
    coroutineScope: CoroutineScope,
    playlist: Playlist,
    modifier: Modifier = Modifier
) = PlaylistGridItem(
    playlist = playlist,
    fillMaxWidth = true,
    modifier = modifier
        .fillMaxWidth()
        .combinedClickable(
            onClick = {
                if (playlist.playlist.isEditable || playlist.playlist.isLocal || playlist.playlist.browseId == null || playlist.songCount != 0) {
                    navController.navigate("local_playlist/${playlist.id}")
                } else {
                    navController.navigate("online_playlist/${playlist.playlist.browseId}")
                }
            },
            onLongClick = {
                menuState.show {
                    if (playlist.playlist.isEditable || playlist.songCount != 0) {
                        PlaylistMenu(
                            navController = navController,
                            playlist = playlist,
                            coroutineScope = coroutineScope,
                            onDismiss = menuState::dismiss
                        )
                    } else {
                        playlist.playlist.browseId?.let { browseId ->
                            YouTubePlaylistMenu(
                                navController = navController,
                                playlist = PlaylistItem(
                                    id = browseId,
                                    title = playlist.playlist.name,
                                    author = null,
                                    songCountText = null,
                                    thumbnail = playlist.thumbnails.getOrNull(0),
                                    playEndpoint = WatchEndpoint(
                                        playlistId = browseId,
                                        params = playlist.playlist.playEndpointParams
                                    ),
                                    shuffleEndpoint = WatchEndpoint(
                                        playlistId = browseId,
                                        params = playlist.playlist.shuffleEndpointParams
                                    ),
                                    radioEndpoint = WatchEndpoint(
                                        playlistId = "RDAMPL$browseId",
                                        params = playlist.playlist.radioEndpointParams
                                    ),
                                    isEditable = false
                                ),
                                coroutineScope = coroutineScope,
                                onDismiss = menuState::dismiss
                            )
                        }
                    }
                }
            }
        )
)
