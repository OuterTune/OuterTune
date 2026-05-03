package com.zionhuang.innertube.pages

import com.zionhuang.innertube.models.Album
import com.zionhuang.innertube.models.Artist
import com.zionhuang.innertube.models.BrowseEndpoint
import com.zionhuang.innertube.models.PlaylistPanelVideoRenderer
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.WatchEndpoint
import com.zionhuang.innertube.models.oddElements
import com.zionhuang.innertube.models.splitBySeparator
import com.zionhuang.innertube.utils.parseTime

data class NextResult(
    val title: String? = null,
    val items: List<SongItem>,
    val currentIndex: Int? = null,
    val lyricsEndpoint: BrowseEndpoint? = null,
    val relatedEndpoint: BrowseEndpoint? = null,
    val continuation: String?,
    val endpoint: WatchEndpoint, // current or continuation next endpoint
)

object NextPage {
    fun fromPlaylistPanelVideoRenderer(renderer: PlaylistPanelVideoRenderer): SongItem? {
        val videoId = renderer.videoId ?: return null
        val title = renderer.title?.runs?.firstOrNull()?.text ?: return null
        val thumbnail = renderer.thumbnail.thumbnails.lastOrNull()?.url ?: return null
        val longByLineRuns = renderer.longBylineText?.runs?.splitBySeparator()
        val artistRuns = longByLineRuns?.firstOrNull()?.oddElements()
            ?: renderer.shortBylineText?.runs?.oddElements()
        val artists = artistRuns?.map {
            Artist(
                name = it.text,
                id = it.navigationEndpoint?.browseEndpoint?.browseId,
            )
        }.orEmpty()
        val album = longByLineRuns?.getOrNull(1)?.firstOrNull()?.takeIf {
            it.navigationEndpoint?.browseEndpoint != null
        }?.let {
            Album(
                name = it.text,
                id = it.navigationEndpoint?.browseEndpoint?.browseId!!,
            )
        }
        return SongItem(
            id = videoId,
            title = title,
            artists = artists,
            album = album,
            duration = renderer.lengthText?.runs?.firstOrNull()?.text?.parseTime(),
            thumbnail = thumbnail,
            explicit = renderer.badges?.find {
                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
            } != null,
            endpoint = renderer.navigationEndpoint.watchEndpoint,
            setVideoId = renderer.playlistSetVideoId,
        )
    }
}
