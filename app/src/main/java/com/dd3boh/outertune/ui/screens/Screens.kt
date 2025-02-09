/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.ui.screens

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.dd3boh.outertune.R

@Immutable
sealed class Screens(
    @StringRes val titleId: Int,
    val icon: ImageVector,
    val route: String,
) {
    data object Home : Screens(R.string.home, Icons.Rounded.Home, "home")
    data object Songs : Screens(R.string.songs, Icons.Rounded.MusicNote, "songs")
    data object Folders : Screens(R.string.folders, Icons.Rounded.Folder, "folders")
    data object Artists : Screens(R.string.artists, Icons.Rounded.Person, "artists")
    data object Albums : Screens(R.string.albums, Icons.Rounded.Album, "albums")
    data object Playlists : Screens(R.string.playlists, Icons.AutoMirrored.Rounded.QueueMusic, "playlists")
    data object Library : Screens(R.string.library, Icons.Rounded.LibraryMusic, "library")

    companion object {
        val MainScreens = listOf(Home, Songs, Folders, Artists, Albums, Playlists)
        val MainScreensNew = listOf(Home, Library)

        fun getScreens(screens: String): List<Screens> {
            val result = ArrayList<Screens>()

            screens.toCharArray().forEach {
                result.add(
                when (it) {
                    'H' -> Home
                    'S' -> Songs
                    'F' -> Folders
                    'A' -> Artists
                    'B' -> Albums
                    'L' -> Playlists
                    else -> Home
                })
            }

            return result
        }
    }
}
