package com.dd3boh.outertune.lyrics

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fetches word-timed TTML from lyrics-api.boidu.dev, parses it with [TTMLParser],
 * and returns Extended LRC so SemanticLyrics.parseLrc() produces Word objects for
 * the karaoke animation in Lyrics.kt.
 *
 * Implements [LyricsProvider] so it plugs straight into the existing provider list
 * in [LyricsHelper] — no other changes needed.
 */
object BetterLyricsProvider : LyricsProvider {

    override val name = "BetterLyrics"
    private const val TAG = "BetterLyricsProvider"
    private const val BASE_URL = "https://lyrics-api.boidu.dev"
    private const val TIMEOUT_MS = 15_000

    override fun isEnabled(context: Context) = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = runCatching {
        val ttml = fetchTtml(title, artist, duration)
            ?: throw IllegalStateException("No TTML for '$title'")

        val lines = TTMLParser.parseTTML(ttml)
        if (lines.isEmpty()) throw IllegalStateException("TTML produced no lines for '$title'")

        val extLrc = TTMLParser.toExtendedLrc(lines)
        Log.d(TAG, "Got ${lines.size} word-timed lines for '$title'")
        extLrc
    }

    private fun fetchTtml(title: String, artist: String, duration: Int): String? {
        val url = buildString {
            append(BASE_URL).append("/getLyrics")
            append("?s=").append(URLEncoder.encode(title,  "UTF-8"))
            append("&a=").append(URLEncoder.encode(artist, "UTF-8"))
            if (duration > 0) append("&d=").append(duration)
        }

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout    = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
        }

        return try {
            connection.connect()
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader().readText()
                    JSONObject(body).optString("ttml").takeIf { it.isNotBlank() }
                }
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> {
                    Log.w(TAG, "HTTP ${connection.responseCode} for '$title'")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error for '$title'", e)
            null
        } finally {
            connection.disconnect()
        }
    }
}
