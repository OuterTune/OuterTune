package com.dd3boh.outertune.ui.menu

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.dd3boh.outertune.LocalDatabase
import com.dd3boh.outertune.LocalDownloadUtil
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.ListItemHeight
import com.dd3boh.outertune.constants.ListThumbnailSize
import com.dd3boh.outertune.db.entities.Event
import com.dd3boh.outertune.db.entities.PlaylistSong
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.extensions.toMediaItem
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.playback.ExoDownloadService
import com.dd3boh.outertune.playback.PlayerConnection.Companion.queueBoard
import com.dd3boh.outertune.playback.queues.YouTubeQueue
import com.dd3boh.outertune.ui.component.DownloadGridMenu
import com.dd3boh.outertune.ui.component.GridMenu
import com.dd3boh.outertune.ui.component.GridMenuItem
import com.dd3boh.outertune.ui.component.ListDialog
import com.dd3boh.outertune.ui.component.SongListItem
import com.dd3boh.outertune.ui.component.TextFieldDialog
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.WatchEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SongMenu(
    originalSong: Song,
    playlistSong: PlaylistSong? = null,
    playlistBrowseId: String? = null,
    event: Event? = null,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val songState = database.song(originalSong.id).collectAsState(initial = originalSong)
    val song = songState.value ?: originalSong
    val download by LocalDownloadUtil.current.getDownload(originalSong.id).collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()

    var showEditDialog by rememberSaveable {
        mutableStateOf(false)
    }

    LaunchedEffect(song.song.liked) {
        downloadUtil.autoDownloadIfLiked(song.song)
    }

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

    var showChooseQueueDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToQueueDialog(
        isVisible = showChooseQueueDialog,
        onAdd = { queueName ->
            queueBoard.add(queueName, listOf(song.toMediaMetadata()), playerConnection,
                forceInsert = true, delta = false)
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
        onGetSong = { playlist ->
            coroutineScope.launch(Dispatchers.IO) {
                playlist.playlist.browseId?.let { browseId ->
                    YouTube.addToPlaylist(browseId, song.id)
                }
            }
            listOf(song.id)
        },
        onDismiss = { showChoosePlaylistDialog = false }
    )

    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSelectArtistDialog) {
        ListDialog(
            onDismiss = { showSelectArtistDialog = false }
        ) {
            items(
                items = song.artists,
                key = { it.id }
            ) { artist ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(ListItemHeight)
                        .clickable {
                            navController.navigate("artist/${artist.id}")
                            showSelectArtistDialog = false
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = artist.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(ListThumbnailSize)
                                .clip(CircleShape)
                        )
                    }
                    Text(
                        text = artist.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }

    SongListItem(
        song = song,
        badges = {},
        trailingContent = {
            IconButton(
                onClick = {
                    database.query {
                        update(song.song.toggleLike())
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
        GridMenuItem(
            icon = Icons.Rounded.Radio,
            title = R.string.start_radio,
            enabled = !song.song.isLocal
        ) {
            onDismiss()
            playerConnection.playQueue(YouTubeQueue(WatchEndpoint(videoId = song.id), song.toMediaMetadata(), playlistId = WatchEndpoint(videoId = song.id).playlistId))
        }
        GridMenuItem(
            icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
            title = R.string.play_next
        ) {
            onDismiss()
            playerConnection.playNext(song.toMediaItem())
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

        if (playlistSong != null) {
            GridMenuItem(
                icon = Icons.Rounded.PlaylistRemove,
                title = R.string.remove_from_playlist
            ) {
                database.transaction {
                    coroutineScope.launch {
                        playlistBrowseId?.let { playlistId ->
                            if (playlistSong.map.setVideoId != null) {
                                YouTube.removeFromPlaylist(
                                    playlistId, playlistSong.map.songId, playlistSong.map.setVideoId
                                )
                            }
                        }
                    }
                    move(playlistSong.map.playlistId, playlistSong.map.position, Int.MAX_VALUE)
                    delete(playlistSong.map.copy(position = Int.MAX_VALUE))
                }

                onDismiss()
            }
        }

        DownloadGridMenu(
            state = download?.state,
            enabled = !song.song.isLocal,
            onDownload = {
                downloadUtil.download(song.toMediaMetadata())
            },
            onRemoveDownload = {
                DownloadService.sendRemoveDownload(
                    context,
                    ExoDownloadService::class.java,
                    song.id,
                    false
                )
            }
        )
        GridMenuItem(
            icon = R.drawable.artist,
            title = R.string.view_artist,
            enabled = song.artists.isNotEmpty()
        ) {
            if (song.artists.size == 1) {
                navController.navigate("artist/${song.artists[0].id}")
                onDismiss()
            } else {
                showSelectArtistDialog = true
            }
        }
        GridMenuItem(
            icon = Icons.Rounded.Album,
            title = R.string.view_album,
            enabled = song.song.albumId != null && !song.song.isLocal
        ) {
            onDismiss()
            navController.navigate("album/${song.song.albumId}")
        }
        GridMenuItem(
            icon = Icons.Rounded.Share,
            title = R.string.share,
            enabled = !song.song.isLocal
        ) {
            onDismiss()
            val intent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${song.id}")
            }
            context.startActivity(Intent.createChooser(intent, null))
        }
        if (song.song.inLibrary == null) {
            GridMenuItem(
                icon = Icons.Rounded.LibraryAdd,
                title = R.string.add_to_library,
                enabled = !song.song.isLocal
            ) {
                database.query {
                    update(song.song.toggleLibrary())
                }
            }
        } else {
            GridMenuItem(
                icon = Icons.Rounded.LibraryAddCheck,
                title = R.string.remove_from_library,
                enabled = !song.song.isLocal
            ) {
                database.query {
                    update(song.song.toggleLibrary())
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
}
