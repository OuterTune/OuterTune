package com.dd3boh.outertune.playback.queues

import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.WatchEndpoint
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.models.toMediaMetadata
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class YouTubeAlbumRadio(
    override val playlistId: String,
    override val startShuffled: Boolean = false
) : Queue {
    override val preloadItem: MediaMetadata? = null
    private val endpoint = WatchEndpoint(
        playlistId = playlistId,
        params = "wAEB"
    )
    private var continuation: String? = null

    override suspend fun getInitialStatus(): Queue.Status = withContext(IO) {
        val albumSongs = YouTube.albumSongs(playlistId).getOrThrow()
        val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
        continuation = nextResult.continuation
        Queue.Status(
            title = nextResult.title,
            items = (albumSongs + nextResult.items.subList(albumSongs.size, nextResult.items.size)).map { it.toMediaMetadata()},
            mediaItemIndex = nextResult.currentIndex ?: 0
        )
    }

    override fun hasNextPage(): Boolean = continuation != null

    override suspend fun nextPage(): List<MediaMetadata> {
        val nextResult = withContext(IO) {
            YouTube.next(endpoint, continuation).getOrThrow()
        }
        continuation = nextResult.continuation
        return nextResult.items.map { it.toMediaMetadata() }
    }
}
