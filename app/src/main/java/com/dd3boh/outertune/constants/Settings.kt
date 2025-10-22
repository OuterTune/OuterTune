package com.dd3boh.outertune.constants

import android.content.Context
import com.dd3boh.outertune.R

/*
---------------------------
Appearance & interface
---------------------------
 */
enum class DarkMode {
    ON, OFF, AUTO
}

enum class PlayerBackgroundStyle {
    FOLLOW_THEME, GRADIENT, BLUR
}

enum class LibraryViewType {
    LIST, GRID;

    fun toggle() = when (this) {
        LIST -> GRID
        GRID -> LIST
    }
}

enum class LyricsPosition {
    LEFT, CENTER, RIGHT
}

const val DEFAULT_ENABLED_TABS = "HSFM"
const val DEFAULT_ENABLED_FILTERS = "ARP"

/*
---------------------------
Sync
---------------------------
 */

enum class SyncMode {
    RO, RW, // USER_CHOICE
}

enum class SyncConflictResolution {
    ADD_ONLY, OVERWRITE_WITH_REMOTE, // OVERWRITE_WITH_LOCAL, USER_CHOICE
}

// when adding an enum:
// 1. add settings checkbox string and state
// 2. add to DEFAULT_SYNC_CONTENT
// 3. add to encode/decode
// 4. figure out if it's necessary to update existing user's keys
enum class SyncContent {
    ALBUMS,
    ARTISTS,
    LIKED_SONGS,
    PLAYLISTS,
    PRIVATE_SONGS,
    RECENT_ACTIVITY,
    NULL
}

/**
 * A: Albums
 * R: Artists
 * P: Playlists
 * L: Liked songs
 * S: Library (privately uploaded) songs
 * C: Recent activity
 * N: <Unused option>
 */
val syncPairs = listOf(
    SyncContent.ALBUMS to 'A',
    SyncContent.ARTISTS to 'R',
    SyncContent.PLAYLISTS to 'P',
    SyncContent.LIKED_SONGS to 'L',
    SyncContent.PRIVATE_SONGS to 'S',
    SyncContent.RECENT_ACTIVITY to 'C'
)

/**
 * Converts the enable sync items list (string) to SyncContent
 *
 * @param sync Encoded string
 */
fun decodeSyncString(sync: String): List<SyncContent> {
    val charToSyncMap = syncPairs.associate { (screen, char) -> char to screen }

    return sync.toCharArray().map { char -> charToSyncMap[char] ?: SyncContent.NULL }
}

/**
 * Converts the SyncContent filters list to string
 *
 * @param list Decoded SyncContent list
 */
fun encodeSyncString(list: List<SyncContent>): String {
    val charToSyncMap = syncPairs.associate { (sync, char) -> char to sync }

    return list.distinct().joinToString("") { sync ->
        charToSyncMap.entries.first { it.value == sync }.key.toString()
    }
}


/*
---------------------------
Local scanner
---------------------------
 */

enum class ScannerImpl {
    MEDIASTORE,
    TAGLIB,
    FFMPEG_EXT,
}

/**
 * Specify how strict the metadata scanner should be
 */
enum class ScannerMatchCriteria {
    LEVEL_1, // Title only
    LEVEL_2, // Title and artists
    LEVEL_3, // Title, artists, albums
}

enum class ScannerM3uMatchCriteria {
    LEVEL_1, // Title only
    LEVEL_2, // Title and artists
    LEVEL_0, // Do not compare, assume it is a match
    // TODO: Do albums for m3u if that even is a thing
}


/*
---------------------------
Player & audio
---------------------------
 */
enum class SeekIncrement(val millisec: Int, val second: Int) {
    OFF(0, 0), FIVE(5000, 5), TEN(10000, 10), FIFTEEN(15000, 15), TWENTY(20000, 20);

    companion object {
        fun getString(context: Context, seekIncrement: SeekIncrement) =
            when(seekIncrement) {
                OFF -> context.getString(androidx.compose.ui.R.string.state_off)
                else -> context.resources.getQuantityString(R.plurals.second, seekIncrement.second, seekIncrement.second)
            }

    }
}
enum class AudioQuality {
    AUTO, HIGH, LOW
}

/*
---------------------------
Library & Content
---------------------------
 */


enum class LikedAutodownloadMode {
    OFF, ON, WIFI_ONLY
}


/*
---------------------------
Misc preferences not bound
to settings category
---------------------------
 */
enum class SongSortType {
    CREATE_DATE, MODIFIED_DATE, RELEASE_DATE, NAME, ARTIST, PLAY_COUNT
}

enum class FolderSortType {
    NAME, // TODO: support CREATE_DATE, MODIFIED_DATE
}

enum class FolderSongSortType {
    CREATE_DATE, MODIFIED_DATE, RELEASE_DATE, NAME, ARTIST, PLAY_COUNT, TRACK_NUMBER
}

enum class PlaylistSongSortType {
    CUSTOM, NAME, ARTIST, ADDED_DATE, MODIFIED_DATE, RELEASE_DATE
}

enum class ArtistSortType {
    CREATE_DATE, NAME, SONG_COUNT
}

enum class ArtistSongSortType {
    CREATE_DATE, NAME
}

enum class AlbumSortType {
    CREATE_DATE, NAME, ARTIST, YEAR, SONG_COUNT, LENGTH
}

enum class PlaylistSortType {
    CREATE_DATE, NAME, SONG_COUNT
}

enum class LibrarySortType {
    CREATE_DATE, NAME
}

enum class SongFilter {
    LIBRARY, LIKED, DOWNLOADED
}

enum class ArtistFilter {
    LIBRARY, LIKED, DOWNLOADED
}

enum class AlbumFilter {
    LIBRARY, LIKED, DOWNLOADED
}

enum class PlaylistFilter {
    LIBRARY, DOWNLOADED
}

enum class SearchSource {
    LOCAL, ONLINE
}

enum class Speed {
    SLOW, MEDIUM, FAST;

    fun toLrcRefreshMillis(): Long =
        when (this) {
            SLOW -> 125
            MEDIUM -> 33
            FAST -> 16
        }
}
