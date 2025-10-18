package com.dd3boh.outertune.ui.menu

import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastSumBy
import androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.LocalDownloadUtil
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.LocalSyncUtils
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.ListThumbnailSize
import com.dd3boh.outertune.constants.SyncMode
import com.dd3boh.outertune.constants.ThumbnailCornerRadius
import com.dd3boh.outertune.constants.YtmSyncModeKey
import com.dd3boh.outertune.db.entities.Event
import com.dd3boh.outertune.db.entities.Playlist
import com.dd3boh.outertune.db.entities.PlaylistSong
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.extensions.toMediaItem
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.playback.ExoDownloadService
import com.dd3boh.outertune.playback.queues.ListQueue
import com.dd3boh.outertune.playback.queues.YouTubeQueue
import com.dd3boh.outertune.ui.component.button.IconButton
import com.dd3boh.outertune.ui.component.items.ListItem
import com.dd3boh.outertune.ui.dialog.AddToPlaylistDialog
import com.dd3boh.outertune.ui.dialog.AddToQueueDialog
import com.dd3boh.outertune.ui.dialog.ArtistDialog
import com.dd3boh.outertune.ui.dialog.DetailsDialog
import com.dd3boh.outertune.ui.dialog.TextFieldDialog
import com.dd3boh.outertune.utils.joinByBullet
import com.dd3boh.outertune.utils.makeTimeString
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.syncCoroutine
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SongMenu(
    originalSong: Song,
    playlistSong: PlaylistSong? = null,
    playlist: Playlist? = null,
    event: Event? = null,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val density = LocalDensity.current
    val downloadUtil = LocalDownloadUtil.current
    val clipboardManager = LocalClipboard.current
    val syncUtils = LocalSyncUtils.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val syncMode by rememberEnumPreference(key = YtmSyncModeKey, defaultValue = SyncMode.RW)

    val song = originalSong
    val download by LocalDownloadUtil.current.getDownload(originalSong.id).collectAsState(initial = null)
    val coroutineScope =
        CoroutineScope(syncCoroutine) // rememberCoroutineScope has exception "rememberCoroutineScope left the composition"

    val currentFormatState = database.format(originalSong.id).collectAsState(initial = null)
    val currentFormat = currentFormatState.value

    var showEditDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showChooseQueueDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showDetailsDialog by rememberSaveable {
        mutableStateOf(false)
    }

    ListItem(
        title = song.song.title,
        subtitle = joinByBullet(
            song.artists.joinToString { it.name },
            makeTimeString(song.song.duration * 1000L)
        ),
        thumbnailContent = {
            val px = (ListThumbnailSize.value * density.density).roundToInt()
            AsyncImage(
                model = song.song.getThumbnailModel(px, px),
                contentDescription = null,
                modifier = Modifier
                    .size(ListThumbnailSize)
                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
            )
        },
        trailingContent = {
            IconButton(
                onClick = {
                    val s = song.song.toggleLike()
                    database.query {
                        update(s)
                    }

                    if (!s.isLocal) {
                        syncUtils.likeSong(s)
                    }
                }
            ) {
                Icon(
                    painter = painterResource(if (song.song.liked) R.drawable.favorite else R.drawable.favorite_border),
                    tint = if (song.song.liked) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    contentDescription = null
                )
            }
        }
    )

    HorizontalDivider()

    GridMenu(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        if (!song.song.isLocal)
            GridMenuItem(
                icon = Icons.Rounded.Radio,
                title = R.string.start_radio
            ) {
                onDismiss()
                playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()), isRadio = true)
            }

        GridMenuItem(
            icon = Icons.Rounded.PlayArrow,
            title = R.string.play
        ) {
            playerConnection.playQueue(
                queue = ListQueue(
                    title = song.title,
                    items = listOf(song.toMediaMetadata())
                )
            )
            onDismiss()
        }
        GridMenuItem(
            icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
            title = R.string.play_next
        ) {
            onDismiss()
            playerConnection.enqueueNext(song.toMediaItem())
        }
        GridMenuItem(
            icon = Icons.Rounded.Edit,
            title = R.string.edit
        ) {
            showEditDialog = true
        }
        GridMenuItem(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
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

        if (playlistSong != null && (playlist?.playlist?.isLocal == true
                    || (playlistSong.song.song.isLocal || syncMode == SyncMode.RW))
        ) {
            GridMenuItem(
                icon = Icons.Rounded.PlaylistRemove,
                title = R.string.remove_from_playlist
            ) {
                database.transaction {
                    move(playlistSong.map.playlistId, playlistSong.map.position, Int.MAX_VALUE)
                    delete(playlistSong.map.copy(position = Int.MAX_VALUE))
                }

                coroutineScope.launch {
                    playlist?.playlist?.browseId?.let { playlistId ->
                        if (playlistSong.map.setVideoId != null) {
                            YouTube.removeFromPlaylist(
                                playlistId, playlistSong.map.songId, playlistSong.map.setVideoId
                            )
                        }
                    }
                }

                onDismiss()
            }
        }

        if (!song.song.isLocal)
            DownloadGridMenu(
                localDateTime = download,
                onDownload = {
                    downloadUtil.download(song.toMediaMetadata())
                },
                onRemoveDownload = {
                    if (song.song.localPath != null) {
                        downloadUtil.delete(song)
                    } else {
                        DownloadService.sendRemoveDownload(
                            context,
                            ExoDownloadService::class.java,
                            song.id,
                            false
                        )
                    }
                }
            )


        GridMenuItem(
            icon = R.drawable.artist,
            title = R.string.view_artist
        ) {
            if (song.artists.size == 1) {
                navController.navigate("artist/${song.artists[0].id}")
                onDismiss()
            } else {
                showSelectArtistDialog = true
            }
        }
        if (song.song.albumId != null && !song.song.isLocal) {
            GridMenuItem(
                icon = Icons.Rounded.Album,
                title = R.string.view_album
            ) {
                onDismiss()
                navController.navigate("album/${song.song.albumId}")
            }
        }
        if (!song.song.isLocal)
            GridMenuItem(
                icon = Icons.Rounded.Share,
                title = R.string.share
            ) {
                onDismiss()
                val intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${song.id}")
                }
                context.startActivity(Intent.createChooser(intent, null))
            }
        GridMenuItem(
            icon = Icons.Rounded.Info,
            title = R.string.details
        ) {
            showDetailsDialog = true
        }
        if (!song.song.isLocal) {
            if (song.song.inLibrary == null) {
                GridMenuItem(
                    icon = Icons.Rounded.LibraryAdd,
                    title = R.string.add_to_library
                ) {
                    database.query {
                        update(song.song.toggleLibrary())
                    }
                }
            } else {
                GridMenuItem(
                    icon = Icons.Rounded.LibraryAddCheck,
                    title = R.string.remove_from_library
                ) {
                    database.query {
                        update(song.song.toggleLibrary())
                    }
                }
            }
        }
        if (event != null) {
            GridMenuItem(
                icon = Icons.Rounded.Delete,
                title = R.string.remove_from_history
            ) {
                onDismiss()
                database.query {
                    delete(event)
                }
            }
        }
    }

    /**
     * ---------------------------
     * Dialogs
     * ---------------------------
     */

    if (showEditDialog) {
        TextFieldDialog(
            icon = { Icon(imageVector = Icons.Rounded.Edit, contentDescription = null) },
            title = { Text(text = stringResource(R.string.edit_song)) },
            onDismiss = { showEditDialog = false },
            initialTextFieldValue = TextFieldValue(song.song.title, TextRange(song.song.title.length)),
            onDone = { title ->
                onDismiss()
                database.query {
                    update(song.song.copy(title = title))
                }
            }
        )
    }

    if (showChooseQueueDialog) {
        AddToQueueDialog(
            onAdd = { queueName ->
                val q = playerConnection.service.queueBoard.addQueue(
                    queueName, listOf(song.toMediaMetadata()),
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
            songIds = listOf(song.id),
            onPreAdd = { playlist ->
                playlist.playlist.browseId?.let { browseId ->
                    YouTube.addToPlaylist(browseId, song.id)
                }
                listOf(song.id)
            },
            onDismiss = { showChoosePlaylistDialog = false }
        )
    }

    if (showSelectArtistDialog) {
        ArtistDialog(
            navController = navController,
            artists = song.artists,
            onDismiss = { showSelectArtistDialog = false }
        )
    }

    if (showDetailsDialog) {
        DetailsDialog(
            mediaMetadata = song.toMediaMetadata(),
            currentFormat = currentFormat,
            currentPlayCount = song.playCount?.fastSumBy { it.count } ?: 0,
            clipboardManager = clipboardManager,
            setVisibility = { showDetailsDialog = it }
        )
    }
}
