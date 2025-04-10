package com.dd3boh.outertune.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.dd3boh.outertune.constants.PlaylistFilter
import com.dd3boh.outertune.constants.PlaylistSortType
import com.dd3boh.outertune.db.entities.Playlist
import com.dd3boh.outertune.db.entities.PlaylistEntity
import com.dd3boh.outertune.db.entities.PlaylistSong
import com.dd3boh.outertune.db.entities.PlaylistSongMap
import com.dd3boh.outertune.extensions.reversed
import com.zionhuang.innertube.models.PlaylistItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


/*
 * Logic related to playlists entities and their mapping
 */

@Dao
interface PlaylistsDao {

    // region Gets
    @Transaction
    @Query("""
        SELECT 
            p.*, 
            COUNT(psm.playlistId) AS songCount,
            SUM(CASE WHEN s.dateDownload IS NOT NULL THEN 1 ELSE 0 END) AS downloadCount
        FROM playlist p
            LEFT JOIN playlist_song_map psm ON p.id = psm.playlistId
            LEFT JOIN song s ON psm.songId = s.id
        WHERE p.id = :playlistId
        GROUP BY p.id
    """)
    fun playlist(playlistId: String): Flow<Playlist?>

    @Transaction
    @Query("""
        SELECT 
            p.*, 
            COUNT(psm.playlistId) AS songCount,
            SUM(CASE WHEN s.dateDownload IS NOT NULL THEN 1 ELSE 0 END) AS downloadCount
        FROM playlist p
            LEFT JOIN playlist_song_map psm ON p.id = psm.playlistId
            LEFT JOIN song s ON psm.songId = s.id
        WHERE p.browseId = :browseId
        GROUP BY p.id
    """)
    fun playlistByBrowseId(browseId: String): Flow<Playlist?>

    @Transaction
    @Query("""
        SELECT 
            p.*, 
            COUNT(psm.playlistId) AS songCount,
            SUM(CASE WHEN s.dateDownload IS NOT NULL THEN 1 ELSE 0 END) AS downloadCount
        FROM playlist p
            LEFT JOIN playlist_song_map psm ON p.id = psm.playlistId
            LEFT JOIN song s ON psm.songId = s.id
        WHERE name LIKE '%' || :query || '%'
            AND s.inLibrary IS NOT NULL
        GROUP BY p.id
        LIMIT :previewSize
    """)
    fun searchPlaylists(query: String, previewSize: Int = Int.MAX_VALUE): Flow<List<Playlist>>

    @Transaction
    @Query("SELECT * FROM playlist_song_map WHERE playlistId = :playlistId ORDER BY position")
    fun playlistSongs(playlistId: String): Flow<List<PlaylistSong>>

    @Query("SELECT songId from playlist_song_map WHERE playlistId = :playlistId AND songId IN (:songIds)")
    fun playlistDuplicates(playlistId: String, songIds: List<String>,): List<String>

    @Query("SELECT * FROM playlist_song_map WHERE songId = :songId")
    fun songMapsToPlaylist(songId: String): List<PlaylistSongMap>

    @Query("SELECT * FROM playlist_song_map WHERE playlistId = :playlistId AND position >= :from ORDER BY position")
    fun songMapsToPlaylist(playlistId: String, from: Int): List<PlaylistSongMap>

    @Transaction
    @Query("""
        SELECT 
            p.*, 
            COUNT(psm.playlistId) AS songCount,
            SUM(CASE WHEN s.dateDownload IS NOT NULL THEN 1 ELSE 0 END) AS downloadCount
        FROM playlist p
            LEFT JOIN playlist_song_map psm ON p.id = psm.playlistId
            LEFT JOIN song s ON psm.songId = s.id
        WHERE p.isEditable AND p.bookmarkedAt IS NOT NULL 
        GROUP BY p.id
        ORDER BY p.rowId
    """)
    fun editablePlaylistsByCreateDateAsc(): Flow<List<Playlist>>

