package com.dd3boh.outertune.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.utils.reportException
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.AlbumItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumViewModel @Inject constructor(
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val albumId = savedStateHandle.get<String>("albumId")!!
    val albumWithSongs = database.albumWithSongs(albumId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val otherVersions = MutableStateFlow<List<AlbumItem>>(emptyList())

    val isLoading = MutableStateFlow(true)

    init {
        viewModelScope.launch {
            isLoading.value = true
            val album = database.album(albumId).first()
            if (album?.album?.isLocal == true) return@launch
            YouTube.album(albumId).onSuccess {
                if (album == null || album.album.songCount != it.songs.size) {
                    database.transaction {
                        if (album == null) insert(it)
                        else update(album.album, it)
                    }
                }
                otherVersions.value = it.otherVersions
            }.onFailure {
                isLoading.value = false
                reportException(it)
                if (it.message?.contains("NOT_FOUND") == true) {
                    // This album no longer exists in YouTube Music
                    database.query {
                        album?.album?.let(::delete)
                    }
                }
            }
        }
    }
}
