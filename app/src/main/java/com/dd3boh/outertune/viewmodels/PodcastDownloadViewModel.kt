package com.dd3boh.outertune.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.models.PodcastEpisodeMetadata
import com.dd3boh.outertune.playback.PodcastDownloadUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class PodcastDownloadViewModel @Inject constructor(
    private val database: MusicDatabase,
    private val podcastDownloadUtil: PodcastDownloadUtil,
) : ViewModel() {
    private val TAG = PodcastDownloadViewModel::class.simpleName.toString()
    private val podcastDao = database.podcastDao()

    // State for in-progress downloads
    private val _downloadingEpisodes = MutableStateFlow<Set<String>>(emptySet())
    val downloadingEpisodes: StateFlow<Set<String>> = _downloadingEpisodes.asStateFlow()

    // Error state
    private val _downloadErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val downloadErrors: StateFlow<Map<String, String>> = _downloadErrors.asStateFlow()

    // Download progress (episodeId -> percentage)
    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    /**
     * Starts downloading an episode
     */
    fun downloadEpisode(episode: PodcastEpisodeMetadata) {
        Log.d(TAG, "Starting download for episode: ${episode.title}")

        viewModelScope.launch {
            try {
                _downloadingEpisodes.value = _downloadingEpisodes.value + episode.id
                _downloadErrors.value = _downloadErrors.value - episode.id

                podcastDownloadUtil.downloadEpisode(episode)

                // Observe the download status
                observeDownloadStatus(episode.id)
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading episode: ${e.message}")
                _downloadErrors.value = _downloadErrors.value + (episode.id to (e.message ?: "Unknown error"))
                _downloadingEpisodes.value = _downloadingEpisodes.value - episode.id
            }
        }
    }

    /**
     * Downloads multiple episodes
     */
    fun downloadEpisodes(episodes: List<PodcastEpisodeMetadata>) {
        Log.d(TAG, "Downloading ${episodes.size} episodes")

        viewModelScope.launch {
            try {
                val episodeIds = episodes.map { it.id }.toSet()
                _downloadingEpisodes.value = _downloadingEpisodes.value + episodeIds
                _downloadErrors.value = _downloadErrors.value - episodeIds.toList()

                podcastDownloadUtil.downloadEpisodes(episodes)

                // Observe the status of each episode
                episodes.forEach { observeDownloadStatus(it.id) }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading episodes: ${e.message}")
                val episodeIds = episodes.map { it.id }
                episodeIds.forEach { episodeId ->
                    _downloadErrors.value = _downloadErrors.value + (episodeId to (e.message ?: "Unknown error"))
                }
                _downloadingEpisodes.value = _downloadingEpisodes.value - episodeIds.toSet()
            }
        }
    }

    /**
     * Downloads all episodes of a podcast
     */
    fun downloadAllEpisodes(podcastId: String) {
        Log.d(TAG, "Downloading all episodes from podcast: $podcastId")

        viewModelScope.launch {
            try {
                podcastDownloadUtil.downloadAllEpisodes(podcastId)
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading all episodes: ${e.message}")
            }
        }
    }

    /**
     * Cancels the download for an episode
     */
    fun cancelDownload(episodeId: String) {
        Log.d(TAG, "Cancelling download for episode: $episodeId")

        viewModelScope.launch {
            try {
                podcastDownloadUtil.cancelDownload(episodeId)
                _downloadingEpisodes.value = _downloadingEpisodes.value - episodeId
                _downloadProgress.value = _downloadProgress.value - episodeId
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling download: ${e.message}")
            }
        }
    }

    /**
     * Deletes a downloaded episode
     */
    fun deleteDownloadedEpisode(episodeId: String) {
        Log.d(TAG, "Deleting downloaded episode: $episodeId")

        viewModelScope.launch {
            try {
                podcastDownloadUtil.deleteDownloadedEpisode(episodeId)
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting episode: ${e.message}")
                _downloadErrors.value = _downloadErrors.value + (episodeId to (e.message ?: "Unknown error"))
            }
        }
    }

    /**
     * Deletes all downloaded episodes from a podcast
     */
    fun deleteAllDownloadedEpisodes(podcastId: String) {
        Log.d(TAG, "Deleting all downloaded episodes from podcast: $podcastId")

        viewModelScope.launch {
            try {
                podcastDownloadUtil.deleteAllDownloadedEpisodes(podcastId)
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting episodes: ${e.message}")
            }
        }
    }

    /**
     * Gets the download status for an episode
     */
    fun getDownloadStatus(episodeId: String): Flow<LocalDateTime?> {
        return podcastDownloadUtil.getDownloadStatus(episodeId)
    }

    /**
     * Gets the number of downloaded episodes for a podcast
     */
    fun getDownloadedEpisodeCount(podcastId: String): Flow<Int> {
        return podcastDownloadUtil.getDownloadedEpisodeCount(podcastId)
    }

    /**
     * Gets the total number of episodes for a podcast
     */
    fun getTotalEpisodeCount(podcastId: String): Flow<Int> {
        return podcastDownloadUtil.getTotalEpisodeCount(podcastId)
    }

    /**
     * Observes the download status for a podcast
     */
    fun observePodcastDownloadStatus(podcastId: String): Flow<Map<String, Boolean>> {
        return podcastDownloadUtil.observePodcastDownloadStatus(podcastId)
    }

    /**
     * Observes all downloaded episodes
     */
    fun observeAllDownloadedEpisodes() = podcastDownloadUtil.getAllDownloadedEpisodes()

    /**
     * Observes the download progress for an episode
     */
    private fun observeDownloadStatus(episodeId: String) {
        viewModelScope.launch {
            try {
                podcastDownloadUtil.getDownloadStatus(episodeId).collect { downloadDate ->
                    if (downloadDate != null) {
                        // Download completed
                        _downloadingEpisodes.value = _downloadingEpisodes.value - episodeId
                        _downloadProgress.value = _downloadProgress.value + (episodeId to 100f)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error observing status: ${e.message}")
            }
        }
    }

    /**
     * Clears errors for a specific episode
     */
    fun clearError(episodeId: String) {
        _downloadErrors.value = _downloadErrors.value - episodeId
    }

    /**
     * Clears all errors
     */
    fun clearAllErrors() {
        _downloadErrors.value = emptyMap()
    }
}
