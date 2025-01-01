package com.dd3boh.outertune.extensions

import com.dd3boh.outertune.db.entities.Song

fun List<Song>.getAvailableSongs(isInternetConnected: Boolean): List<Song> {
    if (isInternetConnected) {
        return this
    }
    return filter { it.song.isAvailableOffline() }
}