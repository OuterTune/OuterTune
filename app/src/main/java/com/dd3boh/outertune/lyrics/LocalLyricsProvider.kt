package com.dd3boh.outertune.lyrics

import android.content.Context
import java.nio.file.Files
import java.nio.file.Paths


object LocalLyricsProvider : LyricsProvider {
    override val name = "Local LRC"
    override fun isEnabled(context: Context) = true

    /**
     * This function is "hot-wired" to adapted to the
     * interface design. As a result, title is actually the file path.
     * The lrc file is assumed to be in the same directory as the song.
     * All the other fields serve no purpose.
     *
     * @param title file path of the song, NOT the song title
     */
    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = runCatching {
        // ex .../music/song.ogg -> .../music/song.lrc
        String(Files.readAllBytes(
            Paths.get(title.substringBeforeLast('.') + ".lrc"))
        )
    }

}
