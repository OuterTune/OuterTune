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
import com.dd3boh.outertune.constants.ArtistFilter
import com.dd3boh.outertune.constants.ArtistSongSortType
import com.dd3boh.outertune.constants.ArtistSortType
import com.dd3boh.outertune.db.entities.Artist
import com.dd3boh.outertune.db.entities.ArtistEntity
import com.dd3boh.outertune.db.entities.Song
import com.dd3boh.outertune.db.entities.SongArtistMap
import com.dd3boh.outertune.extensions.reversed
import com.dd3boh.outertune.ui.utils.resize
import com.zionhuang.innertube.pages.ArtistPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

/*
 * Logic related to artists entities and their mapping
 */

@Dao
interface ArtistsDao {

    // region Gets
    @Query("""
        SELECT 
            artist.*,
            COUNT(song.id) AS songCount,
            SUM(CASE WHEN song.dateDownload IS NOT NULL THEN 1 ELSE 0 END) AS downloadCount
        FROM artist
            LEFT JOIN song_artist_map sam ON artist.id = sam.artistId
            LEFT JOIN song ON sam.songId = song.id
        WHERE artist.id = :id AND song.inLibrary IS NOT NULL
        GROUP BY artist.id
    """)
    fun artist(id: String): Flow<Artist?>

    @Query("SELECT * FROM artist WHERE id = :id")
    fun artistById(id: String): ArtistEntity?

    @Query("SELECT * FROM artist WHERE name = :name")
    fun artistByName(name: String): ArtistEntity?

    @Query("""
        SELECT 
            artist.*,
            COUNT(song.id) AS songCount,
            SUM(CASE WHEN song.dateDownload IS NOT NULL THEN 1 ELSE 0 END) AS downloadCount
        FROM artist
            LEFT JOIN song_artist_map sam ON artist.id = sam.artistId
            LEFT JOIN song ON sam.songId = song.id
        WHERE artist.name LIKE '%' || :query || '%' AND song.inLibrary IS NOT NULL
        GROUP BY artist.id
        HAVING songCount > 0
        LIMIT :previewSize
    """)
    fun searchArtists(query: String, previewSize: Int = Int.MAX_VALUE): Flow<List<Artist>>

    @Transaction
    @Query("SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE song_artist_map.artistId IN (SELECT id FROM artist WHERE name LIKE '%' || :query || '%') LIMIT :previewSize")
    fun searchArtistSongs(query: String, previewSize: Int = Int.MAX_VALUE): Flow<List<Song>>

