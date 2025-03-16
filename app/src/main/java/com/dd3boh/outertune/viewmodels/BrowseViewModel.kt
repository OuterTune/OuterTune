package com.dd3boh.outertune.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.AlbumItem
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.utils.reportException
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.YTItem
import com.zionhuang.innertube.utils.completed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val browseId: String? = savedStateHandle.get<String>("browseId")

    val items = MutableStateFlow<List<YTItem>?>(emptyList())
    val title = MutableStateFlow<String?>("")

    init {
        viewModelScope.launch {
            browseId?.let {
                YouTube.browse(browseId, null).onSuccess { result ->
                    // Store the title
                    title.value = result.title

                    // Flatten the nested structure to get all YTItems
                    val allItems = result.items.flatMap { it.items }
                    items.value = allItems
                }.onFailure {
                    reportException(it)
                }
            }
        }
    }
}
