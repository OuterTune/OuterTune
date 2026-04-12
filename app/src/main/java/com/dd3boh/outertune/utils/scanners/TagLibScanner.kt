/*
 * Copyright (C) 2025 O﻿ute﻿rTu﻿ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.utils.scanners

import android.os.ParcelFileDescriptor
import android.util.Log
import com.dd3boh.outertune.constants.DEBUG_SAVE_OUTPUT
import com.dd3boh.outertune.constants.EXTRACTOR_DEBUG
import com.dd3boh.outertune.constants.SCANNER_DEBUG
import com.dd3boh.outertune.db.entities.AlbumEntity
import com.dd3boh.outertune.db.entities.ArtistEntity
import com.dd3boh.outertune.db.entities.FormatEntity
import com.dd3boh.outertune.db.entities.GenreEntity
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.db.entities.SongEntity
import com.dd3boh.outertune.models.SongTempData
import com.dd3boh.outertune.ui.utils.ARTIST_SEPARATORS
import com.dd3boh.outertune.ui.utils.EXTRACTOR_TAG
import com.kyant.taglib.TagLib
import java.io.File
import java.lang.Integer.parseInt
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset


class TagLibScanner : MetadataScanner {

    /**
     * Given a path to a file, extract necessary metadata.
     *
     * @param file Full file path
     */
    override suspend fun getAllMetadataFromFile(file: File): SongTempData {
        if (EXTRACTOR_DEBUG)
            Log.v(EXTRACTOR_TAG, "Starting Full Extractor session on: ${file.absolutePath}")

        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            val songId = SongEntity.generateSongId()
            var rawTitle: String? = null
            var albumName: String? = null
            var trackNumber: Int? = null
            var discNumber: Int? = null
            var year: Int? = null
            var date: LocalDateTime? = null
            var codec: String
            var bitrate: Int
            var sampleRate: Int
            var channels: Int
            var rawDuration: Int
            var replayGain: Double? = null

            var extraData: String = "" // extra data field
            var allData = "" // for debugging

            var artistList = ArrayList<ArtistEntity>()
            var genresList = ArrayList<GenreEntity>()


            // Read audio properties
            val audioProperties = TagLib.getAudioProperties(fd.dup().detachFd())!!
            rawDuration = audioProperties.length
            channels = audioProperties.channels
            sampleRate = audioProperties.sampleRate
            bitrate = audioProperties.bitrate * 1000
            codec = audioProperties.codec


            // Read metadata
            val metadata = TagLib.getMetadata(fd = fd.dup().detachFd(), readPictures = false)!!

            /**
             * I have never seen such incomprehensible behaviour, and believe me, I have seen some
             * shit while developing this app.
             *
             * Why the everylovingfuck does this not work when you try to for each key????  but for
             * some assbackwards reason this mess works????? So much for trying to write optimized code
             */
            metadata.propertyMap.forEach { key, value ->
                value.forEach {
                    if (EXTRACTOR_DEBUG && DEBUG_SAVE_OUTPUT) {
                        allData += "\n$key: $it"
                    }

                    when (key) {
                        // why the fsck does an error here get swallowed silently????
                        "ARTISTS", "ARTIST", "artist" -> {
                            val splitArtists = it.split(ARTIST_SEPARATORS)
                            splitArtists.forEach { artistVal ->
                                artistList.add(ArtistEntity(ArtistEntity.generateArtistId(), artistVal, isLocal = true))
                            }
                        }

                        "ALBUM", "album" -> albumName = it
                        "TITLE", "title" -> rawTitle = it
                        "GENRE", "genre" -> {
                            val splitGenres = it.split(ARTIST_SEPARATORS)
                            splitGenres.forEach { genreVal ->
                                genresList.add(GenreEntity(GenreEntity.generateGenreId(), genreVal, isLocal = true))

                            }
                        }
                        // date can have multiple list elements, though let it parse via string regardless
                        "DATE", "date" -> {
                            try {
                                if (date == null) {
                                    date = LocalDate.parse(it.trim()).atStartOfDay()
                                }
                            } catch (e: Exception) {
                                if (SCANNER_DEBUG) {
                                    e.printStackTrace()
                                }
                                try {
                                    if (year == null) {
                                        year = date?.year ?: parseInt(it.trim())
                                    }
                                } catch (e: Exception) {
                                    if (SCANNER_DEBUG) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }

                        "TRACKNUMBER" -> {
                            try {
                            trackNumber = parseInt(it)
                            } catch (e: Exception) {
                                if (SCANNER_DEBUG) {
                                    e.printStackTrace()
                                }
                            }
                        }
                        "DISCNUMBER" -> {
                            try {
                                discNumber = parseInt(it)
                            } catch (e: Exception) {
                                if (SCANNER_DEBUG) {
                                    e.printStackTrace()
                                }
                            }
                        }
                        else -> {
                            extraData += "$key: $it\n"
                        }
                    }
                }
            }

            if (EXTRACTOR_DEBUG && DEBUG_SAVE_OUTPUT) {
                Log.v(EXTRACTOR_TAG,"Full output for: ${file.absolutePath} \n $allData")
            }


            /**
             * These vars need a bit more parsing
             */

            val timeNow = LocalDateTime.now()

            val title: String = if (rawTitle != null && rawTitle.isBlank() == false) { // songs with no title tag
                rawTitle.trim()
            } else {
                file.absolutePath.substringAfterLast('/').substringBeforeLast('.')
            }

            val duration: Long = (rawDuration / 1000).toLong()

            // should never be invalid if scanner even gets here fine...
            val dateModified = LocalDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneOffset.UTC)
            val albumId = if (albumName != null) AlbumEntity.generateAlbumId() else null

            /**
             * Parse the more complicated structures
             */

            // parse album
            val albumEntity = if (albumName != null && albumId != null) AlbumEntity(
                id = albumId,
                title = albumName,
                thumbnailUrl = file.absolutePath,
                songCount = 1,
                duration = duration.toInt(),
                isLocal = true
            ) else null


            // deduplicate
            artistList = artistList.filterNot { it.name == "" }.distinctBy { it.name.lowercase() } as ArrayList<ArtistEntity>
            genresList = genresList.filterNot { it.title == "" }.distinctBy { it.title.lowercase() } as ArrayList<GenreEntity>

            return SongTempData(
                Song(
                    song = SongEntity(
                        id = songId,
                        title = title,
                        duration = duration.toInt(), // we use seconds for duration
                        thumbnailUrl = file.absolutePath,
                        trackNumber = trackNumber,
                        discNumber = discNumber,
                        albumId = albumId,
                        albumName = albumName,
                        year = year,
                        date = date,
                        dateModified = dateModified,
                        isLocal = true,
                        inLibrary = timeNow,
                        localPath = file.absolutePath
                    ),
                    artists = artistList,
                    // album not working
                    album = albumEntity,
                    genre = genresList
                ),
                FormatEntity(
                    id = songId,
                    itag = -1,
                    mimeType = "audio/${file.extension}",
                    codecs = codec,
                    bitrate = bitrate,
                    sampleRate = sampleRate,
                    contentLength = duration,
                    extraComment = if (!extraData.isBlank()) extraData else null,
                )
            )
        }
    }
}