package com.dd3boh.outertune.providers.ytm

import android.util.Log
import com.zionhuang.innertube.YouTube
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Direct Innertube player API caller.
 * Mirrors ViTune's client order: iOS → AndroidMusic → WebRemix.
 * No poToken or login required for iOS client.
 */
object InnertubeStreamingProvider {

    private const val TAG = "InnertubeStreamingProvider"
    private const val PLAYER_MUSIC = "https://music.youtube.com/youtubei/v1/player"
    private const val PLAYER_YT    = "https://www.youtube.com/youtubei/v1/player"

    // itag priority: opus 160k > aac 128k > opus 70k > opus 50k
    private val FORMAT_PREFERENCE = listOf(251, 140, 250, 249)

    private data class ClientConfig(
        val clientName: String,
        val clientVersion: String,
        val clientId: Int,
        val userAgent: String,
        val apiKey: String,
        val platform: String = "MOBILE",
        val osName: String? = null,
        val osVersion: String? = null,
        val androidSdkVersion: Int? = null,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val referer: String? = null,
        val useYtEndpoint: Boolean = false,   // false → music.youtube.com, true → www.youtube.com
    )

    private val IOS = ClientConfig(
        clientName    = "IOS",
        clientVersion = "20.03.02",
        clientId      = 5,
        userAgent     = "com.google.ios.youtube/20.03.02 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X;)",
        apiKey        = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc",
        osName        = "iPhone",
        osVersion     = "18.2.1.22C161",
        deviceMake    = "Apple",
        deviceModel   = "iPhone16,2",
        useYtEndpoint = true,
    )

    private val ANDROID_MUSIC = ClientConfig(
        clientName       = "ANDROID_MUSIC",
        clientVersion    = "7.27.52",
        clientId         = 21,
        userAgent        = "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 11) gzip",
        apiKey           = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI",
        platform         = "MOBILE",
        osVersion        = "11",
        androidSdkVersion = 30,
        referer          = "https://music.youtube.com/",
    )

    private val WEB_REMIX = ClientConfig(
        clientName    = "WEB_REMIX",
        clientVersion = "1.20220606.03.00",
        clientId      = 67,
        userAgent     = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36",
        apiKey        = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30",
        platform      = "DESKTOP",
        referer       = "https://music.youtube.com/",
    )

    fun getStreamingUrlBlocking(videoId: String): String? =
        tryClient(videoId, IOS)
            ?: tryClient(videoId, ANDROID_MUSIC)
            ?: tryClient(videoId, WEB_REMIX)

    private fun tryClient(videoId: String, config: ClientConfig): String? = try {
        val clientObj = JSONObject().apply {
            put("clientName", config.clientName)
            put("clientVersion", config.clientVersion)
            config.platform.let { put("platform", it) }
            config.osName?.let { put("osName", it) }
            config.osVersion?.let { put("osVersion", it) }
            config.androidSdkVersion?.let { put("androidSdkVersion", it) }
            config.deviceMake?.let { put("deviceMake", it) }
            config.deviceModel?.let { put("deviceModel", it) }
            YouTube.visitorData?.takeIf { it.isNotEmpty() }?.let { put("visitorData", it) }
        }

        val body = JSONObject()
            .put("videoId", videoId)
            .put("context", JSONObject().put("client", clientObj))
            .put("contentCheckOk", "true")
            .put("racyCheckOk", "true")
            .toString()

        val endpoint = if (config.useYtEndpoint) PLAYER_YT else PLAYER_MUSIC
        parseStreamUrl(postJson(body, config, endpoint))
    } catch (e: Exception) {
        Log.w(TAG, "[${config.clientName}] failed for $videoId: ${e.message}")
        null
    }

    private fun postJson(body: String, config: ClientConfig, endpoint: String): String {
        val conn = URL("$endpoint?key=${config.apiKey}&prettyPrint=false")
            .openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", config.userAgent)
            conn.setRequestProperty("X-YouTube-Client-Name", config.clientId.toString())
            conn.setRequestProperty("X-YouTube-Client-Version", config.clientVersion)
            config.referer?.let {
                conn.setRequestProperty("Origin", it.trimEnd('/').let { r -> r.substringBeforeLast('/') + "//".let { _ -> r } })
                conn.setRequestProperty("Referer", it)
            }
            YouTube.cookie?.takeIf { it.isNotEmpty() }
                ?.let { conn.setRequestProperty("Cookie", it) }
            conn.connectTimeout = 10_000
            conn.readTimeout    = 10_000
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }
            return BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseStreamUrl(json: String): String? {
        val root = JSONObject(json)
        if (root.optJSONObject("playabilityStatus")?.optString("status") != "OK") return null

        val formats = mutableMapOf<Int, String>()
        root.optJSONObject("streamingData")
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
