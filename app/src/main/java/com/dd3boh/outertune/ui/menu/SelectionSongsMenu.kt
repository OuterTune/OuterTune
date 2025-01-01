package com.dd3boh.outertune.ui.menu

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.LocalDownloadUtil
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.extensions.toMediaItem
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.playback.ExoDownloadService
import com.dd3boh.outertune.playback.PlayerConnection.Companion.queueBoard
import com.dd3boh.outertune.playback.queues.ListQueue
import com.dd3boh.outertune.ui.component.DefaultDialog
import com.dd3boh.outertune.ui.component.DownloadGridMenu
import com.dd3boh.outertune.ui.component.GridMenu
import com.dd3boh.outertune.ui.component.GridMenuItem
import java.time.LocalDateTime

/**
 * Generic song menu
 */
@Composable
fun SelectionMediaMetadataMenu(
    selection: List<MediaMetadata>,
    onDismiss: () -> Unit,
    clearAction: () -> Unit,
    onRemoveFromHistory: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val allInLibrary by remember(selection) { // exclude local songs
        mutableStateOf(selection.isNotEmpty() && selection.all { !it.isLocal && it.inLibrary != null })
    }
    val allLocal by remember(selection) { // if only local songs in this selection
        mutableStateOf(selection.isNotEmpty() && selection.all { it.isLocal })
    }

    val allLiked by remember(selection) {
        mutableStateOf(selection.isNotEmpty() && selection.all { it.liked })
    }

    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    LaunchedEffect(selection) {
        if (selection.isEmpty()) {
            onDismiss()
        } else {
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (selection.all { downloads[it.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (selection.all {
                        downloads[it.id]?.state == Download.STATE_QUEUED
                                || downloads[it.id]?.state == Download.STATE_DOWNLOADING
                                || downloads[it.id]?.state == Download.STATE_COMPLETED
                    }) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
            }
        }
    }

    var showChooseQueueDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToQueueDialog(
        isVisible = showChooseQueueDialog,
        onAdd = { queueName ->
            queueBoard.addQueue(queueName, selection, playerConnection, forceInsert = true, delta = false)
            queueBoard.setCurrQueue(playerConnection)
        },
        onDismiss = {
            showChooseQueueDialog = false
        }
    )

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            selection.map { it.id }
        },
        onDismiss = { showChoosePlaylistDialog = false }
    )

    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.remove_download_playlist_confirm, "selection"),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                    }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        selection.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.id,
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

    GridMenu (
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        GridMenuItem(
            icon = R.drawable.play,
            title = R.string.play
        ) {
            onDismiss()
            playerConnection.playQueue(
                ListQueue(
                    title = "Selection",
                    items = selection
                )
            )
            clearAction()
        }

        GridMenuItem(
            icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
            title = R.string.play_next,
        ) {
            onDismiss()
            playerConnection.enqueueNext(selection.map { it.toMediaItem() })
            clearAction()
        }

        GridMenuItem(
            icon = R.drawable.shuffle,
            title = R.string.shuffle
        ) {
            onDismiss()
            playerConnection.playQueue(
                ListQueue(
                    title = "Selection",
                    items = selection.shuffled()
                )
            )
            clearAction()
        }

        GridMenuItem(
            icon = R.drawable.queue_music,
            title = R.string.add_to_queue
        ) {
            showChooseQueueDialog = true
        }

        GridMenuItem(
            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
            title = R.string.add_to_playlist
        ) {
            showChoosePlaylistDialog = true
        }

        if (!allLocal) {
            if (allInLibrary) {
                GridMenuItem(
                    icon = Icons.Rounded.LibraryAddCheck,
                    title = R.string.remove_all_from_library
                ) {
                    database.transaction {
                        selection.forEach { song ->
                            toggleInLibrary(song.id, null)
                        }
                    }
                }
            } else {
                GridMenuItem(
                    icon = Icons.Rounded.LibraryAdd,
                    title = R.string.add_all_to_library
                ) {
                    database.transaction {
                        selection.forEach { song ->
                            if (!song.isLocal) {
                                toggleInLibrary(song.id, LocalDateTime.now())
                            }
                        }
                    }
                }
            }
        }

        GridMenuItem(
            icon = if (allLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            tint = { if (allLiked) MaterialTheme.colorScheme.error else LocalContentColor.current },
            title = if (allLiked) R.string.action_remove_like_all else R.string.action_like_all,
        ) {
            database.query {
                if (allLiked) {
                    selection.forEach { song ->
                        update(song.toSongEntity().toggleLike())
                    }
                } else {
                    selection.filter { !it.liked }.forEach { song ->
                        update(song.toSongEntity().toggleLike())
                    }
                }
            }
        }

        DownloadGridMenu(
            state = downloadState,
            onDownload = {
                val songs = selection.filterNot { it.isLocal }
                downloadUtil.download(songs)
            },
            onRemoveDownload = {
                showRemoveDownloadDialog = true
            }
        )

        if (onRemoveFromHistory != null) {
            GridMenuItem(
                icon = Icons.Rounded.Delete,
                title = R.string.remove_from_history,
            ) {
                onRemoveFromHistory()
                onDismiss()
                clearAction()
            }
        }
    }
}
