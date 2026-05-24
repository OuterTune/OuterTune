package com.dd3boh.outertune.extensions

import com.dd3boh.outertune.db.entities.PodcastEpisodeEntity
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.models.PodcastEpisodeMetadata
import java.time.LocalDateTime

/**
 * Podcast extensions related to downloads and playback
 */

/**
 * Returns true if the episode is fully downloaded
 */
fun PodcastEpisodeMetadata.isDownloaded(): Boolean {
    return isLocal && dateDownload != null && localPath != null
}

/**
 * Returns true if the episode is currently downloading
 */
fun PodcastEpisodeMetadata.isDownloading(): Boolean {
    return !isLocal && dateDownload == null
}

/**
 * Returns true if the episode has been fully listened to
 * (>95% of the content)
 */
fun PodcastEpisodeMetadata.isFullyListened(): Boolean {
    if (duration <= 0) return listened
    return getListeningPercentage() > 95f
}

/**
 * Returns true if the episode has been partially listened to
 */
fun PodcastEpisodeMetadata.isPartiallyListened(): Boolean {
    return !listened && listeningProgress > 0 && !isFullyListened()
}

/**
 * Returns the episode status as a string for the UI
 */
fun PodcastEpisodeMetadata.getStatusString(): String {
    return when {
        isDownloaded() -> "Downloaded"
        isFullyListened() -> "Listened"
        isPartiallyListened() -> "In Progress (${getProgressTimeString()})"
        else -> "Not Downloaded"
    }
}

/**
 * Converts to MediaMetadata for playback in the existing player
 */
fun PodcastEpisodeMetadata.toMediaMetadata(): MediaMetadata {
    return MediaMetadata(
        id = this.id,
        title = this.title,
        artists = listOf(
            MediaMetadata.Artist(id = podcastId, name = "Podcast")
        ),
        duration = this.duration,
        genre = null,
        thumbnailUrl = this.thumbnailUrl,
        isLocal = this.isLocal,
        localPath = this.localPath,
    )
}

fun PodcastEpisodeEntity.toMediaMetadata(): MediaMetadata {
    return MediaMetadata(
        id = this.id,
        title = this.title,
        artists = listOf(
            MediaMetadata.Artist(id = podcastId, name = "Podcast")
        ),
        duration = this.duration,
        genre = null,
        thumbnailUrl = this.thumbnailUrl,
        isLocal = this.isLocal,
        localPath = this.localPath,
    )
}

/**
 * Updates the episode download state
 */
fun PodcastEpisodeEntity.markAsDownloaded(
    localPath: String,
    dateDownload: LocalDateTime = LocalDateTime.now()
): PodcastEpisodeEntity {
    return this.copy(
        isLocal = true,
        localPath = localPath,
        dateDownload = dateDownload
    )
}

/**
 * Marks the episode as not downloaded
 */
fun PodcastEpisodeEntity.markAsNotDownloaded(): PodcastEpisodeEntity {
    return this.copy(
        isLocal = false,
        localPath = null,
        dateDownload = null
    )
}

/**
 * Updates the listening progress
 */
fun PodcastEpisodeEntity.updateProgress(progress: Int): PodcastEpisodeEntity {
    return this.copy(
        listeningProgress = progress,
        dateListenedLast = LocalDateTime.now()
    )
}

/**
 * Marks as fully listened
 */
fun PodcastEpisodeEntity.markAsListened(): PodcastEpisodeEntity {
    return this.copy(
        listened = true,
        listeningProgress = duration,
        dateListenedLast = LocalDateTime.now()
    )
}

/**
 * Marks as not listened (reset)
 */
fun PodcastEpisodeEntity.markAsUnlistened(): PodcastEpisodeEntity {
    return this.copy(
        listened = false,
        listeningProgress = 0,
        dateListenedLast = null
    )
}
