package com.dd3boh.outertune.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.akanework.gramophone.logic.utils.SemanticLyrics

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val id: String,
    val lyrics: String,
) {
    companion object {
        const val LYRICS_NOT_FOUND = "LYRICS_NOT_FOUND"
        val uninitializedLyric = SemanticLyrics.UnsyncedLyrics(listOf(Pair(LYRICS_NOT_FOUND, null)))
    }
}