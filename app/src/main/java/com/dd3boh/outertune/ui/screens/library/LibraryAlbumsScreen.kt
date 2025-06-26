package com.dd3boh.outertune.ui.screens.library

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.MainActivity
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.AlbumFilter
import com.dd3boh.outertune.constants.AlbumFilterKey
import com.dd3boh.outertune.constants.AlbumSortDescendingKey
import com.dd3boh.outertune.constants.AlbumSortType
import com.dd3boh.outertune.constants.AlbumSortTypeKey
import com.dd3boh.outertune.constants.AlbumViewTypeKey
import com.dd3boh.outertune.constants.CONTENT_TYPE_ALBUM
import com.dd3boh.outertune.constants.CONTENT_TYPE_HEADER
import com.dd3boh.outertune.constants.GridThumbnailHeight
import com.dd3boh.outertune.constants.LibraryViewType
import com.dd3boh.outertune.constants.LibraryViewTypeKey
import com.dd3boh.outertune.constants.LocalLibraryEnableKey
import com.dd3boh.outertune.ui.component.ChipsRow
import com.dd3boh.outertune.ui.component.EmptyPlaceholder
import com.dd3boh.outertune.ui.component.LibraryAlbumGridItem
import com.dd3boh.outertune.ui.component.LibraryAlbumListItem
import com.dd3boh.outertune.ui.component.LocalMenuState
import com.dd3boh.outertune.ui.component.SortHeader
import com.dd3boh.outertune.ui.utils.MEDIA_PERMISSION_LEVEL
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.viewmodels.LibraryAlbumsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryAlbumsScreen(
    navController: NavController,
    viewModel: LibraryAlbumsViewModel = hiltViewModel(),
    libraryFilterContent: @Composable() (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    var filter by rememberEnumPreference(AlbumFilterKey, AlbumFilter.LIKED)
    libraryFilterContent?.let { filter = AlbumFilter.LIKED }

    var albumViewType by rememberEnumPreference(AlbumViewTypeKey, LibraryViewType.GRID)
    val libraryViewType by rememberEnumPreference(LibraryViewTypeKey, LibraryViewType.GRID)
    val viewType = if (libraryFilterContent != null) libraryViewType else albumViewType

    val (sortType, onSortTypeChange) = rememberEnumPreference(AlbumSortTypeKey, AlbumSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(AlbumSortDescendingKey, true)
    val localLibEnable by rememberPreference(LocalLibraryEnableKey, defaultValue = true)

    val albums by viewModel.allAlbums.collectAsState()
    val isSyncingLibraryAlbums by viewModel.isSyncingRemoteAlbums.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    LaunchedEffect(Unit) { viewModel.syncAlbums() }

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            when (viewType) {
                LibraryViewType.LIST -> lazyListState.animateScrollToItem(0)
                LibraryViewType.GRID -> lazyGridState.animateScrollToItem(0)
            }
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val filterContent = @Composable {
        var showStoragePerm by remember {
            mutableStateOf(context.checkSelfPermission(MEDIA_PERMISSION_LEVEL) != PackageManager.PERMISSION_GRANTED)
        }
        Column {
            if (localLibEnable && showStoragePerm) {
                TextButton(
                    onClick = {
                        showStoragePerm =
                            false // allow user to hide error when clicked. This also makes the code a lot nicer too...
                        (context as MainActivity).permissionLauncher.launch(MEDIA_PERMISSION_LEVEL)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        text = stringResource(R.string.missing_media_permission_warning),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            Row {
                ChipsRow(
                    chips = listOf(
                        AlbumFilter.LIKED to stringResource(R.string.filter_liked),
                        AlbumFilter.LIBRARY to stringResource(R.string.filter_library),
                        AlbumFilter.DOWNLOADED to stringResource(R.string.filter_downloaded)
                    ),
                    currentValue = filter,
                    onValueUpdate = {
                        filter = it
                        if (it == AlbumFilter.LIBRARY) viewModel.syncAlbums()
                    },
                    modifier = Modifier.weight(1f),
                    isLoading = { filter -> filter == AlbumFilter.LIBRARY && isSyncingLibraryAlbums }
                )

                IconButton(
                    onClick = {
                        albumViewType = albumViewType.toggle()
                    },
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Icon(
                        imageVector =
                            when (albumViewType) {
                                LibraryViewType.LIST -> Icons.AutoMirrored.Rounded.List
                                LibraryViewType.GRID -> Icons.Rounded.GridView
                            },
                        contentDescription = null
                    )
                }
            }
        }
    }

    val headerContent = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            SortHeader(
                sortType = sortType,
                sortDescending = sortDescending,
                onSortTypeChange = onSortTypeChange,
                onSortDescendingChange = onSortDescendingChange,
                sortTypeText = { sortType ->
                    when (sortType) {
                        AlbumSortType.CREATE_DATE -> R.string.sort_by_create_date
                        AlbumSortType.NAME -> R.string.sort_by_name
                        AlbumSortType.ARTIST -> R.string.sort_by_artist
                        AlbumSortType.YEAR -> R.string.sort_by_year
                        AlbumSortType.SONG_COUNT -> R.string.sort_by_song_count
                        AlbumSortType.LENGTH -> R.string.sort_by_length
                        AlbumSortType.PLAY_TIME -> R.string.sort_by_play_time
                    }
                }
            )

            Spacer(Modifier.weight(1f))

            albums?.let { albums ->
                Text(
                    text = pluralStringResource(R.plurals.n_album, albums.size, albums.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(
                state = pullRefreshState,
                isRefreshing = isSyncingLibraryAlbums,
                onRefresh = {
                    viewModel.syncAlbums(true)
                }
            ),
    ) {
        when (viewType) {
            LibraryViewType.LIST ->
                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                ) {
                    item(
                        key = "filter",
                        contentType = CONTENT_TYPE_HEADER
                    ) {
                        libraryFilterContent?.let { it() } ?: filterContent()
                    }

                    item(
                        key = "header",
                        contentType = CONTENT_TYPE_HEADER
                    ) {
                        headerContent()
                    }

                    albums?.let { albums ->
                        if (albums.isEmpty()) {
                            item {
                                EmptyPlaceholder(
                                    icon = Icons.Rounded.Album,
                                    text = stringResource(R.string.library_album_empty),
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                        items(
                            items = albums,
                            key = { it.id },
                            contentType = { CONTENT_TYPE_ALBUM }
                        ) { album ->
                            LibraryAlbumListItem(
                                navController = navController,
                                menuState = menuState,
                                album = album,
                                isActive = album.id == mediaMetadata?.album?.id,
                                isPlaying = isPlaying,
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }

            LibraryViewType.GRID ->
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                ) {
                    item(
                        key = "filter",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER
                    ) {
                        libraryFilterContent?.let { it() } ?: filterContent()
                    }

                    item(
                        key = "header",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER
                    ) {
                        headerContent()
                    }

                    albums?.let { albums ->
                        if (albums.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                EmptyPlaceholder(
                                    icon = Icons.Rounded.Album,
                                    text = stringResource(R.string.library_album_empty),
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                        items(
                            items = albums,
                            key = { it.id },
                            contentType = { CONTENT_TYPE_ALBUM }
                        ) { album ->
                            LibraryAlbumGridItem(
                                navController = navController,
                                menuState = menuState,
                                coroutineScope = coroutineScope,
                                album = album,
                                isActive = album.id == mediaMetadata?.album?.id,
                                isPlaying = isPlaying,
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
        }

        Indicator(
            isRefreshing = isSyncingLibraryAlbums,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
        )
    }
}
