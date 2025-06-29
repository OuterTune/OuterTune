package com.dd3boh.outertune.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.apache.commons.lang3.RandomStringUtils
import java.time.LocalDateTime

@Immutable
@Entity(tableName = "genre")
class GenreEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val browseId: String? = null,
    val bookmarkedAt: LocalDateTime? = null,
    val thumbnailUrl: String? = null,
    val playEndpointParams: String? = null,
    val shuffleEndpointParams: String? = null,
    val radioEndpointParams: String? = null,
    @ColumnInfo(name = "isLocal", defaultValue = false.toString())
    val isLocal: Boolean = false,
    // In hopes of not having to modify the database again, I barf vals
) {

    companion object {
        fun generateGenreId() = "LG" + RandomStringUtils.insecure().next(8, true, false)
    }
}