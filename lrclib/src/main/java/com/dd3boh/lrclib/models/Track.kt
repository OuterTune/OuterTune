package com.dd3boh.lrclib.models

import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
data class Track(
    val id: Int,
    val trackName: String,
    val artistName: String,
    val duration: Double,
    val plainLyrics: String?,
    val syncedLyrics: String?,
)

internal fun List<Track>.bestMatchingFor(duration: Int) =
    if (duration == -1) firstOrNull()
    else firstOrNull { abs(it.duration.toInt() - duration) <= 8 }
