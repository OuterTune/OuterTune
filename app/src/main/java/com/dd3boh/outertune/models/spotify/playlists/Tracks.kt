package com.dd3boh.outertune.models.spotify.playlists

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Tracks(
    @SerialName("total")
    val totalTracksCount: Int
)
