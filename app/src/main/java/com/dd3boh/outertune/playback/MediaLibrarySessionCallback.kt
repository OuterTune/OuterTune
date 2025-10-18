package com.dd3boh.outertune.playback

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.MediaSessionConstants
import com.dd3boh.outertune.constants.SongSortType
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.PlaylistEntity
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.extensions.metadata
import com.dd3boh.outertune.extensions.toMediaItem
import com.dd3boh.outertune.extensions.toggleRepeatMode
import com.dd3boh.outertune.extensions.toggleShuffleMode
import com.dd3boh.outertune.utils.reportException
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.plus
import javax.inject.Inject

class MediaLibrarySessionCallback @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val downloadUtil: DownloadUtil,
) : MediaLibrarySession.Callback {
    private val TAG = MediaLibrarySessionCallback::class.simpleName.toString()
    private val scope = CoroutineScope(Dispatchers.Main) + Job()
    lateinit var service: MusicService
    var toggleLike: () -> Unit = {}
    var toggleStartRadio: () -> Unit = {}
    var toggleLibrary: () -> Unit = {}

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        val connectionResult = super.onConnect(session, controller)
        return MediaSession.ConnectionResult.accept(
            connectionResult.availableSessionCommands.buildUpon()
                .add(MediaSessionConstants.CommandToggleLibrary)
                .add(MediaSessionConstants.CommandToggleLike)
                .add(MediaSessionConstants.CommandToggleStartRadio)
                .add(MediaSessionConstants.CommandToggleShuffle)
                .add(MediaSessionConstants.CommandToggleRepeatMode)
                .add(SessionCommand(MusicService.COMMAND_GET_BINDER, Bundle.EMPTY))
                .build(),
            connectionResult.availablePlayerCommands
        )
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        when (customCommand.customAction) {
            MediaSessionConstants.ACTION_TOGGLE_LIKE -> toggleLike()
            MediaSessionConstants.ACTION_TOGGLE_START_RADIO -> toggleStartRadio()
            MediaSessionConstants.ACTION_TOGGLE_LIBRARY -> toggleLibrary()
            MediaSessionConstants.ACTION_TOGGLE_SHUFFLE -> session.player.toggleShuffleMode()
            MediaSessionConstants.ACTION_TOGGLE_REPEAT_MODE -> session.player.toggleRepeatMode()
            MusicService.COMMAND_GET_BINDER -> return Futures.immediateFuture(
                SessionResult(SessionResult.RESULT_SUCCESS).apply {
                    extras.putBinder("music_binder", service.MusicBinder())
                }
            )
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean,
    ): ListenableFuture<MediaItemsWithStartPosition> = scope.future(Dispatchers.IO) {
        // TODO: when this is stable, change to debug
        Log.i(TAG, "onPlaybackResumption() called")
        val q = database.getResumptionQueue()
        if (q == null) {
            Log.w(TAG, "No resumption queue data. Loading empty list")
            return@future MediaItemsWithStartPosition(emptyList(), C.INDEX_UNSET, C.TIME_UNSET)
        }
        Log.i(TAG, "Resumption queue found. Loading queue: size = ${q.queue.size}, queue name = ${q.title}, " +
                "queuePosShuffled = ${q.getQueuePosShuffled()}, lastSongPos = ${q.lastSongPos},")

       if (isForPlayback) {
           return@future MediaItemsWithStartPosition(
               q.getCurrentQueueShuffled().map { it.toMediaItem(isBrowsable = true) },
               q.getQueuePosShuffled(),
               q.lastSongPos
           )
       } else {
           return@future MediaItemsWithStartPosition(
               listOf(q.getCurrentSong()!!.toMediaItem(isBrowsable = true)),
               q.getQueuePosShuffled(),
               q.lastSongPos
           )
       }
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
        LibraryResult.ofItem(
            MediaItem.Builder()
                .setMediaId(MusicService.ROOT)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsPlayable(false)
                        .setIsBrowsable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build()
                )
                .build(),
            params
        )
    )

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future(Dispatchers.IO) {
        LibraryResult.ofItemList(
            when (parentId) {
                MusicService.ROOT -> listOf(
                    browsableMediaItem(
                        MusicService.SONG,
                        context.getString(R.string.songs),
                        null,
                        drawableUri(R.drawable.music_note),
                        MediaMetadata.MEDIA_TYPE_PLAYLIST
                    ),
                    browsableMediaItem(
                        MusicService.ARTIST,
                        context.getString(R.string.artists),
                        null,
                        drawableUri(R.drawable.artist),
                        MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS
                    ),
                    browsableMediaItem(
                        MusicService.ALBUM,
                        context.getString(R.string.albums),
                        null,
                        drawableUri(R.drawable.album),
                        MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                    ),
                    browsableMediaItem(
                        MusicService.PLAYLIST,
                        context.getString(R.string.playlists),
                        null,
                        drawableUri(R.drawable.queue_music),
                        MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                    )
                )

                MusicService.SONG -> database.songsByCreateDateAsc().first().map { it.toMediaItem(parentId) }
                MusicService.ARTIST -> database.artistsInLibraryAsc().first().map { artist ->
                    browsableMediaItem(
                        "${MusicService.ARTIST}/${artist.id}",
                        artist.artist.name,
                        context.resources.getQuantityString(R.plurals.n_song, artist.songCount, artist.songCount),
                        artist.artist.thumbnailUrl?.toUri(),
                        MediaMetadata.MEDIA_TYPE_ARTIST
                    )
                }

                MusicService.ALBUM -> database.albumsInLibraryAsc().first().map { album ->
                    browsableMediaItem(
                        "${MusicService.ALBUM}/${album.id}",
                        album.album.title,
                        album.artists.joinToString { it.name },
                        album.album.thumbnailUrl?.toUri(),
                        MediaMetadata.MEDIA_TYPE_ALBUM
                    )
                }

                MusicService.PLAYLIST -> {
                    val likedSongCount = database.likedSongsCount().first()
                    val downloadedSongCount = downloadUtil.downloads.value.size
                    listOf(
                        browsableMediaItem(
                            "${MusicService.PLAYLIST}/${PlaylistEntity.LIKED_PLAYLIST_ID}",
                            context.getString(R.string.liked_songs),
                            context.resources.getQuantityString(R.plurals.n_song, likedSongCount, likedSongCount),
                            drawableUri(R.drawable.favorite),
                            MediaMetadata.MEDIA_TYPE_PLAYLIST
                        ),
                        browsableMediaItem(
                            "${MusicService.PLAYLIST}/${PlaylistEntity.DOWNLOADED_PLAYLIST_ID}",
                            context.getString(R.string.downloaded_songs),
                            context.resources.getQuantityString(
                                R.plurals.n_song,
                                downloadedSongCount,
                                downloadedSongCount
                            ),
                            drawableUri(R.drawable.download),
                            MediaMetadata.MEDIA_TYPE_PLAYLIST
                        )
                    ) + database.playlistInLibraryAsc().first().map { playlist ->
                        browsableMediaItem(
                            "${MusicService.PLAYLIST}/${playlist.id}",
                            playlist.playlist.name,
                            context.resources.getQuantityString(
                                R.plurals.n_song,
                                playlist.songCount,
                                playlist.songCount
                            ),
                            drawableUri(R.drawable.queue_music),
                            MediaMetadata.MEDIA_TYPE_PLAYLIST
                        )
                    }
                }

                else -> when {
                    parentId.startsWith("${MusicService.ARTIST}/") ->
                        database.artistSongsByCreateDateAsc(parentId.removePrefix("${MusicService.ARTIST}/")).first()
                            .map {
                                it.toMediaItem(parentId)
                            }

                    parentId.startsWith("${MusicService.ALBUM}/") ->
                        database.albumSongs(parentId.removePrefix("${MusicService.ALBUM}/")).first().map {
                            it.toMediaItem(parentId)
                        }

                    parentId.startsWith("${MusicService.PLAYLIST}/") ->
                        when (val playlistId = parentId.removePrefix("${MusicService.PLAYLIST}/")) {
                            PlaylistEntity.LIKED_PLAYLIST_ID -> database.likedSongs(SongSortType.CREATE_DATE, true)
                            PlaylistEntity.DOWNLOADED_PLAYLIST_ID -> database.downloadNoLocalSongs()
                            else -> database.playlistSongs(playlistId).map { list ->
                                list.map { it.song }
                            }
                        }.first().map {
                            it.toMediaItem(parentId)
                        }

                    else -> emptyList()
                }
            },
            params
        )
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = scope.future(Dispatchers.IO) {
        database.song(mediaId).first()?.toMediaItem()?.let {
            LibraryResult.ofItem(it, null)
        } ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaItemsWithStartPosition> = scope.future {
        // Play from Android Auto
        Log.d(TAG, "MediaLibrarySessionCallback.onSetMediaItems")
        val defaultResult = MediaItemsWithStartPosition(emptyList(), startIndex, startPositionMs)
        val path = mediaItems.firstOrNull()?.mediaId?.split("/")
            ?: return@future defaultResult
        Log.d(TAG, "Path: " + path.joinToString(";"))

        val queue: Triple<List<MediaItem>, Int, Long> = when (path.firstOrNull()) {
            MusicService.SONG -> {
                val songId = path.getOrNull(1) ?: return@future defaultResult
                val allSongs = database.songsByCreateDateAsc().first()
                Triple(
                    allSongs.map { it.toMediaItem() },
                    allSongs.indexOfFirst { it.id == songId }.takeIf { it != -1 } ?: 0,
                    startPositionMs
                )
            }

            MusicService.ARTIST -> {
                val songId = path.getOrNull(2) ?: return@future defaultResult
                val artistId = path.getOrNull(1) ?: return@future defaultResult
                val songs = database.artistSongsByCreateDateAsc(artistId).first()
                Triple(
                    songs.map { it.toMediaItem() },
                    songs.indexOfFirst { it.id == songId }.takeIf { it != -1 } ?: 0,
                    startPositionMs
                )
            }

            MusicService.ALBUM -> {
                val songId = path.getOrNull(2) ?: return@future defaultResult
                val albumId = path.getOrNull(1) ?: return@future defaultResult
                val albumWithSongs = database.albumWithSongs(albumId).first() ?: return@future defaultResult
                Triple(
                    albumWithSongs.songs.map { it.toMediaItem() },
                    albumWithSongs.songs.indexOfFirst { it.id == songId }.takeIf { it != -1 } ?: 0,
                    startPositionMs
                )
            }

            MusicService.PLAYLIST -> {
                val songId = path.getOrNull(2) ?: return@future defaultResult
                val playlistId = path.getOrNull(1) ?: return@future defaultResult
                val songs = when (playlistId) {
                    PlaylistEntity.LIKED_PLAYLIST_ID -> database.likedSongs(SongSortType.CREATE_DATE, descending = true)
                    PlaylistEntity.DOWNLOADED_PLAYLIST_ID -> database.downloadNoLocalSongs()
                    else -> database.playlistSongs(playlistId).map { list ->
                        list.map { it.song }
                    }
                }.first()
                Triple(
                    songs.map { it.toMediaItem() },
                    songs.indexOfFirst { it.id == songId }.takeIf { it != -1 } ?: 0,
                    startPositionMs
                )
            }

            MusicService.SEARCH -> {
                val songId = path.getOrNull(2) ?: return@future defaultResult
                val searchQuery = path.getOrNull(1) ?: return@future defaultResult
                var results = combine(
                    database.searchSongs(searchQuery),
                    database.searchArtistSongs(searchQuery),
                ) { songs, artistSongs ->
                    (songs + artistSongs).distinctBy { it.id }
                }

                val items = results.first().map { it.toMediaItem() }
                val index = items.indexOfFirst { it.mediaId == songId }
                Triple(items, if (index > 0) index else 0, C.TIME_UNSET)
            }

            else -> Triple(emptyList<MediaItem>(), startIndex, startPositionMs)
        }

        val queueTitle = context.getString(R.string.android_auto)
        service.queueBoard.addQueue(
            queueTitle,
            queue.first.map { it.metadata },
            shuffled = false,
            replace = true,
            delta = false,
            startIndex = queue.second
        )
        MediaItemsWithStartPosition(queue.first, queue.second, queue.third)
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        Log.d(TAG, "MediaLibrarySessionCallback.onSearch: $query")
        session.notifySearchResultChanged(browser, query, 1, params)
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        Log.d(TAG, "MediaLibrarySessionCallback.onGetSearchResult: $query")
        return scope.future {
            if (query.isEmpty()) {
                return@future LibraryResult.ofItemList(emptyList(), params)
            }

            try {
                var results = combine(
                    database.searchSongs(query),
                    database.searchArtistSongs(query),
                ) { songs, artistSongs ->
                    (songs + artistSongs).distinctBy { it.id }
                }

                val items = results.first()
                    .map {
                        it.toMediaItem(
                            path = "${MusicService.SEARCH}/$query",
                            isPlayable = true,
                            isBrowsable = true
                        )
                    }
                LibraryResult.ofItemList(items, params)
            } catch (e: Exception) {
                Log.d(TAG, "Could not get search results")
                reportException(e)
                LibraryResult.ofItemList(emptyList(), params)
            }
        }
    }

    private fun drawableUri(@DrawableRes id: Int) = Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(context.resources.getResourcePackageName(id))
        .appendPath(context.resources.getResourceTypeName(id))
        .appendPath(context.resources.getResourceEntryName(id))
        .build()

    private fun browsableMediaItem(
        id: String,
        title: String,
        subtitle: String?,
        iconUri: Uri?,
        mediaType: Int = MediaMetadata.MEDIA_TYPE_MUSIC
    ) =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setArtist(subtitle)
                    .setArtworkUri(iconUri)
                    .setIsPlayable(false)
                    .setIsBrowsable(true)
                    .setMediaType(mediaType)
                    .build()
            )
            .build()

    private fun Song.toMediaItem(path: String, isPlayable: Boolean = true, isBrowsable: Boolean = false) =
        MediaItem.Builder()
            .setMediaId("$path/$id")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setSubtitle(artists.joinToString { it.name })
                    .setArtist(artists.joinToString { it.name })
                    .setArtworkUri(if (song.isLocal) song.localPath?.toUri() else song.thumbnailUrl?.toUri())
                    .setIsPlayable(isPlayable)
                    .setIsBrowsable(isBrowsable)
                    .setMediaType(MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()

    private fun com.dd3boh.outertune.models.MediaMetadata.toMediaItem(isPlayable: Boolean = true, isBrowsable: Boolean = false) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
           MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(artists.joinToString { it.name })
                .setArtist(artists.joinToString { it.name })
                .setArtworkUri(if (isLocal) localPath?.toUri() else thumbnailUrl?.toUri())
                .setAlbumTitle(album?.title)
                .setIsPlayable(isPlayable)
                .setIsBrowsable(isBrowsable)
                .setMediaType(MEDIA_TYPE_MUSIC)
                .build()
        )
        .build()
}