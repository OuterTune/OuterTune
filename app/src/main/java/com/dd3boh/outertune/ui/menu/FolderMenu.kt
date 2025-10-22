package com.dd3boh.outertune.ui.menu

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Output
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
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
import androidx.compose.ui.platform.LocalContext
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
import com.dd3boh.outertune.ui.component.items.SongFolderItem
import com.dd3boh.outertune.ui.dialog.AddToPlaylistDialog
import com.dd3boh.outertune.ui.dialog.AddToQueueDialog
import com.dd3boh.outertune.utils.joinByBullet
import com.dd3boh.outertune.utils.lmScannerCoroutine
import com.dd3boh.outertune.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException

@Composable
fun FolderMenu(
    folder: DirectoryTree,
    coroutineScope: CoroutineScope,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val allFolderSongs = remember { mutableStateListOf<Song>() }
    var subDirSongCount by remember {
        mutableIntStateOf(0)
    }

    val m3uLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(lmScannerCoroutine) {
                try {
                    var result = "#EXTM3U\n"
                    allFolderSongs.forEach { s ->
                        val se = s.song
                        result += "#EXTINF:${se.duration},${s.artists.joinToString(";") { it.name }} - ${s.title}\n"
                        result += if (se.isLocal) "${se.id}, ${se.localPath}" else "https://youtube.com/watch?v=${se.id}"
                        result += "\n"
                    }
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(result.toByteArray(Charsets.UTF_8))
                    }
                } catch (e: IOException) {
                    reportException(e)
                }
            }
        }
    }

    suspend fun fetchAllSongsRecursive(onFetch: (() -> Unit)? = null) {
        val dbSongs = database.localSongsInDirDeep(folder.getFullSquashedDir()).first()
        allFolderSongs.clear()
        allFolderSongs.addAll(dbSongs)
        if (onFetch != null) {
            onFetch()
        }
    }

    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
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
        if (folder.toList().isEmpty()) return@GridMenu // all these action require some songs
        GridMenuItem(
            icon = Icons.Rounded.PlayArrow,
            title = R.string.play
        ) {
            onDismiss()
            coroutineScope.launch(Dispatchers.IO) {
                fetchAllSongsRecursive {
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
            coroutineScope.launch(Dispatchers.IO) {
                fetchAllSongsRecursive {
                    playerConnection.enqueueNext(allFolderSongs.map { it.toMediaItem() })
                }
            }
        }
        GridMenuItem(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            title = R.string.add_to_queue
        ) {
            showChooseQueueDialog = true
            coroutineScope.launch(Dispatchers.IO) {
                fetchAllSongsRecursive()
            }
        }
        GridMenuItem(
            icon = Icons.Rounded.Shuffle,
            title = R.string.shuffle
        ) {
            coroutineScope.launch(Dispatchers.IO) {
                fetchAllSongsRecursive {
                    playerConnection.playQueue(
                        ListQueue(
                            title = folder.currentDir.substringAfterLast('/'),
                            items = allFolderSongs.map { it.toMediaMetadata() },
                            startShuffled = true
                        )
                    )
                }
            }
            onDismiss()
        }
        GridMenuItem(
            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
            title = R.string.add_to_playlist
        ) {
            showChoosePlaylistDialog = true
            coroutineScope.launch(Dispatchers.IO) {
                fetchAllSongsRecursive()
            }
        }
        GridMenuItem(
            icon = Icons.Rounded.Output,
            title = R.string.m3u_export
        ) {
            m3uLauncher.launch("${folder.currentDir.trim('/')}.m3u")
        }
    }

    /**
     * ---------------------------
     * Dialogs
     * ---------------------------
     */

    if (showChooseQueueDialog) {
        AddToQueueDialog(
            onAdd = { queueName ->
                if (allFolderSongs.isEmpty()) return@AddToQueueDialog
                val q = playerConnection.service.queueBoard.addQueue(
                    queueName, allFolderSongs.map { it.toMediaMetadata() },
                    forceInsert = true, delta = false
                )
                q?.let {
                    playerConnection.service.queueBoard.setCurrQueue(it)
                }
            },
            onDismiss = {
                showChooseQueueDialog = false
            }
        )
    }

    if (showChoosePlaylistDialog) {
        AddToPlaylistDialog(
            navController = navController,
            songIds = if (allFolderSongs.isEmpty()) emptyList() else allFolderSongs.map { it.id },
            onDismiss = { showChoosePlaylistDialog = false }
        )
    }
}
