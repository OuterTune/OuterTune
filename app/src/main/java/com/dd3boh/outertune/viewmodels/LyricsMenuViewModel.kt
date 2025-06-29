package com.dd3boh.outertune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dd3boh.outertune.constants.LYRIC_FETCH_TIMEOUT
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.lyrics.LyricsHelper
import com.dd3boh.outertune.lyrics.LyricsResult
import com.dd3boh.outertune.models.MediaMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class LyricsMenuViewModel @Inject constructor(
    private val lyricsHelper: LyricsHelper,
    val database: MusicDatabase,
) : ViewModel() {
    private var job: Job? = null
    val results = MutableStateFlow(emptyList<LyricsResult>())
    val isLoading = MutableStateFlow(false)

    fun search(mediaId: String, title: String, artist: String, duration: Int) {
        isLoading.value = true
        results.value = emptyList()
        job?.cancel()
        job = viewModelScope.launch(Dispatchers.IO) {
            withTimeoutOrNull(LYRIC_FETCH_TIMEOUT) {
                lyricsHelper.getAllLyrics(mediaId, title, artist, duration) { result ->
                    results.update {
                        it + result
                    }
                }
            }
            isLoading.value = false
        }
    }

    fun cancelSearch() {
        job?.cancel()
        job = null
    }

    fun refetchLyrics(mediaMetadata: MediaMetadata) {
        CoroutineScope(Dispatchers.IO).launch {
            database.deleteLyricById(mediaMetadata.id)
            withTimeoutOrNull(LYRIC_FETCH_TIMEOUT) {
                lyricsHelper.getLyrics(mediaMetadata)
            }
        }
    }
}
