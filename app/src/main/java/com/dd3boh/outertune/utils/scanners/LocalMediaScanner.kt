/*
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.utils.scanners

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMapNotNull
import androidx.datastore.preferences.core.edit
import androidx.documentfile.provider.DocumentFile
import com.dd3boh.outertune.constants.AutomaticScannerKey
import com.dd3boh.outertune.constants.ENABLE_FFMETADATAEX
import com.dd3boh.outertune.constants.SCANNER_DEBUG
import com.dd3boh.outertune.constants.SYNC_SCANNER
import com.dd3boh.outertune.constants.ScannerImpl
import com.dd3boh.outertune.constants.ScannerImplKey
import com.dd3boh.outertune.constants.ScannerM3uMatchCriteria
import com.dd3boh.outertune.constants.ScannerMatchCriteria
import com.dd3boh.outertune.constants.scannerWhitelistExts
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.AlbumEntity
import com.dd3boh.outertune.db.entities.Artist
import com.dd3boh.outertune.db.entities.ArtistEntity
import com.dd3boh.outertune.db.entities.FormatEntity
import com.dd3boh.outertune.db.entities.GenreEntity
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.db.entities.SongAlbumMap
import com.dd3boh.outertune.db.entities.SongArtistMap
import com.dd3boh.outertune.db.entities.SongEntity
import com.dd3boh.outertune.db.entities.SongGenreMap
import com.dd3boh.outertune.models.CulmSongs
import com.dd3boh.outertune.models.DirectoryTree
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.models.SongTempData
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.ui.utils.ARTIST_SEPARATORS
import com.dd3boh.outertune.utils.closestAlbumMatch
import com.dd3boh.outertune.utils.closestMatch
import com.dd3boh.outertune.utils.dataStore
import com.dd3boh.outertune.utils.lmScannerCoroutine
import com.dd3boh.outertune.utils.reportException
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.ArtistItem
import com.zionhuang.innertube.models.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

class LocalMediaScanner(val context: Context, val scannerImpl: ScannerImpl) {
    private val TAG = LocalMediaScanner::class.simpleName.toString()
    private var advancedScannerImpl: MetadataScanner = when (scannerImpl) {
        ScannerImpl.TAGLIB -> TagLibScanner()
        ScannerImpl.FFMPEG_EXT -> if (ENABLE_FFMETADATAEX) FFmpegScanner() else TagLibScanner()
        ScannerImpl.MEDIASTORE -> MediaStoreExtractor() // unused
    }

    init {
        Log.i(
            TAG,
            "Creating scanner instance with scannerImpl:  ${advancedScannerImpl.javaClass.name}, requested: $scannerImpl"
        )
    }

    suspend fun advancedScan(
        uri: Uri,
    ): SongTempData {
        val file = fileFromUri(context, uri) ?: throw IOException("Could not access file")
        return advancedScan(file)
    }

    /**
     * Compiles a song with all it's necessary metadata. Unlike MediaStore,
     * this also supports multiple artists, multiple genres (TBD), and a few extra details (TBD).
     */
    suspend fun advancedScan(
        file: File,
    ): SongTempData {
        val path = file.absolutePath
        try {
            if (!file.exists()) throw IOException("File not found")

            // decide which scanner to use
            val ffmpegData =
                if (advancedScannerImpl is TagLibScanner || (ENABLE_FFMETADATAEX && advancedScannerImpl is FFmpegScanner)) {
                    advancedScannerImpl.getAllMetadataFromFile(file)
                } else {
                    throw RuntimeException("Unsupported extractor")
                }

            return ffmpegData
        } catch (e: Exception) {
            when (e) {
                is IOException, is IllegalArgumentException, is IllegalStateException -> {
                    if (SCANNER_DEBUG) {
                        e.printStackTrace()
                    }
                    throw InvalidAudioFileException("Failed to access file or not in a playable format: ${e.message} for: $path")
                }

                else -> {
                    if (SCANNER_DEBUG) {
                        Log.w(TAG, "ERROR READING METADATA: ${e.message} for: $path")
                        e.printStackTrace()
                    }

                    // we still want the song to be playable even if metadata extractor fails
                    return SongTempData(
                        Song(
                            SongEntity(
                                SongEntity.generateSongId(),
                                path.substringAfterLast('/'),
                                thumbnailUrl = null,
                                isLocal = true,
                                inLibrary = LocalDateTime.now(),
                                localPath = path
                            ),
                            artists = ArrayList()
                        ),
                        null
                    )
                }
            }
        }

    }


    /**
     * Scan the given scan paths for songs given a list of paths to scan for.
     * This will replace all data in the database for a given song.
     *
     * @param scanPaths List of whitelist paths to scan under. This assumes
     * the current directory is /storage/emulated/0/ a.k.a, /sdcard.
     * For example, to scan under Music and Documents/songs --> ("Music", Documents/songs)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun scanLocal(
        scanPaths: String,
        excludedScanPaths: String,
    ): List<Uri> {
        val songs = ArrayList<Uri>()
        Log.i(TAG, "------------ SCAN: Starting Full Scanner ------------")
        scannerState.value = 1
        scannerProgressProbe.value = 0
        scannerProgressTotal.value = 0
        scannerProgressCurrent.value = -1

        val scanPaths = uriListFromString(scanPaths)
        val excludedScanPaths = uriListFromString(excludedScanPaths)

        getScanFiles(scanPaths, excludedScanPaths, context).forEach { uri ->
            if (SCANNER_DEBUG)
                Log.v(TAG, "PATH: $uri")

            songs.add(uri)
        }

        scannerState.value = 0
        Log.i(TAG, "------------ SCAN: Finished Full Scanner ------------")
        return songs.toList()
    }

    /**
     * Update the Database with local files
     *
     * @param database
     * @param newSongs
     * @param matchStrength How lax should the scanner be
     * @param strictFileNames Whether to consider file names
     * @param refreshExisting Setting this this to true will updated existing songs
     * with new information, else existing song's data will not be touched, regardless
     * whether it was actually changed on disk
     *
     * Inserts a song if not found
     * Updates a song information depending on if refreshExisting value
     */
    suspend fun syncDB(
        database: MusicDatabase,
        newSongs: java.util.ArrayList<SongTempData>,
        matchStrength: ScannerMatchCriteria,
        strictFileNames: Boolean,
        strictFilePaths: Boolean,
        refreshExisting: Boolean = false,
        noDisable: Boolean = false
    ) {
        if (scannerState.value > 0) {
            Log.i(TAG, "------------ SYNC: Scanner in use. Aborting Local Library Sync ------------")
            return
        }
        Log.i(TAG, "------------ SYNC: Starting Local Library Sync ------------")
        scannerState.value = 3
//        scannerProgressProbe.value = 0 // using separate variable instead
        // deduplicate
        val finalSongs = ArrayList<SongTempData>()
        if (strictFilePaths) {
            finalSongs.addAll(newSongs)
        } else {
            newSongs.forEach { song ->
                if (finalSongs.none { s -> compareSong(song.song, s.song, matchStrength, strictFileNames) }) {
                    finalSongs.add(song)
                }
            }
        }
        Log.d(TAG, "Entries to process: ${newSongs.size}. After dedup: ${finalSongs.size}")
        scannerProgressTotal.value = finalSongs.size
        scannerProgressCurrent.value = 0
        scannerProgressProbe.value = 0
        val mod = if (newSongs.size < 200) {
            30
        } else if (newSongs.size < 800) {
            70
        } else {
            140
        }

        val allLocalSongs = database.allLocalDbSongs().first()
        // sync
        var runs = 0
        finalSongs.forEach { song ->
            runs++
            if (SCANNER_DEBUG && runs % mod == 0) {
                Log.d(TAG, "------------ SYNC: Local Library Sync: $runs/${finalSongs.size} processed ------------")
            }
            if (runs % mod == 0) {
                scannerProgressCurrent.value += mod
            }

            if (scannerRequestCancel) {
                if (SCANNER_DEBUG)
                    Log.i(TAG, "WARNING: Requested to cancel Local Library Sync. Aborting.")
                throw ScannerAbortException("Scanner canceled during Local Library Sync")
            }

            // check if this song is known to the library
            val songMatch = allLocalSongs.filter {
                return@filter it.song.title.lowercase().contains(song.song.title) &&
                        compareSong(it, song.song, matchStrength, strictFileNames, strictFilePaths)
            }

            if (SCANNER_DEBUG) {
                Log.v(TAG, "Found songs that match: ${songMatch.size}")
                if (songMatch.isNotEmpty()) {
                    Log.v(TAG, "FIRST Found songs ${songMatch.first().song.title}")
                }
            }


            if (songMatch.isNotEmpty()) { // known song, update the song info in the database
                if (SCANNER_DEBUG)
                    Log.v(TAG, "Found in database, updating song: ${song.song.title} rescan = $refreshExisting")

                val oldSong = songMatch.first().song
                val songToUpdate = song.song.song.copy(id = oldSong.id, localPath = song.song.song.localPath)

                // don't run if we will update these values in rescan anyways
                // always ensure inLibrary and local path values are valid
                if (!refreshExisting && (oldSong.inLibrary == null || oldSong.localPath == null)) {
                    database.transaction {
                        update(songToUpdate)

                        // update format
                        if (song.format != null) {
                            upsert(song.format.copy(id = songToUpdate.id))
                        }
                    }
                }


                if (!refreshExisting) {
                    if (oldSong.localPath != songToUpdate.localPath && oldSong.inLibrary != null) {
                        // always update the path and library path they change
                        database.updateLocalSongPath(songToUpdate.id, songToUpdate.inLibrary, songToUpdate.localPath)
                    }
                    return@forEach
                }
                // below is only for when rescan is enabled

                val artistsToDo = ArrayList<Pair<ArtistEntity?, ArtistEntity>>()
                val genreToDo = ArrayList<Pair<GenreEntity?, GenreEntity>>()

                // update artists and genre
                database.query {
                    song.song.artists.forEachIndexed { index, it ->
                        val dbQuery = runBlocking(Dispatchers.IO) {
                            artistLikeName(it.name).firstOrNull()?.sortedBy { item -> item.name.length }
                        }
                        val dbArtist = dbQuery?.let { item -> closestMatch(it.name, item) }
                        artistsToDo.add(Pair(dbArtist, it))
                    }
                    song.song.genre?.forEachIndexed { index, it ->
                        val dbGenre = runBlocking(Dispatchers.IO) {
                            genreByAproxName(it.title).firstOrNull()?.firstOrNull()
                        }
                        genreToDo.add(Pair(dbGenre, it))
                    }
                }

                database.transaction {
                    update(songToUpdate)
                    if (song.format != null) {
                        upsert(song.format.copy(id = songToUpdate.id))
                    }

                    // destroy existing artist links
                    unlinkSongArtists(songToUpdate.id)
                    unlinkSongAlbums(songToUpdate.id)

                    artistsToDo.forEachIndexed { index, item ->
                        if (item.first == null) {
                            // artist does not exist in db, add it then link it
                            insert(item.second)
                            insert(SongArtistMap(songToUpdate.id, item.second.id, index))
                        } else {
                            // artist does  exist in db, link to it
                            insert(SongArtistMap(songToUpdate.id, item.first!!.id, index))
                        }
                    }

                    genreToDo.forEachIndexed { index, item ->
                        if (item.first == null) {
                            // genre does not exist in db, add it then link it
                            insert(item.second)
                            insert(SongGenreMap(songToUpdate.id, item.second.id, index))
                        } else {
                            // genre does exist in db, link to it
                            insert(SongGenreMap(songToUpdate.id, item.first!!.id, index))
                        }
                    }
                }

                // locks up the database till the end of time when inside the main transaction. Your guess why is as good as mine.
                song.song.album?.let {
                    val dbQuery =
                        database.localAlbumsByName(it.title).firstOrNull()?.sortedBy { item -> item.title.length }
                    val dbAlbum = dbQuery?.let { item -> closestAlbumMatch(it.title, item) }

                    database.transaction {
                        if (dbAlbum == null) {
                            // album does not exist in db, add it then link it
                            insert(it)
                            insert(SongAlbumMap(songToUpdate.id, it.id, 0))
                        } else {
                            // album does  exist in db, link to it
                            if ((dbAlbum.thumbnailUrl == null && it.thumbnailUrl != null) ||
                                (dbAlbum.thumbnailUrl != null && it.thumbnailUrl != null &&
                                        dbAlbum.thumbnailUrl != it.thumbnailUrl)
                            ) {
                                // update the thumbnailUrl if both a new one has been provided and the existing one is null
                                upsert(dbAlbum.copy(thumbnailUrl = it.thumbnailUrl))
                            }
                            insert(SongAlbumMap(songToUpdate.id, dbAlbum.id, dbAlbum.songCount))
                        }
                    }
                }

            } else { // new song
                if (SCANNER_DEBUG)
                    Log.v(TAG, "NOT found in database, adding song: ${song.song.title}")

                database.transaction {
                    insert(song.song.toMediaMetadata())
                    song.format?.let {
                        upsert(it.copy(id = song.song.id))
                    }
                }
            }
        }

        scannerProgressCurrent.value = scannerProgressTotal.value
        // do not delete songs from database automatically, we just disable them
        if (!noDisable) {
            finalize(database)
            disableSongs(finalSongs.map { it.song }, database)
        }
        scannerState.value = 0
        Log.i(TAG, "------------ SYNC: Finished Local Library Sync ------------")
    }

    /**
     * A faster scanner implementation that adds new songs to the database,
     * and does not touch older songs entries (apart from removing
     * inacessable songs from libaray).
     *
     * No remote artist lookup is done
     *
     * WARNING: cachedDirectoryTree is not refreshed and may lead to inconsistencies.
     * It is highly recommend to rebuild the tree after scanner operation
     *
     * @param newSongs List of songs. This is expecting a barebones DirectoryTree
     * (only paths are necessary), thus you may use the output of refreshLocal().toList()
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun quickSync(
        database: MusicDatabase,
        newSongs: List<Uri>,
        matchCriteria: ScannerMatchCriteria,
        strictFileNames: Boolean,
        strictFilePaths: Boolean,
    ) {
        Log.i(TAG, "------------ SYNC: Starting Quick (additive delta) Library Sync ------------")
        Log.d(TAG, "Entries to process: ${newSongs.size}")
        scannerState.value = 2
        scannerProgressTotal.value = newSongs.size
        scannerProgressCurrent.value = 0
        scannerProgressProbe.value = 0

        Log.d(TAG, "Scanning for files...")
        // get list of all songs in db, then get songs unknown to the database
        val allSongs = database.allLocalSongs().first().fastMapNotNull { it.song.localPath }.toSet()
        val converted = newSongs.fastMapNotNull { fileFromUri(context, it)?.absolutePath }
        val delta = converted.minus(allSongs)
        Log.d(TAG, "Songs found: ${delta.size}")
        val mod = if (newSongs.size < 20) {
            2
        } else if (newSongs.size < 50) {
            8
        } else {
            20
        }

        val finalSongs = ArrayList<SongTempData>()
        val scannerJobs = ArrayList<Deferred<SongTempData?>>()

        // Get song basic metadata
        delta.forEach { s ->
            if (scannerRequestCancel) {
                Log.i(TAG, "WARNING: Requested to cancel. Aborting.")
                throw ScannerAbortException("Scanner canceled during Quick (additive delta) Library Sync")
            }

            if (SCANNER_DEBUG) {
                Log.v(TAG, "PATH: $s")
            }

            if (!SYNC_SCANNER) {
                // use async scanner
                runBlocking {
                    scannerJobs.add(
                        async(lmScannerCoroutine) {
                            var ret: SongTempData?
                            if (scannerRequestCancel) {
                                Log.i(TAG, "WARNING: Canceling advanced scanner job.")
                                throw ScannerAbortException("")
                            }
                            try {
                                ret = advancedScan(File(s))
                                scannerProgressProbe.value++
                                if (SCANNER_DEBUG && scannerProgressProbe.value % mod == 0) {
                                    Log.d(
                                        TAG,
                                        "------------ SCAN: Full Scanner: ${scannerProgressProbe.value} discovered ------------"
                                    )
                                }
                                if (scannerProgressProbe.value % mod == 0) {
                                    scannerProgressCurrent.value = scannerProgressProbe.value
                                }
                            } catch (e: InvalidAudioFileException) {
                                ret = null
                            }
                            ret
                        }
                    )
                }
            } else {
                if (scannerRequestCancel) {
                    Log.i(TAG, "WARNING: Requested to cancel. Aborting.")
                    throw ScannerAbortException("Scanner canceled during Quick (additive delta) Library Sync")
                }
                // force synchronous scanning of songs. Do not catch errors
                finalSongs.add(advancedScan(File(s)))
                scannerProgressProbe.value++
                if (SCANNER_DEBUG && scannerProgressProbe.value % 5 == 0) {
                    Log.d(
                        TAG,
                        "------------ SCAN: Full Scanner: ${scannerProgressProbe.value} discovered ------------"
                    )
                }
                if (scannerProgressProbe.value % 5 == 0) {
                    scannerProgressCurrent.value = scannerProgressProbe.value
                }
            }
        }

        if (!SYNC_SCANNER) {
            // use async scanner
            scannerJobs.awaitAll()
        }

        // add to finished list
        scannerJobs.forEach {
            val song = it.getCompleted()
            song?.song?.let { finalSongs.add(song) }
        }

        if (finalSongs.isNotEmpty()) {
            scannerState.value = 0
            syncDB(database, finalSongs, matchCriteria, strictFileNames, strictFilePaths, noDisable = true)
            scannerState.value = 2
        } else {
            Log.i(TAG, "Not syncing, no valid songs found!")
        }

        scannerProgressCurrent.value = scannerProgressProbe.value
        // we handle disabling songs here instead
        scannerState.value = 3
        finalize(database)
        disableSongsByPath(converted, database)

        scannerState.value = 0
        Log.i(TAG, "------------ SYNC: Finished Quick (additive delta) Library Sync ------------")
    }


    /**
     * Run a full scan and ful database update. This will update all song data in the
     * database of all songs, and also disable inacessable songs
     *
     * No remote artist lookup is done
     *
     * WARNING: cachedDirectoryTree is not refreshed and may lead to inconsistencies.
     * It is highly recommend to rebuild the tree after scanner operation
     *
     * @param newSongs List of songs. This is expecting a barebones DirectoryTree
     * (only paths are necessary), thus you may use the output of refreshLocal().toList()
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun fullSync(
        database: MusicDatabase,
        newSongs: List<Uri>,
        matchCriteria: ScannerMatchCriteria,
        strictFileNames: Boolean,
        strictFilePaths: Boolean,
    ) {
        Log.i(TAG, "------------ SYNC: Starting FULL Library Sync ------------")
        Log.d(TAG, "Entries to process: ${newSongs.size}")
        scannerState.value = 2
        scannerProgressTotal.value = newSongs.size
        scannerProgressCurrent.value = 0
        scannerProgressProbe.value = 0
        val mod = if (newSongs.size < 20) {
            2
        } else if (newSongs.size < 50) {
            8
        } else {
            20
        }

        val finalSongs = ArrayList<SongTempData>()
        val scannerJobs = ArrayList<Deferred<SongTempData?>>()

        // Get song basic metadata
        newSongs.forEach { uri ->
            if (scannerRequestCancel) {
                Log.i(TAG, "WARNING: Requested to cancel. Aborting.")
                throw ScannerAbortException("Scanner canceled during FULL Library Sync")
            }

            if (SCANNER_DEBUG)
                Log.d(TAG, "PATH: $uri")

            if (!SYNC_SCANNER) {
                // use async scanner
                runBlocking {
                    scannerJobs.add(
                        async(lmScannerCoroutine) {
                            if (scannerRequestCancel) {
                                Log.i(TAG, "WARNING: Canceling advanced scanner job.")
                                throw ScannerAbortException("")
                            }
                            try {
                                val ret = advancedScan(uri)
                                scannerProgressProbe.value++
                                if (SCANNER_DEBUG && scannerProgressProbe.value % mod == 0) {
                                    Log.d(
                                        TAG,
                                        "------------ SCAN: Full Scanner: ${scannerProgressProbe.value} discovered ------------"
                                    )
                                }
                                if (scannerProgressProbe.value % mod == 0) {
                                    scannerProgressCurrent.value = scannerProgressProbe.value
                                }
                                ret
                            } catch (e: InvalidAudioFileException) {
                                null
                            }
                        }
                    )
                }
            } else {
                // force synchronous scanning of songs. Do not catch errors
                finalSongs.add(advancedScan(uri))
            }
        }

        if (!SYNC_SCANNER) {
            // use async scanner
            scannerJobs.awaitAll()
        }

        // add to finished list
        scannerJobs.forEach {
            val song = it.getCompleted()
            song?.song?.let { finalSongs.add(song) }
        }

        scannerProgressCurrent.value = scannerProgressProbe.value
        if (finalSongs.isNotEmpty()) {
            /**
             * TODO: Delete all local format entity before scan
             */
            scannerState.value = 0
            syncDB(database, finalSongs, matchCriteria, strictFileNames, strictFilePaths, refreshExisting = true)
            scannerState.value = 2
        } else {
            Log.i(TAG, "Not syncing, no valid songs found!")
        }

        scannerState.value = 0
        Log.i(TAG, "------------ SYNC: Finished Quick (additive delta) Library Sync ------------")
    }


    /**
     * Run a full scan and ful database update. This will update all song data in the
     * database of all songs, and also disable inacessable songs
     *
     * No remote artist lookup is done
     *
     * WARNING: cachedDirectoryTree is not refreshed and may lead to inconsistencies.
     * It is highly recommend to rebuild the tree after scanner operation
     *
     * @param newSongs List of songs. This is expecting a barebones DirectoryTree
     * (only paths are necessary), thus you may use the output of refreshLocal().toList()
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun fullMediaStoreSync(
        database: MusicDatabase,
        scanPaths: List<Uri>,
        excludedScanPaths: List<Uri>,
        matchCriteria: ScannerMatchCriteria,
        strictFileNames: Boolean,
        strictFilePaths: Boolean,
        refreshExisting: Boolean,
    ) {
        Log.i(
            TAG,
            "------------ SYNC: Starting MediaStore FULL Library Sync, refreshExisting = $refreshExisting ------------"
        )
        scannerState.value = 2
        scannerProgressCurrent.value = 0
        scannerProgressProbe.value = 0

        val projection = arrayListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DURATION, // 29
            MediaStore.Audio.Media.ARTIST, // 30
            MediaStore.Audio.Media.ALBUM, // 30
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.YEAR, // 30
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.VOLUME_NAME, // 29
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.BITRATE, // 30
            MediaStore.Audio.Media.SIZE,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Audio.Media.GENRE) // 30
                add(MediaStore.Audio.Media.CD_TRACK_NUMBER) // 30
//                add(MediaStore.Audio.Media.WRITER)
                add(MediaStore.Audio.Media.DISC_NUMBER) // 30
            }
        }

        val finalSongs = ArrayList<SongTempData>()


        val contentResolver: ContentResolver = context.contentResolver
        val selectionBuilder = StringBuilder("${MediaStore.Audio.Media.IS_MUSIC} != 0")
        val selectionArgs = mutableListOf<String>()
        scanPaths.forEachIndexed { index, path ->
            val convertedPath = absoluteFilePathFromUri(context, path)
            if (index == 0) {
                selectionBuilder.append(" AND (")
            } else {
                selectionBuilder.append(" OR ")
            }
            selectionBuilder.append("${MediaStore.Audio.Media.DATA} LIKE ?")
            selectionArgs.add("$convertedPath%")
        }
        selectionBuilder.append(")")
        val selection = selectionBuilder.toString()

        // Query for audio files
        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection.toTypedArray(),
            selection,
            selectionArgs.toTypedArray(),
            null
        )
        cursor?.use { cursor ->
            // Columns indices
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val storageVolumeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.VOLUME_NAME)

            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val bitrateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.BITRATE)

            var genreColumn: Int? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                genreColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.GENRE)
            }
            val trackNumberColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.CD_TRACK_NUMBER)
//            val writerColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.WRITER)
            val discNumberColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISC_NUMBER)

            while (cursor.moveToNext()) {
                val id = SongEntity.generateSongId()
                val name = cursor.getString(nameColumn) // file name
                var title = cursor.getString(titleColumn) // song title
                val duration = cursor.getInt(durationColumn) / 1000
                val artist = cursor.getString(artistColumn)
                val album = cursor.getString(albumColumn)
                val rawYear = cursor.getString(yearColumn)
                val rawDateModified = cursor.getString(dateModifiedColumn)
                val path = cursor.getString(pathColumn)
                val storageVol = cursor.getString(storageVolumeColumn)

                if (excludedScanPaths.any { path.startsWith(it.path ?: "") }) continue

                // extra stream info
                val bitrate = cursor.getInt(bitrateColumn)
                val mime = cursor.getString(mimeColumn)


                var genre: String? = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    genre = cursor.getString(genreColumn!!)
                }
                val trackNumber = cursor.getInt(trackNumberColumn)
//                val writer = cursor.getString(writerColumn)
                val discNumber = cursor.getInt(discNumberColumn)

                if (SCANNER_DEBUG)
                    Log.d(TAG, "ID: $id, Name: $name, ARTIST: $artist, PATH: $storageVol --> $path")

                if (title.isBlank()) { // songs with no title tag
                    title = name.substringBeforeLast('.')
                }

                val year = rawYear?.toIntOrNull()
                var dateModified: LocalDateTime? = null

                try {
                    rawDateModified?.toLongOrNull()?.let {
                        dateModified = LocalDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneOffset.UTC)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val artistList = ArrayList<ArtistEntity>()
                val genresList = ArrayList<GenreEntity>()


                artist.split(ARTIST_SEPARATORS).forEach { artistVal ->
                    artistList.add(ArtistEntity(ArtistEntity.generateArtistId(), artistVal, isLocal = true))
                }

                genre?.split(ARTIST_SEPARATORS)?.forEach { genreVal ->
                    genresList.add(GenreEntity(GenreEntity.generateGenreId(), genreVal, isLocal = true))
                }
                val albumID = AlbumEntity.generateAlbumId()
                val albumEntity = if (album != null) AlbumEntity(
                    id = albumID,
                    title = album,
                    thumbnailUrl = path,
                    songCount = 1,
                    duration = duration,
                    isLocal = true
                ) else null

                finalSongs.add(
                    SongTempData(
                        Song(
                            song = SongEntity(
                                id = id,
                                title = title,
                                duration = duration,
                                thumbnailUrl = path,
                                inLibrary = LocalDateTime.now(),
                                isLocal = true,
                                localPath = path,
                                trackNumber = trackNumber,
                                discNumber = discNumber,
                                albumId = albumID, // this is replaced later anwyays
                                albumName = album,
                                year = year,
                                dateModified = dateModified,
                            ),
                            artists = artistList,
                            // album not working
                            album = albumEntity,
                            genre = genresList
                        ),
                        FormatEntity(
                            id = id,
                            itag = -1,
                            mimeType = mime,
                            codecs = mime.substringAfter('/'),
                            bitrate = bitrate,
                            sampleRate = -1,
                            contentLength = duration.toLong(),
                            loudnessDb = null,
                        )
                    )
                )
            }
        }

        scannerProgressCurrent.value = scannerProgressProbe.value
        if (finalSongs.isNotEmpty()) {
            /**
             * TODO: Delete all local format entity before scan
             */
            scannerState.value = 0
            syncDB(
                database, finalSongs, matchCriteria, strictFileNames, strictFilePaths,
                refreshExisting = refreshExisting, noDisable = true
            )

        } else {
            Log.i(TAG, "Not syncing, no valid songs found!")
        }
        // we handle disabling songs here instead
        scannerState.value = 3
        finalize(database)
        disableSongsByPath(finalSongs.mapNotNull { it.song.song.localPath }, database)
        scannerState.value = 0


        scannerState.value = 0
        Log.i(TAG, "------------ SYNC: Finished MediaStore FULL Library Sync ------------")
    }


    /**
     * Converts all local artists to remote artists if possible
     */
    suspend fun localToRemoteArtist(database: MusicDatabase) {
        if (scannerState.value > 0) {
            Log.i(TAG, "------------ SYNC: Scanner in use. Aborting youtubeArtistLookup job ------------")
            return
        }

        Log.i(TAG, "------------ SYNC: Starting youtubeArtistLookup job ------------")
        val prevScannerState = scannerState.value
        scannerState.value = 5

        val allLocal = database.allLocalArtists().first()
        scannerProgressTotal.value = allLocal.size
        scannerProgressCurrent.value = 0
        scannerProgressProbe.value = 0
        val mod = if (allLocal.size < 20) {
            2
        } else if (allLocal.size < 50) {
            8
        } else {
            20
        }

        allLocal.forEach { element ->
            val artistVal = element.name.trim()

            // check if this artist exists in DB already
            val databaseArtistMatch = database.fuzzySearchArtists(artistVal).first().filter { artist ->
                // only look for remote artists here
                return@filter artist.name == artistVal && !artist.isLocal
            }

            if (SCANNER_DEBUG)
                Log.v(TAG, "ARTIST FOUND IN DB??? Results size: ${databaseArtistMatch.size}")

            // cancel here since this is where the real heavy action is
            if (scannerRequestCancel) {
                Log.i(TAG, "WARNING: Requested to cancel youtubeArtistLookup job. Aborting.")
                throw ScannerAbortException("Scanner canceled during youtubeArtistLookup job")
            }

            // resolve artist from YTM if not found in DB
            if (databaseArtistMatch.isEmpty()) {
                try {
                    youtubeArtistLookup(artistVal)?.let {
                        // add new artist, switch all old references, then delete old one
                        database.insert(it)
                        try {
                            swapArtists(element, it, database)
                        } catch (e: Exception) {
                            reportException(e)
                        }
                    }
                } catch (e: Exception) {
                    // don't touch anything if ytm fails --> keep old artist
                }
            } else {
                // swap with database artist
                try {
                    swapArtists(element, databaseArtistMatch.first(), database)
                } catch (e: Exception) {
                    reportException(e)
                }
            }

            scannerProgressProbe.value++
            if (scannerProgressProbe.value % mod == 0) {
                scannerProgressCurrent.value = scannerProgressProbe.value
            }
            if (SCANNER_DEBUG && scannerProgressProbe.value % mod == 0) {
                Log.v(
                    TAG,
                    "------------ SYNC: youtubeArtistLookup job: $ scannerProgressCurrent.value/${scannerProgressTotal.value} artists processed ------------"
                )
            }
        }

        if (scannerRequestCancel) {
            Log.i(TAG, "WARNING: Requested to cancel during localToRemoteArtist. Aborting.")
            throw ScannerAbortException("Scanner canceled during localToRemoteArtist")
        }

        scannerState.value = prevScannerState
        Log.i(TAG, "------------ SYNC: youtubeArtistLookup job ended------------")
    }

    private suspend fun disableSongsByPath(newSongs: List<String>, database: MusicDatabase) {
        Log.i(TAG, "Start finalize (disable songs) job. Number of valid songs: ${newSongs.size}")
        // get list of all local songs in db
        database.disableInvalidLocalSongs() // make sure path is existing
        val allSongs = database.allLocalSongs().first()

        // disable if not in directory anymore
        for (song in allSongs) {
            if (song.song.localPath == null) {
                continue
            }

            // new songs is all songs that are known to be valid
            // delete all songs in the DB that do not match a path
            if (newSongs.none { it == song.song.localPath }) {
                if (SCANNER_DEBUG)
                    Log.v(TAG, "Disabling song ${song.song.localPath}")
                database.transaction {
                    disableLocalSong(song.song.id)
                }
            }
        }
    }

    private suspend fun disableSongs(newSongs: List<Song>, database: MusicDatabase) {
        Log.i(TAG, "Start finalize (disable songs) job. Number of valid songs: ${newSongs.size}")

        // get list of all local songs in db
        database.disableInvalidLocalSongs() // make sure path is existing
        val allSongs = database.allLocalSongs().first()

        // disable if not in directory anymore
        for (song in allSongs) {
            if (song.song.localPath == null) {
                continue
            }

            // new songs is all songs that are known to be valid
            // delete all songs in the DB that do not match a path
            if (newSongs.none { it.song.localPath == song.song.localPath }) {
                if (SCANNER_DEBUG)
                    Log.v(TAG, "Disabling song ${song.song.localPath}")
                database.disableLocalSong(song.song.id)
            }
        }
    }

    /**
     * Remove inaccessible, and duplicate songs from the library
     */
    private suspend fun finalize(database: MusicDatabase) {
        Log.i(TAG, "Start finalize (database cleanup job)")

        // remove duplicates
        val dupes = database.duplicatedLocalSongs().first().toMutableList()
        var index = 0

        Log.d(TAG, "Start finalize (duplicate removal) job. Number of candidates: ${dupes.size}")
        while (index < dupes.size) {
            // collect all the duplicates
            val contenders = ArrayList<Pair<SongEntity, Int>>()
            val localPath = dupes[index].localPath
            while (index < dupes.size && dupes[index].localPath == localPath) {
                contenders.add(Pair(dupes[index], database.getLifetimePlayCount(dupes[index].id).first()))
                index++
            }
            // yeet the lower play count songs
            contenders.remove(contenders.maxByOrNull { it.second })
            contenders.forEach {
                if (SCANNER_DEBUG)
                    Log.v(TAG, "Deleting song ${it.first.id} (${it.first.title})")
                database.delete(it.first)
            }
        }

        // remove duplicated local artists
        val dbArtists: MutableList<Artist> = database.localArtistsByName().first().toMutableList()
        while (dbArtists.isNotEmpty()) {
            // gather same artists (precondition: artists are ordered by name
            val tmp = ArrayList<Artist>()
            val oldestArtist: Artist = dbArtists.removeAt(0)
            tmp.add(oldestArtist)
            while (dbArtists.isNotEmpty() && dbArtists.first().title == tmp.first().title) {
                tmp.add(dbArtists.removeAt(0))
            }

            if (tmp.size > 1) {
                // merge all duplicate artists into the oldest one
                tmp.removeAt(0)
                tmp.sortBy { it.artist.bookmarkedAt }
                tmp.forEach { swapArtists(it.artist, oldestArtist.artist, database) }
            }
        }

        // remove duplicated local albums
        val dbAlbums: MutableList<AlbumEntity> = database.allLocalAlbumsByName().first().toMutableList()
        while (dbAlbums.isNotEmpty()) {
            // gather same artists (precondition: artists are ordered by name
            val tmp = ArrayList<AlbumEntity>()
            val oldestAlbum: AlbumEntity = dbAlbums.removeAt(0)
            tmp.add(oldestAlbum)
            while (dbAlbums.isNotEmpty() && dbAlbums.first().title == tmp.first().title) {
                tmp.add(dbAlbums.removeAt(0))
            }

            if (tmp.size > 1) {
                // merge all duplicate artists into the oldest one
                tmp.removeAt(0)
                tmp.sortBy { it.bookmarkedAt }
                tmp.forEach { swapAlbums(it, oldestAlbum, database) }
            }
        }
        Log.d(TAG, "Finished finalize (duplicate removal) job")
    }


    companion object {
        // do not put any thing that should adhere to the scanner lock in here
        const val TAG = "LocalMediaScanner"

        private var ownerId = -1
        private var localScanner: LocalMediaScanner? = null


        var scannerRequestCancel = false

        /**
         * -1: Inactive
         * 0: Idle
         * 1: Discovering (Crawling files)
         * 2: Scanning (Extract metadata and checking playability
         * 3: Syncing (Update database)
         * 4: Scan finished
         * 5: Ytm artist linking
         */
        var scannerState = MutableStateFlow(-1)
        var scannerProgressTotal = MutableStateFlow(-1)
        var scannerProgressCurrent = MutableStateFlow(-1)
        var scannerProgressProbe = MutableStateFlow(-1)


        /**
         * ==========================
         * Scanner management
         * ==========================
         */

        /**
         * Trust me bro, it should never be null
         */
        fun getScanner(context: Context, scannerImpl: ScannerImpl, owner: Int): LocalMediaScanner {

            if (localScanner == null) {
                // reset to taglib if ffMetadataEx disappears
                if (scannerImpl == ScannerImpl.FFMPEG_EXT && !ENABLE_FFMETADATAEX) {
                    CoroutineScope(lmScannerCoroutine).launch {
                        context.dataStore.edit { settings ->
                            settings[ScannerImplKey] = ScannerImpl.TAGLIB.toString()
                            settings[AutomaticScannerKey] = false
                            runBlocking(Dispatchers.Main) {
                                // TODO: string resource (but will anyone even notice this...)
                                Toast.makeText(context, "FFmpeg extractors are missing", Toast.LENGTH_SHORT).show()
                                Toast.makeText(
                                    context,
                                    "Auto scanner has been disabled to prevent data conflicts. You will need to enable this in local media settings again if you want automatic scanning.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
                localScanner = LocalMediaScanner(context, scannerImpl)
                scannerProgressTotal.value = 0
                scannerProgressCurrent.value = -1
                scannerProgressProbe.value = 0
            }

            ownerId = owner
            return localScanner!!
        }

        suspend fun destroyScanner(owner: Int) {
            if (owner != ownerId && ownerId != -1) {
                Log.w(TAG, "Scanner instance can only be destroyed by the owner. Aborting. Check your ownerId.")
                return
            }
            ownerId = -1
            localScanner = null
            scannerState.value = -1
            scannerRequestCancel = false
            scannerProgressTotal.value = -1
            scannerProgressCurrent.value = -1
            scannerProgressProbe.value = -1

            Log.i(TAG, "Scanner instance destroyed")
        }


        /**
         * ==========================
         * Scanner extra scan utils
         * ==========================
         */


        /**
         * Build a list of files to scan, taking in exclusions into account. Exclusions
         * will override inclusions. All subdirectories will also be affected.
         *
         * Uri.path can be assumed to be non-null
         */
        fun getScanFiles(scanPaths: List<Uri>, excludedScanPaths: List<Uri>, context: Context): List<Uri> {
            val allSongs = ArrayList<Uri>()
            val resultingPaths =
                scanPaths.filterNot { incl ->
                    excludedScanPaths.any { excl -> incl.path?.startsWith(excl.path.toString()) == true }
                }

            resultingPaths.forEach { path ->
                try {
                    val file = documentFileFromUri(context, path)
                    if (file != null) {
                        val songsHere = ArrayList<DocumentFile>()
                        scanDfRecursive(file, songsHere) {
                            // Allow: audio mime, or certain audio exts
                            // Disallow: x-mpegurl (m3u)
                            val mime = it.type ?: return@scanDfRecursive false
                            if (!mime.startsWith("audio")) {
                                if (it.name?.substringAfterLast('.') !in scannerWhitelistExts) {
                                    return@scanDfRecursive false
                                }
                            }
                            if (mime == "audio/x-mpegurl") {
                                return@scanDfRecursive false
                            }

                            return@scanDfRecursive true
                        }

                        allSongs.addAll(songsHere.fastFilter { incl ->
                            !excludedScanPaths.any {
                                incl.uri.path?.startsWith(it.path.toString()) == true
                            }
                        }.map { it.uri })
                    }
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                    throw Exception("oh well idk man this should never happen")
                }
            }

            return allSongs.fastDistinctBy { it.toString() }
        }

        fun scanDfRecursive(
            dir: DocumentFile,
            result: ArrayList<DocumentFile>,
            scanHidden: Boolean = false,
            validator: ((DocumentFile) -> Boolean)? = null
        ): DocumentFile? {
            val files = dir.listFiles()
            for (file in files) {
                if (!scanHidden && file.name?.startsWith(".") == true) continue
                if (file.isDirectory && (scanHidden || !file.listFiles().any { it.name == ".nomedia" })) {
                    // look into subdirs
                    scanDfRecursive(file, result, scanHidden, validator)
                } else {
                    // add if file matches
                    if (validator == null || validator(file)) {
                        result.add(file)
                        scannerProgressProbe.value++
                        if (scannerProgressProbe.value % 20 == 0) {
                            scannerProgressTotal.value = scannerProgressProbe.value
                        }
                    }
                }
            }
            return null
        }

        /**
         * Quickly rebuild a skeleton directory tree of local files based on the database
         *
         * Notes:
         * If files move around, that's on you to re run the scanner.
         * If the metadata changes, that's also on you to re run the scanner.
         *
         * @param scanPaths List of whitelist paths to scan under. This assumes
         * the current directory is /storage/emulated/0/ a.k.a, /sdcard.
         * For example, to scan under Music and Documents/songs --> ("Music", Documents/songs)
         * @param filter Raw file path
         */
        suspend fun refreshLocal(
            context: Context,
            database: MusicDatabase,
            filter: String
        ): DirectoryTree {
            val newDirectoryStructure = DirectoryTree(filter.trimEnd { it == '/' }, CulmSongs(0))

            // get songs from db
            var existingSongs: List<Song> = database.localSongsInDirShallow(filter).first()

            Log.i(TAG, "------------ SCAN: Starting Quick Directory Rebuild ------------")

            // Build directory tree with existing files
            existingSongs.forEach { s ->
                val path = s.song.localPath ?: return@forEach
                val filterPath =
                    (if (path.startsWith(filter)) path.substringAfter(filter) else path).trimStart { it == '/' }
                newDirectoryStructure.insert(filterPath, s)
            }

            Log.i(TAG, "------------ SCAN: Finished Quick Directory Rebuild ------------")
            return newDirectoryStructure.androidStorageWorkaround()
        }


        /**
         * ==========================
         * Scanner helpers
         * ==========================
         */


        /**
         * Check if artists are the same
         *
         *  Both null == same artists
         *  Either null == different artists
         */
        fun compareArtist(a: List<ArtistEntity>, b: List<ArtistEntity>): Boolean {
            if (a.isEmpty() && b.isEmpty()) {
                return true
            } else if (a.isEmpty() || b.isEmpty()) {
                return false
            }

            // compare entries
            if (a.size != b.size) {
                return false
            }
            val matchingArtists = a.filter { artist ->
                b.any { it.name.lowercase(Locale.getDefault()) == artist.name.lowercase(Locale.getDefault()) }
            }

            return matchingArtists.size == a.size
        }

        /**
         * Check if albums are the same
         *
         *  Both null == same albums
         *  Either null == different albums
         */
        fun compareAlbum(a: AlbumEntity?, b: AlbumEntity?): Boolean {
            if (a == null && b == null) return false

            return a?.title?.lowercase(Locale.getDefault()) == b?.title?.lowercase(Locale.getDefault())
        }

        /**
         * Check the similarity of a song
         *
         * @param a
         * @param b
         * @param matchStrength How lax should the scanner be
         */
        fun compareM3uSong(
            a: Song,
            b: Song,
            matchStrength: ScannerM3uMatchCriteria = ScannerM3uMatchCriteria.LEVEL_1,
        ): Boolean {
            val matchStrength = when (matchStrength) {
                ScannerM3uMatchCriteria.LEVEL_1 -> ScannerMatchCriteria.LEVEL_1
                ScannerM3uMatchCriteria.LEVEL_2 -> ScannerMatchCriteria.LEVEL_2
                else -> ScannerMatchCriteria.LEVEL_1
            }
            return compareSong(a, b, matchStrength)
        }

        /**
         * Check the similarity of a song
         *
         * @param a
         * @param b
         * @param matchStrength How lax should the scanner be
         * @param strictFileNames Whether to consider file names
         */
        fun compareSong(
            a: Song,
            b: Song,
            matchStrength: ScannerMatchCriteria = ScannerMatchCriteria.LEVEL_2,
            strictFileNames: Boolean = false,
            strictFilePaths: Boolean = false,
        ): Boolean {
            if (strictFilePaths) {
                return a.song.localPath == b.song.localPath
            }
            // if match file names
            if (strictFileNames &&
                (a.song.localPath?.substringAfterLast('/') !=
                        b.song.localPath?.substringAfterLast('/'))
            ) {
                return false
            }

            /**
             * Compare file paths
             *
             * I draw the "user error" line here
             */
            fun closeEnough(): Boolean {
                return a.song.localPath == b.song.localPath
            }

            // compare songs based on scanner strength
            return when (matchStrength) {
                ScannerMatchCriteria.LEVEL_1 -> a.song.title == b.song.title
                ScannerMatchCriteria.LEVEL_2 -> closeEnough() || (a.song.title == b.song.title &&
                        compareArtist(a.artists, b.artists))

                ScannerMatchCriteria.LEVEL_3 -> closeEnough() || (a.song.title == b.song.title &&
                        compareArtist(a.artists, b.artists) && compareAlbum(a.album, b.album))
            }
        }

        /**
         * Search for an artist on YouTube Music.
         */
        suspend fun youtubeSongLookup(query: String, songUrl: String?): List<MediaMetadata> {
            var ytmResult = ArrayList<MediaMetadata>()

            var exactSong: SongItem? = null
            if (songUrl != null) {
                runCatching {
                    YouTube.queue(listOf(songUrl.substringAfter("/watch?v=").substringBefore("&")))
                }.onSuccess {
                    exactSong = it.getOrNull()?.firstOrNull()
                }.onFailure {
                    reportException(it)
                }
            }

            // prefer song from url
            if (exactSong != null) {
                ytmResult.add(exactSong.toMediaMetadata())
                if (SCANNER_DEBUG)
                    Log.v(TAG, "Found exact song: ${exactSong.title} [${exactSong.id}]")
                return ytmResult
            }
            YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).onSuccess { result ->

                val foundSong = result.items.filter {
                    // TODO: might want to implement proper matching to remove outlandish results
                    it is SongItem
                }
                ytmResult.addAll(foundSong.map { (it as SongItem).toMediaMetadata() })

                if (SCANNER_DEBUG)
                    Log.v(TAG, "Remote song: ${foundSong.firstOrNull()?.title} [${foundSong.firstOrNull()?.id}]")
            }.onFailure {
                throw Exception("Failed to search on YouTube Music: ${it.message}")
            }

            return ytmResult
        }

        /**
         * Search for an artist on YouTube Music.
         *
         * If no artist is found, create one locally
         */
        suspend fun youtubeArtistLookup(query: String): ArtistEntity? {
            var ytmResult: ArtistEntity? = null

            // hit up YouTube for artist
            YouTube.search(query, YouTube.SearchFilter.FILTER_ARTIST).onSuccess { result ->

                val foundArtist = result.items.filter { it is ArtistItem }.firstOrNull {
                    // TODO: might want to implement smarter matching
                    it.title.lowercase(Locale.getDefault()) == query.lowercase(Locale.getDefault())
                } as ArtistItem? ?: throw Exception("Failed to search: Artist not found on YouTube Music")
                ytmResult = ArtistEntity(
                    foundArtist.id,
                    foundArtist.title,
                    foundArtist.thumbnail,
                    foundArtist.channelId
                )

                if (SCANNER_DEBUG)
                    Log.v(TAG, "Found remote artist:  ${foundArtist.title} [${foundArtist.id}]")
            }.onFailure {
                throw Exception("Failed to search on YouTube Music")
            }

            return ytmResult
        }

        /**
         * Swap all participation(s) with old artist to use new artist
         *
         * p.s. This is here instead of DatabaseDao because it won't compile there because
         * "oooga boooga error in generated code"
         */
        fun swapArtists(old: ArtistEntity, new: ArtistEntity, database: MusicDatabase) {
            if (database.artistById(old.id) == null) {
                throw Exception("Attempting to swap with non-existent old artist in database with id: ${old.id}")
            }
            if (database.artistById(new.id) == null) {
                throw Exception("Attempting to swap with non-existent new artist in database with id: ${new.id}")
            }

            database.transaction {
                // update participation(s)
                database.updateSongArtistMap(old.id, new.id)
                database.updateAlbumArtistMap(old.id, new.id)

                // nuke old artist
                database.safeDeleteArtist(old.id)
            }
        }

        fun swapAlbums(old: AlbumEntity, new: AlbumEntity, database: MusicDatabase) {
            if (database.albumById(old.id) == null) {
                throw Exception("Attempting to swap with non-existent old album in database with id: ${old.id}")
            }
            if (database.albumById(new.id) == null) {
                throw Exception("Attempting to swap with non-existent new album in database with id: ${new.id}")
            }

            database.transaction {
                // update participation(s)
                database.updateSongAlbumMap(old.id, new.id)
                database.updateArtistAlbumMap(old.id, new.id)

                // nuke old artist
                database.safeDeleteAlbum(old.id)
            }
        }
    }
}

class InvalidAudioFileException(message: String) : Throwable(message)
class ScannerAbortException(message: String) : Throwable(message)
class ScannerCriticalFailureException(message: String) : Throwable(message)
