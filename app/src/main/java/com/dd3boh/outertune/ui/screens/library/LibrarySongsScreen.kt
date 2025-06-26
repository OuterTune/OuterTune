package com.dd3boh.outertune.ui.screens.library

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachReversed
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.MainActivity
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.CONTENT_TYPE_HEADER
import com.dd3boh.outertune.constants.CONTENT_TYPE_SONG
import com.dd3boh.outertune.constants.LocalLibraryEnableKey
import com.dd3boh.outertune.constants.SongFilter
import com.dd3boh.outertune.constants.SongFilterKey
import com.dd3boh.outertune.constants.SongSortDescendingKey
import com.dd3boh.outertune.constants.SongSortType
import com.dd3boh.outertune.constants.SongSortTypeKey
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.playback.queues.ListQueue
import com.dd3boh.outertune.ui.component.ChipsRow
import com.dd3boh.outertune.ui.component.EmptyPlaceholder
import com.dd3boh.outertune.ui.component.FloatingFooter
import com.dd3boh.outertune.ui.component.HideOnScrollFAB
import com.dd3boh.outertune.ui.component.LocalMenuState
import com.dd3boh.outertune.ui.component.SelectHeader
import com.dd3boh.outertune.ui.component.SongListItem
import com.dd3boh.outertune.ui.component.SortHeader
import com.dd3boh.outertune.ui.utils.MEDIA_PERMISSION_LEVEL
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.viewmodels.LibrarySongsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySongsScreen(
    navController: NavController,
    viewModel: LibrarySongsViewModel = hiltViewModel(),
    libraryFilterContent: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val snackbarHostState = remember { SnackbarHostState() }

    var filter by rememberEnumPreference(SongFilterKey, SongFilter.LIKED)
    libraryFilterContent?.let { filter = SongFilter.LIBRARY }
    val localLibEnable by rememberPreference(LocalLibraryEnableKey, defaultValue = true)

    val (sortType, onSortTypeChange) = rememberEnumPreference(SongSortTypeKey, SongSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)

    val songs by viewModel.allSongs.collectAsState()
    val isSyncingRemoteLikedSongs by viewModel.isSyncingRemoteLikedSongs.collectAsState()
    val isSyncingRemoteSongs by viewModel.isSyncingRemoteSongs.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    val lazyListState = rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazyListState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    // multiselect
    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }
    if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    LaunchedEffect(inSelectMode) {
        backStackEntry?.savedStateHandle?.set("inSelectMode", inSelectMode)
    }

    LaunchedEffect(songs) {
        selection.fastForEachReversed { songId ->
            if (songs?.find { it.id == songId } == null) {
                selection.remove(songId)
            }
        }
    }

    LaunchedEffect(Unit) {
        when (filter) {
            SongFilter.LIKED -> viewModel.syncLikedSongs()
            SongFilter.LIBRARY -> viewModel.syncLibrarySongs()
            else -> return@LaunchedEffect
        }
    }

    val filterContent = @Composable {
        ChipsRow(
            chips = listOf(
                SongFilter.LIKED to stringResource(R.string.filter_liked),
                SongFilter.LIBRARY to stringResource(R.string.filter_library),
                SongFilter.DOWNLOADED to stringResource(R.string.filter_downloaded)
            ),
            currentValue = filter,
            onValueUpdate = {
                filter = it
                if (it == SongFilter.LIKED) viewModel.syncLikedSongs()
                else if (it == SongFilter.LIBRARY) viewModel.syncLibrarySongs()
            },
            isLoading = { filter ->
                (filter == SongFilter.LIKED && isSyncingRemoteLikedSongs) || (filter == SongFilter.LIBRARY && isSyncingRemoteSongs)
            }
        )
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
                        SongSortType.CREATE_DATE -> if (filter == SongFilter.LIKED) R.string.sort_by_like_date else R.string.sort_by_create_date
                        SongSortType.MODIFIED_DATE -> R.string.sort_by_date_modified
                        SongSortType.RELEASE_DATE -> R.string.sort_by_date_released
                        SongSortType.NAME -> R.string.sort_by_name
                        SongSortType.ARTIST -> R.string.sort_by_artist
                        SongSortType.PLAY_TIME -> R.string.sort_by_play_time
                        SongSortType.PLAY_COUNT -> R.string.sort_by_play_count
                    }
                }
            )

            Spacer(Modifier.weight(1f))

            songs?.let { songs ->
                Text(
                    text = pluralStringResource(R.plurals.n_song, songs.size, songs.size),
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
                isRefreshing = isSyncingRemoteLikedSongs || isSyncingRemoteSongs,
                onRefresh = {
                    when (filter) {
                        SongFilter.LIKED -> viewModel.syncLikedSongs(true)
                        SongFilter.LIBRARY -> viewModel.syncLibrarySongs(true)
                        else -> return@pullToRefresh
                    }
                }
            ),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.padding(bottom = if (inSelectMode) 64.dp else 0.dp)
        ) {
            item(
                key = "filter",
                contentType = CONTENT_TYPE_HEADER
            ) {
                Column(
                    modifier = Modifier.background(MaterialTheme.colorScheme.background)
                ) {
                    var showStoragePerm by remember {
                        mutableStateOf(context.checkSelfPermission(MEDIA_PERMISSION_LEVEL) != PackageManager.PERMISSION_GRANTED)
                    }
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
                    libraryFilterContent?.let { it() } ?: filterContent()
                }
            }

            item(
                key = "header",
                contentType = CONTENT_TYPE_HEADER
            ) {
                headerContent()
            }

            songs?.let { songs ->
                if (songs.isEmpty()) {
                    item {
                        EmptyPlaceholder(
                            icon = Icons.Rounded.MusicNote,
                            text = stringResource(R.string.library_song_empty),
                            modifier = Modifier.animateItem()
                        )
                    }
                }
                itemsIndexed(
                    items = songs,
                    key = { _, item -> item.id },
                    contentType = { _, _ -> CONTENT_TYPE_SONG }
                ) { index, song ->
                    SongListItem(
                        song = song,
                        showInLibraryIcon = filter != SongFilter.LIBRARY,
                        onPlay = {
                            playerConnection.playQueue(
                                ListQueue(
                                    title = context.getString(R.string.queue_all_songs),
                                    items = songs.map { it.toMediaMetadata() },
                                    startIndex = index
                                )
                            )
                        },
                        onSelectedChange = {
                            inSelectMode = true
                            if (it) {
                                selection.add(song.id)
                            } else {
                                selection.remove(song.id)
                            }
                        },
                        inSelectMode = inSelectMode,
                        isSelected = selection.contains(song.id),
                        navController = navController,
                        snackbarHostState = snackbarHostState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                    )
                }
            }
        }

        HideOnScrollFAB(
            visible = !songs.isNullOrEmpty(),
            lazyListState = lazyListState,
            icon = Icons.Rounded.Shuffle,
            onClick = {
                playerConnection.playQueue(
                    ListQueue(
                        title = context.getString(R.string.queue_all_songs),
                        items = songs?.map { it.toMediaMetadata() } ?: emptyList(),
                        startShuffled = true,
                    )
                )
            }
        )

        Indicator(
            isRefreshing = isSyncingRemoteLikedSongs || isSyncingRemoteSongs,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
        )
        FloatingFooter(visible = inSelectMode && songs != null) {
            val s: List<Song> = (songs as Iterable<Song>).toList()
            SelectHeader(
                navController = navController,
                selectedItems = selection.mapNotNull { songId ->
                    s.find { it.id == songId }
                }.map { it.toMediaMetadata() },
                totalItemCount = s.size,
                onSelectAll = {
                    selection.clear()
                    selection.addAll(s.map { it.id })
                },
                onDeselectAll = { selection.clear() },
                menuState = menuState,
                onDismiss = onExitSelectionMode
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .align(Alignment.BottomCenter)
        )
    }
}
