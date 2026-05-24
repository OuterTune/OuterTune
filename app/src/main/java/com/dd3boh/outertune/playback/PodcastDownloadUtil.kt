package com.dd3boh.outertune.playback

import android.content.Context
import android.util.Log
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.PodcastEpisodeEntity
import com.dd3boh.outertune.extensions.markAsDownloaded
import com.dd3boh.outertune.extensions.markAsNotDownloaded
import com.dd3boh.outertune.extensions.toMediaMetadata
import com.dd3boh.outertune.models.PodcastEpisodeMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class para manejar descargas de episodios de podcasts
 * Integra con el sistema existente de DownloadUtil
 */
@Singleton
class PodcastDownloadUtil @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val downloadUtil: DownloadUtil,
) {
    private val TAG = PodcastDownloadUtil::class.simpleName.toString()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val podcastDao = database.podcastDao()

    /**
     * Downloads a single podcast episode
     */
    fun downloadEpisode(episode: PodcastEpisodeMetadata) {
        Log.d(TAG, "Starting download for episode: ${episode.title}")
        downloadEpisodes(listOf(episode))
    }

    /**
     * Downloads multiple podcast episodes
     */
    fun downloadEpisodes(episodes: List<PodcastEpisodeMetadata>) {
        if (episodes.isEmpty()) {
            Log.w(TAG, "Episode list is empty")
            return
        }

        Log.d(TAG, "Downloading ${episodes.size} podcast episodes")

        // Convert to MediaMetadata and use the existing download system
        val mediaMetadataList = episodes.map { it.toMediaMetadata() }
        downloadUtil.download(mediaMetadataList)

        // Update the database once downloads complete
        scope.launch {
            episodes.forEach { episode ->
                podcastDao.updateEpisodeDownloadStatus(
                    episodeId = episode.id,
                    isLocal = true,
                    localPath = null, // Will be updated when DownloadUtil finishes
                    dateDownload = LocalDateTime.now()
                )
            }
        }
    }

    /**
     * Downloads all episodes of a podcast
     */
    fun downloadAllEpisodes(podcastId: String) {
        Log.d(TAG, "Downloading all episodes from podcast: $podcastId")

        scope.launch {
            val episodes = podcastDao.getEpisodes(podcastId)
            episodes.collect { episodeList ->
                val episodesToDownload = episodeList.filter { !it.isLocal }
                if (episodesToDownload.isNotEmpty()) {
                    val metadataList = episodesToDownload.map { it.toMetadata().toMediaMetadata() }
                    downloadUtil.download(metadataList)
                }
            }
        }
    }

    /**
     * Obtiene el estado de descarga de un episodio
     * Returns the download date if downloaded, null otherwise
     */
    fun getDownloadStatus(episodeId: String): Flow<LocalDateTime?> {
        return downloadUtil.getDownload(episodeId)
    }

    /**
     * Cancels the download for an episode
     */
    fun cancelDownload(episodeId: String) {
        Log.d(TAG, "Cancelling download for episode: $episodeId")
        // Note: The implementation depends on whether DownloadUtil exposes a cancel method
        // For now, we can mark it as not downloaded in the database
        scope.launch {
            val episode = podcastDao.getEpisode(episodeId)
            episode.collect { episodeEntity ->
                if (episodeEntity != null) {
                    podcastDao.updateEpisode(episodeEntity.markAsNotDownloaded())
                }
            }
        }
    }

    /**
     * Deletes a downloaded episode
     */
    fun deleteDownloadedEpisode(episodeId: String) {
        Log.d(TAG, "Deleting downloaded episode: $episodeId")

        scope.launch {
            val episode = podcastDao.getEpisode(episodeId)
            episode.collect { episodeEntity ->
                if (episodeEntity != null && episodeEntity.isLocal) {
                    // Delete the file from disk if necessary
                    if (episodeEntity.localPath != null) {
                        try {
                            val file = java.io.File(episodeEntity.localPath)
                            if (file.exists()) {
                                file.delete()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error al eliminar archivo: ${e.message}")
                        }
                    }

                    // Update the database
                    podcastDao.updateEpisode(episodeEntity.markAsNotDownloaded())
                }
            }
        }
    }

    /**
     * Deletes all downloaded episodes from a podcast
     */
    fun deleteAllDownloadedEpisodes(podcastId: String) {
        Log.d(TAG, "Deleting all downloaded episodes from podcast: $podcastId")

        scope.launch {
            val episodes = podcastDao.getDownloadedEpisodes(podcastId)
            episodes.collect { downloadedEpisodes ->
                downloadedEpisodes.forEach { episode ->
                    deleteDownloadedEpisode(episode.id)
                }
            }
        }
    }

    /**
     * Gets the total downloaded size for a podcast in bytes
     * (Useful for showing the user how much space is being used)
     */
    fun getDownloadedEpisodeSize(episodeId: String): Long {
        return try {
            val episode = podcastDao.getEpisode(episodeId)
            var size = 0L
            episode.apply {
                // This is where we would read the local file size
                // For now, return 0
            }
            size
        } catch (e: Exception) {
            Log.e(TAG, "Error getting size: ${e.message}")
            0L
        }
    }

    /**
     * Observa el estado de descargas de episodios de un podcast
     */
    fun observePodcastDownloadStatus(podcastId: String): Flow<Map<String, Boolean>> {
        return podcastDao.getEpisodes(podcastId).map { episodes ->
            episodes.associate { episode ->
                episode.id to episode.isLocal
            }
        }
    }

    /**
     * Returns the number of downloaded episodes for a podcast
     */
    fun getDownloadedEpisodeCount(podcastId: String): Flow<Int> {
        return podcastDao.getDownloadedEpisodes(podcastId).map { it.size }
    }

    /**
     * Returns the total number of episodes
     */
    fun getTotalEpisodeCount(podcastId: String): Flow<Int> {
        return podcastDao.getTotalEpisodeCount(podcastId)
    }

    /**
     * Gets all downloaded episodes globally
     */
    fun getAllDownloadedEpisodes(): Flow<List<PodcastEpisodeEntity>> {
        return podcastDao.getDownloadedEpisodes()
    }
}
