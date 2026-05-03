package com.zionhuang.innertube.pages

import com.zionhuang.innertube.models.Album
import com.zionhuang.innertube.models.Artist
import com.zionhuang.innertube.models.MusicResponsiveListItemRenderer
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.oddElements
import com.zionhuang.innertube.utils.parseTime

data class PlaylistPage(
    val playlist: PlaylistItem,
    val songs: List<SongItem>,
    val songsContinuation: String?,
    val continuation: String?,
) {
    companion object {
        private fun MusicResponsiveListItemRenderer.resolvePlaylistVideoId(): String? =
            playlistItemData?.videoId
                ?: navigationEndpoint?.watchEndpoint?.videoId
                ?: navigationEndpoint?.watchPlaylistEndpoint?.videoId
                ?: overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer
                    ?.playNavigationEndpoint?.watchEndpoint?.videoId
                ?: overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer
                    ?.playNavigationEndpoint?.watchPlaylistEndpoint?.videoId

        fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): SongItem? {
            val id = renderer.resolvePlaylistVideoId() ?: return null
            val title = renderer.flexColumns.firstOrNull()
                ?.musicResponsiveListItemFlexColumnRenderer?.text
                ?.runs?.firstOrNull()?.text
                ?: PageHelper.extractRuns(renderer.flexColumns, "MUSIC_VIDEO").firstOrNull()?.text
                ?: return null
            return SongItem(
                id = id,
                title = title,
                artists = renderer.flexColumns.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.oddElements()?.map {
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId,
                    )
                }.orEmpty(),
                album = renderer.flexColumns.getOrNull(2)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.let {
                    Album(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId ?: return@let null
                    )
                },
                duration = renderer.fixedColumns?.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text?.parseTime(),
                thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                explicit = renderer.badges?.find {
                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                } != null,
                endpoint = renderer.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint,
                setVideoId = renderer.playlistItemData?.playlistSetVideoId
                    ?: renderer.navigationEndpoint?.watchEndpoint?.playlistSetVideoId
                    ?: renderer.navigationEndpoint?.watchPlaylistEndpoint?.playlistSetVideoId,
            )
        }
    }
}
