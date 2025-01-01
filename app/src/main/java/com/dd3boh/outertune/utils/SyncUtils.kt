package com.dd3boh.outertune.utils

import android.content.Context
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.ArtistEntity
import com.dd3boh.outertune.db.entities.PlaylistEntity
import com.dd3boh.outertune.db.entities.PlaylistSongMap
import com.dd3boh.outertune.db.entities.SongEntity
import com.dd3boh.outertune.extensions.isInternetConnected
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.playback.DownloadUtil
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.ArtistItem
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.utils.completed
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton class for syncing local data from remote YouTube Music
 */
@Singleton
class SyncUtils @Inject constructor(
    val database: MusicDatabase,
    private val downloadUtil: DownloadUtil,
    @ApplicationContext private val context: Context
) {
    private val _isSyncingRemoteLikedSongs = MutableStateFlow(false)
    private val _isSyncingRemoteSongs = MutableStateFlow(false)
    private val _isSyncingRemoteAlbums = MutableStateFlow(false)
    private val _isSyncingRemoteArtists = MutableStateFlow(false)
    private val _isSyncingRemotePlaylists = MutableStateFlow(false)

    val isSyncingRemoteLikedSongs: StateFlow<Boolean> = _isSyncingRemoteLikedSongs.asStateFlow()
    val isSyncingRemoteSongs: StateFlow<Boolean> = _isSyncingRemoteSongs.asStateFlow()
    val isSyncingRemoteAlbums: StateFlow<Boolean> = _isSyncingRemoteAlbums.asStateFlow()
    val isSyncingRemoteArtists: StateFlow<Boolean> = _isSyncingRemoteArtists.asStateFlow()
    val isSyncingRemotePlaylists: StateFlow<Boolean> = _isSyncingRemotePlaylists.asStateFlow()

    private val logTag = "SyncUtils"

    suspend fun syncAll() {
        coroutineScope {
            launch { syncRemoteLikedSongs() }
            launch { syncRemoteSongs() }
            launch { syncRemoteAlbums() }
            launch { syncRemoteArtists() }
            launch { syncRemotePlaylists() }
        }
    }

    /**
     * Singleton syncRemoteLikedSongs
     */
    suspend fun syncRemoteLikedSongs() {
        if (!_isSyncingRemoteLikedSongs.compareAndSet(expect = false, update = true)) {
            Timber.tag(logTag).d("Liked songs synchronization already in progress")
            return // Synchronization already in progress
        }

        try {
            Timber.tag(logTag).d("Liked songs synchronization started")

            // Get remote and local liked songs
            YouTube.playlist("LM").completed().onSuccess{ page->
                if (!context.isInternetConnected()) {
                    return
                }

                val remoteSongs = page.songs.reversed()

                // Identify local songs to unlike
                val songsToUnlike = database.likedSongsByNameAsc().first()
                    .filterNot { it.song.isLocal }
                    .filterNot { localSong -> remoteSongs.any { it.id == localSong.id } }

                // Unlike local songs in the database
                coroutineScope {
                    songsToUnlike.forEach { song ->
                        launch(Dispatchers.IO) {
                            database.update(song.song.localToggleLike())
                        }
                    }
                }

                // Insert or like songs in the database
                for (remoteSong in remoteSongs) {
                    val localSong = database.song(remoteSong.id).firstOrNull()
                    database.transaction {
                        if (localSong == null) {
                            insert(remoteSong.toMediaMetadata(), SongEntity::localToggleLike)
                        } else if (!localSong.song.liked) {
                            update(localSong.song.localToggleLike())
                        }
                    }
                }
            }

            val songs = database.likedSongsNotDownloaded().first().map { it.song }
            downloadUtil.autoDownloadIfLiked(songs)
        } finally {
            Timber.tag(logTag).d("Liked songs synchronization ended")
            _isSyncingRemoteLikedSongs.value = false
        }
    }

    /**
     * Singleton syncRemoteSongs
     */
    suspend fun syncRemoteSongs() {
        if (!_isSyncingRemoteSongs.compareAndSet(expect = false, update = true)) {
            Timber.tag(logTag).d("Library songs synchronization already in progress")
            return // Synchronization already in progress
        }

        try {
            Timber.tag(logTag).d("Library songs synchronization started")

            // Get remote songs (from library and uploads)
            val remoteSongs = getRemoteData<SongItem>("FEmusic_liked_videos", "FEmusic_library_privately_owned_tracks")
            if (!context.isInternetConnected()) {
                return
            }

            // Identify local songs to remove
            val songsToRemoveFromLibrary = database.songsByNameAsc().first()
                .filterNot { it.song.isLocal }
                .filterNot { localSong -> remoteSongs.any { it.id == localSong.id } }

            // Remove local songs from the database
            coroutineScope {
                songsToRemoveFromLibrary.forEach { song ->
                    launch(Dispatchers.IO) {
                        database.update(song.song.toggleLibrary())
                    }
                }
            }

            // Inset or mark songs to library
            coroutineScope {
                val jobs = remoteSongs.map { song ->
                    launch(Dispatchers.IO) {
                        val dbSong = database.song(song.id).firstOrNull()
                        database.transaction {
                            if (dbSong == null) {
                                insert(song.toMediaMetadata(), SongEntity::toggleLibrary)
                            } else if (dbSong.song.inLibrary == null) {
                                update(dbSong.song.toggleLibrary())
                            }
                        }
                    }
                }
                jobs.joinAll()
            }
        } finally {
            Timber.tag(logTag).d("Library songs synchronization ended")
            _isSyncingRemoteSongs.value = false
        }
    }

    /**
     * Singleton syncRemoteAlbums
     */
    suspend fun syncRemoteAlbums() {
        if (!_isSyncingRemoteAlbums.compareAndSet(expect = false, update = true)) {
            Timber.tag(logTag).d("Library albums synchronization already in progress")
            return // Synchronization already in progress
        }

        try {
            Timber.tag(logTag).d("Library albums synchronization started")

            // Get remote albums (from library and uploads)
            val remoteAlbums = getRemoteData<AlbumItem>("FEmusic_liked_albums", "FEmusic_library_privately_owned_releases")
            if (!context.isInternetConnected()) {
                return
            }

            // Identify local albums to remove
            val albumsToRemoveFromLibrary = database.albumsLikedAsc().first()
                .filterNot { it.album.isLocal }
                .filterNot { localAlbum -> remoteAlbums.any { it.id == localAlbum.id } }

            // Remove albums from local database
            coroutineScope {
                albumsToRemoveFromLibrary.forEach { album ->
                    launch(Dispatchers.IO) {
                        database.update(album.album.localToggleLike())
                    }
                }
            }

            // Add or mark albums in local database
            coroutineScope {
                remoteAlbums.forEach { remoteAlbum ->
                    launch(Dispatchers.IO) {
                        val localAlbum = database.album(remoteAlbum.id).firstOrNull()
                        if (localAlbum == null) {
                            database.insert(remoteAlbum)
                            database.album(remoteAlbum.id).firstOrNull()?.let {
                                database.update(it.album.localToggleLike())
                            }
                        } else if (localAlbum.album.bookmarkedAt == null) {
                            database.update(localAlbum.album.localToggleLike())
                        }
                    }
                }
            }
        } finally {
            Timber.tag(logTag).d("Library albums synchronization ended")
            _isSyncingRemoteAlbums.value = false // Use the correct AtomicBoolean
        }
    }

    /**
     * Singleton syncRemoteArtists
     */
    suspend fun syncRemoteArtists() {
        if (!_isSyncingRemoteArtists.compareAndSet(expect = false, update = true)) {
            Timber.tag(logTag).d("Artist subscriptions synchronization already in progress")
            return // Synchronization already in progress
        }

        try {
            Timber.tag(logTag).d("Artist subscriptions synchronization started")

            // Get remote artists (from library and uploads)
            val remoteArtists = getRemoteData<ArtistItem>("FEmusic_library_corpus_track_artists", "FEmusic_library_privately_owned_artists")
            if (!context.isInternetConnected()) {
                return
            }

            // Get local artists
            val artistsToRemoveFromSubscriptions = database.artistsBookmarkedAsc().first()
                .filterNot { it.artist.isLocal }
                .filterNot { localArtist -> remoteArtists.any { it.id == localArtist.id } }

            // Remove local artists from the database
            coroutineScope {
                artistsToRemoveFromSubscriptions.forEach { artist ->
                    launch(Dispatchers.IO) {
                        database.update(artist.artist.localToggleLike())
                    }
                }
            }

            // Add or mark artists in the database
            coroutineScope {
                remoteArtists.forEach { remoteArtist ->
                    launch(Dispatchers.IO) {
                        val localArtist = database.artist(remoteArtist.id).firstOrNull()
                        database.transaction {
                            if (localArtist == null) {
                                insert(
                                    ArtistEntity(
                                        id = remoteArtist.id,
                                        name = remoteArtist.title,
                                        thumbnailUrl = remoteArtist.thumbnail,
                                        channelId = remoteArtist.channelId,
                                        bookmarkedAt = LocalDateTime.now()
                                    )
                                )
                            } else if (localArtist.artist.bookmarkedAt == null) {
                                update(localArtist.artist.localToggleLike())
                            }
                        }
                    }
                }
            }
        } finally {
            Timber.tag(logTag).d("Artist subscriptions synchronization ended")
            _isSyncingRemoteArtists.value = false
        }
    }

    /**
     * Singleton syncRemotePlaylists
     */
    suspend fun syncRemotePlaylists() {
        if (!_isSyncingRemotePlaylists.compareAndSet(expect = false, update = true)) {
            Timber.tag(logTag).d("Library playlist synchronization already in progress")
            return
        }

        try {
            Timber.tag(logTag).d("Library playlist synchronization started")

            // Get remote and local playlists
            YouTube.library("FEmusic_liked_playlists").completed().onSuccess { page ->
                if (!context.isInternetConnected()) {
                    return
                }

                val remotePlaylists = page.items.filterIsInstance<PlaylistItem>()
                    .filterNot { it.id == "LM" ||  it.id == "SE" }
                    .reversed()

                val localPlaylists = database.playlistInLibraryAsc().first()

                // Identify playlists to remove
                val playlistsToRemove = localPlaylists
                    .filterNot { it.playlist.isLocal }
                    .filterNot { it.playlist.browseId == null }
                    .filterNot { localPlaylist -> remotePlaylists.any { it.id == localPlaylist.playlist.browseId } }

                // Remove playlists from the database
                coroutineScope {
                    playlistsToRemove.forEach { playlist ->
                        launch(Dispatchers.IO) {
                            database.update(playlist.playlist.localToggleLike())
                        }
                    }
                }

                // Add or update playlists in the database
                coroutineScope {
                    remotePlaylists.forEach { remotePlaylist ->
                        launch(Dispatchers.IO) {
                            var localPlaylist =
                                localPlaylists.find { remotePlaylist.id == it.playlist.browseId }?.playlist
                            if (localPlaylist == null) {
                                localPlaylist = PlaylistEntity(
                                    name = remotePlaylist.title,
                                    browseId = remotePlaylist.id,
                                    isEditable = remotePlaylist.isEditable,
                                    bookmarkedAt = LocalDateTime.now(),
                                    thumbnailUrl = remotePlaylist.thumbnail,
                                    remoteSongCount = remotePlaylist.songCountText?.let {
                                        Regex("""\d+""").find(it)?.value?.toIntOrNull()
                                    },
                                    playEndpointParams = remotePlaylist.playEndpoint?.params,
                                    shuffleEndpointParams = remotePlaylist.shuffleEndpoint?.params,
                                    radioEndpointParams = remotePlaylist.radioEndpoint?.params
                                )
                                database.insert(localPlaylist)
                            } else {
                                database.update(localPlaylist, remotePlaylist)
                            }

                            // Fetch the playlist again after potential insertion/update
                            val updatedPlaylist =
                                database.playlistByBrowseId(remotePlaylist.id).firstOrNull()
                            updatedPlaylist?.let {
                                val playlistSongMaps = database.songMapsToPlaylist(updatedPlaylist.id)
                                if (updatedPlaylist.playlist.isEditable || playlistSongMaps.isNotEmpty()) {
                                    syncPlaylist(remotePlaylist.id, updatedPlaylist.id)
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            _isSyncingRemotePlaylists.value = false
            Timber.tag(logTag).d("Library playlist synchronization ended")
        }
    }

    suspend fun syncPlaylist(browseId: String, playlistId: String) {
        YouTube.playlist(browseId).completed().onSuccess { playlistPage ->
            if (!context.isInternetConnected()) {
                return
            }

            coroutineScope {
                launch(Dispatchers.IO) {
                    database.transaction {
                        clearPlaylist(playlistId)
                        val songEntities = playlistPage.songs
                            .map(SongItem::toMediaMetadata)
                            .onEach { insert(it) }

                        val playlistSongMaps = songEntities.mapIndexed { position, song ->
                            PlaylistSongMap(
                                songId = song.id,
                                playlistId = playlistId,
                                position = position,
                                setVideoId = song.setVideoId
                            )
                        }
                        playlistSongMaps.forEach { insert(it) }
                    }
                }
            }
        }
    }

    private suspend inline fun <reified T> getRemoteData(libraryId: String, uploadsId: String): MutableList<T> {
        val browseIds = mapOf(
            libraryId to 0,
            uploadsId to 1
        )

        val remote = mutableListOf<T>()
        coroutineScope {
            val fetchJobs = browseIds.map { (browseId, tab) ->
                async {
                    YouTube.library(browseId, tab).completed().onSuccess { page ->
                        val data = page.items.filterIsInstance<T>().reversed()
                        synchronized(remote) { remote.addAll(data) }
                    }
                }
            }
            fetchJobs.awaitAll()
        }

        return remote
    }
}
