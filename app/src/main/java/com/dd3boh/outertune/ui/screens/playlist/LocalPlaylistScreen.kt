package com.dd3boh.outertune.ui.screens.playlist

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton as M3IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastSumBy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.LocalDownloadUtil
import com.dd3boh.outertune.LocalMenuState
import com.dd3boh.outertune.LocalNetworkConnected
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.LocalSnackbarHostState
import com.dd3boh.outertune.LocalSyncUtils
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.AlbumCornerRadius
import com.dd3boh.outertune.constants.AlbumThumbnailSize
import com.dd3boh.outertune.constants.CONTENT_TYPE_HEADER
import com.dd3boh.outertune.constants.CONTENT_TYPE_SONG
import com.dd3boh.outertune.constants.ListThumbnailSize
import com.dd3boh.outertune.constants.PlaylistEditLockKey
import com.dd3boh.outertune.constants.PlaylistSongSortDescendingKey
import com.dd3boh.outertune.constants.PlaylistSongSortType
import com.dd3boh.outertune.constants.PlaylistSongSortTypeKey
import com.dd3boh.outertune.constants.SwipeToQueueKey
import com.dd3boh.outertune.constants.SyncMode
import com.dd3boh.outertune.constants.YtmSyncModeKey
import com.dd3boh.outertune.db.entities.Playlist
import com.dd3boh.outertune.db.entities.PlaylistSong
import com.dd3boh.outertune.extensions.move
import com.dd3boh.outertune.extensions.toMediaItem
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.playback.ExoDownloadService
import com.dd3boh.outertune.playback.queues.ListQueue
import com.dd3boh.outertune.ui.component.AutoResizeText
import com.dd3boh.outertune.ui.component.EmptyPlaceholder
import com.dd3boh.outertune.ui.component.FloatingFooter
import com.dd3boh.outertune.ui.component.FontSizeRange
import com.dd3boh.outertune.ui.component.LazyColumnScrollbar
import com.dd3boh.outertune.ui.component.ScrollToTopManager
import com.dd3boh.outertune.ui.component.SelectHeader
import com.dd3boh.outertune.ui.component.SortHeader
import com.dd3boh.outertune.ui.component.button.IconButton
import com.dd3boh.outertune.ui.component.items.PlaylistThumbnail
import com.dd3boh.outertune.ui.component.items.SongListItem
import com.dd3boh.outertune.ui.dialog.DefaultDialog
import com.dd3boh.outertune.ui.dialog.TextFieldDialog
import com.dd3boh.outertune.ui.utils.backToMain
import com.dd3boh.outertune.ui.utils.getNSongsString
import com.dd3boh.outertune.utils.makeTimeString
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.utils.syncCoroutine
import com.dd3boh.outertune.viewmodels.LocalPlaylistViewModel
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LocalPlaylistViewModel = hiltViewModel(),
) {
    Log.v("LocalPlaylistScreen", "P_RC-1")
    val context = LocalContext.current
    val density = LocalDensity.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val snackbarHostState = LocalSnackbarHostState.current

    val playlistWithSongs by viewModel.playlistWithSongs.collectAsState()

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val mutableSongs = remember { mutableStateListOf<PlaylistSong>() }

    val (sortType, onSortTypeChange) = rememberEnumPreference(PlaylistSongSortTypeKey, PlaylistSongSortType.CUSTOM)
    val (sortDescending, onSortDescendingChange) = rememberPreference(PlaylistSongSortDescendingKey, true)
    var locked by rememberPreference(PlaylistEditLockKey, defaultValue = false)
    val swipeEnabled by rememberPreference(SwipeToQueueKey, true)
    val syncMode by rememberEnumPreference(key = YtmSyncModeKey, defaultValue = SyncMode.RW)

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf() }
    var playlistFilterText by rememberSaveable { mutableStateOf("") }
    var filterToSelectedOnly by rememberSaveable { mutableStateOf(false) }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
        filterToSelectedOnly = false
    }

    if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    LaunchedEffect(filterToSelectedOnly, selection.size) {
        if (filterToSelectedOnly && selection.isEmpty()) {
            filterToSelectedOnly = false
        }
    }

    val editable: Boolean =
        playlistWithSongs.first?.playlist?.isLocal == true || (playlistWithSongs.first?.playlist?.isEditable == true && syncMode == SyncMode.RW)

    LaunchedEffect(
        playlistWithSongs.second,
        playlistFilterText,
        filterToSelectedOnly,
        inSelectMode,
        selection.size,
        selection.joinToString(),
    ) {
        delay(if (playlistFilterText.isBlank()) 0 else 200)
        val base = playlistWithSongs.second
        var list = base
        val q = playlistFilterText.trim()
        if (q.isNotEmpty()) {
            list = base.filter { song ->
                song.song.title.contains(q, ignoreCase = true) ||
                    song.song.artists.fastAny { it.name.contains(q, ignoreCase = true) }
            }
        }
        if (inSelectMode && filterToSelectedOnly && selection.isNotEmpty()) {
            val ids = selection.toSet()
            list = list.filter { it.song.id in ids }
        }
        mutableSongs.clear()
        mutableSongs.addAll(list)
    }

    var showEditDialog by remember {
        mutableStateOf(false)
    }

    if (showEditDialog) {
        playlistWithSongs.first?.playlist?.let { playlistEntity ->
            TextFieldDialog(
                icon = { Icon(imageVector = Icons.Rounded.Edit, contentDescription = null) },
                title = { Text(text = stringResource(R.string.edit_playlist)) },
                onDismiss = { showEditDialog = false },
                initialTextFieldValue = TextFieldValue(playlistEntity.name, TextRange(playlistEntity.name.length)),
                onDone = { name ->
                    database.query {
                        update(playlistEntity.copy(name = name))
                    }

                    viewModel.viewModelScope.launch(syncCoroutine) {
                        playlistEntity.browseId?.let { YouTube.renamePlaylist(it, name) }
                    }
                }
            )
        }
    }

    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.remove_download_playlist_confirm, playlistWithSongs.first?.playlist!!.name),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = { showRemoveDownloadDialog = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        if (!editable) {
                            database.transaction {
                                playlistWithSongs.first?.id?.let { clearPlaylist(it) }
                            }
                        }

                        playlistWithSongs.second.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.song.id,
                                false
                            )
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    var showDeletePlaylistDialog by remember {
        mutableStateOf(false)
    }

    if (showDeletePlaylistDialog) {
        DefaultDialog(
            onDismiss = { showDeletePlaylistDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.delete_playlist_confirm, playlistWithSongs.first?.playlist!!.name),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                    }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                        database.query {
                            playlistWithSongs.first?.let { delete(it.playlist) }
                        }

                        viewModel.viewModelScope.launch(Dispatchers.IO) {
                            playlistWithSongs.first?.playlist?.browseId?.let { YouTube.deletePlaylist(it) }
                        }

                        navController.popBackStack()
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    val headerItems = 2
    val lazyListState = rememberLazyListState()
    var dragInfo by remember {
        mutableStateOf<Pair<Int, Int>?>(null)
    }
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
//        scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    ) { from, to ->
        if (to.index >= headerItems && from.index >= headerItems) {
            val currentDragInfo = dragInfo
            dragInfo = if (currentDragInfo == null) {
                (from.index - headerItems) to (to.index - headerItems)
            } else {
                currentDragInfo.first to (to.index - headerItems)
            }

            mutableSongs.move(from.index - headerItems, to.index - headerItems)
        }
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            dragInfo?.let { (from, to) ->
                database.transaction {
                    move(viewModel.playlistId, from, to)
                }
                if (playlistWithSongs.first?.playlist?.isLocal == false) {
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        val from = from
                        val to = to
                        val playlistSongMap = database.songMapsToPlaylist(viewModel.playlistId, 0)

                        var fromIndex = from //- headerItems
                        val toIndex = to //- headerItems

                        var successorIndex = if (fromIndex > toIndex) toIndex else toIndex + 1

                        /*
                        * Because of how YouTube Music handles playlist changes, you necessarily need to
                        * have the SetVideoId of the successor when trying to move a song inside of a
                        * playlist.
                        * For this reason, if we are trying to move a song to the last element of a playlist,
                        * we need to first move it as penultimate and then move the last element before it.
                        */
                        if (successorIndex >= playlistSongMap.size) {
                            playlistSongMap[fromIndex].setVideoId?.let { setVideoId ->
                                playlistSongMap[toIndex].setVideoId?.let { successorSetVideoId ->
                                    playlistWithSongs.first?.playlist?.browseId?.let { browseId ->
                                        YouTube.moveSongPlaylist(browseId, setVideoId, successorSetVideoId)
                                    }
                                }
                            }

                            successorIndex = fromIndex
                            fromIndex = toIndex
                        }

                        playlistSongMap[fromIndex].setVideoId?.let { setVideoId ->
                            playlistSongMap[successorIndex].setVideoId?.let { successorSetVideoId ->
                                playlistWithSongs.first?.playlist?.browseId?.let { browseId ->
                                    YouTube.moveSongPlaylist(browseId, setVideoId, successorSetVideoId)
                                }
                            }
                        }
                    }
                }
                dragInfo = null
            }
        }
    }

    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Log.v("LocalPlaylistScreen", "P_RC-2.1")
        ScrollToTopManager(navController, lazyListState)
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
            modifier = Modifier.padding(bottom = if (inSelectMode) 64.dp else 0.dp)
        ) {
            Log.v("LocalPlaylistScreen", "P_RC-2.2")
            playlistWithSongs.first?.let { playlist ->
                if (playlist.songCount == 0) {
                    item {
                        EmptyPlaceholder(
                            icon = Icons.Rounded.MusicNote,
                            text = stringResource(R.string.playlist_is_empty),
                            modifier = Modifier.animateItem()
                        )
                    }
                } else {
                    item(
                        key = "playlist header",
                        contentType = CONTENT_TYPE_HEADER
                    ) {
                        LocalPlaylistHeader(
                            playlist = playlist,
                            songs = playlistWithSongs.second,
                            onShowEditDialog = { showEditDialog = true },
                            onShowRemoveDownloadDialog = { showRemoveDownloadDialog = true },
                            snackbarHostState = snackbarHostState,
                            modifier = Modifier
                        )
                    }

                    item(
                        key = "action header",
                        contentType = CONTENT_TYPE_HEADER
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp)
                        ) {
                            SortHeader(
                                sortType = sortType,
                                sortDescending = sortDescending,
                                onSortTypeChange = onSortTypeChange,
                                onSortDescendingChange = onSortDescendingChange,
                                sortTypeText = { sortType ->
                                    when (sortType) {
                                        PlaylistSongSortType.CUSTOM -> R.string.sort_by_custom
                                        PlaylistSongSortType.NAME -> R.string.sort_by_name
                                        PlaylistSongSortType.ARTIST -> R.string.sort_by_artist
                                        PlaylistSongSortType.ADDED_DATE -> R.string.sort_by_create_date
                                        PlaylistSongSortType.MODIFIED_DATE -> R.string.sort_by_date_modified
                                        PlaylistSongSortType.RELEASE_DATE -> R.string.sort_by_date_released
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )

                            if (editable && !inSelectMode) {
                                IconButton(
                                    onClick = { locked = !locked },
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    }

                    item(
                        key = "playlist song filter",
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        OutlinedTextField(
                            value = playlistFilterText,
                            onValueChange = { playlistFilterText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            placeholder = {
                                Text(stringResource(R.string.playlist_songs_filter_hint))
                            },
                            singleLine = true,
                            trailingIcon = {
                                if (playlistFilterText.isNotEmpty()) {
                                    M3IconButton(onClick = { playlistFilterText = "" }) {
                                        Icon(
                                            Icons.Rounded.Clear,
                                            contentDescription = stringResource(android.R.string.cancel),
                                        )
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                            ),
                        )
                    }
                }
            }

            // songs
            val thumbnailSize = (ListThumbnailSize.value * density.density).roundToInt()
            itemsIndexed(
                items = mutableSongs,
                key = { _, song -> song.map.id },
                contentType = { _, song -> CONTENT_TYPE_SONG },
            ) { index, song ->
                ReorderableItem(
                    state = reorderableState,
                    key = song.map.id,
                    enabled = editable && playlistFilterText.isBlank() && !filterToSelectedOnly
                ) {
                    SongListItem(
                        song = song.song,
                        thumbnailSize = thumbnailSize,
                        playlistSong = song,
                        playlist =  playlistWithSongs.first,
                        navController = navController,
                        snackbarHostState = snackbarHostState,

                        isActive = song.song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        swipeEnabled = swipeEnabled,
                        onSelectedChange = {
                            inSelectMode = true
                            if (it) {
                                selection.add(song.song.id)
                            } else {
                                selection.remove(song.song.id)
                            }
                        },
                        inSelectMode = inSelectMode,
                        isSelected = selection.contains(song.song.id),

                        onPlay = {
                            playerConnection.playQueue(
                                ListQueue(
                                    title =  playlistWithSongs.first!!.playlist.name,
                                    items = mutableSongs.map { it.song.toMediaMetadata() },
                                    startIndex = index,
                                    playlistId =  playlistWithSongs.first?.playlist?.browseId
                                )
                            )
                        },
                        dragHandleModifier = if (
                            sortType == PlaylistSongSortType.CUSTOM &&
                            !locked &&
                            playlistFilterText.isBlank() &&
                            !filterToSelectedOnly &&
                            editable
                        ) {
                            Modifier.draggableHandle()
                        } else {
                            null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background),
                    )
                }
            }
        }

        LazyColumnScrollbar(
            state = lazyListState,
        )

        TopAppBar(
            title = {
                if (showTopBarTitle) {
                    Text(playlistWithSongs.first?.playlist?.name.orEmpty())
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = { navController.navigateUp() },
                    onLongClick = { navController.backToMain() }
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null
                    )
                }
            },
            scrollBehavior = scrollBehavior
        )

        FloatingFooter(inSelectMode) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (selection.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        TextButton(onClick = { filterToSelectedOnly = !filterToSelectedOnly }) {
                            Text(
                                text = if (filterToSelectedOnly) {
                                    stringResource(R.string.playlist_show_all_tracks)
                                } else {
                                    stringResource(R.string.playlist_selected_only)
                                },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SelectHeader(
                        navController = navController,
                        selectedItems = selection.mapNotNull { id ->
                            playlistWithSongs.second.find { it.song.id == id }?.song
                        }.map { it.toMediaMetadata() },
                        totalItemCount = playlistWithSongs.second.map { it.song }.size,
                        onSelectAll = {
                            selection.clear()
                            selection.addAll(playlistWithSongs.second.map { it.song }.map { it.song.id })
                        },
                        onDeselectAll = { selection.clear() },
                        menuState = menuState,
                        onDismiss = onExitSelectionMode
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
//                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime))
                .align(Alignment.BottomCenter)
        )
    }
}


@Composable
fun LocalPlaylistHeader(
    playlist: Playlist,
    songs: List<PlaylistSong>,
    onShowEditDialog: () -> Unit,
    onShowRemoveDownloadDialog: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier,
) {
    Log.v("LocalPlaylistScreen", "P_H_RC-1")
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val isNetworkConnected = LocalNetworkConnected.current
    val scope = rememberCoroutineScope()
    val syncUtils = LocalSyncUtils.current

    val playlistLength = remember(songs) {
        songs.fastSumBy { it.song.song.duration }
    }

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

//    LaunchedEffect(songs) {
//        val songs = songs.filterNot { it.song.song.isLocal }
//        if (songs.isEmpty()) return@LaunchedEffect
//        downloadUtil.downloads.collect { downloads ->
//            downloadState = getDownloadState(songs.map { downloads[it.song.id] })
//        }
//    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.padding(12.dp)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            PlaylistThumbnail(
                playlist = playlist.playlist,
                thumbnails = playlist.thumbnails,
                size = AlbumThumbnailSize,
                shape = RoundedCornerShape(AlbumCornerRadius),
                iconPadding = AlbumThumbnailSize / 16,
                iconTint = LocalContentColor.current.copy(alpha = 0.8f),
            )

            Column(
                verticalArrangement = Arrangement.Center,
            ) {
                AutoResizeText(
                    text = playlist.playlist.name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSizeRange = FontSizeRange(16.sp, 22.sp)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (playlist.downloadCount > 0) {
                        Icon(
                            imageVector = Icons.Rounded.OfflinePin,
                            contentDescription = null,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 2.dp)
                        )
                    }

                    Text(
                        text = getNSongsString(songs.size, playlist.downloadCount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal
                    )
                }

                Text(
                    text = makeTimeString(playlistLength * 1000L),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal
                )

                Row {
                    IconButton(
                        onClick = onShowEditDialog
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null
                        )
                    }

                    if (playlist.playlist.browseId != null) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    syncUtils.syncPlaylist(playlist.playlist.browseId, playlist.id)
                                    snackbarHostState.showSnackbar(
                                        message = context.getString(R.string.playlist_synced),
                                        withDismissAction = true
                                    )
                                }
                            },
                            enabled = isNetworkConnected
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Sync,
                                contentDescription = null
                            )
                        }
                    }

                    if (songs.any { !it.song.song.isLocal }) {
                        when (downloadState) {
                            Download.STATE_COMPLETED -> {
                                IconButton(
                                    onClick = onShowRemoveDownloadDialog
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.OfflinePin,
                                        contentDescription = null
                                    )
                                }
                            }

                            Download.STATE_DOWNLOADING -> {
                                IconButton(
                                    onClick = {
                                        songs.forEach { song ->
                                            DownloadService.sendRemoveDownload(
                                                context,
                                                ExoDownloadService::class.java,
                                                song.song.id,
                                                false
                                            )
                                        }
                                    }
                                ) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            else -> {
                                IconButton(
                                    onClick = {
                                        downloadUtil.download(songs.map { it.song.toMediaMetadata() })
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = null
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                playerConnection.enqueueEnd(
                                    items = songs.map { it.song.toMediaItem() }
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    playerConnection.playQueue(
                        ListQueue(
                            title = playlist.playlist.name,
                            items = songs.map { it.song.toMediaMetadata() }.toList()
                        )
                    )
                },
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.play))
            }

            OutlinedButton(
                onClick = {
                    playerConnection.playQueue(
                        ListQueue(
                            title = playlist.playlist.name,
                            items = songs.map { it.song.toMediaMetadata() },
                            startShuffled = true,
                        )
                    )
                },
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.shuffle))
            }
        }
    }
}
