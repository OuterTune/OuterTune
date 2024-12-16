package com.dd3boh.outertune.models.spotify.playlists

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyPlaylistPaginatedResponse(
    @SerialName("next")
    var nextUrl: String? = null,
    @SerialName("previous")
    var previousUrl: String? = null,
    @SerialName("total")
    var totalResults: Int? = null,
    var items: List<SpotifyPlaylistItem> = emptyList()
)