    @Query("SELECT * FROM artist WHERE name LIKE '%' || :query || '%' LIMIT :previewSize")
    fun fuzzySearchArtists(query: String, previewSize: Int = Int.MAX_VALUE): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artist WHERE isLocal != 1")
    fun allRemoteArtists(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artist WHERE isLocal = 1")
    fun allLocalArtists(): Flow<List<ArtistEntity>>

    @Query("""
        SELECT 
            artist.*,
            COUNT(song.id) AS songCount,
            SUM(CASE WHEN song.dateDownload IS NOT NULL THEN 1 ELSE 0 END) AS downloadCount
        FROM artist
            LEFT JOIN song_artist_map sam ON artist.id = sam.artistId
            LEFT JOIN song ON sam.songId = song.id
            LEFT JOIN (
                SELECT 
                    songId, 
                    SUM(playTime) AS songTotalPlayTime
                FROM event
                WHERE timestamp > :fromTimeStamp
                GROUP BY songId
            ) AS e ON sam.songId = e.songId
        WHERE song.inLibrary IS NOT NULL
        GROUP BY artist.id
        ORDER BY SUM(e.songTotalPlayTime) DESC
        LIMIT :limit
    """)
    fun mostPlayedArtists(fromTimeStamp: Long, limit: Int = 6): Flow<List<Artist>>

    @RawQuery(observedEntities = [ArtistEntity::class])
    fun _getArtists(query: SupportSQLiteQuery): Flow<List<Artist>>

    fun artists(filter: ArtistFilter, sortType: ArtistSortType, descending: Boolean): Flow<List<Artist>> {
        val orderBy = when (sortType) {
            ArtistSortType.CREATE_DATE -> "artist.rowId ASC"
            ArtistSortType.NAME -> "artist.name COLLATE NOCASE ASC"
            ArtistSortType.SONG_COUNT -> "songCount ASC"
            ArtistSortType.PLAY_TIME -> "SUM(totalPlayTime) ASC"
        }

        val where = when (filter) {
            ArtistFilter.DOWNLOADED -> "song.dateDownload IS NOT NULL"
            ArtistFilter.LIBRARY -> "song.inLibrary IS NOT NULL"
            ArtistFilter.LIKED -> "artist.bookmarkedAt IS NOT NULL"
        }

        val having = when (filter) {
            ArtistFilter.DOWNLOADED -> "AND downloadCount > 0"
            else -> ""
        }

        val query = SimpleSQLiteQuery("""
            SELECT 
                artist.*,
                COUNT(song.id) AS songCount,
                SUM(CASE WHEN song.dateDownload IS NOT NULL THEN 1 ELSE 0 END) AS downloadCount
            FROM artist
                LEFT JOIN song_artist_map sam ON artist.id = sam.artistId
                LEFT JOIN song ON sam.songId = song.id
            WHERE $where
            GROUP BY artist.id
            HAVING songCount > 0 $having
            ORDER BY $orderBy
        """)

        return _getArtists(query).map { artists ->
            artists
                .filter { it.artist.isYouTubeArtist || it.artist.isLocalArtist } // TODO: add ui to filter by local or remote or something idk
                .reversed(descending)
        }
    }

    fun artistsInLibraryAsc() = artists(ArtistFilter.LIBRARY, ArtistSortType.CREATE_DATE, false)
    fun artistsBookmarkedAsc() = artists(ArtistFilter.LIKED, ArtistSortType.CREATE_DATE, false)
    // endregion

    // region Artist Songs Sort
    @Transaction
    @Query("SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = :artistId AND inLibrary IS NOT NULL ORDER BY inLibrary")
    fun artistSongsByCreateDateAsc(artistId: String): Flow<List<Song>>

    @Transaction
    @Query("SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = :artistId AND inLibrary IS NOT NULL ORDER BY title COLLATE NOCASE ASC")
    fun artistSongsByNameAsc(artistId: String): Flow<List<Song>>

    @Transaction
    @Query("SELECT song.* FROM song_artist_map JOIN song ON song_artist_map.songId = song.id WHERE artistId = :artistId AND inLibrary IS NOT NULL ORDER BY totalPlayTime")
    fun artistSongsByPlayTimeAsc(artistId: String): Flow<List<Song>>

    fun artistSongs(artistId: String, sortType: ArtistSongSortType, descending: Boolean) =
        when (sortType) {
            ArtistSongSortType.CREATE_DATE -> artistSongsByCreateDateAsc(artistId)
            ArtistSongSortType.NAME -> artistSongsByNameAsc(artistId)
            ArtistSongSortType.PLAY_TIME -> artistSongsByPlayTimeAsc(artistId)
        }.map { it.reversed(descending) }
    // endregion
    // endregion

    // region Inserts
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(artist: ArtistEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(map: SongArtistMap)
    // endregion

    // region Updates
    @Update
    fun update(artist: ArtistEntity)

    @Transaction
    fun update(artist: ArtistEntity, artistPage: ArtistPage) {
        update(
            artist.copy(
                name = artistPage.artist.title,
                thumbnailUrl = artistPage.artist.thumbnail?.resize(544, 544),
                lastUpdateTime = LocalDateTime.now()
            )
        )
    }

    @Transaction
    @Query("UPDATE song_artist_map SET artistId = :newId WHERE artistId = :oldId")
    fun updateSongArtistMap(oldId: String, newId: String)
    // endregion

    // region Deletes
    @Delete
    fun delete(artist: ArtistEntity)

   @Query("""
        DELETE FROM Artist
        WHERE NOT EXISTS (
            SELECT 1
            FROM song_artist_map
            WHERE song_artist_map.artistId = :artistId
        )
        AND id = :artistId
    """)
    fun safeDeleteArtist(artistId: String)

    @Transaction
    @Query("DELETE FROM artist WHERE isLocal = 1")
    fun nukeLocalArtists()
    // endregion
}