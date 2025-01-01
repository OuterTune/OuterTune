package com.dd3boh.outertune.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.LocalDownloadUtil
import com.dd3boh.outertune.LocalIsNetworkConnected
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.SuggestionItemHeight
import com.dd3boh.outertune.extensions.isAvailableOffline
import com.dd3boh.outertune.extensions.toMediaItem
import com.dd3boh.outertune.extensions.togglePlayPause
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.playback.queues.ListQueue
import com.dd3boh.outertune.playback.queues.YouTubeQueue
import com.dd3boh.outertune.ui.component.LocalMenuState
import com.dd3boh.outertune.ui.component.SearchBarIconOffsetX
import com.dd3boh.outertune.ui.component.SwipeToQueueBox
import com.dd3boh.outertune.ui.component.YouTubeListItem
import com.dd3boh.outertune.ui.menu.YouTubeAlbumMenu
import com.dd3boh.outertune.ui.menu.YouTubeArtistMenu
import com.dd3boh.outertune.ui.menu.YouTubePlaylistMenu
import com.dd3boh.outertune.ui.menu.YouTubeSongMenu
import com.dd3boh.outertune.viewmodels.OnlineSearchSuggestionViewModel
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.ArtistItem
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.SongItem
import kotlinx.coroutines.flow.drop

@Composable
fun OnlineSearchScreen(
    query: String,
    onQueryChange: (TextFieldValue) -> Unit,
    navController: NavController,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: OnlineSearchSuggestionViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isNetworkConnected = LocalIsNetworkConnected.current
    val downloads by LocalDownloadUtil.current.downloads.collectAsState()
    val scope = rememberCoroutineScope()

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val viewState by viewModel.viewState.collectAsState()

    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect {
                keyboardController?.hide()
            }
    }

    LaunchedEffect(query) {
        viewModel.query.value = query
    }

    LazyColumn(
        state = lazyListState
    ) {
        items(
            items = viewState.history,
            key = { it.query }
        ) { history ->
            SuggestionItem(
                query = history.query,
                online = false,
                onClick = {
                    onSearch(history.query)
                    onDismiss()
                },
                onDelete = {
                    database.query {
                        delete(history)
                    }
                },
                onFillTextField = {
                    onQueryChange(
                        TextFieldValue(
                            text = history.query,
                            selection = TextRange(history.query.length)
                        )
                    )
                },
                modifier = Modifier.animateItem()
            )
        }

        items(
            items = viewState.suggestions,
            key = { it }
        ) { query ->
            SuggestionItem(
                query = query,
                online = true,
                onClick = {
                    onSearch(query)
                    onDismiss()
                },
                onFillTextField = {
                    onQueryChange(
                        TextFieldValue(
                            text = query,
                            selection = TextRange(query.length)
                        )
                    )
                },
                modifier = Modifier.animateItem()
            )
        }

        if (viewState.items.isNotEmpty() && viewState.history.size + viewState.suggestions.size > 0) {
            item {
                HorizontalDivider()
            }
        }

        items(
            items = viewState.items,
            key = { it.id }
        ) { item ->
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
                                            is SongItem ->
                                                YouTubeSongMenu(
                                                    song = item,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )

                                            is AlbumItem ->
                                                YouTubeAlbumMenu(
                                                    albumItem = item,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )

                                            is ArtistItem ->
                                                YouTubeArtistMenu(
                                                    artist = item,
                                                    onDismiss = menuState::dismiss,
                                                )

                                            is PlaylistItem ->
                                                YouTubePlaylistMenu(
                                                    playlist = item,
                                                    coroutineScope = scope,
                                                    onDismiss = menuState::dismiss,
                                                )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = null
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .clickable {
                            when (item) {
                                is SongItem -> {
                                    if (available) {
                                        if (item.id == mediaMetadata?.id) {
                                            playerConnection.player.togglePlayPause()
                                        } else if (item.id.startsWith("LA")) {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = "Search: $query",
                                                    items = viewState.items.map { it as SongItem}.map { it.toMediaMetadata() }
                                                ),
                                                replace = true,
                                                title = "Search: $query",
                                            )
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
                                                title = "Search: $query",
                                            )
                                            onDismiss()
                                        }
                                    }
                                }

                                is AlbumItem -> {
                                    navController.navigate("album/${item.id}")
                                    onDismiss()
                                }

                                is ArtistItem -> {
                                    navController.navigate("artist/${item.id}")
                                    onDismiss()
                                }

                                is PlaylistItem -> {
                                    navController.navigate("online_playlist/${item.id}")
                                    onDismiss()
                                }
                            }
                        }
                        .animateItem()
                )
            }

            if (item !is SongItem) content()
            else {
                SwipeToQueueBox(
                    enabled = available,
                    item = item.toMediaItem(),
                    content = { content() },
                    snackbarHostState = snackbarHostState
                )
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
}

@Composable
fun SuggestionItem(
    modifier: Modifier = Modifier,
    query: String,
    online: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    onFillTextField: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(SuggestionItemHeight)
            .clickable(onClick = onClick)
            .padding(end = SearchBarIconOffsetX)
    ) {
        Icon(
            if (online) Icons.Rounded.Search else Icons.Rounded.History,
            contentDescription = null,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .alpha(0.5f)
        )

        Text(
            text = query,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (!online) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.alpha(0.5f)
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = null
                )
            }
        }

        IconButton(
            onClick = onFillTextField,
            modifier = Modifier.alpha(0.5f)
        ) {
            Icon(
                Icons.Rounded.ArrowOutward,
                contentDescription = null
            )
        }
    }
}
