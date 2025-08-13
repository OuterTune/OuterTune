package com.dd3boh.outertune.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dd3boh.outertune.constants.PlaylistSongSortDescendingKey
import com.dd3boh.outertune.constants.PlaylistSongSortType
import com.dd3boh.outertune.constants.PlaylistSongSortTypeKey
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.extensions.reversed
import com.dd3boh.outertune.extensions.toEnum
import com.dd3boh.outertune.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocalPlaylistViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val playlistId = savedStateHandle.get<String>("playlistId")!!
    val playlist = database.playlist(playlistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val playlistSongs = combine(
        database.playlistSongs(playlistId),
        context.dataStore.data
            .map {
                it[PlaylistSongSortTypeKey].toEnum(PlaylistSongSortType.CUSTOM) to (it[PlaylistSongSortDescendingKey] ?: true)
            }
            .distinctUntilChanged()
    ) { songs, (sortType, sortDescending) ->
        when (sortType) {
            PlaylistSongSortType.CUSTOM -> songs
            PlaylistSongSortType.NAME -> songs.sortedBy { it.song.song.title.lowercase() }
            PlaylistSongSortType.ARTIST -> songs.sortedBy { song ->
                song.song.artists.joinToString { it.name }.lowercase()
            }
            PlaylistSongSortType.ADDED_DATE -> songs.sortedBy { it.song.song.inLibrary }
            PlaylistSongSortType.MODIFIED_DATE -> songs.sortedBy { it.song.song.dateModified }
            PlaylistSongSortType.RELEASE_DATE -> songs.sortedBy { it.song.song.getDateLong() }
        }.reversed(sortDescending && sortType != PlaylistSongSortType.CUSTOM)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // Fix playlist song order
        viewModelScope.launch {
            val sortedSongs = playlistSongs.first().sortedWith(compareBy({ it.map.position }, { it.map.id }))
            database.transaction {
                sortedSongs.forEachIndexed { index, song ->
                    if (song.map.position != index) {
                        update(song.map.copy(position = index))
                    }
                }
            }
        }
    }
}