    @Transaction
    @Query("""
        SELECT 
            p.*, 
            COUNT(psm.playlistId) AS songCount,
            SUM(CASE WHEN s.dateDownload IS NOT NULL THEN 1 ELSE 0 END) AS downloadCount
        FROM playlist p
            LEFT JOIN playlist_song_map psm ON p.id = psm.playlistId
            LEFT JOIN song s ON psm.songId = s.id
        WHERE p.isLocal AND p.bookmarkedAt IS NOT NULL 
        GROUP BY p.id
        ORDER BY p.rowId
    """)
    fun localPlaylistsByCreateDateAsc(): Flow<List<Playlist>>

    @RawQuery(observedEntities = [PlaylistEntity::class])
    fun _getPlaylists(query: SupportSQLiteQuery): Flow<List<Playlist>>

    fun playlists(filter: PlaylistFilter, sortType: PlaylistSortType, descending: Boolean): Flow<List<Playlist>> {
        val orderBy = when (sortType) {
            PlaylistSortType.CREATE_DATE -> "p.rowId ASC"
            PlaylistSortType.NAME -> "p.name COLLATE NOCASE ASC"
            PlaylistSortType.SONG_COUNT -> "songCount ASC"
        }

        val having = when (filter) {
            PlaylistFilter.DOWNLOADED -> "HAVING SUM(CASE WHEN s.dateDownload IS NOT NULL THEN 1 ELSE 0 END) > 0"
            else -> ""
        }

        val query = SimpleSQLiteQuery("""
            SELECT 
                p.*, 
                COUNT(psm.playlistId) AS songCount,
                SUM(CASE WHEN s.dateDownload IS NOT NULL THEN 1 ELSE 0 END) AS downloadCount
            FROM playlist p
                LEFT JOIN playlist_song_map psm ON p.id = psm.playlistId
                LEFT JOIN song s ON psm.songId = s.id
            WHERE p.bookmarkedAt IS NOT NULL OR p.isLocal = 1
            GROUP BY p.id
            $having
            ORDER BY $orderBy
        """)

        return _getPlaylists(query).map{ it.reversed(descending) }
    }

    fun playlistInLibraryAsc() = playlists(PlaylistFilter.LIBRARY, PlaylistSortType.CREATE_DATE, false)
    // endregion

    // region Inserts
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: PlaylistSongMap)
    // endregion

    // region Updates
    @Update
    fun update(playlist: PlaylistEntity)

    @Update
    fun update(map: PlaylistSongMap)

    @Update
    fun update(playlistEntity: PlaylistEntity, playlistItem: PlaylistItem) {
        update(playlistEntity.copy(
            name = playlistItem.title,
            browseId = playlistItem.id,
            isEditable = playlistItem.isEditable || playlistItem.author == null, // for some reason null == your account,
            thumbnailUrl = playlistItem.thumbnail,
            remoteSongCount = playlistItem.songCountText?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() },
            playEndpointParams = playlistItem.playEndpoint?.params,
            shuffleEndpointParams = playlistItem.shuffleEndpoint?.params,
            radioEndpointParams = playlistItem.radioEndpoint?.params
        ))
    }

    @Transaction
    fun addSongToPlaylist(playlist: Playlist, songIds: List<String>) {
        var position = playlist.songCount
        songIds.forEach { id ->
            insert(
                PlaylistSongMap(
                    songId = id,
                    playlistId = playlist.id,
                    position = position++
                )
            )
        }
    }

    @Transaction
    @Query("UPDATE playlist SET isLocal = 1 WHERE id = :playlistId")
    fun playlistDesync(playlistId: String)

    @Transaction
    @Query(
        """
        UPDATE playlist_song_map SET position = 
            CASE 
                WHEN position < :fromPosition THEN position + 1
                WHEN position > :fromPosition THEN position - 1
                ELSE :toPosition
            END 
        WHERE playlistId = :playlistId AND position BETWEEN MIN(:fromPosition, :toPosition) AND MAX(:fromPosition, :toPosition)
    """
    )
    fun move(playlistId: String, fromPosition: Int, toPosition: Int)
    // endregion

    // region Deletes
    @Delete
    fun delete(playlist: PlaylistEntity)

    @Query("DELETE FROM playlist WHERE browseId = :browseId")
    fun deletePlaylistById(browseId: String)

    @Query("DELETE FROM playlist_song_map WHERE playlistId = :playlistId")
    fun clearPlaylist(playlistId: String)

    @Delete
    fun delete(playlistSongMap: PlaylistSongMap)
    // endregion
}