package com.dd3boh.outertune.ui.screens.library

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastSumBy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.LocalMenuState
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.LocalSnackbarHostState
import com.dd3boh.outertune.MainActivity
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.CONTENT_TYPE_FOLDER
import com.dd3boh.outertune.constants.CONTENT_TYPE_HEADER
import com.dd3boh.outertune.constants.CONTENT_TYPE_SONG
import com.dd3boh.outertune.constants.FlatSubfoldersKey
import com.dd3boh.outertune.constants.FolderSongSortDescendingKey
import com.dd3boh.outertune.constants.FolderSongSortType
import com.dd3boh.outertune.constants.FolderSongSortTypeKey
import com.dd3boh.outertune.constants.FolderSortType
import com.dd3boh.outertune.constants.FolderSortTypeKey
import com.dd3boh.outertune.constants.LastLocalScanKey
import com.dd3boh.outertune.constants.LocalLibraryEnableKey
import com.dd3boh.outertune.constants.SwipeToQueueKey
import com.dd3boh.outertune.constants.TopBarInsets
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.models.DirectoryTree
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.playback.queues.ListQueue
import com.dd3boh.outertune.ui.component.FloatingFooter
import com.dd3boh.outertune.ui.component.LazyColumnScrollbar
import com.dd3boh.outertune.ui.component.SelectHeader
import com.dd3boh.outertune.ui.component.SortHeader
import com.dd3boh.outertune.ui.component.button.IconButton
import com.dd3boh.outertune.ui.component.button.IconTextButton
import com.dd3boh.outertune.ui.component.button.ResizableIconButton
import com.dd3boh.outertune.ui.component.items.SongFolderItem
import com.dd3boh.outertune.ui.component.items.SongListItem
import com.dd3boh.outertune.ui.component.shimmer.ListItemPlaceHolder
import com.dd3boh.outertune.ui.component.shimmer.ShimmerHost
import com.dd3boh.outertune.ui.menu.FolderMenu
import com.dd3boh.outertune.ui.screens.Screens
import com.dd3boh.outertune.ui.utils.MEDIA_PERMISSION_LEVEL
import com.dd3boh.outertune.ui.utils.STORAGE_ROOT
import com.dd3boh.outertune.ui.utils.backToMain
import com.dd3boh.outertune.utils.fixFilePath
import com.dd3boh.outertune.utils.numberToAlpha
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.viewmodels.LibraryFoldersViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun FolderScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LibraryFoldersViewModel = hiltViewModel(),
    libraryFilterContent: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val snackbarHostState = LocalSnackbarHostState.current

    val (flatSubfolders, onFlatSubfoldersChange) = rememberPreference(FlatSubfoldersKey, defaultValue = true)
    val lastLocalScan by rememberPreference(LastLocalScanKey, 0L)
    val localLibEnable by rememberPreference(LocalLibraryEnableKey, defaultValue = true)

    val (sortType, onSortTypeChange) = rememberEnumPreference(FolderSongSortTypeKey, FolderSongSortType.NAME)
    val (sortDescending, onSortDescendingChange) = rememberPreference(FolderSongSortDescendingKey, true)
    val (folderSortType, onFolderSortTypeChange) = rememberEnumPreference(FolderSortTypeKey, FolderSortType.NAME)
    val swipeEnabled by rememberPreference(SwipeToQueueKey, true)

    val lazyListState = rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    val currDir: DirectoryTree by viewModel.localSongDirectoryTree.collectAsState()
    val subDirSongCount by viewModel.localSongDtSongCount.collectAsState()

    LaunchedEffect(lastLocalScan) {
        if (viewModel.uiInit && !currDir.isSkeleton && viewModel.lastLocalScan != lastLocalScan) {
            viewModel.lastLocalScan = lastLocalScan
            navController.backToMain()
            viewModel.getLocalSongs()
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.lastLocalScan == 0L) {
            viewModel.lastLocalScan = lastLocalScan
        }
        if (!currDir.isSkeleton) {
            viewModel.uiInit = true
        }

        if (!viewModel.uiInit) {
            coroutineScope.launch(Dispatchers.IO) {
                viewModel.getLocalSongs()
                viewModel.getSongCount()
                viewModel.uiInit = true
            }
        }
    }

    val mutableSongs = remember {
        mutableStateListOf<Song>()
    }

    // search
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    val filteredSongs = remember { viewModel.filteredSongs }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(query) {
        snapshotFlow { query }.debounce { 300L }.collectLatest {
            viewModel.searchInDir(query.text)
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
    } else if (isSearching) {
        BackHandler(onBack = { isSearching = false })
    }

    LaunchedEffect(inSelectMode) {
        backStackEntry?.savedStateHandle?.set("inSelectMode", inSelectMode)
    }

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazyListState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    LaunchedEffect(sortType, sortDescending, currDir) {
        val tempList = currDir.files.map { it }.toMutableList()
        // sort songs
        tempList.sortBy {
            when (sortType) {
                FolderSongSortType.CREATE_DATE -> numberToAlpha(it.song.inLibrary?.toEpochSecond(ZoneOffset.UTC) ?: -1L)
                FolderSongSortType.MODIFIED_DATE -> numberToAlpha(it.song.getDateModifiedLong() ?: -1L)
                FolderSongSortType.RELEASE_DATE -> numberToAlpha(it.song.getDateLong() ?: -1L)
                FolderSongSortType.NAME -> it.song.title.lowercase()
                FolderSongSortType.ARTIST -> it.artists.joinToString { artist -> artist.name }.lowercase()
                FolderSongSortType.PLAY_COUNT -> numberToAlpha((it.playCount?.fastSumBy { it.count })?.toLong() ?: 0L)
                FolderSongSortType.TRACK_NUMBER -> numberToAlpha(it.song.trackNumber?.toLong() ?: Long.MAX_VALUE)
            }
        }
        // sort folders
        currDir.subdirs.sortBy { it.currentDir.lowercase() } // only sort by name

        if (sortDescending) {
            currDir.subdirs.reverse()
            tempList.reverse()
        }

        mutableSongs.clear()
        mutableSongs.addAll(tempList.distinctBy { it.id })
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.padding(bottom = if (inSelectMode) 64.dp else 0.dp)
        ) {
            item(
                key = "header",
                contentType = CONTENT_TYPE_HEADER
            ) {
                Column(
                    modifier = Modifier.background(MaterialTheme.colorScheme.background)
                ) {
                    Column {
                        if (libraryFilterContent == null) {
                            var showStoragePerm by remember {
                                mutableStateOf(context.checkSelfPermission(MEDIA_PERMISSION_LEVEL) != PackageManager.PERMISSION_GRANTED)
                            }
                            if (localLibEnable && showStoragePerm) {
                                TextButton(
                                    onClick = {
                                        // allow user to hide error when clicked. This also makes the code a lot nicer too.
                                        showStoragePerm = false
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
                        } else {
                            libraryFilterContent()
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            // search
                            IconButton(
                                onClick = {
                                    isSearching = true
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.Search,
                                    contentDescription = null
                                )
                            }
                            if (isSearching) {
                                TextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    placeholder = {
                                        Text(
                                            text = stringResource(R.string.search),
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.titleLarge,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester)
                                )
                            } else {
                                // scanner icon
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                ) {
                                    IconTextButton(R.string.scanner_local_title, Icons.Rounded.SdCard) {
                                        navController.navigate("settings/local")
                                    }
                                }
                            }

                            if (!isSearching) {
                                // tree/list view
                                ResizableIconButton(
                                    icon = if (flatSubfolders) Icons.AutoMirrored.Rounded.List else Icons.Rounded.AccountTree,
                                    onClick = {
                                        onFlatSubfoldersChange(!flatSubfolders)
                                    },
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp)
                    ) {
                        SortHeader(
                            sortType = sortType,
                            sortDescending = sortDescending,
                            onSortTypeChange = onSortTypeChange,
                            onSortDescendingChange = onSortDescendingChange,
                            sortTypeText = { sortType ->
                                when (sortType) {
                                    FolderSongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                    FolderSongSortType.MODIFIED_DATE -> R.string.sort_by_date_modified
                                    FolderSongSortType.RELEASE_DATE -> R.string.sort_by_date_released
                                    FolderSongSortType.NAME -> R.string.sort_by_name
                                    FolderSongSortType.ARTIST -> R.string.sort_by_artist
                                    FolderSongSortType.PLAY_COUNT -> R.string.sort_by_play_count
                                    FolderSongSortType.TRACK_NUMBER -> R.string.sort_by_track_number
                                }
                            }
                        )

                        Spacer(Modifier.weight(1f))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = pluralStringResource(R.plurals.n_song, subDirSongCount, subDirSongCount),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    menuState.show {
                                        FolderMenu(
                                            folder = currDir,
                                            navController = navController,
                                            onDismiss = menuState::dismiss
                                        )
                                    }
                                    haptic.performHapticFeedback(HapticFeedbackType.Companion.ContextClick)
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = null
                                )
                            }
                        }
                    }
                }
            }
            if (!isSearching) {
                if (fixFilePath(currDir.getFullPath()) != STORAGE_ROOT)
                    item(
                        key = "previous",
                        contentType = CONTENT_TYPE_FOLDER
                    ) {
                        SongFolderItem(
                            folderTitle = "..",
                            subtitle = "Previous folder",
                            modifier = Modifier
                                .clickable {
                                    if (currDir.culmSongs.value > 0) {
                                        navController.navigateUp()
                                    }
                                }
                        )
                    }


                // all subdirectories listed here
                itemsIndexed(
                    items = if (flatSubfolders) currDir.getFlattenedSubdirs() else currDir.subdirs,
                    key = { _, item -> item.currentDir },
                    contentType = { _, _ -> CONTENT_TYPE_FOLDER }
                ) { index, folder ->
                    if (!flatSubfolders || folder.getFullSquashedDir() != fixFilePath(currDir.getFullPath())) // rm dupe dir hax
                        SongFolderItem(
                            folder = folder,
                            folderTitle = if (folder.files.isEmpty()) folder.getSquashedDir() else null,
                            subtitle = null,
                            modifier = Modifier
                                .combinedClickable {
                                    val route =
                                        Screens.Folders.route + "/" + folder.getFullSquashedDir().replace('/', ';')
                                    navController.navigate(route)
                                }
                                .animateItem(),
                            menuState = menuState,
                            navController = navController
                        )
                }

                // separator
                if (currDir.subdirs.isNotEmpty() && mutableSongs.isNotEmpty()) {
                    item(
                        key = "folder_songs_divider",
                    ) {
                        HorizontalDivider(
                            thickness = DividerDefaults.Thickness,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            }

            if (currDir.isSkeleton) {
                item {
                    ShimmerHost {
                        repeat(8) {
                            ListItemPlaceHolder()
                        }
                    }
                }
            }
            if (currDir.isSkeleton) return@LazyColumn

            // all songs get listed here
            itemsIndexed(
                items = if (isSearching) filteredSongs else mutableSongs,
                key = { _, item -> item.id },
                contentType = { _, _ -> CONTENT_TYPE_SONG }
            ) { index, song ->
                SongListItem(
                    song = song,
                    onPlay = {
                        playerConnection.playQueue(
                            ListQueue(
                                title = currDir.currentDir.substringAfterLast('/'),
                                items = mutableSongs.map { it.toMediaMetadata() },
                                startIndex = mutableSongs.indexOf(song)
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
                    swipeEnabled = swipeEnabled,
                    navController = navController,
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                )
            }
        }
        LazyColumnScrollbar(
            state = lazyListState,
        )

        TopAppBar(title = {
            Column {
                val title = currDir.currentDir.substringAfterLast('/')
                val subtitle = currDir.getFullPath().substringBeforeLast('/')
                Text(
                    text = if (currDir.currentDir == "storage") {
                        stringResource(R.string.local_player_settings_title)
                    } else {
                        title
                    },
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )

                if (!subtitle.isBlank()) {
                    Text(
                        text = subtitle,
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }, navigationIcon = {
            IconButton(
                onClick = {
                    if (isSearching) {
                        isSearching = false
                        query = TextFieldValue()
                    } else {
                        navController.navigateUp()
                    }
                },
                onLongClick = {
                    if (!isSearching) {
                        navController.backToMain()
                    }
                },
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null
                )
            }
        }, windowInsets = TopBarInsets, scrollBehavior = scrollBehavior)

        FloatingFooter(inSelectMode) {
            SelectHeader(
                navController = navController,
                selectedItems = selection.mapNotNull { songId ->
                    mutableSongs.find { it.id == songId }
                }.map { it.toMediaMetadata() },
                totalItemCount = mutableSongs.size,
                onSelectAll = {
                    selection.clear()
                    selection.addAll(mutableSongs.map { it.id }.distinctBy { it })
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
