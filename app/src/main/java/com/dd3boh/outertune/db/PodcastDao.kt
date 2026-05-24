package com.dd3boh.outertune.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dd3boh.outertune.db.entities.PodcastEntity
import com.dd3boh.outertune.db.entities.PodcastEpisodeEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface PodcastDao {
    
    // ========== PODCAST OPERATIONS ==========
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPodcast(podcast: PodcastEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPodcasts(podcasts: List<PodcastEntity>)

    @Update
    suspend fun updatePodcast(podcast: PodcastEntity)

    @Delete
    suspend fun deletePodcast(podcast: PodcastEntity)

    @Query("DELETE FROM podcast WHERE id = :podcastId")
    suspend fun deletePodcastById(podcastId: String)

    @Query("SELECT * FROM podcast WHERE id = :podcastId")
    fun getPodcast(podcastId: String): Flow<PodcastEntity?>

    @Query("SELECT * FROM podcast ORDER BY dateAdded DESC")
    fun getAllPodcasts(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcast WHERE inLibrary IS NOT NULL ORDER BY inLibrary DESC")
    fun getLibraryPodcasts(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcast WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%'")
    fun searchPodcasts(query: String): Flow<List<PodcastEntity>>

    @Query("UPDATE podcast SET inLibrary = :inLibrary WHERE id = :podcastId")
    suspend fun updatePodcastLibraryStatus(podcastId: String, inLibrary: LocalDateTime?)

    @Query("UPDATE podcast SET lastUpdated = :lastUpdated WHERE id = :podcastId")
    suspend fun updatePodcastLastUpdated(podcastId: String, lastUpdated: LocalDateTime)

    
    // ========== EPISODE OPERATIONS ==========
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(episode: PodcastEpisodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<PodcastEpisodeEntity>)

    @Update
    suspend fun updateEpisode(episode: PodcastEpisodeEntity)

    @Delete
    suspend fun deleteEpisode(episode: PodcastEpisodeEntity)

    @Query("DELETE FROM podcast_episode WHERE id = :episodeId")
    suspend fun deleteEpisodeById(episodeId: String)

    @Query("SELECT * FROM podcast_episode WHERE id = :episodeId")
    fun getEpisode(episodeId: String): Flow<PodcastEpisodeEntity?>

    @Query("SELECT * FROM podcast_episode WHERE podcastId = :podcastId ORDER BY pubDate DESC")
    fun getEpisodes(podcastId: String): Flow<List<PodcastEpisodeEntity>>

    @Query("SELECT * FROM podcast_episode WHERE podcastId = :podcastId AND listened = 0 ORDER BY pubDate DESC")
    fun getUnlistenedEpisodes(podcastId: String): Flow<List<PodcastEpisodeEntity>>

    @Query("SELECT * FROM podcast_episode WHERE isLocal = 1 ORDER BY pubDate DESC")
    fun getDownloadedEpisodes(): Flow<List<PodcastEpisodeEntity>>

    @Query("SELECT * FROM podcast_episode WHERE podcastId = :podcastId AND isLocal = 1 ORDER BY pubDate DESC")
    fun getDownloadedEpisodes(podcastId: String): Flow<List<PodcastEpisodeEntity>>

    @Query("UPDATE podcast_episode SET listeningProgress = :progress WHERE id = :episodeId")
    suspend fun updateListeningProgress(episodeId: String, progress: Int)

    @Query("UPDATE podcast_episode SET listened = 1, dateListenedLast = :dateListened WHERE id = :episodeId")
    suspend fun markAsListened(episodeId: String, dateListened: LocalDateTime)

    @Query("UPDATE podcast_episode SET isLocal = :isLocal, localPath = :localPath, dateDownload = :dateDownload WHERE id = :episodeId")
    suspend fun updateEpisodeDownloadStatus(
        episodeId: String,
        isLocal: Boolean,
        localPath: String?,
        dateDownload: LocalDateTime?
    )

    @Query("SELECT COUNT(*) FROM podcast_episode WHERE podcastId = :podcastId AND listened = 1")
    fun getListenedEpisodeCount(podcastId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM podcast_episode WHERE podcastId = :podcastId")
    fun getTotalEpisodeCount(podcastId: String): Flow<Int>

    // ========== RECENT UNLISTENED EPISODES ==========
    
    @Query("""
        SELECT * FROM podcast_episode 
        WHERE podcastId IN (SELECT id FROM podcast WHERE inLibrary IS NOT NULL)
        AND listened = 0
        ORDER BY pubDate DESC
        LIMIT :limit
    """)
    fun getRecentUnlistenedEpisodes(limit: Int = 20): Flow<List<PodcastEpisodeEntity>>
}
