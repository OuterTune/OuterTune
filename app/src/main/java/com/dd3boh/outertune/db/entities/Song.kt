package com.dd3boh.outertune.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

@Immutable
data class Song @JvmOverloads constructor(
    @Embedded val song: SongEntity,
    @Relation(
        entity = ArtistEntity::class,
        entityColumn = "id",
        parentColumn = "id",
        associateBy = Junction(
            value = SortedSongArtistMap::class,
            parentColumn = "songId",
            entityColumn = "artistId"
        )
    )
    val artists: List<ArtistEntity>,
    @Relation(
        entity = AlbumEntity::class,
        entityColumn = "id",
        parentColumn = "id",
        associateBy = Junction(
            value = SongAlbumMap::class,
            parentColumn = "songId",
            entityColumn = "albumId"
        )
    )
    val album: AlbumEntity? = null,
    @Relation(
        entity = GenreEntity::class,
        entityColumn = "id",
        parentColumn = "id",
        associateBy = Junction(
            value = SongGenreMap::class,
            parentColumn = "songId",
            entityColumn = "genreId"
        )
    )
    val genre: List<GenreEntity>? = null,

    @Relation(
        entity = PlayCountEntity::class,
        entityColumn = "song",
        parentColumn = "id",
    )
    val playCount: List<PlayCountEntity>? = null,
) : LocalItem() {
    override val id: String
        get() = song.id
    override val title: String
        get() = song.title
    override val thumbnailUrl: String?
        get() = song.thumbnailUrl
}

data class QueueSong(
    @Embedded val song: Song,
    @ColumnInfo(name = "shuffledIndex") val shuffledIndex: Int
)