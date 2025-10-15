package com.dd3boh.outertune.ui.screens
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3Api as ExperimentalM3
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.LocalPlayerAwareWindowInsets
import com.dd3boh.outertune.LocalSnackbarHostState
import com.dd3boh.outertune.constants.ThumbnailCornerRadius
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.ui.utils.appBarScrollBehavior
import com.dd3boh.outertune.viewmodels.PartyEvent
import com.dd3boh.outertune.viewmodels.PartyState
import com.dd3boh.outertune.viewmodels.PartyTrack
import com.dd3boh.outertune.viewmodels.PartyViewModel
import com.dd3boh.outertune.viewmodels.LibraryAlbumsViewModel
import com.dd3boh.outertune.viewmodels.LibraryPlaylistsViewModel
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import com.dd3boh.outertune.ui.components.PartyQrDialog
import com.dd3boh.outertune.ui.components.QrScannerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyScreen(
    navController: NavController,
    partyCode: String,
    scrollBehavior: TopAppBarScrollBehavior = appBarScrollBehavior(),
    partyViewModel: PartyViewModel = hiltViewModel(),
    playlistsViewModel: LibraryPlaylistsViewModel = hiltViewModel(),
    albumsViewModel: LibraryAlbumsViewModel = hiltViewModel()
) {
    val partyState by partyViewModel.partyState.collectAsState()
    val isConnected by partyViewModel.isConnected.collectAsState()
    val events by partyViewModel.events.collectAsState()

    val clipboardManager = LocalClipboardManager.current
    val database = LocalDatabase.current
    val context = LocalContext.current
    val snackbarHost = LocalSnackbarHostState.current
    val playerConnection = LocalPlayerConnection.current
    var showMembersDialog by remember { mutableStateOf(false) }
    var showPlaylistImport by remember { mutableStateOf(false) }
    var showAlbumImport by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(PartyTab.Queue) }
    val scope = rememberCoroutineScope()
    var showQr by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }

    // Handle party events
    LaunchedEffect(events) {
        events?.let { event ->
            when (event) {
                is PartyEvent.PartyEnded -> {
                    navController.navigateUp()
                }
                is PartyEvent.Error -> {
                    // TODO: show snackbar if desired
                }
                else -> Unit
            }
            partyViewModel.clearEvents()
        }
    }

    // Ensure PartyViewModel is wired to MusicService during party
    LaunchedEffect(playerConnection) {
        playerConnection?.let { partyViewModel.setPlayerConnection(it) }
    }

    // Join party if not already connected
    LaunchedEffect(partyCode) {
        if (!isConnected && partyCode.isNotBlank()) {
            partyViewModel.joinParty(partyCode)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                partyState?.name ?: "OuterConnect",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            val isHostTitle = partyState?.hostId == com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                            if (isHostTitle) {
                                HostBadge()
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showQr = true }
                        ) {
                            Text(
                                text = partyState?.code ?: partyCode,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(onClick = {
                                partyState?.code?.let { clipboardManager.setText(AnnotatedString(it)) }
                            }) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy party code",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                // No back arrow: users must leave via the explicit Leave button to keep
                // the party running in background if they just navigate away.
                actions = {
                    IconButton(onClick = { showMembersDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Groups,
                            contentDescription = "View members"
                        )
                    }
                    val isHost = partyState?.hostId == com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                    IconButton(onClick = {
                        if (isHost) {
                            // Host: issue end_party decree. Navigation will happen after party removal.
                            partyViewModel.leaveParty()
                        } else {
                            // Member: leave immediately
                            partyViewModel.leaveParty()
                            navController.navigateUp()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
                            contentDescription = if (isHost) "End Party for Everyone" else "Leave Party",
                            tint = if (isHost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = scrollBehavior
            )
        },
        // Apply only bottom inset so TopAppBar owns top spacing
        modifier = Modifier.windowInsetsPadding(
            LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
        )
    ) { innerPadding ->
        // Block system back from leaving the party; require explicit Leave button.
        BackHandler(enabled = true) {
            scope.launch { snackbarHost.showSnackbar("Use the Leave button in the top bar to exit the party.") }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Removed redundant top spacer that created extra empty space

            // Album art with host-only overlay controls; tap chevron below to open sheet
            partyState?.let { state ->
                val isHost = state.hostId == com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                // Use authoritative progress from the service for UI actions
                val effectivePosition = state.currentPositionMs.coerceAtLeast(0L)
                AlbumArtSection(
                    partyState = state,
                    isHost = isHost,
                    onPlayPause = { if (isHost) partyViewModel.updatePlaybackState(!state.isPlaying, effectivePosition) },
                    onSkipPrevious = { if (isHost) partyViewModel.prevTrack() },
                    onSkipNext = { if (isHost) partyViewModel.nextTrack() }
                )
                // If host has ended the party, show a blocking banner and auto-exit for MEMBERS only
                if (state.isPartyEnding && !isHost) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = {},
                        confirmButton = {},
                        title = { Text("Party ended by host") },
                        text = { Text("The host has ended the party. Returning to the main screen…") }
                    )
                    LaunchedEffect("ending-${'$'}{state.code}") {
                        delay(2800)
                        navController.navigateUp()
                    }
                }
                ImportButtonsRow(
                    onImportPlaylists = { showPlaylistImport = true },
                    onImportAlbums = { showAlbumImport = true }
                )

                // Collapsed queue preview with chevron to open sheet
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { bottomSheetVisible = true },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Rounded.ExpandLess, contentDescription = "Open queue & search")
                    }
                }

                // Debounced auto-search on query change (used by bottom sheet search)
                LaunchedEffect(query) {
                    if (query.isBlank()) {
                        searchResults = emptyList()
                        isSearching = false
                        return@LaunchedEffect
                    }
                    isSearching = true
                    delay(350)
                    val result = withContext(Dispatchers.IO) {
                        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    }
                    val songs = result?.items?.filterIsInstance<SongItem>()?.take(25) ?: emptyList()
                    searchResults = songs
                    isSearching = false
                }


                // Slide-up sheet for Search + Queue/Results
                PartyBottomSheet(
                    visible = bottomSheetVisible,
                    onDismiss = { bottomSheetVisible = false },
                    activeTab = activeTab,
                    onTabChange = { activeTab = it },
                    query = query,
                    onQueryChange = { query = it },
                    isSearching = isSearching,
                    results = searchResults,
                    onEnqueue = { meta ->
                        // Enqueue and, if host and idle, start playback
                        partyViewModel.addTracksAndMaybePlay(listOf(meta))
                        scope.launch { snackbarHost.showSnackbar("Added to queue") }
                    },
                    queue = state.queue,
                    isHost = isHost,
                    onRemoveAt = { idx -> partyViewModel.removeTrackAt(idx) },
                    onMove = { from, to -> partyViewModel.moveTrack(from, to) }
                )

                // Import dialogs
                if (showPlaylistImport) {
                    ImportDialogList(
                        title = "Import playlist",
                        items = playlistsViewModel.allPlaylists.collectAsState().value ?: emptyList(),
                        getTitle = { it.playlist.name },
                        getSubtitle = { "${it.songCount} songs" },
                        getThumbnail = { it.playlist.thumbnailUrl ?: it.thumbnails.firstOrNull() },
                        onDismiss = { showPlaylistImport = false },
                        onItemClick = { playlist ->
                            scope.launch(Dispatchers.IO) {
                                val list = database.playlistSongs(playlist.id).first()
                                if (list.isNotEmpty()) {
                                    val metadatas = list.map { it.song.toMediaMetadata() }
                                    partyViewModel.addTracksToQueue(metadatas)
                                    withContext(Dispatchers.Main) {
                                        showPlaylistImport = false
                                        scope.launch { snackbarHost.showSnackbar("Added ${metadatas.size} to queue") }
                                    }
                                }
                            }
                        }
                    )
                }

                if (showAlbumImport) {
                    ImportDialogList(
                        title = "Import album",
                        items = albumsViewModel.allAlbums.collectAsState().value ?: emptyList(),
                        getTitle = { it.album.title },
                        getSubtitle = { it.artists.joinToString(", ") { a -> a.name } },
                        getThumbnail = { it.album.thumbnailUrl },
                        onDismiss = { showAlbumImport = false },
                        onItemClick = { album ->
                            scope.launch(Dispatchers.IO) {
                                val songs = database.albumSongs(album.id).first()
                                if (songs.isNotEmpty()) {
                                    val metadatas = songs.map { it.toMediaMetadata() }
                                    partyViewModel.addTracksToQueue(metadatas)
                                    withContext(Dispatchers.Main) {
                                        showAlbumImport = false
                                        scope.launch { snackbarHost.showSnackbar("Added ${metadatas.size} to queue") }
                                    }
                                }
                            }
                        }
                    )
                }

                if (showQr) {
                    PartyQrDialog(code = state.code, onDismiss = { showQr = false })
                }

                if (showScanner) {
                    QrScannerDialog(
                        onResult = { code ->
                            showScanner = false
                            if (code.isNotBlank()) {
                                navController.navigate("party/${'$'}code")
                            }
                        },
                        onDismiss = { showScanner = false }
                    )
                }
            } ?: run {
                // Loading/placeholder
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Connecting to party…",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumArtSection(
    partyState: PartyState,
    isHost: Boolean,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Album Art 1:1
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                partyState.currentTrack?.thumbnailUrl?.takeIf { it.isNotEmpty() }?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Uneditable progress bar along the bottom edge of the thumbnail
                val durationMs = partyState.currentTrack?.durationMs ?: 0L
                val positionMs = partyState.currentPositionMs
                val progress = if (durationMs > 0) positionMs.coerceAtLeast(0L).toFloat() / durationMs.toFloat() else 0f
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    color = MaterialTheme.colorScheme.primary
                )

                if (isHost) {
                    // Overlay controls (no seek bar)
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        IconButton(onClick = onSkipPrevious) { Icon(Icons.Default.SkipPrevious, contentDescription = null) }
                        Spacer(Modifier.width(16.dp))
                        IconButton(
                            onClick = onPlayPause,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(if (partyState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        Spacer(Modifier.width(16.dp))
                        IconButton(onClick = onSkipNext) { Icon(Icons.Default.SkipNext, contentDescription = null) }
                    }
                }
            }

            // Host chip near controls
            if (isHost) {
                Spacer(Modifier.height(8.dp))
                YouAreHostChip()
            }

            // Current / Total time (authoritative, uneditable)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDuration(partyState.currentPositionMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                val total = partyState.currentTrack?.durationMs ?: 0L
                Text(
                    text = if (total > 0) formatDuration(total) else "0:00",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = partyState.currentTrack?.title ?: "No song playing",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = partyState.currentTrack?.artist ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QueueSection(
    queue: List<PartyTrack>,
    isHost: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Up Next",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "The queue is empty",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Use search or import to add songs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                queue.forEach { song ->
                    QueueItem(
                        song = song,
                        isHost = isHost,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueItem(
    song: PartyTrack,
    isHost: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            song.thumbnailUrl.let { url ->
                if (url.isNotEmpty()) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Song Info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${song.artist} • Added by ${song.addedBy}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Drag handle with subtle disabled styling when not host
        Icon(
            imageVector = Icons.Rounded.DragHandle,
            contentDescription = if (isHost) "Reorder" else "Reorder (host only)",
            modifier = Modifier.padding(start = 8.dp),
            tint = if (isHost) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
    }
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = (milliseconds / 1000) % 60
    val minutes = (milliseconds / 1000) / 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun InlineSearchAndImportRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onImportPlaylists: () -> Unit,
    onImportAlbums: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search songs to add…") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = onSearch) {
                    Icon(imageVector = Icons.Rounded.Search, contentDescription = "Search")
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth()
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(onClick = onImportPlaylists) {
                Icon(imageVector = Icons.Rounded.LibraryMusic, contentDescription = "Import playlist")
            }

            Spacer(Modifier.width(12.dp))

            FilledTonalIconButton(onClick = onImportAlbums) {
                Icon(imageVector = Icons.Rounded.LibraryMusic, contentDescription = "Import album")
            }
        }
    }
}

@Composable
private fun ImportButtonsRow(
    onImportPlaylists: () -> Unit,
    onImportAlbums: () -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalIconButton(onClick = onImportPlaylists) {
            Icon(imageVector = Icons.Rounded.LibraryMusic, contentDescription = "Import playlist")
        }

        Spacer(Modifier.width(12.dp))

        FilledTonalIconButton(onClick = onImportAlbums) {
            Icon(imageVector = Icons.Rounded.LibraryMusic, contentDescription = "Import album")
        }
    }
}

@Composable
private fun <T> ImportDialogList(
    title: String,
    items: List<T>,
    getTitle: (T) -> String,
    getSubtitle: (T) -> String?,
    getThumbnail: (T) -> String?,
    onDismiss: () -> Unit,
    onItemClick: (T) -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.lazy.LazyColumn(
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(items.size) { idx ->
                        val item = items[idx]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onItemClick(item) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val thumb = getThumbnail(item)
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (!thumb.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = thumb,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = getTitle(item),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                getSubtitle(item)?.let { sub ->
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

private enum class PartyTab { Queue, Results }

// Local state for bottom sheet visibility
private var bottomSheetVisible by androidx.compose.runtime.mutableStateOf(false)

@OptIn(ExperimentalM3::class)
@Composable
private fun PartyBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    activeTab: PartyTab,
    onTabChange: (PartyTab) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    results: List<SongItem>,
    onEnqueue: (com.dd3boh.outertune.models.MediaMetadata) -> Unit,
    queue: List<PartyTrack>,
    isHost: Boolean,
    onRemoveAt: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    if (!visible) return
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) { Icon(imageVector = Icons.Rounded.ExpandMore, contentDescription = "Collapse") }
            // Tabs
            PrimaryTabRow(selectedTabIndex = if (activeTab == PartyTab.Queue) 0 else 1) {
                Tab(selected = activeTab == PartyTab.Queue, onClick = { onTabChange(PartyTab.Queue) }, text = { Text("Queue") })
                Tab(selected = activeTab == PartyTab.Results, onClick = { onTabChange(PartyTab.Results) }, text = { Text("Results") })
            }

            Spacer(Modifier.height(8.dp))

            if (activeTab == PartyTab.Results) {
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search songs to add…") },
                    trailingIcon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        disabledContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                when {
                    isSearching -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) { androidx.compose.material3.CircularProgressIndicator() }
                    }
                    results.isEmpty() -> Text(
                        text = if (query.isBlank()) "Type to search songs" else "No results",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> {
                        LazyColumn {
                            itemsIndexed(results, key = { index, _ -> index }) { _, item ->
                                QueueItem(
                                    song = PartyTrack(
                                        id = item.id,
                                        title = item.title,
                                        artist = item.artists.joinToString(", ") { it.name },
                                        duration = (item.duration ?: 0).toLong(),
                                        thumbnailUrl = item.toMediaMetadata().thumbnailUrl ?: "",
                                        addedBy = ""
                                    ),
                                    isHost = false,
                                    modifier = Modifier
                                        .clickable { onEnqueue(item.toMediaMetadata()) }
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // Queue with drag-to-reorder and remove
                val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
                var dragInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
                val localQueue = remember(queue) { androidx.compose.runtime.mutableStateListOf<PartyTrack>().apply { addAll(queue) } }
                LaunchedEffect(queue) {
                    if (dragInfo == null) {
                        localQueue.clear(); localQueue.addAll(queue)
                    }
                }
                val reorderState = rememberReorderableLazyListState(lazyListState = lazyListState) { from, to ->
                    val current = dragInfo
                    dragInfo = if (current == null) from.index to to.index else current.first to to.index
                    localQueue.add(to.index, localQueue.removeAt(from.index))
                }
                LaunchedEffect(reorderState.isAnyItemDragging) {
                    if (!reorderState.isAnyItemDragging) {
                        dragInfo?.let { (from, to) -> if (from != to) onMove(from, to) }
                        dragInfo = null
                    }
                }
                LazyColumn(state = lazyListState) {
                    itemsIndexed(localQueue, key = { _, it -> "${it.id}-${it.addedAt}" }) { index, song ->
                        ReorderableItem(state = reorderState, key = "${song.id}-${song.addedAt}", enabled = isHost) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                QueueItem(
                                    song = song,
                                    isHost = isHost,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 4.dp)
                                )
                                // Always show controls, but disable and dim when not host
                                Icon(
                                    imageVector = Icons.Rounded.DragHandle,
                                    contentDescription = if (isHost) "Drag" else "Drag (host only)",
                                    tint = if (isHost) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                                IconButton(
                                    onClick = { if (isHost) onRemoveAt(index) },
                                    enabled = isHost
                                ) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = if (isHost) "Remove" else "Remove (host only)",
                                        tint = if (isHost) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HostBadge() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = "HOST",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun YouAreHostChip() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(50)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Icon(
                imageVector = Icons.Rounded.Groups,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "You are the host",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}