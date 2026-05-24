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

    // Progreso de descargas (episodeId -> porcentaje)
    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    /**
     * Inicia la descarga de un episodio
     */
    fun downloadEpisode(episode: PodcastEpisodeMetadata) {
        Log.d(TAG, "Iniciando descarga de episodio: ${episode.title}")

        viewModelScope.launch {
            try {
                _downloadingEpisodes.value = _downloadingEpisodes.value + episode.id
                _downloadErrors.value = _downloadErrors.value - episode.id

                podcastDownloadUtil.downloadEpisode(episode)

                // Observar el estado de la descarga
                observeDownloadStatus(episode.id)
            } catch (e: Exception) {
                Log.e(TAG, "Error descargando episodio: ${e.message}")
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

                // Observar estado de cada episodio
                episodes.forEach { observeDownloadStatus(it.id) }
            } catch (e: Exception) {
                Log.e(TAG, "Error descargando episodios: ${e.message}")
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
                Log.e(TAG, "Error descargando todos los episodios: ${e.message}")
            }
        }
    }

    /**
     * Cancela la descarga de un episodio
     */
    fun cancelDownload(episodeId: String) {
        Log.d(TAG, "Cancelando descarga de episodio: $episodeId")

        viewModelScope.launch {
            try {
                podcastDownloadUtil.cancelDownload(episodeId)
                _downloadingEpisodes.value = _downloadingEpisodes.value - episodeId
                _downloadProgress.value = _downloadProgress.value - episodeId
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelando descarga: ${e.message}")
            }
        }
    }

    /**
     * Elimina un episodio descargado
     */
    fun deleteDownloadedEpisode(episodeId: String) {
        Log.d(TAG, "Eliminando episodio descargado: $episodeId")

        viewModelScope.launch {
            try {
                podcastDownloadUtil.deleteDownloadedEpisode(episodeId)
            } catch (e: Exception) {
                Log.e(TAG, "Error eliminando episodio: ${e.message}")
                _downloadErrors.value = _downloadErrors.value + (episodeId to (e.message ?: "Unknown error"))
            }
        }
    }

    /**
     * Elimina todos los episodios descargados de un podcast
     */
    fun deleteAllDownloadedEpisodes(podcastId: String) {
        Log.d(TAG, "Eliminando todos los episodios descargados del podcast: $podcastId")

        viewModelScope.launch {
            try {
                podcastDownloadUtil.deleteAllDownloadedEpisodes(podcastId)
            } catch (e: Exception) {
                Log.e(TAG, "Error eliminando episodios: ${e.message}")
            }
        }
    }

    /**
     * Obtiene el estado de descarga de un episodio
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
     * Observa el estado de descargas de un podcast
     */
    fun observePodcastDownloadStatus(podcastId: String): Flow<Map<String, Boolean>> {
        return podcastDownloadUtil.observePodcastDownloadStatus(podcastId)
    }

    /**
     * Observa todos los episodios descargados
     */
    fun observeAllDownloadedEpisodes() = podcastDownloadUtil.getAllDownloadedEpisodes()

    /**
     * Observa el progreso de descarga de un episodio
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
                Log.e(TAG, "Error observando estado: ${e.message}")
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
     * Limpia todos los errores
     */
    fun clearAllErrors() {
        _downloadErrors.value = emptyMap()
    }
}
