package com.dd3boh.outertune.ui.menu

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.extensions.toMediaItem
import com.dd3boh.outertune.models.DirectoryTree
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.playback.queues.ListQueue
import com.dd3boh.outertune.ui.component.GridMenu
import com.dd3boh.outertune.ui.component.GridMenuItem
import com.dd3boh.outertune.ui.component.SongFolderItem
import com.dd3boh.outertune.utils.joinByBullet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun FolderMenu(
    folder: DirectoryTree,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val allFolderSongs = remember { mutableStateListOf<Song>() }
    var subDirSongCount by remember {
        mutableIntStateOf(0)
    }

    fun fetchAllSongsRecursive(onFetch: (() -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            val dbSongs = database.localSongsInDir(folder.getFullSquashedDir()).first()
            allFolderSongs.clear()
            allFolderSongs.addAll(dbSongs)
            if (onFetch != null) {
                onFetch()
            }
        }
    }

    LaunchedEffect(Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            database.localSongCountInPath(folder.getFullPath()).first()
            subDirSongCount = database.localSongCountInPath(folder.getFullPath()).first()
        }
    }

    var showChooseQueueDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToQueueDialog(
        isVisible = showChooseQueueDialog,
        onAdd = { queueName ->
            if (allFolderSongs.isEmpty()) return@AddToQueueDialog
            playerConnection.service.queueBoard.addQueue(
                queueName, allFolderSongs.map { it.toMediaMetadata() },
                forceInsert = true, delta = false
            )
            playerConnection.service.queueBoard.setCurrQueue()
        },
        onDismiss = {
            showChooseQueueDialog = false
        }
    )

    AddToPlaylistDialog(
        navController = navController,
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            if (allFolderSongs.isEmpty()) return@AddToPlaylistDialog emptyList()
            allFolderSongs.map { it.id }
        },
        onDismiss = { showChoosePlaylistDialog = false }
    )

    // folder info
    SongFolderItem(
        folderTitle = folder.getSquashedDir(),
        modifier = Modifier,
        subtitle = joinByBullet(
            pluralStringResource(R.plurals.n_song, subDirSongCount, subDirSongCount),
            folder.parent
        ),
    )

    HorizontalDivider()

    // options
    GridMenu(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        GridMenuItem(
            icon = Icons.Rounded.PlayArrow,
            title = R.string.play
        ) {
            onDismiss()
            fetchAllSongsRecursive {
                CoroutineScope(Dispatchers.Main).launch {
                    playerConnection.playQueue(
                        ListQueue(
                            title = folder.getSquashedDir().substringAfterLast('/'),
                            items = allFolderSongs.map { it.toMediaMetadata() },
                        )
                    )
                }
            }
        }
        GridMenuItem(
            icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
            title = R.string.play_next
        ) {
            onDismiss()
            fetchAllSongsRecursive {
                CoroutineScope(Dispatchers.Main).launch {
                    playerConnection.enqueueNext(allFolderSongs.map { it.toMediaItem() })
                }
            }
        }
        GridMenuItem(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            title = R.string.add_to_queue
        ) {
            showChooseQueueDialog = true
            fetchAllSongsRecursive()
        }
        GridMenuItem(
            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
            title = R.string.add_to_playlist
        ) {
            showChoosePlaylistDialog = true
            fetchAllSongsRecursive()
        }
    }
}
