package com.dd3boh.outertune.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.dd3boh.outertune.constants.AudioQuality
import com.dd3boh.outertune.constants.AudioQualityKey
import com.dd3boh.outertune.constants.DownloadExtraPathKey
import com.dd3boh.outertune.constants.DownloadPathKey
import com.dd3boh.outertune.constants.LikedAutodownloadMode
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.FormatEntity
import com.dd3boh.outertune.db.entities.PlaylistSong
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.db.entities.SongEntity
import com.dd3boh.outertune.di.AppModule.PlayerCache
import com.dd3boh.outertune.di.DownloadCache
import com.dd3boh.outertune.extensions.getLikeAutoDownload
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.playback.downloadManager.DownloadDirectoryManagerOt
import com.dd3boh.outertune.playback.downloadManager.DownloadManagerOt
import com.dd3boh.outertune.utils.YTPlayerUtils
import com.dd3boh.outertune.utils.dataStore
import com.dd3boh.outertune.utils.enumPreference
import com.dd3boh.outertune.utils.get
import com.dd3boh.outertune.utils.reportException
import com.dd3boh.outertune.utils.scanners.InvalidAudioFileException
import com.dd3boh.outertune.utils.scanners.fileFromUri
import com.dd3boh.outertune.utils.scanners.uriListFromString
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadUtil @Inject constructor(
    @ApplicationContext private val context: Context,
    val database: MusicDatabase,
    val databaseProvider: DatabaseProvider,
    @DownloadCache val downloadCache: SimpleCache,
    @PlayerCache val playerCache: SimpleCache,
) {
    val TAG = DownloadUtil::class.simpleName.toString()

    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
    private val audioQuality by enumPreference(context, AudioQualityKey, AudioQuality.AUTO)
    private val songUrlCache = HashMap<String, Pair<String, Long>>()
    private val dataSourceFactory = ResolvingDataSource.Factory(
        CacheDataSource.Factory()
            .setCache(playerCache)
            .setUpstreamDataSourceFactory(
                OkHttpDataSource.Factory(
                    OkHttpClient.Builder()
                        .proxy(YouTube.proxy)
                        .build()
                )
            )
    ) { dataSpec ->
        val mediaId = dataSpec.key ?: error("No media id")
        val length = if (dataSpec.length >= 0) dataSpec.length else 1
        if (playerCache.isCached(mediaId, dataSpec.position, length)) {
            return@Factory dataSpec
        }

        songUrlCache[mediaId]?.takeIf { it.second > System.currentTimeMillis() }?.let {
            return@Factory dataSpec.withUri(it.first.toUri())
        }

        val playbackData = runBlocking(Dispatchers.IO) {
            YTPlayerUtils.playerResponseForPlayback(
                mediaId,
                audioQuality = audioQuality,
                connectivityManager = connectivityManager,
            )
        }.getOrThrow()
        val format = playbackData.format

        database.query {
            upsert(
                FormatEntity(
                    id = mediaId,
                    itag = format.itag,
                    mimeType = format.mimeType.split(";")[0],
                    codecs = format.mimeType.split("codecs=")[1].removeSurrounding("\""),
                    bitrate = format.bitrate,
                    sampleRate = format.audioSampleRate,
                    contentLength = format.contentLength!!,
                    loudnessDb = playbackData.audioConfig?.loudnessDb,
                    playbackTrackingUrl = playbackData.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                )
            )
        }

        val streamUrl = playbackData.streamUrl.let {
            // Specify range to avoid YouTube's throttling
            "${it}&range=0-${format.contentLength ?: 10000000}"
        }

        songUrlCache[mediaId] = streamUrl to System.currentTimeMillis() + (playbackData.streamExpiresInSeconds * 1000L)
        dataSpec.withUri(streamUrl.toUri())
    }
    val downloadNotificationHelper = DownloadNotificationHelper(context, ExoDownloadService.CHANNEL_ID)
    val downloadManager: DownloadManager =
        DownloadManager(context, databaseProvider, downloadCache, dataSourceFactory, Executor(Runnable::run)).apply {
            maxParallelDownloads = 3
            addListener(
                ExoDownloadService.TerminalStateNotificationHelper(
                    context = context,
                    notificationHelper = downloadNotificationHelper,
                    nextNotificationId = ExoDownloadService.NOTIFICATION_ID + 1
                )
            )
        }
    val downloads = MutableStateFlow<Map<String, Download>>(emptyMap())

    // Custom OT
    val customDownloads = MutableStateFlow<Map<String, LocalDateTime>>(emptyMap())
    var localMgr = DownloadDirectoryManagerOt(
        context,
        context.dataStore.get(DownloadPathKey, "").toUri(),
        uriListFromString(context.dataStore.get(DownloadExtraPathKey, ""))
    )
    val downloadMgr = DownloadManagerOt(localMgr)
    var isProcessingDownloads = MutableStateFlow(false)

    fun getCustomDownload(songId: String): Boolean = customDownloads.value.any { songId == it.key }

    fun getDownload(songId: String): Flow<Download?> = downloads.map { it[songId] }

    fun download(songs: List<MediaMetadata>) {
        songs.forEach { song -> downloadSong(song.id, song.title) }
    }

    fun download(song: MediaMetadata) {
        downloadSong(song.id, song.title)
    }

    fun download(song: SongEntity) {
        downloadSong(song.id, song.title)
    }

    private fun downloadSong(id: String, title: String) {
        if (getCustomDownload(id)) return
        val downloadRequest = DownloadRequest.Builder(id, id.toUri())
            .setCustomCacheKey(id)
            .setData(title.toByteArray())
            .build()
        DownloadService.sendAddDownload(
            context,
            ExoDownloadService::class.java,
            downloadRequest,
            false
        )
    }

    fun resumeDownloadsOnStart() {
        DownloadService.sendResumeDownloads(
            context,
            ExoDownloadService::class.java,
            false
        )
    }

    fun autoDownloadIfLiked(songs: List<SongEntity>) {
        songs.forEach { song -> autoDownloadIfLiked(song) }
    }

    fun autoDownloadIfLiked(song: SongEntity) {
        if (!song.liked || song.dateDownload != null) {
            return
        }

        val isWifiConnected = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false

        if (
            context.getLikeAutoDownload() == LikedAutodownloadMode.ON
            || (context.getLikeAutoDownload() == LikedAutodownloadMode.WIFI_ONLY && isWifiConnected)
        ) {
            download(song)
        }
    }


// Deletes from custom dl

    fun delete(song: PlaylistSong) = deleteSong(song.song.id)

    fun delete(song: SongItem) = deleteSong(song.id)

    fun delete(song: Song) = deleteSong(song.song.id)

    fun delete(song: SongEntity) = deleteSong(song.id)

    fun delete(song: MediaMetadata) = deleteSong(song.id)

    private fun deleteSong(id: String): Boolean {
        val deleted = localMgr.deleteFile(id)
        if (!deleted) return false
        downloads.update { map ->
            map.toMutableMap().apply {
                remove(id)
            }
        }

        runBlocking {
            database.song(id).first()?.song?.copy(localPath = null)
            database.updateDownloadStatus(id, null)
        }
        return true
    }

    /**
     * Retrieve song from cache, and delete it from cache afterwards
     */
    fun getAndDeleteFromCache(cache: SimpleCache, mediaId: String): ByteArray? {
        val spans: Set<CacheSpan> = cache.getCachedSpans(mediaId)
        if (spans.isEmpty()) return null

        val output = ByteArrayOutputStream()
        try {
            for (span in spans) {
                val file: File? = span.file
                FileInputStream(file).use { fis ->
                    fis.copyTo(output)
                }
            }

            cache.removeResource(mediaId)
            return output.toByteArray()
        } catch (e: IOException) {
            reportException(e)
        } finally {
            output.close()
        }
        return null
    }

    /**
     * Migrated existing downloads from the download cache to the new system in external storage
     */
    fun migrateDownloads() {
        if (isProcessingDownloads.value) return
        isProcessingDownloads.value = true
        CoroutineScope(Dispatchers.IO).launch {
            var runs = 0
            try {
                // "skeleton" of old download manager to access old download data
                val dataSourceFactory = ResolvingDataSource.Factory(
                    CacheDataSource.Factory()
                        .setCache(playerCache)
                        .setUpstreamDataSourceFactory(
                            OkHttpDataSource.Factory(
                                OkHttpClient.Builder()
                                    .proxy(YouTube.proxy)
                                    .build()
                            )
                        )
                ) { dataSpec ->
                    return@Factory dataSpec
                }

                val downloadManager: DownloadManager = DownloadManager(
                    context,
                    databaseProvider,
                    downloadCache,
                    dataSourceFactory,
                    Executor(Runnable::run)
                ).apply {
                    maxParallelDownloads = 3
                }

                // actual migration code
                val downloadedSongs = mutableMapOf<String, Download>()
                val cursor = downloadManager.downloadIndex.getDownloads()
                while (cursor.moveToNext()) {
                    downloadedSongs[cursor.download.request.id] = cursor.download
                }

                // copy all completed downloads
                val toMigrate = downloadedSongs.filter { it.value.state == Download.STATE_COMPLETED }
                toMigrate.forEach { s ->
                    if (runs++ % 10 == 0) {
                        Log.d(TAG, "Migrating download: $runs/${toMigrate.size}")
                        if (runs % 20 == 0) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "$runs/${toMigrate.size}", LENGTH_SHORT).show()
                            }
                        }
                    }
                    val songFromCache = getAndDeleteFromCache(downloadCache, s.key)
                    if (songFromCache != null) {
                        downloadMgr.enqueue(
                            mediaId = s.key,
                            data = songFromCache,
                            displayName = runBlocking { database.song(s.key).first()?.title ?: "" })
                    }
                }
            } catch (e: Exception) {
                reportException(e)
            } finally {
                isProcessingDownloads.value = false
            }
        }
    }


    fun cd() {
        localMgr = DownloadDirectoryManagerOt(
            context,
            context.dataStore.get(DownloadPathKey, "").toUri(),
            uriListFromString(context.dataStore.get(DownloadExtraPathKey, ""))
        )
    }

    /**
     * Rescan download directory and updates songs
     */
    fun rescanDownloads() {
        isProcessingDownloads.value = true
        val dbDownloads = runBlocking(Dispatchers.IO) { database.downloadedOrQueuedSongs().first() }
        val result = mutableMapOf<String, LocalDateTime>()

        // remove missing files
        val missingFiles = localMgr.getMissingFiles(dbDownloads.filterNot { it.song.dateDownload == null })
        missingFiles.forEach {
            runBlocking(Dispatchers.IO) { database.removeDownloadSong(it.song.id) }
        }

        // register new files
        val availableDownloads = dbDownloads.minus(missingFiles)
        availableDownloads.forEach { s ->
            result[s.song.id] = s.song.dateDownload!! // sql should cover our butts
        }
        isProcessingDownloads.value = false

        customDownloads.value = result
    }


    /**
     * Scan and import downloaded songs from main and extra directories.
     *
     * This is intended for re-importing existing songs (ex. songs get moved, after restoring app backup), thus all
     * songs will already need to exist in the database.
     */
    fun scanDownloads() {
        if (isProcessingDownloads.value) return
        isProcessingDownloads.value = true
        CoroutineScope(Dispatchers.IO).launch {
//            val scanner = LocalMediaScanner.getScanner(context, ScannerImpl.TAGLIB, SCANNER_OWNER_DL)
            runBlocking(Dispatchers.IO) { database.removeAllDownloadedSongs() }
            val result = mutableMapOf<String, LocalDateTime>()
            val timeNow = LocalDateTime.now()

            // remove missing files
            val availableFiles = localMgr.getAvailableFiles()
            availableFiles.forEach { f ->
                runBlocking(Dispatchers.IO) {
                    try {
                        val file = fileFromUri(context, f.value)
                        if (file == null) throw (InvalidAudioFileException("Hello darkness my old friend"))
                        // TODO: validate files in download folder
//                        val format: FormatEntity? = scanner.advancedScan(f.value).format
//                        if (format != null) {
//                            database.upsert(format)
//                        }
                        database.registerDownloadSong(f.key, timeNow, file.absolutePath)

                    } catch (e: InvalidAudioFileException) {
                        reportException(e)
                    }
                }
            }
//            LocalMediaScanner.destroyScanner(SCANNER_OWNER_DL)

            // pull from db again
            val dbDownloads = runBlocking(Dispatchers.IO) { database.downloadedSongs().first() }
            dbDownloads.forEach { s ->
                result[s.song.id] = timeNow
            }

            isProcessingDownloads.value = false
            customDownloads.value = result
        }
    }


    init {
        val result = mutableMapOf<String, Download>()
        val cursor = downloadManager.downloadIndex.getDownloads()
        while (cursor.moveToNext()) {
            result[cursor.download.request.id] = cursor.download
        }

        // custom download location
        CoroutineScope(Dispatchers.IO).launch {
            rescanDownloads()
        }

        downloads.value = result
        downloadManager.addListener(
            object : DownloadManager.Listener {
                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?
                ) {
                    downloads.update { map ->
                        map.toMutableMap().apply {
                            set(download.request.id, download)
                        }
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        if (download.state == Download.STATE_COMPLETED) {
                            val updateTime =
                                Instant.ofEpochMilli(download.updateTimeMs).atZone(ZoneOffset.UTC).toLocalDateTime()
                            database.updateDownloadStatus(download.request.id, updateTime)
                        } else {
                            database.updateDownloadStatus(download.request.id, null)
                        }
                    }
                }
            }
        )
    }
}