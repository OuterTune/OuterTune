package com.dd3boh.outertune.models.spotify.tracks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Track(
    @SerialName("track")
    val trackItem: TrackItem
)
