package com.dd3boh.outertune.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalDownloadUtil
import com.dd3boh.outertune.LocalIsNetworkConnected
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.AppBarHeight
import com.dd3boh.outertune.constants.SearchFilterHeight
import com.dd3boh.outertune.extensions.isAvailableOffline
import com.dd3boh.outertune.extensions.toMediaItem
import com.dd3boh.outertune.extensions.togglePlayPause
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.playback.queues.ListQueue
import com.dd3boh.outertune.playback.queues.YouTubeQueue
import com.dd3boh.outertune.ui.component.ChipsRow
import com.dd3boh.outertune.ui.component.EmptyPlaceholder
import com.dd3boh.outertune.ui.component.LocalMenuState
import com.dd3boh.outertune.ui.component.NavigationTitle
import com.dd3boh.outertune.ui.component.SwipeToQueueBox
import com.dd3boh.outertune.ui.component.YouTubeListItem
import com.dd3boh.outertune.ui.component.shimmer.ListItemPlaceHolder
import com.dd3boh.outertune.ui.component.shimmer.ShimmerHost
import com.dd3boh.outertune.ui.menu.YouTubeAlbumMenu
import com.dd3boh.outertune.ui.menu.YouTubeArtistMenu
import com.dd3boh.outertune.ui.menu.YouTubePlaylistMenu
import com.dd3boh.outertune.ui.menu.YouTubeSongMenu
import com.dd3boh.outertune.viewmodels.OnlineSearchViewModel
import com.zionhuang.innertube.YouTube.SearchFilter.Companion.FILTER_ALBUM
import com.zionhuang.innertube.YouTube.SearchFilter.Companion.FILTER_ARTIST
import com.zionhuang.innertube.YouTube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import com.zionhuang.innertube.YouTube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import com.zionhuang.innertube.YouTube.SearchFilter.Companion.FILTER_SONG
import com.zionhuang.innertube.YouTube.SearchFilter.Companion.FILTER_VIDEO
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.ArtistItem
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.YTItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnlineSearchResult(
    navController: NavController,
    viewModel: OnlineSearchViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isNetworkConnected = LocalIsNetworkConnected.current
    val downloads by LocalDownloadUtil.current.downloads.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    val searchFilter by viewModel.filter.collectAsState()
    val searchSummary = viewModel.summaryPage
    val itemsPage by remember(searchFilter) {
        derivedStateOf {
            searchFilter?.value?.let {
                viewModel.viewStateMap[it]
            }
        }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }.collect { shouldLoadMore ->
            if (!shouldLoadMore) return@collect
            viewModel.loadMore()
        }
    }

    val ytItemContent: @Composable LazyItemScope.(YTItem) -> Unit = { item: YTItem ->
        var available = true
        if (item is SongItem) { available = downloads[item.id]?.isAvailableOffline() ?: false || isNetworkConnected }

        val content: @Composable () -> Unit = {
            YouTubeListItem(
                item = item,
                isActive = when (item) {
                    is SongItem -> mediaMetadata?.id == item.id
                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                    else -> false
                },
                isPlaying = isPlaying,
                trailingContent = {
                    if (available) {
                        IconButton(
                            onClick = {
                                menuState.show {
                                    when (item) {
                                        is SongItem -> YouTubeSongMenu(
                                            song = item,
                                            navController = navController,
                                            onDismiss = menuState::dismiss
                                        )

                                        is AlbumItem -> YouTubeAlbumMenu(
                                            albumItem = item,
                                            navController = navController,
                                            onDismiss = menuState::dismiss
                                        )

                                        is ArtistItem -> YouTubeArtistMenu(
                                            artist = item,
                                            onDismiss = menuState::dismiss
                                        )

                                        is PlaylistItem -> YouTubePlaylistMenu(
                                            playlist = item,
                                            coroutineScope = coroutineScope,
                                            onDismiss = menuState::dismiss
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = null
                            )
                        }
                    }
                },
                modifier = Modifier
                    .combinedClickable(
                        onClick = {
                            when (item) {
                                is SongItem -> {
                                    if (available) {
                                        if (item.id == mediaMetadata?.id) {
                                            playerConnection.player.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                if (isNetworkConnected) {
                                                    YouTubeQueue.radio(item.toMediaMetadata())
                                                }
                                                else {
                                                    ListQueue(
                                                        title = "${context.getString(R.string.queue_searched_songs)} $viewModel.query",
                                                        items = listOf(item.toMediaMetadata())
                                                    )
                                                },
                                                replace = true,
                                            )
                                        }
                                    }
                                }

                                is AlbumItem -> navController.navigate("album/${item.id}")
                                is ArtistItem -> navController.navigate("artist/${item.id}")
                                is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                            }
                        },
                        onLongClick = {
                            menuState.show {
                                when (item) {
                                    is SongItem -> YouTubeSongMenu(
                                        song = item,
                                        navController = navController,
                                        onDismiss = menuState::dismiss
                                    )

                                    else -> {}
                                }
                            }
                        }
                    )
                    .animateItem()
            )
        }

        if (item !is SongItem) content()
        else SwipeToQueueBox(
            enabled = available,
            item = item.toMediaItem(),
            content = { content() },
            snackbarHostState = snackbarHostState
        )
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = LocalPlayerAwareWindowInsets.current
            .add(WindowInsets(top = SearchFilterHeight))
            .asPaddingValues()
    ) {
        if (searchFilter == null) {
            searchSummary?.summaries?.forEach { summary ->
                item {
                    NavigationTitle(summary.title)
                }

                items(
                    items = summary.items,
                    key = { "${summary.title}/${it.id}" },
                    itemContent = ytItemContent
                )
            }

            if (searchSummary?.summaries?.isEmpty() == true) {
                item {
                    EmptyPlaceholder(
                        icon = Icons.Rounded.Search,
                        text = stringResource(R.string.no_results_found),
                        modifier = Modifier.animateItem()
                    )
                }
            }
        } else {
            items(
                items = itemsPage?.items.orEmpty(),
                key = { it.id },
                itemContent = ytItemContent
            )

            if (itemsPage?.continuation != null) {
                item(key = "loading") {
                    ShimmerHost {
                        repeat(3) {
                            ListItemPlaceHolder()
                        }
                    }
                }
            }

            if (itemsPage?.items?.isEmpty() == true) {
                item {
                    EmptyPlaceholder(
                        icon = Icons.Rounded.Search,
                        text = stringResource(R.string.no_results_found),
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }

        if (searchFilter == null && searchSummary == null || searchFilter != null && itemsPage == null) {
            item {
                ShimmerHost {
                    repeat(8) {
                        ListItemPlaceHolder()
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .align(Alignment.BottomCenter)
        )
    }

    ChipsRow(
        chips = listOf(
            null to stringResource(R.string.filter_all),
            FILTER_SONG to stringResource(R.string.filter_songs),
            FILTER_VIDEO to stringResource(R.string.filter_videos),
            FILTER_ALBUM to stringResource(R.string.filter_albums),
            FILTER_ARTIST to stringResource(R.string.filter_artists),
            FILTER_COMMUNITY_PLAYLIST to stringResource(R.string.filter_community_playlists),
            FILTER_FEATURED_PLAYLIST to stringResource(R.string.filter_featured_playlists)
        ),
        currentValue = searchFilter,
        onValueUpdate = {
            if (viewModel.filter.value != it) {
                viewModel.filter.value = it
            }
            coroutineScope.launch {
                lazyListState.animateScrollToItem(0)
            }
        },
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            .padding(top = AppBarHeight)
    )
}
