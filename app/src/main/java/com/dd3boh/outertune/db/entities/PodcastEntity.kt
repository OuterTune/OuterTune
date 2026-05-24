package com.dd3boh.outertune.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import org.apache.commons.lang3.RandomStringUtils

@Immutable
@Entity(tableName = "podcast")
data class PodcastEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String? = null,
    val author: String? = null,
    val thumbnailUrl: String? = null,
    val feedUrl: String,
    val language: String? = null,
    val categories: String? = null, // JSON array or comma-separated
    @ColumnInfo(index = true)
    val inLibrary: LocalDateTime? = null,
    val dateAdded: LocalDateTime? = null,
    val lastUpdated: LocalDateTime? = null,
) {
    companion object {
        fun generatePodcastId() = "PC" + RandomStringUtils.insecure().next(8, true, false)
    }
}

@Immutable
@Entity(
    tableName = "podcast_episode",
    foreignKeys = [
        ForeignKey(
            entity = PodcastEntity::class,
            parentColumn = "id",
            childColumns = ["podcastId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["podcastId"]),
        Index(value = ["isLocal"])
    ]
)
data class PodcastEpisodeEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(index = true)
    val podcastId: String, // Foreign key to PodcastEntity
    val title: String,
    val description: String? = null,
    val audioUrl: String,
    val duration: Int = -1, // en segundos, -1 si desconocido
    val thumbnailUrl: String? = null,
    val pubDate: LocalDateTime? = null,
    @ColumnInfo(name = "isLocal", defaultValue = false.toString())
    val isLocal: Boolean = false,
    @ColumnInfo(index = true)
    val localPath: String? = null,
    val dateDownload: LocalDateTime? = null, // doubles as "isDownloaded"
    val listened: Boolean = false,
    val listeningProgress: Int = 0, // en segundos
    val dateListenedLast: LocalDateTime? = null,
    val dateAdded: LocalDateTime? = null,
) {
    fun toggleLibrary() = copy(
        inLibrary = if (inLibrary == null) LocalDateTime.now() else null
    )

    fun updateListeningProgress(progress: Int) = copy(
        listeningProgress = progress,
        dateListenedLast = LocalDateTime.now()
    )

    fun markAsListened() = copy(
        listened = true,
        dateListenedLast = LocalDateTime.now()
    )

    companion object {
        fun generateEpisodeId() = "EP" + RandomStringUtils.insecure().next(8, true, false)
    }
}

/**
 * Relationship between a Podcast and its episodes with additional information
 */
@Immutable
data class PodcastWithEpisodes(
    val podcast: PodcastEntity,
    val episodes: List<PodcastEpisodeEntity> = emptyList()
)
