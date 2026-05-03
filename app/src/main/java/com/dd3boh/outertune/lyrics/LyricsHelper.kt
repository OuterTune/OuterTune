package com.dd3boh.outertune.lyrics

import android.content.Context
import android.util.LruCache
import com.dd3boh.outertune.constants.LyricSourcePrefKey
import com.dd3boh.outertune.constants.LyricTrimKey
import com.dd3boh.outertune.constants.MultilineLrcKey
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.LyricsEntity
import com.dd3boh.outertune.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.utils.dataStore
import com.dd3boh.outertune.utils.get
import com.dd3boh.outertune.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.akanework.gramophone.logic.utils.LrcUtils
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.logic.utils.parseLrc
import javax.inject.Inject

class LyricsHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    val database: MusicDatabase
) {
    private val lyricsProviders = listOf(
        BetterLyricsProvider,          // word-by-word TTML → Extended LRC (tried first)
        YouTubeSubtitleLyricsProvider,
        LrcLibLyricsProvider,
        KuGouLyricsProvider,
        YouTubeLyricsProvider,
    )
    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)

    /**
     * Retrieve lyrics from all sources.
     *
     * BetterLyricsProvider is tried first among remote sources because it returns
     * Extended LRC with per-word <timestamps>, enabling the karaoke animation.
     * If it has no match the remaining providers are tried in order as before.
     */
    suspend fun getLyrics(mediaMetadata: MediaMetadata): SemanticLyrics? {
        val trim = context.dataStore.get(LyricTrimKey, defaultValue = false)
        val multiline = context.dataStore.get(MultilineLrcKey, defaultValue = true)
        val prefLocal = context.dataStore.get(LyricSourcePrefKey, true)

        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
            return parseLrc(cached.lyrics, trim, multiline)
        }

        val dbLyrics = database.lyrics(mediaMetadata.id).let { it.first()?.lyrics }
        if (dbLyrics != null && !prefLocal) {
            return parseLrc(dbLyrics, trim, multiline)
        }

        val localLyrics: SemanticLyrics? =
            getLocalLyrics(mediaMetadata, LrcUtils.LrcParserOptions(trim, multiline, "Unable to parse lyrics"))
        val remoteLyrics: String?

        if (prefLocal) {
            if (localLyrics != null) return localLyrics
            if (dbLyrics != null) return parseLrc(dbLyrics, trim, multiline)

            remoteLyrics = getRemoteLyrics(mediaMetadata)
            if (remoteLyrics != null) {
                database.query {
                    upsert(LyricsEntity(id = mediaMetadata.id, lyrics = remoteLyrics))
                }
                return parseLrc(remoteLyrics, trim, multiline)
            }
        } else {
            remoteLyrics = getRemoteLyrics(mediaMetadata)
            if (remoteLyrics != null) {
                database.query {
                    upsert(LyricsEntity(id = mediaMetadata.id, lyrics = remoteLyrics))
                }
                return parseLrc(remoteLyrics, trim, multiline)
            } else if (localLyrics != null) {
                return localLyrics
            }
        }

        database.query {
            upsert(LyricsEntity(id = mediaMetadata.id, lyrics = LYRICS_NOT_FOUND))
        }
        return null
    }

    private suspend fun getRemoteLyrics(mediaMetadata: MediaMetadata): String? {
        lyricsProviders.forEach { provider ->
            if (provider.isEnabled(context)) {
                provider.getLyrics(
                    mediaMetadata.id,
                    mediaMetadata.title,
                    mediaMetadata.artists.joinToString { it.name },
                    mediaMetadata.duration
                ).onSuccess { lyrics ->
                    return lyrics
                }.onFailure {
                    reportException(it)
                }
            }
        }
        return null
    }

    private fun getLocalLyrics(
        mediaMetadata: MediaMetadata,
        parserOptions: LrcUtils.LrcParserOptions
    ): SemanticLyrics? {
        if (LocalLyricsProvider.isEnabled(context) && mediaMetadata.localPath != null) {
            return LocalLyricsProvider.getLyricsNew(mediaMetadata.localPath, parserOptions)
        }
        return null
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        callback: (LyricsResult) -> Unit,
    ) {
        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach { callback(it) }
            return
        }
        val allResult = mutableListOf<LyricsResult>()
        lyricsProviders.forEach { provider ->
            if (provider.isEnabled(context)) {
                provider.getAllLyrics(mediaId, songTitle, songArtists, duration) { lyrics ->
                    val result = LyricsResult(provider.name, lyrics)
                    allResult += result
                    callback(result)
                }
            }
        }
        cache.put(cacheKey, allResult)
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)
