package com.dd3boh.outertune.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.StatPeriod
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.playback.queues.YouTubeQueue
import com.dd3boh.outertune.ui.component.AlbumGridItem
import com.dd3boh.outertune.ui.component.ArtistGridItem
import com.dd3boh.outertune.ui.component.ChipsRow
import com.dd3boh.outertune.ui.component.IconButton
import com.dd3boh.outertune.ui.component.LocalMenuState
import com.dd3boh.outertune.ui.component.NavigationTitle
import com.dd3boh.outertune.ui.component.SongListItem
import com.dd3boh.outertune.ui.menu.AlbumMenu
import com.dd3boh.outertune.ui.menu.ArtistMenu
import com.dd3boh.outertune.ui.utils.backToMain
import com.dd3boh.outertune.viewmodels.StatsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val statPeriod by viewModel.statPeriod.collectAsState()
    val mostPlayedSongs by viewModel.mostPlayedSongs.collectAsState()
    val mostPlayedArtists by viewModel.mostPlayedArtists.collectAsState()
    val mostPlayedAlbums by viewModel.mostPlayedAlbums.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom).asPaddingValues(),
        modifier = Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top))
    ) {
        item {
            ChipsRow(
                chips = listOf(
                    StatPeriod.`1_WEEK` to pluralStringResource(R.plurals.n_week, 1, 1),
                    StatPeriod.`1_MONTH` to pluralStringResource(R.plurals.n_month, 1, 1),
                    StatPeriod.`3_MONTH` to pluralStringResource(R.plurals.n_month, 3, 3),
                    StatPeriod.`6_MONTH` to pluralStringResource(R.plurals.n_month, 6, 6),
                    StatPeriod.`1_YEAR` to pluralStringResource(R.plurals.n_year, 1, 1),
                    StatPeriod.ALL to stringResource(R.string.filter_all)
                ),
                currentValue = statPeriod,
                onValueUpdate = { viewModel.statPeriod.value = it }
            )
        }

        item(key = "mostPlayedSongs") {
            NavigationTitle(
                title = stringResource(R.string.most_played_songs),
                modifier = Modifier.animateItem()
            )
        }

        items(
            items = mostPlayedSongs,
            key = { it.id }
        ) { song ->
            SongListItem(
                song = song,
                onPlay = {
                    playerConnection.playQueue(
                        YouTubeQueue.radio(song.toMediaMetadata())
                    )
                },
                onSelectedChange = {},
                inSelectMode = false,
                isSelected = false,
                navController = navController,
                modifier = Modifier.fillMaxWidth().animateItem()
            )
        }

        item(key = "mostPlayedArtists") {
            NavigationTitle(
                title = stringResource(R.string.most_played_artists),
                modifier = Modifier.animateItem()
            )

            LazyRow(
                modifier = Modifier.animateItem()
            ) {
                items(
                    items = mostPlayedArtists,
                    key = { it.id }
                ) { artist ->
                    ArtistGridItem(
                        artist = artist,
                        modifier = Modifier
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
                            .animateItem()
                    )
                }
            }
        }

        if (mostPlayedAlbums.isNotEmpty()) {
            item(key = "mostPlayedAlbums") {
                NavigationTitle(
                    title = stringResource(R.string.most_played_albums),
                    modifier = Modifier.animateItem()
                )

                LazyRow(
                    modifier = Modifier.animateItem()
                ) {
                    items(
                        items = mostPlayedAlbums,
                        key = { it.id }
                    ) { album ->
                        AlbumGridItem(
                            album = album,
                            isActive = album.id == mediaMetadata?.album?.id,
                            isPlaying = isPlaying,
                            coroutineScope = coroutineScope,
                            modifier = Modifier
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
                                .animateItem()
                        )
                    }
                }
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.stats)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null
                )
            }
        }
    )
}
