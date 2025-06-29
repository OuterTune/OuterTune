/*
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.utils

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.dd3boh.outertune.constants.LastAlbumSyncKey
import com.dd3boh.outertune.constants.LastArtistSyncKey
import com.dd3boh.outertune.constants.LastFullSyncKey
import com.dd3boh.outertune.constants.LastLibSongSyncKey
import com.dd3boh.outertune.constants.LastLikeSongSyncKey
import com.dd3boh.outertune.constants.LastPlaylistSyncKey
import com.dd3boh.outertune.constants.LastRecentActivitySyncKey
import com.dd3boh.outertune.constants.SyncConflictResolution
import com.dd3boh.outertune.constants.SyncContent
import com.dd3boh.outertune.constants.YtmSyncConflictKey
import com.dd3boh.outertune.constants.YtmSyncContentKey
import com.dd3boh.outertune.constants.decodeSyncString
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.ArtistEntity
import com.dd3boh.outertune.db.entities.PlaylistEntity
import com.dd3boh.outertune.db.entities.PlaylistSongMap
import com.dd3boh.outertune.db.entities.SongEntity
import com.dd3boh.outertune.extensions.isInternetConnected
import com.dd3boh.outertune.extensions.isAutoSyncEnabled
import com.dd3boh.outertune.extensions.toEnum
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.playback.DownloadUtil
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.ArtistItem
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.utils.completed
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import java.time.LocalDateTime
import java.time.ZoneOffset
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
    private val _isSyncingRecentActivity = MutableStateFlow(false)

    val isSyncingRemoteLikedSongs: StateFlow<Boolean> = _isSyncingRemoteLikedSongs.asStateFlow()
    val isSyncingRemoteSongs: StateFlow<Boolean> = _isSyncingRemoteSongs.asStateFlow()
    val isSyncingRemoteAlbums: StateFlow<Boolean> = _isSyncingRemoteAlbums.asStateFlow()
    val isSyncingRemoteArtists: StateFlow<Boolean> = _isSyncingRemoteArtists.asStateFlow()
    val isSyncingRemotePlaylists: StateFlow<Boolean> = _isSyncingRemotePlaylists.asStateFlow()
    val isSyncingRecentActivity: StateFlow<Boolean> = _isSyncingRecentActivity.asStateFlow()

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    val syncCoroutine = newSingleThreadContext("syncUtils")

    private val TAG = "SyncUtils"
    private val syncCd = 60000 * 30

    companion object {
        const val DEFAULT_SYNC_CONTENT = "ARPLSC"
    }

    suspend fun tryAutoSync(bypassCd: Boolean = false) {
        if (!context.isAutoSyncEnabled()) {
            return
        }
        Log.d(TAG, "Starting auto sync job")
        if (!bypassCd) {
            val lastSync = context.dataStore.get(LastFullSyncKey, LocalDateTime.now().toEpochSecond(ZoneOffset.UTC))
            val currentTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            if (currentTime - lastSync > syncCd) {
                Log.d(TAG, "Aborting auto sync. ${(currentTime - lastSync) * 60000} minutes until eligible")
                return
            }
        }
        coroutineScope {
            launch { syncRemoteLikedSongs() }
            launch { syncRemoteSongs() }
            launch { syncRemoteAlbums() }
            launch { syncRemoteArtists() }
            launch { syncRemotePlaylists() }
            context.dataStore.edit { settings ->
                settings[LastFullSyncKey] = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            }
        }
    }

    private fun checkEnabled(item: SyncContent): Boolean {
        return decodeSyncString(context.dataStore.get(YtmSyncContentKey, DEFAULT_SYNC_CONTENT)).contains(item)
    }
    private fun checkPartialSyncEligibility(key: Preferences.Key<Long>): Boolean {
        val lastSync = context.dataStore.get(key, LocalDateTime.now().toEpochSecond(ZoneOffset.UTC))
        val currentTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
        if (currentTime - lastSync > syncCd) {
            Log.d(TAG, "Aborting auto sync. ${(currentTime - lastSync) * 60000} minutes until eligible")
            return false
        }
        return true
    }

    private fun checkOverwrite(item: SyncConflictResolution): Boolean {
        return context.dataStore.get(YtmSyncConflictKey, SyncConflictResolution.ADD_ONLY.name)
            .toEnum(defaultValue = SyncConflictResolution.ADD_ONLY) == item
    }

    /**
     * Like single song
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun likeSong(s: SongEntity) {
        CoroutineScope(syncCoroutine).launch {
            YouTube.likeVideo(s.id, s.liked)
        }
    }

    /**
     * Add/remove to library single song
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun changeInLibrary(s: SongEntity) {
        CoroutineScope(syncCoroutine).launch {
            // we don't have an api call yet
        }
    }

    /**
     * Singleton syncRemoteLikedSongs
     */
    suspend fun syncRemoteLikedSongs(bypass: Boolean = false) {
        // REQUIRED: internet, no ongoing sync, and category enabled
        if (!_isSyncingRemoteLikedSongs.value && (!checkEnabled(SyncContent.LIKED_SONGS) || !context.isInternetConnected())) {
            if (_isSyncingRemoteLikedSongs.value)
                Log.i(TAG, "Library songs synchronization already in progress")
            return
        }
        // OPTIONAL: auto sync and cooldown
        if (!bypass) {
            if (!context.isAutoSyncEnabled() || !checkPartialSyncEligibility(LastLikeSongSyncKey)) {
                return
            }
        }
        _isSyncingRemoteLikedSongs.value = true

        try {
            Log.d(TAG, "Liked songs synchronization started")

            // Get remote and local liked songs
            YouTube.playlist("LM").completed().onSuccess { page ->
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
            context.dataStore.edit { settings ->
                settings[LastLikeSongSyncKey] = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            }
            Log.i(TAG, "Liked songs synchronization ended")
            _isSyncingRemoteLikedSongs.value = false
        }
    }

    /**
     * Singleton syncRemoteSongs
     */
    suspend fun syncRemoteSongs(bypass: Boolean = false) {
        // REQUIRED: internet, no ongoing sync, and category enabled
        if (!_isSyncingRemoteSongs.value && (!checkEnabled(SyncContent.PRIVATE_SONGS) || !context.isInternetConnected())) {
            if (_isSyncingRemoteSongs.value)
                Log.i(TAG, "Library songs synchronization already in progress")
            return
        }
        // OPTIONAL: auto sync and cooldown
        if (!bypass) {
            if (!context.isAutoSyncEnabled() || !checkPartialSyncEligibility(LastLibSongSyncKey)) {
                return
            }
        }
        _isSyncingRemoteSongs.value = true

        try {
            Log.i(TAG, "Library songs synchronization started")

            // Get remote songs (from library and uploads)
            val remoteSongs = getRemoteData<SongItem>("FEmusic_liked_videos", "FEmusic_library_privately_owned_tracks")
            if (!context.isInternetConnected()) {
                return
            }

            if (checkOverwrite(SyncConflictResolution.OVERWRITE_WITH_REMOTE)) {
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
            context.dataStore.edit { settings ->
                settings[LastLibSongSyncKey] = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            }
            Log.i(TAG, "Library songs synchronization ended")
            _isSyncingRemoteSongs.value = false
        }
    }

    /**
     * Singleton syncRemoteAlbums
     */
    suspend fun syncRemoteAlbums(bypass: Boolean = false) {
        // REQUIRED: internet, no ongoing sync, and category enabled
        if (!_isSyncingRemoteAlbums.value && (!checkEnabled(SyncContent.ALBUMS) || !context.isInternetConnected())) {
            if (_isSyncingRemoteAlbums.value)
                Log.i(TAG, "Library songs synchronization already in progress")
            return
        }
        // OPTIONAL: auto sync and cooldown
        if (!bypass) {
            if (!context.isAutoSyncEnabled() || !checkPartialSyncEligibility(LastAlbumSyncKey)) {
                return
            }
        }
        _isSyncingRemoteAlbums.value = true

        try {
            Log.i(TAG, "Library albums synchronization started")

            // Get remote albums (from library and uploads)
            val remoteAlbums =
                getRemoteData<AlbumItem>("FEmusic_liked_albums", "FEmusic_library_privately_owned_releases")
            if (!context.isInternetConnected()) {
                return
            }

            if (checkOverwrite(SyncConflictResolution.OVERWRITE_WITH_REMOTE)) {
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
            context.dataStore.edit { settings ->
                settings[LastAlbumSyncKey] = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            }
            Log.i(TAG, "Library albums synchronization ended")
            _isSyncingRemoteAlbums.value = false // Use the correct AtomicBoolean
        }
    }

    /**
     * Singleton syncRemoteArtists
     */
    suspend fun syncRemoteArtists(bypass: Boolean = false) {
        // REQUIRED: internet, no ongoing sync, and category enabled
        if (!_isSyncingRemoteArtists.value && (!checkEnabled(SyncContent.ARTISTS) || !context.isInternetConnected())) {
            if (_isSyncingRemoteArtists.value)
                Log.i(TAG, "Library songs synchronization already in progress")
            return
        }
        // OPTIONAL: auto sync and cooldown
        if (!bypass) {
            if (!context.isAutoSyncEnabled() || !checkPartialSyncEligibility(LastArtistSyncKey)) {
                return
            }
        }
        _isSyncingRemoteArtists.value = true

        try {
            Log.i(TAG, "Artist subscriptions synchronization started")

            // Get remote artists (from library and uploads)
            val likedArtists = getRemoteData<ArtistItem>(
                "FEmusic_library_corpus_artists",
                "FEmusic_library_privately_owned_artists"
            )
            val trackArtists = getRemoteData<ArtistItem>(
                "FEmusic_library_corpus_track_artists",
                "FEmusic_library_privately_owned_artists"
            )
            val remoteArtists = mutableListOf<ArtistItem>().apply {
                addAll(likedArtists)
                addAll(trackArtists.filterNot { trackArtist ->
                    likedArtists.any { it.id == trackArtist.id }
                })
            }

            if (!context.isInternetConnected()) {
                return
            }

            if (checkOverwrite(SyncConflictResolution.OVERWRITE_WITH_REMOTE)) {
                // Get local artists
                val artistsToRemoveFromSubscriptions = database.artistsBookmarkedAsc().first()
                    .filterNot { it.artist.isLocal }
                    .filterNot { localArtist -> likedArtists.any { it.id == localArtist.id } }

                // Remove local artists from the database
                coroutineScope {
                    artistsToRemoveFromSubscriptions.forEach { artist ->
                        launch(Dispatchers.IO) {
                            database.update(artist.artist.localToggleLike())
                        }
                    }
                }
            }

            // Add or mark artists in the database
            coroutineScope {
                remoteArtists.forEach { remoteArtist ->
                    launch(Dispatchers.IO) {
                        val localArtist = database.artist(remoteArtist.id).firstOrNull()
                        val isLikedArtist = likedArtists.contains(remoteArtist)

                        database.transaction {
                            if (localArtist == null) {
                                insert(
                                    ArtistEntity(
                                        id = remoteArtist.id,
                                        name = remoteArtist.title,
                                        thumbnailUrl = remoteArtist.thumbnail,
                                        channelId = remoteArtist.channelId,
                                        bookmarkedAt = if (isLikedArtist) LocalDateTime.now() else null
                                    )
                                )
                            } else if (localArtist.artist.bookmarkedAt == null && isLikedArtist) {
                                update(localArtist.artist.localToggleLike())
                            }
                        }
                    }
                }
            }
        } finally {
            context.dataStore.edit { settings ->
                settings[LastArtistSyncKey] = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            }
            Log.i(TAG, "Artist subscriptions synchronization ended")
            _isSyncingRemoteArtists.value = false
        }
    }

    /**
     * Singleton syncRemotePlaylists
     */
    suspend fun syncRemotePlaylists(bypass: Boolean = false) {
        // REQUIRED: internet, no ongoing sync, and category enabled
        if (!_isSyncingRemotePlaylists.value && (!checkEnabled(SyncContent.PLAYLISTS) || !context.isInternetConnected())) {
            if (_isSyncingRemotePlaylists.value)
                Log.i(TAG, "Library songs synchronization already in progress")
            return
        }
        // OPTIONAL: auto sync and cooldown
        if (!bypass) {
            if (!context.isAutoSyncEnabled() || !checkPartialSyncEligibility(LastPlaylistSyncKey)) {
                return
            }
        }
        _isSyncingRemotePlaylists.value = true

        try {
            Log.i(TAG, "Library playlist synchronization started")

            // Get remote and local playlists
            YouTube.library("FEmusic_liked_playlists").completed().onSuccess { page ->
                if (!context.isInternetConnected()) {
                    return
                }

                val remotePlaylists = page.items.filterIsInstance<PlaylistItem>()
                    .filterNot { it.id == "LM" || it.id == "SE" }
                    .reversed()

                val localPlaylists = database.playlistInLibraryAsc().first()

                if (checkOverwrite(SyncConflictResolution.OVERWRITE_WITH_REMOTE)) {
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
                                    isEditable = remotePlaylist.isEditable || remotePlaylist.author == null, // for some reason null == your account
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
            context.dataStore.edit { settings ->
                settings[LastPlaylistSyncKey] = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            }
            _isSyncingRemotePlaylists.value = false
            Log.i(TAG, "Library playlist synchronization ended")
        }
    }

    suspend fun syncPlaylist(browseId: String, playlistId: String) {
        // this is also used for individual playlist sync
        if (!context.isInternetConnected()) {
            return
        }
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

    suspend fun syncRecentActivity(bypass: Boolean = false) {
        // REQUIRED: internet, no ongoing sync, and category enabled
        if (!_isSyncingRecentActivity.value && (!checkEnabled(SyncContent.RECENT_ACTIVITY) || !context.isInternetConnected())) {
            if (_isSyncingRecentActivity.value)
                Log.i(TAG, "Library songs synchronization already in progress")
            return
        }
        // OPTIONAL: auto sync and cooldown
        if (!bypass) {
            if (!context.isAutoSyncEnabled() || !checkPartialSyncEligibility(LastRecentActivitySyncKey)) {
                return
            }
        }
        _isSyncingRecentActivity.value = true

        YouTube.libraryRecentActivity().onSuccess { page ->
            val recentActivity = page.items.take(9).drop(1)

            coroutineScope {
                launch(Dispatchers.IO) {
                    database.clearRecentActivity()

                    recentActivity.reversed().forEach { database.insertRecentActivityItem(it) }
                }
            }
        }

        context.dataStore.edit { settings ->
            settings[LastRecentActivitySyncKey] = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
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
