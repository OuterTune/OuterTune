/*
 * Copyright (C) 2025 O﻿ute﻿rTu﻿ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.utils.scanners

import android.os.ParcelFileDescriptor
import com.dd3boh.outertune.db.entities.AlbumEntity
import com.dd3boh.outertune.db.entities.ArtistEntity
import com.dd3boh.outertune.db.entities.FormatEntity
import com.dd3boh.outertune.db.entities.GenreEntity
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.db.entities.SongEntity
import com.dd3boh.outertune.models.SongTempData
import com.dd3boh.outertune.ui.utils.ARTIST_SEPARATORS
import com.dd3boh.outertune.ui.utils.DEBUG_SAVE_OUTPUT
import com.dd3boh.outertune.ui.utils.EXTRACTOR_DEBUG
import com.dd3boh.outertune.ui.utils.EXTRACTOR_TAG
import com.dd3boh.outertune.ui.utils.SCANNER_DEBUG
import com.kyant.taglib.TagLib
import timber.log.Timber
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
     * @param path Full file path
     */
    override fun getAllMetadataFromPath(path: String): SongTempData {
        throw NotImplementedError("TagLib extractor does not support direct paths. Please provide a file. This assumes the file exists.")
    }

    /**
     * Given a path to a file, extract necessary metadata.
     *
     * @param file Full file path
     */
    override fun getAllMetadataFromFile(file: File): SongTempData {
        if (EXTRACTOR_DEBUG)
            Timber.tag(EXTRACTOR_TAG).d("Starting Full Extractor session on: ${file.path}")

        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            val songId = SongEntity.generateSongId()
            var rawTitle: String? = null
            var albumName: String? = null
            var year: Int? = null
            var date: LocalDateTime? = null
            var codec: String? = null
            var type: String? = null
            var bitrate: Int
            var sampleRate: Int
            var channels: Int
            var rawDuration: Int
            var replayGain: Double? = null

            var allData = "" // for debugging

            var artistList = ArrayList<ArtistEntity>()
            var genresList = ArrayList<GenreEntity>()


            // Read audio properties
            val audioProperties = TagLib.getAudioProperties(fd.dup().detachFd())!!
            rawDuration = audioProperties.length
            channels = audioProperties.channels
            sampleRate = audioProperties.sampleRate
            bitrate = audioProperties.bitrate * 1000


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

                        "ALBUM", "album" -> albumName == it
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
                                    println("Could not parse date, trying to parse year...")
                                    e.printStackTrace()
                                }
                                try {
                                    if (year == null) {
                                        year = date?.year ?: parseInt(it.trim())
                                    }
                                } catch (e: Exception) {
                                    if (SCANNER_DEBUG) {
                                        println("Could not parse year")
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }

            if (EXTRACTOR_DEBUG && DEBUG_SAVE_OUTPUT) {
                Timber.tag(EXTRACTOR_TAG).d("Full output for: ${file.path} \n $allData")
            }


            /**
             * These vars need a bit more parsing
             */

            val title: String = if (rawTitle != null && rawTitle.isBlank() == false) { // songs with no title tag
                rawTitle.trim()
            } else {
                file.path.substringAfterLast('/').substringBeforeLast('.')
            }

            val duration: Long = (rawDuration / 1000).toLong()


            // should never be invalid if scanner even gets here fine...
            val dateModified = LocalDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneOffset.UTC)
            val albumId = if (albumName != null) AlbumEntity.generateAlbumId() else null
            val mime = if (type != null && codec != null) {
                "${type?.trim()}/${codec?.trim()}"
            } else {
                "Unknown"
            }

            /**
             * Parse the more complicated structures
             */

            // parse album
            val albumEntity = if (albumName != null && albumId != null) AlbumEntity(
                id = albumId,
                title = albumName,
                songCount = 1,
                duration = duration.toInt()
            ) else null


            // deduplicate
            artistList = artistList.filterNot { it.name == "" }.distinctBy { it.name } as ArrayList<ArtistEntity>
            genresList = genresList.filterNot { it.title == "" }.distinctBy { it.title } as ArrayList<GenreEntity>

            return SongTempData(
                Song(
                    song = SongEntity(
                        id = songId,
                        title = title,
                        duration = duration.toInt(), // we use seconds for duration
                        thumbnailUrl = file.path,
                        albumId = albumId,
                        albumName = albumName,
                        year = year,
                        date = date,
                        dateModified = dateModified,
                        isLocal = true,
                        inLibrary = LocalDateTime.now(),
                        localPath = file.path
                    ),
                    artists = artistList,
                    // album not working
                    album = albumEntity,
                    genre = genresList
                ),
                FormatEntity(
                    id = songId,
                    itag = -1,
                    mimeType = "Not implemented", // mime,
                    codecs = "Not implemented", //codec?.trim() ?: "Unknown",
                    bitrate = bitrate,
                    sampleRate = sampleRate,
                    contentLength = duration.toLong(),
                    loudnessDb = replayGain,
                    playbackTrackingUrl = null
                )
            )
        }
    }
}