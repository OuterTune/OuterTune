package com.dd3boh.outertune.models.spotify.tracks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyResultPaginatedResponse(
    @SerialName("total")
    val totalCountOfLikedSongs: Int,
    @SerialName("next")
    val nextPaginatedUrl: String? = null,
    @SerialName("items")
    val tracks: List<Track>
)
