package com.dd3boh.outertune.providers.ytm

import android.util.Log
import com.zionhuang.innertube.YouTube
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object InnertubeStreamingProvider {

    private const val TAG = "InnertubeStreamingProvider"
    private const val PLAYER_URL = "https://music.youtube.com/youtubei/v1/player"
    private const val API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"

    // itag priority: opus 160k > aac 128k > opus 70k > opus 50k
    private val FORMAT_PREFERENCE = listOf(251, 140, 250, 249)

    fun getStreamingUrlBlocking(videoId: String): String? {
        return tryClient(videoId, "ANDROID_MUSIC", "7.27.52", androidSdk = 30)
            ?: tryClient(videoId, "WEB_REMIX", "1.20220606.03.00")
    }

    private fun tryClient(
        videoId: String,
        clientName: String,
        clientVersion: String,
        androidSdk: Int? = null,
    ): String? = try {
        val clientObj = JSONObject()
            .put("clientName", clientName)
            .put("clientVersion", clientVersion)
            .apply { androidSdk?.let { put("androidSdkVersion", it) } }
            .apply {
                YouTube.visitorData?.takeIf { it.isNotEmpty() }
                    ?.let { put("visitorData", it) }
            }

        val body = JSONObject()
            .put("videoId", videoId)
            .put("context", JSONObject().put("client", clientObj))
            .toString()

        parseStreamUrl(postJson(body, clientName))
    } catch (e: Exception) {
        Log.w(TAG, "[$clientName] failed for $videoId: ${e.message}")
        null
    }

    private fun postJson(body: String, clientName: String): String {
        val conn = URL("$PLAYER_URL?key=$API_KEY").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Origin", "https://music.youtube.com")
            conn.setRequestProperty("Referer", "https://music.youtube.com/")
            conn.setRequestProperty(
                "User-Agent",
                if (clientName == "ANDROID_MUSIC")
                    "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 11) gzip"
                else
                    "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0"
            )
            YouTube.cookie?.takeIf { it.isNotEmpty() }
                ?.let { conn.setRequestProperty("Cookie", it) }
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }
            return BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseStreamUrl(json: String): String? {
        val formats = mutableMapOf<Int, String>()
        JSONObject(json)
            .optJSONObject("streamingData")
            ?.optJSONArray("adaptiveFormats")
            ?.let { arr ->
                for (i in 0 until arr.length()) {
                    val f = arr.getJSONObject(i)
                    val url = f.optString("url").takeIf { it.isNotEmpty() } ?: continue
                    formats[f.optInt("itag")] = url
                }
            }
        return FORMAT_PREFERENCE.firstNotNullOfOrNull { formats[it] }
    }
}
