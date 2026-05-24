package com.dd3boh.outertune.models

import androidx.compose.runtime.Immutable
import com.dd3boh.outertune.db.entities.PodcastEntity
import com.dd3boh.outertune.db.entities.PodcastEpisodeEntity
import com.dd3boh.outertune.utils.LocalArtworkPath
import java.io.Serializable
import java.time.LocalDateTime

@Immutable
data class PodcastMetadata(
    val id: String,
    val title: String,
    val author: String? = null,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val feedUrl: String,
    val language: String? = null,
    val categories: List<String> = emptyList(),
    val inLibrary: LocalDateTime? = null,
    val dateAdded: LocalDateTime? = null,
    val lastUpdated: LocalDateTime? = null,
) : Serializable {
    fun toEntity() = PodcastEntity(
        id = id,
        title = title,
        author = author,
        description = description,
        thumbnailUrl = thumbnailUrl,
        feedUrl = feedUrl,
        language = language,
        categories = if (categories.isNotEmpty()) categories.joinToString(",") else null,
        inLibrary = inLibrary,
        dateAdded = dateAdded,
        lastUpdated = lastUpdated,
    )

    fun getThumbnailModel(sizeX: Int = -1, sizeY: Int = -1): Any? {
        return LocalArtworkPath(thumbnailUrl, sizeX, sizeY)
    }
}

@Immutable
data class PodcastEpisodeMetadata(
    val id: String,
    val podcastId: String,
    val title: String,
    val description: String? = null,
    val audioUrl: String,
    val duration: Int = -1, // en segundos
    val thumbnailUrl: String? = null,
    val pubDate: LocalDateTime? = null,
    val isLocal: Boolean = false,
    val localPath: String? = null,
    val dateDownload: LocalDateTime? = null,
    val listened: Boolean = false,
    val listeningProgress: Int = 0, // en segundos
    val dateListenedLast: LocalDateTime? = null,
    val dateAdded: LocalDateTime? = null,
) : Serializable {
    fun toEntity() = PodcastEpisodeEntity(
        id = id,
        podcastId = podcastId,
        title = title,
        description = description,
        audioUrl = audioUrl,
        duration = duration,
        thumbnailUrl = thumbnailUrl,
        pubDate = pubDate,
        isLocal = isLocal,
        localPath = localPath,
        dateDownload = dateDownload,
        listened = listened,
        listeningProgress = listeningProgress,
        dateListenedLast = dateListenedLast,
        dateAdded = dateAdded,
    )

    fun getThumbnailModel(sizeX: Int = -1, sizeY: Int = -1): Any? {
        return LocalArtworkPath(thumbnailUrl, sizeX, sizeY)
    }

    /**
     * Returns the listening progress in "mm:ss" format
     */
    fun getProgressTimeString(): String {
        val minutes = listeningProgress / 60
        val seconds = listeningProgress % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * Returns the duration in "mm:ss" format
     */
    fun getDurationTimeString(): String {
        if (duration == -1) return "--:--"
        val minutes = duration / 60
        val seconds = duration % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * Calcula el porcentaje de escucha
     */
    fun getListeningPercentage(): Float {
        return if (duration > 0) (listeningProgress.toFloat() / duration) * 100f else 0f
    }
}

// Conversion extensions
fun PodcastEntity.toMetadata() = PodcastMetadata(
    id = id,
    title = title,
    author = author,
    description = description,
    thumbnailUrl = thumbnailUrl,
    feedUrl = feedUrl,
    language = language,
    categories = categories?.split(",")?.map { it.trim() } ?: emptyList(),
    inLibrary = inLibrary,
    dateAdded = dateAdded,
    lastUpdated = lastUpdated,
)

fun PodcastEpisodeEntity.toMetadata() = PodcastEpisodeMetadata(
    id = id,
    podcastId = podcastId,
    title = title,
    description = description,
    audioUrl = audioUrl,
    duration = duration,
    thumbnailUrl = thumbnailUrl,
    pubDate = pubDate,
    isLocal = isLocal,
    localPath = localPath,
    dateDownload = dateDownload,
    listened = listened,
    listeningProgress = listeningProgress,
    dateListenedLast = dateListenedLast,
    dateAdded = dateAdded,
)

/**
 * Convierte un episodio de podcast a MediaMetadata para reutilizar el reproductor
 */
fun PodcastEpisodeMetadata.toMediaMetadata(): MediaMetadata {
    return MediaMetadata(
        id = this.id,
        title = this.title,
        artists = listOf(
            MediaMetadata.Artist(id = podcastId, name = "Podcast")
        ),
        duration = this.duration,
        thumbnailUrl = this.thumbnailUrl,
        isLocal = this.isLocal,
        localPath = this.localPath,
    )
}

fun PodcastEpisodeEntity.toMediaMetadata(): MediaMetadata {
    return this.toMetadata().toMediaMetadata()
}
