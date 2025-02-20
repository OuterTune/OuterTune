/*
 * Copyright (C) 2025 O‌ute‌rTu‌ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.utils.scanners

import android.content.Context
import android.media.MediaPlayer
import android.os.Environment
import androidx.datastore.preferences.core.edit
import com.dd3boh.outertune.constants.AutomaticScannerKey
import com.dd3boh.outertune.constants.ScannerImpl
import com.dd3boh.outertune.constants.ScannerImplKey
import com.dd3boh.outertune.constants.ScannerMatchCriteria
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.ArtistEntity
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.db.entities.SongArtistMap
import com.dd3boh.outertune.db.entities.SongEntity
import com.dd3boh.outertune.db.entities.SongGenreMap
import com.dd3boh.outertune.models.DirectoryTree
import com.dd3boh.outertune.models.SongTempData
import com.dd3boh.outertune.models.toMediaMetadata
import com.dd3boh.outertune.ui.utils.EXTRACTOR_DEBUG
import com.dd3boh.outertune.ui.utils.EXTRACTOR_TAG
import com.dd3boh.outertune.ui.utils.SCANNER_DEBUG
import com.dd3boh.outertune.ui.utils.STORAGE_ROOT
import com.dd3boh.outertune.ui.utils.SYNC_SCANNER
import com.dd3boh.outertune.ui.utils.cacheDirectoryTree
import com.dd3boh.outertune.ui.utils.scannerSession
import com.dd3boh.outertune.utils.closestMatch
import com.dd3boh.outertune.utils.dataStore
import com.dd3boh.outertune.utils.isPackageInstalled
import com.dd3boh.outertune.utils.reportException
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalDateTime
import java.util.Locale


class LocalMediaScanner(val context: Context, val scannerImpl: ScannerImpl) {
    private var advancedScannerImpl: MetadataScanner = when (scannerImpl) {
        ScannerImpl.TAGLIB -> TagLibScanner()
        ScannerImpl.FFMPEG_EXT -> FFMpegScanner(context)
    }

    init {
        if (EXTRACTOR_DEBUG)
            Timber.tag(EXTRACTOR_TAG).d("Creating scanner instance with scannerImpl: $scannerImpl")
    }

    /**
     * Compiles a song with all it's necessary metadata. Unlike MediaStore,
     * this also supports multiple artists, multiple genres (TBD), and a few extra details (TBD).
     */
    private fun advancedScan(
        path: String,
    ): SongTempData {
        try {
            // test if system can play
            val testPlayer = MediaPlayer()
            testPlayer.setDataSource(path)
            testPlayer.prepare()
            testPlayer.release()

            // decide which scanner to use
            val ffmpegData = if (advancedScannerImpl is FFMpegScanner) {
                advancedScannerImpl.getAllMetadataFromPath(path)
            } else if (advancedScannerImpl is TagLibScanner) {
                advancedScannerImpl.getAllMetadataFromFile(File(path))
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
                    throw InvalidAudioFileException("Not in a playable format: ${e.message} for: $path")
                }

                else -> {
                    if (SCANNER_DEBUG) {
                        Timber.tag(TAG).d(
                            "ERROR READING METADATA: ${e.message} for: $path"
                        )
                        e.printStackTrace()
                    }

                    // we still want the song to be playable even if metadata extractor fails
                    return SongTempData(
                        Song(
                            SongEntity(
                                SongEntity.generateSongId(),
                                path.substringAfterLast('/'),
                                thumbnailUrl = path,
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
     * Scan MediaStore for songs given a list of paths to scan for.
     * This will replace all data in the database for a given song.
     *
     * @param scanPaths List of whitelist paths to scan under. This assumes
     * the current directory is /storage/emulated/0/ a.k.a, /sdcard.
     * For example, to scan under Music and Documents/songs --> ("Music", Documents/songs)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun scanLocal(
        database: MusicDatabase,
        scanPaths: List<String>,
        excludedScanPaths: List<String>,
        pathsOnly: Boolean = false,
    ): MutableStateFlow<DirectoryTree> {
        val newDirectoryStructure = DirectoryTree(STORAGE_ROOT)
        Timber.tag(TAG).d("------------ SCAN: Starting Full Scanner ------------")
        scannerShowLoading.value = true

        val scannerJobs = ArrayList<Deferred<SongTempData?>>()
        runBlocking {
            getScanPaths(scanPaths, excludedScanPaths).forEach { path ->
                // we can expect lrc is not a song
                if (path.substringAfterLast('.') == "lrc") {
                    return@forEach
                }
                if (SCANNER_DEBUG)
                    Timber.tag(TAG).d("PATH: $path")

                /**
                 * TODO: do not link album (and whatever song id) with youtube yet, figure that out later
                 */

                // just get the paths
                if (pathsOnly) {
                    newDirectoryStructure.insert(
                        path.substringAfter(STORAGE_ROOT),
                        Song(SongEntity("", "", localPath = path), artists = ArrayList())
                    )
                    return@forEach
                }

                // extract metadata now
                if (!SYNC_SCANNER) {
                    // use async scanner
                    scannerJobs.add(
                        async(scannerSession) {
                            var ret: SongTempData?
                            if (scannerRequestCancel) {
                                if (SCANNER_DEBUG)
                                    Timber.tag(TAG).d("WARNING: Canceling advanced scanner job.")
                                throw ScannerAbortException("")
                            }
                            try {
                                ret = advancedScan(path)
                                scannerProgressTotal.value ++
                            } catch (e: InvalidAudioFileException) {
                                ret = null
                            }
                            ret
                        }
                    )
                } else {
                    if (scannerRequestCancel) {
                        if (SCANNER_DEBUG)
                            Timber.tag(TAG).d("WARNING: Requested to cancel Full Scanner. Aborting.")
                        scannerRequestCancel = false
                        throw ScannerAbortException("Scanner canceled during Full Scanner (synchronous)")
                    }

                    // force synchronous scanning of songs. Do not catch errors
                    val toInsert = advancedScan(path)
                    toInsert.song.song.localPath?.let { s ->
                        newDirectoryStructure.insert(
                            s.substringAfter(STORAGE_ROOT), toInsert.song
                        )
                        scannerProgressTotal.value ++
                    }
                }
            }


            if (!SYNC_SCANNER) {
                // use async scanner
                scannerJobs.awaitAll()
                if (scannerRequestCancel) {
                    if (SCANNER_DEBUG)
                        Timber.tag(TAG).d("WARNING: Requested to cancel Full Scanner. Aborting.")
                    scannerRequestCancel = false
                    throw ScannerAbortException("Scanner canceled during Full Scanner (asynchronous)")
                }
            }
        }

        // build the tree
        scannerJobs.forEach {
            val song = it.getCompleted()

            song?.song?.song?.localPath?.let { s ->
                newDirectoryStructure.insert(
                    s.substringAfter(STORAGE_ROOT), song.song
                )
            }
        }

        scannerShowLoading.value = false
        Timber.tag(TAG).d("------------ SCAN: Finished Full Scanner ------------")
        cacheDirectoryTree(newDirectoryStructure.androidStorageWorkaround().trimRoot())
        return MutableStateFlow(newDirectoryStructure)
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
    fun syncDB(
        database: MusicDatabase,
        newSongs: java.util.ArrayList<SongTempData>,
        matchStrength: ScannerMatchCriteria,
        strictFileNames: Boolean,
        refreshExisting: Boolean = false,
        noDisable: Boolean = false
    ) {
        if (scannerActive.value) {
            Timber.tag(TAG).d("------------ SYNC: Scanner in use. Aborting Local Library Sync ------------")
            return
        }
        Timber.tag(TAG).d("------------ SYNC: Starting Local Library Sync ------------")
        scannerActive.value = true
        scannerShowLoading.value = true
        // deduplicate
        val finalSongs = ArrayList<SongTempData>()
        newSongs.forEach { song ->
            if (finalSongs.none { s -> compareSong(song.song, s.song, matchStrength, strictFileNames) }) {
                finalSongs.add(song)
            }
        }
        Timber.tag(TAG).d("Entries to process: ${newSongs.size}. After dedup: ${finalSongs.size}")
        scannerProgressTotal.value = finalSongs.size
        scannerProgressCurrent.value = 0

        // sync
        var runs = 0
        finalSongs.forEach { song ->
            runs ++
            if (SCANNER_DEBUG && runs % 20 == 0) {
                Timber.tag(TAG).d("------------ SYNC: Local Library Sync: $runs/${finalSongs.size} processed ------------")
            }
            if (runs % 5 == 0) {
                scannerProgressCurrent.value += 5
            }

            if (scannerRequestCancel) {
                if (SCANNER_DEBUG)
                    Timber.tag(TAG).d("WARNING: Requested to cancel Local Library Sync. Aborting.")
                scannerRequestCancel = false
                throw ScannerAbortException("Scanner canceled during Local Library Sync")
            }

            val querySong = database.searchSongsAllLocal(song.song.title)


            runBlocking(Dispatchers.IO) {
                // check if this song is known to the library
                val songMatch = querySong.first().filter {
                    return@filter compareSong(it, song.song, matchStrength, strictFileNames)
                }

                if (SCANNER_DEBUG) {
                    Timber.tag(TAG)
                        .d("Found songs that match: ${songMatch.size}, Total results from database: ${querySong.first().size}")
                    if (songMatch.isNotEmpty()) {
                        Timber.tag(TAG)
                            .d("FIRST Found songs ${songMatch.first().song.title}")
                    }
                }


                if (songMatch.isNotEmpty()) { // known song, update the song info in the database
                    if (SCANNER_DEBUG)
                        Timber.tag(TAG)
                            .d("Found in database, updating song: ${song.song.title} rescan = $refreshExisting")

                    val oldSong = songMatch.first().song
                    val songToUpdate = song.song.song.copy(id = oldSong.id, localPath = song.song.song.localPath)

                    // don't run if we will update these values in rescan anyways
                    // always ensure inLibrary and local path values are valid
                    if (!refreshExisting && (oldSong.inLibrary == null || oldSong.localPath == null)) {
                        database.update(songToUpdate)

                        // update format
                        if (song.format != null) {
                            database.query {
                                upsert(song.format.copy(id = songToUpdate.id))
                            }
                        }
                    }

                    if (!refreshExisting) { // below is only for when rescan is enabled
                        // always update the path
                        database.updateLocalSongPath(songToUpdate.id, songToUpdate.inLibrary, songToUpdate.localPath)
                        return@runBlocking
                    }

                    database.transaction {
                        update(songToUpdate)

                        // destroy existing artist links
                        unlinkSongArtists(songToUpdate.id)
                    }

                    // update artists
                    var artistPos = 0
                    song.song.artists.forEach {
                        val dbQuery =
                            database.searchArtists(it.name).firstOrNull()?.sortedBy { item -> item.artist.name.length }
                        val dbArtist = dbQuery?.let { item -> closestMatch(it.name, item) }

                        database.transaction {
                            if (dbArtist == null) {
                                // artist does not exist in db, add it then link it
                                insert(it)
                                insert(SongArtistMap(songToUpdate.id, it.id, artistPos))
                            } else {
                                // artist does  exist in db, link to it
                                insert(SongArtistMap(songToUpdate.id, dbArtist.artist.id, artistPos))
                            }
                        }

                        artistPos++
                    }

                    artistPos = 0 // reuse this var for genres
                    song.song.genre?.forEach {
                        val dbGenre = database.genreByAproxName(it.title).firstOrNull()?.firstOrNull()

                        database.transaction {
                            if (dbGenre == null) {
                                // genre does not exist in db, add it then link it
                                insert(it)
                                insert(SongGenreMap(songToUpdate.id, it.id, artistPos))
                            } else {
                                // genre does exist in db, link to it
                                insert(SongGenreMap(songToUpdate.id, dbGenre.id, artistPos))
                            }
                        }

                        artistPos++
                    }

                    // update format
                    if (song.format != null) {
                        database.query {
                            upsert(song.format.copy(id = songToUpdate.id))
                        }
                    }

                } else { // new song
                    if (SCANNER_DEBUG)
                        Timber.tag(TAG).d("NOT found in database, adding song: ${song.song.title}")

                    database.insert(song.song.toMediaMetadata())
                }
            }
        }

        // do not delete songs from database automatically, we just disable them
        if (!noDisable) {
            finalize(finalSongs.map { it.song }, database)
        }
        scannerShowLoading.value = false
        scannerActive.value = false
        Timber.tag(TAG).d("------------ SYNC: Finished Local Library Sync ------------")
    }

    /**
     * A faster scanner implementation that adds new songs to the database,
     * and does not touch older songs entires (apart from removing
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
    fun quickSync(
        database: MusicDatabase,
        newSongs: List<Song>,
        matchCriteria: ScannerMatchCriteria,
        strictFileNames: Boolean,
    ) {
        Timber.tag(TAG).d("------------ SYNC: Starting Quick (additive delta) Library Sync ------------")
        Timber.tag(TAG).d("Entries to process: ${newSongs.size}")
        scannerShowLoading.value = true

        runBlocking(Dispatchers.IO) {
            // get list of all songs in db, then get songs unknown to the database
            val allSongs = database.allLocalSongs().first()
            val delta = newSongs.filterNot {
                allSongs.any { dbSong -> compareSong(it, dbSong, matchCriteria, true) } // ignore user strictFileNames prefs for initial matching
            }

            val finalSongs = ArrayList<SongTempData>()
            val scannerJobs = ArrayList<Deferred<SongTempData?>>()
            runBlocking {
                // Get song basic metadata
                delta.forEach { s ->
                    if (scannerRequestCancel) {
                        if (SCANNER_DEBUG)
                            Timber.tag(TAG).d("WARNING: Requested to cancel. Aborting.")
                        scannerRequestCancel = false
                        throw ScannerAbortException("Scanner canceled during Quick (additive delta) Library Sync")
                    }

                    // we can expect lrc is not a song
                    if (s.song.localPath?.substringAfterLast('.') == "lrc") {
                        return@forEach
                    }

                    val path = "" + s.song.localPath

                    if (SCANNER_DEBUG)
                        Timber.tag(TAG).d("PATH: $path")

                    /**
                     * TODO: do not link album (and whatever song id) with youtube yet, figure that out later
                     */

                    if (!SYNC_SCANNER) {
                        // use async scanner
                        scannerJobs.add(
                            async(scannerSession) {
                                if (scannerRequestCancel) {
                                    if (SCANNER_DEBUG)
                                        Timber.tag(TAG).d("WARNING: Canceling advanced scanner job.")
                                    throw ScannerAbortException("")
                                }
                                try {
                                    advancedScan(path)
                                } catch (e: InvalidAudioFileException) {
                                    null
                                }
                            }
                        )
                    } else {
                        // force synchronous scanning of songs. Do not catch errors
                        finalSongs.add(advancedScan(path))
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
                syncDB(database, finalSongs, matchCriteria, strictFileNames, noDisable = true)
            } else {
                if (SCANNER_DEBUG)
                    Timber.tag(TAG).d("Not syncing, no valid songs found!")
            }

            // we handle disabling songs here instead
            finalize(newSongs, database)
        }

        cacheDirectoryTree(null)
        scannerShowLoading.value = false
        Timber.tag(TAG).d("------------ SYNC: Finished Quick (additive delta) Library Sync ------------")
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
    fun fullSync(
        database: MusicDatabase,
        newSongs: List<Song>,
        matchCriteria: ScannerMatchCriteria,
        strictFileNames: Boolean,
    ) {
        Timber.tag(TAG).d("------------ SYNC: Starting FULL Library Sync ------------")
        Timber.tag(TAG).d("Entries to process: ${newSongs.size}")
        scannerShowLoading.value = true

        runBlocking(Dispatchers.IO) {
            val finalSongs = ArrayList<SongTempData>()
            val scannerJobs = ArrayList<Deferred<SongTempData?>>()
            runBlocking {
                // Get song basic metadata
                newSongs.forEach { s ->
                    if (scannerRequestCancel) {
                        if (SCANNER_DEBUG)
                            Timber.tag(TAG).d("WARNING: Requested to cancel. Aborting.")
                        scannerRequestCancel = false
                        throw ScannerAbortException("Scanner canceled during FULL Library Sync")
                    }

                    // we can expect lrc is not a song
                    if (s.song.localPath?.substringAfterLast('.') == "lrc") {
                        return@forEach
                    }

                    val path = "" + s.song.localPath

                    if (SCANNER_DEBUG)
                        Timber.tag(TAG).d("PATH: $path")

                    /**
                     * TODO: do not link album (and whatever song id) with youtube yet, figure that out later
                     */

                    if (!SYNC_SCANNER) {
                        // use async scanner
                        scannerJobs.add(
                            async(scannerSession) {
                                if (scannerRequestCancel) {
                                    if (SCANNER_DEBUG)
                                        Timber.tag(TAG).d("WARNING: Canceling advanced scanner job.")
                                    throw ScannerAbortException("")
                                }
                                try {
                                    advancedScan(path)
                                } catch (e: InvalidAudioFileException) {
                                    null
                                }
                            }
                        )
                    } else {
                        // force synchronous scanning of songs. Do not catch errors
                        finalSongs.add(advancedScan(path))
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
                /**
                 * TODO: Delete all local format entity before scan
                 */
                syncDB(database, finalSongs, matchCriteria, strictFileNames, refreshExisting = true)
            } else {
                if (SCANNER_DEBUG)
                    Timber.tag(TAG).d("Not syncing, no valid songs found!")
            }
        }

        cacheDirectoryTree(null)
        scannerShowLoading.value = false
        Timber.tag(TAG).d("------------ SYNC: Finished Quick (additive delta) Library Sync ------------")
    }


    /**
     * Converts all local artists to remote artists if possible
     */
    fun localToRemoteArtist(database: MusicDatabase) {
        if (scannerActive.value) {
            Timber.tag(TAG).d("------------ SYNC: Scanner in use. Aborting youtubeArtistLookup job ------------")
            return
        }
        var runs = 0
        Timber.tag(TAG).d("------------ SYNC: Starting youtubeArtistLookup job ------------")
        scannerActive.value = true
        scannerShowLoading.value = true
        runBlocking(Dispatchers.IO) {
            val allLocal = database.allLocalArtists().first()

            allLocal.forEach { element ->
                runs ++
                if (runs % 20 == 0) {
                    Timber.tag(TAG).d("------------ SYNC: youtubeArtistLookup job: $runs artists processed ------------")
                }

                val artistVal = element.name.trim()

                // check if this artist exists in DB already
                val databaseArtistMatch =
                    runBlocking(Dispatchers.IO) {
                        database.fuzzySearchArtists(artistVal).first().filter { artist ->
                            // only look for remote artists here
                            return@filter artist.name == artistVal && !artist.isLocalArtist
                        }
                    }

                if (SCANNER_DEBUG)
                    Timber.tag(TAG)
                        .d("ARTIST FOUND IN DB??? Results size: ${databaseArtistMatch.size}")

                // cancel here since this is where the real heavy action is
                if (scannerRequestCancel) {
                    if (SCANNER_DEBUG)
                        Timber.tag(TAG).d("WARNING: Requested to cancel youtubeArtistLookup job. Aborting.")
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
            }

            if (scannerRequestCancel) {
                if (SCANNER_DEBUG)
                    Timber.tag(TAG).d("WARNING: Requested to cancel during localToRemoteArtist. Aborting.")
                throw ScannerAbortException("Scanner canceled during localToRemoteArtist")
            }
        }

        scannerShowLoading.value = false
        scannerActive.value = false
        Timber.tag(TAG).d("------------ SYNC: youtubeArtistLookup job ended------------")
    }


    /**
     * Remove inaccessible, and duplicate songs from the library
     */
    private fun finalize(newSongs: List<Song>, database: MusicDatabase) {
        if (SCANNER_DEBUG)
            Timber.tag(TAG).d("Start finalize (disable songs) job. Number of valid songs: ${newSongs.size}")
        runBlocking(Dispatchers.IO) {
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
                        Timber.tag(TAG).d("Disabling song ${song.song.localPath}")
                    database.transaction {
                        disableLocalSong(song.song.id)
                    }
                }
            }

            // remove duplicates
            val dupes = database.duplicatedLocalSongs().first().toMutableList()
            var index = 0

            if (SCANNER_DEBUG)
                Timber.tag(TAG).d("Start finalize (duplicate removal) job. Number of candidates: ${dupes.size}")

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
                        Timber.tag(TAG).d("Deleting song ${it.first.id} (${it.first.title})")
                    database.delete(it.first)
                }
            }
        }
    }


    /**
     * Destroys all local library data (local songs and artists, does not include YTM downloads)
     * from the database
     */
    fun nukeLocalDB(database: MusicDatabase) {
        Timber.tag(TAG)
            .w("NUKING LOCAL FILE LIBRARY FROM DATABASE! Nuke status: ${database.nukeLocalData()}")
    }


    companion object {
        // do not put any thing that should adhere to the scanner lock in here
        const val TAG = "LocalMediaScanner"

        private var localScanner: LocalMediaScanner? = null


        /**
         * TODO: Create a lock for background jobs like youtubeartists and etc
         */
        var scannerActive = MutableStateFlow(false) // TODO: make this an enum. scan -> sync -> ytmartist
        var scannerShowLoading = MutableStateFlow(false)
        var scannerFinished = MutableStateFlow(false)
        var scannerRequestCancel = false

        var scannerProgressTotal = MutableStateFlow(-1)
        var scannerProgressCurrent = MutableStateFlow(-1)


        /**
         * ==========================
         * Scanner management
         * ==========================
         */

        /**
         * Trust me bro, it should never be null
         */
        fun getScanner(context: Context, scannerImpl: ScannerImpl): LocalMediaScanner {
            /*
            if the FFmpeg extractor is suddenly removed and a scan is ran, reset to taglib, disable auto scanner.
            we don't want to run the taglib scanner fallback if the user explicitly selected FFmpeg as differences
            can muck with the song detection. Throw the error to the ui where it can be handled there
             */
            val isFFmpegInstalled = isPackageInstalled("wah.mikooomich.ffMetadataEx", context.packageManager)
            if (scannerImpl == ScannerImpl.FFMPEG_EXT && !isFFmpegInstalled) {
                runBlocking {
                    context.dataStore.edit { settings ->
                        settings[ScannerImplKey] = ScannerImpl.TAGLIB.toString()
                        settings[AutomaticScannerKey] = false
                    }
                }
                throw ScannerAbortException("FFmpeg extractor was selected, but the package is no longer available. Reset to taglib scanner and disabled automatic scanning")
            }

            if (localScanner == null) {
                localScanner = LocalMediaScanner(context, if (isFFmpegInstalled) scannerImpl else ScannerImpl.TAGLIB)
                scannerProgressTotal.value = 0
            }

            return localScanner!!
        }

        fun destroyScanner() {
            localScanner = null
            scannerActive.value = false
            scannerShowLoading.value = false
            scannerFinished.value = false
            scannerRequestCancel = false
            scannerProgressTotal.value = -1
            scannerProgressCurrent.value = -1

            if (EXTRACTOR_DEBUG)
                Timber.tag(EXTRACTOR_TAG).d("Scanner instance destroyed")
        }


        /**
         * ==========================
         * Scanner helpers
         * ==========================
         */


        /**
         * Get real path from UI
         * @param path in format "/tree/<media>:<rest of path>"
         */
        private fun getRealPathFromUri(path: String): String {
            val primaryStorageRoot = Environment.getExternalStorageDirectory().absolutePath
            // Google plz don't change ur api kthx
            val storageMedia = path.substringAfter("/tree/").substringBefore(':')
            return if (storageMedia == "primary") {
                path.replaceFirst("/tree/primary:", "$primaryStorageRoot/")
            } else {
                "/storage/$storageMedia/${path.substringAfter(':')}"
            }
        }

        /**
         * Build a list of paths to scan in, taking in exclusions into account. Exclusions
         * will override inclusions. All subdirectories will also be affected.
         */
        fun getScanPaths(scanPaths: List<String>, excludedScanPaths: List<String>): ArrayList<String> {
            val allSongs = ArrayList<String>()

            val resultingPaths =
                scanPaths.filterNot { incl ->
                    excludedScanPaths.any { excl ->
                        if (excl.isBlank()) false else incl.startsWith(excl)
                    }
                }

            val exclusions = excludedScanPaths.map { getRealPathFromUri(it) }

            resultingPaths.forEach { path ->
                try {
                    val songsHere =
                        File(getRealPathFromUri(path)).walk().filter { it.isFile }.toList().map { it.absolutePath }
                    allSongs.addAll(songsHere.filterNot { include -> exclusions.any { include.startsWith(it) } })
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                    throw Exception("oh well idk man this should never happen")
                }
            }

            return allSongs
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
         */
        fun refreshLocal(
            database: MusicDatabase,
            scanPaths: List<String>,
            excludedScanPaths: List<String>,
        ): MutableStateFlow<DirectoryTree> {
            val newDirectoryStructure = DirectoryTree(STORAGE_ROOT)

            // get songs from db
            var existingSongs: List<Song>
            runBlocking(Dispatchers.IO) {
                existingSongs = database.allLocalSongs().first()
            }

            Timber.tag(TAG).d("------------ SCAN: Starting Quick Directory Rebuild ------------")
            getScanPaths(scanPaths, excludedScanPaths).forEach { path ->
                if (SCANNER_DEBUG)
                    Timber.tag(TAG).d("Quick scanner: PATH: $path")

                // Build directory tree with existing files
                val possibleMatch =
                    existingSongs.firstOrNull { it.song.localPath == path }

                if (possibleMatch != null) {
                    newDirectoryStructure.insert(
                        path, possibleMatch
                    )
                }
            }

            Timber.tag(TAG).d("------------ SCAN: Finished Quick Directory Rebuild ------------")
            cacheDirectoryTree(newDirectoryStructure.androidStorageWorkaround().trimRoot())
            return MutableStateFlow(newDirectoryStructure)
        }

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
            strictFileNames: Boolean = false
        ): Boolean {
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
                        compareArtist(a.artists, b.artists) /* && album compare goes here */)
            }
        }

        /**
         * Search for an artist on YouTube Music.
         *
         * If no artist is found, create one locally
         */
        fun youtubeArtistLookup(query: String): ArtistEntity? {
            var ytmResult: ArtistEntity? = null

            // hit up YouTube for artist
            runBlocking(Dispatchers.IO) {
                YouTube.search(query, YouTube.SearchFilter.FILTER_ARTIST).onSuccess { result ->

                    val foundArtist = result.items.firstOrNull {
                        it.title.lowercase(Locale.getDefault()) == query.lowercase(Locale.getDefault())
                    } ?: throw Exception("Failed to search: Artist not found on YouTube Music")
                    ytmResult = ArtistEntity(
                        foundArtist.id,
                        foundArtist.title,
                        foundArtist.thumbnail
                    )

                    if (SCANNER_DEBUG)
                        Timber.tag(TAG)
                            .d("Found remote artist:  ${result.items.first().title}")
                }.onFailure {
                    throw Exception("Failed to search on YouTube Music")
                }

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
    }
}

class InvalidAudioFileException(message: String) : Throwable(message)
class ScannerAbortException(message: String) : Throwable(message)
class ScannerCriticalFailureException(message: String) : Throwable(message)