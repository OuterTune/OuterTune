/*
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.utils

import android.net.ConnectivityManager
import android.util.Log
import androidx.media3.common.PlaybackException
import com.dd3boh.outertune.constants.AudioQuality
import com.dd3boh.outertune.utils.YTPlayerUtils.MAIN_CLIENT
import com.dd3boh.outertune.utils.YTPlayerUtils.STREAM_FALLBACK_CLIENTS
import com.dd3boh.outertune.utils.YTPlayerUtils.validateStatus
import com.dd3boh.outertune.utils.potoken.PoTokenGenerator
import com.dd3boh.outertune.utils.potoken.PoTokenResult
import com.zionhuang.innertube.NewPipeUtils
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.YouTubeClient
import com.zionhuang.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.zionhuang.innertube.models.YouTubeClient.Companion.IOS
import com.zionhuang.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.zionhuang.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.zionhuang.innertube.models.response.PlayerResponse
import okhttp3.OkHttpClient

object YTPlayerUtils {

    private const val TAG = "YTPlayerUtils"

    private val httpClient = OkHttpClient.Builder()
        .proxy(YouTube.proxy)
        .build()

    private val poTokenGenerator = PoTokenGenerator()

    /**
     * WEB_REMIX is the main client: provides correct metadata (loudnessDb) and uses
     * poToken (useWebPoTokens=true) for stable, long-lived stream URLs.
     */
    private val MAIN_CLIENT: YouTubeClient = WEB_REMIX

    /**
     * Fallback clients tried in order when MAIN_CLIENT streams don't work.
     * The last entry is always used without validateStatus.
     */
    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        TVHTML5_SIMPLY_EMBEDDED_PLAYER, // login + sigTs → stable (skipped if not logged in)
        ANDROID_VR_NO_AUTH,             // no auth, direct URLs, no poToken needed
        IOS,                            // last resort; 403 after ~30s
    )


    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
    )

    /**
     * Custom player response intended to use for playback.
     * Metadata like audioConfig and videoDetails are from [MAIN_CLIENT].
     * Format & stream can be from [MAIN_CLIENT] or [STREAM_FALLBACK_CLIENTS].
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): Result<PlaybackData> = runCatching {
        Log.d(TAG, "[$videoId] ====== playerResponseForPlayback START ======")
        YTPlayerDebugInfo.reset(videoId)

        val signatureTimestamp = getSignatureTimestampOrNull(videoId)

        val isLoggedIn = YouTube.cookie != null
        val sessionId =
            if (isLoggedIn) YouTube.dataSyncId
            else YouTube.visitorData

        Log.d(TAG, "[$videoId] isLoggedIn=$isLoggedIn signatureTimestamp=$signatureTimestamp")
        Log.d(TAG, "[$videoId] sessionId=${sessionId?.take(20)?.let { "$it..." } ?: "null"}")

        val potResult = getWebClientPoTokenOrNull(videoId, sessionId)
        val webPlayerPot = potResult?.playerRequestPoToken
        val webStreamingPot = potResult?.streamingDataPoToken
        if (potResult != null) {
            Log.d(TAG, "[$videoId] poToken GENERATED: playerPot=${webPlayerPot?.take(20)}... streamPot=${webStreamingPot?.take(20)}...")
            YTPlayerDebugInfo.potStatus = "OK (${webPlayerPot?.take(12)}…)"
        } else {
            Log.w(TAG, "[$videoId] poToken FAILED or null — WEB_REMIX will not get &pot=")
            YTPlayerDebugInfo.potStatus = "NONE"
        }

        // WEB_REMIX is MAIN_CLIENT: useWebPoTokens=true → poToken goes into request body
        Log.d(TAG, "[$videoId] MAIN_CLIENT=${MAIN_CLIENT.clientName} useWebPoTokens=${MAIN_CLIENT.useWebPoTokens}" +
                " sigTs=$signatureTimestamp playerPot=${webPlayerPot?.take(20) ?: "null"}")

        val mainPlayerResponse =
            YouTube.player(videoId, playlistId, MAIN_CLIENT, signatureTimestamp, webPlayerPot)
                .getOrThrow()

        Log.d(TAG, "[$videoId] MAIN_CLIENT playability=${mainPlayerResponse.playabilityStatus.status}" +
                " formats=${mainPlayerResponse.streamingData?.adaptiveFormats?.size ?: 0}")

        val audioConfig = mainPlayerResponse.playerConfig?.audioConfig
        val videoDetails = mainPlayerResponse.videoDetails
        val playbackTracking = mainPlayerResponse.playbackTracking

        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null
        var winningClient: YouTubeClient? = null

        for (clientIndex in (-1 until STREAM_FALLBACK_CLIENTS.size)) {
            format = null; streamUrl = null; streamExpiresInSeconds = null

            val client: YouTubeClient
            if (clientIndex == -1) {
                client = MAIN_CLIENT
                streamPlayerResponse = mainPlayerResponse
                Log.i(TAG, "[$videoId] TRY[main] ${client.clientName} usePoToken=${client.useWebPoTokens}")
                YTPlayerDebugInfo.logAttempt(client.clientName, "main")
            } else {
                client = STREAM_FALLBACK_CLIENTS[clientIndex]
                Log.i(TAG, "[$videoId] TRY[fb$clientIndex] ${client.clientName}" +
                        " loginRequired=${client.loginRequired} usePoToken=${client.useWebPoTokens}")

                if (client.loginRequired && !isLoggedIn) {
                    Log.w(TAG, "[$videoId] SKIP ${client.clientName}: loginRequired && !isLoggedIn")
                    YTPlayerDebugInfo.logAttempt(client.clientName, "SKIP(loginRequired)")
                    continue
                }

                Log.d(TAG, "[$videoId] ${client.clientName} player request:" +
                        " sigTs=$signatureTimestamp" +
                        " pot=${if (client.useWebPoTokens) webPlayerPot?.take(20) ?: "null(NO POT!)" else "n/a(useWebPoTokens=false)"}")

                streamPlayerResponse = YouTube.player(videoId, playlistId, client, signatureTimestamp, webPlayerPot)
                    .onFailure { Log.e(TAG, "[$videoId] ${client.clientName} player() threw: $it") }
                    .getOrNull()
            }

            val statusStr = streamPlayerResponse?.playabilityStatus?.let {
                it.status + (it.reason?.let { r -> "($r)" } ?: "")
            } ?: "NULL_RESPONSE"
            Log.i(TAG, "[$videoId] ${client.clientName} playability=$statusStr")

            if (streamPlayerResponse?.playabilityStatus?.status != "OK") {
                YTPlayerDebugInfo.logAttempt(client.clientName, "FAIL(playability=$statusStr)")
                continue
            }

            format = findFormat(streamPlayerResponse, audioQuality, connectivityManager)
            if (format == null) {
                Log.w(TAG, "[$videoId] SKIP ${client.clientName}: no audio format found")
                YTPlayerDebugInfo.logAttempt(client.clientName, "FAIL(no-format)")
                continue
            }
            Log.d(TAG, "[$videoId] ${client.clientName} format: itag=${format.itag} ${format.mimeType} ${format.bitrate}bps")

            val rawUrl = findUrlOrNull(format, videoId)
            if (rawUrl == null) {
                Log.e(TAG, "[$videoId] SKIP ${client.clientName}: NewPipe findUrlOrNull=null" +
                        " (cipher=${format.signatureCipher != null} url=${format.url != null})")
                YTPlayerDebugInfo.logAttempt(client.clientName,
                    "FAIL(NewPipe cipher=${format.signatureCipher != null})")
                YTPlayerDebugInfo.newPipeOk = false
                continue
            }
            YTPlayerDebugInfo.newPipeOk = true

            streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds
            if (streamExpiresInSeconds == null) {
                Log.w(TAG, "[$videoId] SKIP ${client.clientName}: expiresInSeconds null")
                YTPlayerDebugInfo.logAttempt(client.clientName, "FAIL(no-expiry)")
                continue
            }

            streamUrl = rawUrl
            if (client.useWebPoTokens) {
                if (webStreamingPot != null) {
                    streamUrl += "&pot=$webStreamingPot"
                    Log.i(TAG, "[$videoId] ${client.clientName} &pot= appended ✓")
                } else {
                    Log.e(TAG, "[$videoId] ${client.clientName} useWebPoTokens=true but streamPot=NULL — no &pot= appended!")
                }
            }

            YTPlayerDebugInfo.winnerClient = client.clientName
            YTPlayerDebugInfo.urlPrefix = streamUrl.take(50)

            val isLastClient = clientIndex == STREAM_FALLBACK_CLIENTS.size - 1
            if (isLastClient) {
                Log.w(TAG, "[$videoId] LAST client ${client.clientName} — skipping validateStatus")
                YTPlayerDebugInfo.logAttempt(client.clientName, "USED(last,no-validate)")
                winningClient = client
                break
            }

            val valid = validateStatus(streamUrl)
            Log.i(TAG, "[$videoId] ${client.clientName} validateStatus=$valid expiresIn=${streamExpiresInSeconds}s")
            if (valid) {
                Log.i(TAG, "[$videoId] WINNER: ${client.clientName} ✓")
                YTPlayerDebugInfo.logAttempt(client.clientName, "USED(validate=OK)")
                winningClient = client
                break
            } else {
                Log.w(TAG, "[$videoId] ${client.clientName} validateStatus=FAIL → next client")
                YTPlayerDebugInfo.logAttempt(client.clientName, "FAIL(validate)")
            }
        }

        Log.i(TAG, "[$videoId] === done: winner=${winningClient?.clientName ?: "NONE"} ===")

        if (streamPlayerResponse == null) throw Exception("No stream player response")
        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            throw PlaybackException(
                streamPlayerResponse.playabilityStatus.reason,
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }
        if (streamExpiresInSeconds == null) throw Exception("Missing stream expiry")
        if (format == null) throw Exception("No format found")
        if (streamUrl == null) throw Exception("No stream URL")

        Log.i(TAG, "[$videoId] FINAL url[50]=${streamUrl.take(50)} expiresIn=${streamExpiresInSeconds}s")
        PlaybackData(audioConfig, videoDetails, playbackTracking, format, streamUrl, streamExpiresInSeconds)
    }

    /**
     * Simple player response intended to use for metadata only.
     * Stream URLs of this response might not work so don't use them.
     */
    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> =
        YouTube.player(videoId, playlistId, client = WEB_REMIX) // ANDROID_VR does not work with history

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): PlayerResponse.StreamingData.Format? =
        playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudio }
            ?.maxByOrNull {
                it.bitrate * when (audioQuality) {
                    AudioQuality.AUTO -> if (connectivityManager.isActiveNetworkMetered) -1 else 1
                    AudioQuality.HIGH -> 1
                    AudioQuality.LOW -> -1
                } + (if (it.mimeType.startsWith("audio/webm")) 10240 else 0) // prefer opus stream
            }

    /**
     * Checks if the stream url returns a successful status.
     * Uses a 1-byte range GET instead of HEAD because YouTube CDN often rejects HEAD requests
     * with 403 even for valid URLs, which would cause valid clients to be skipped.
     */
    private fun validateStatus(url: String): Boolean {
        try {
            val request = okhttp3.Request.Builder()
                .get()
                .header("Range", "bytes=0-1")
                .url(url)
                .build()
            val response = httpClient.newCall(request).execute()
            response.body?.close()
            return response.isSuccessful || response.code == 206
        } catch (e: Exception) {
            reportException(e)
        }
        return false
    }

    /**
     * Wrapper around the [NewPipeUtils.getSignatureTimestamp] function which reports exceptions
     */
    private fun getSignatureTimestampOrNull(
        videoId: String
    ): Int? {
        return NewPipeUtils.getSignatureTimestamp(videoId)
            .onFailure {
                reportException(it)
            }
            .getOrNull()
    }

    /**
     * Wrapper around the [NewPipeUtils.getStreamUrl] function which reports exceptions
     */
    private fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String
    ): String? {
        return NewPipeUtils.getStreamUrl(format, videoId)
            .onFailure {
                reportException(it)
            }
            .getOrNull()
    }

    /**
     * Wrapper around the [PoTokenGenerator.getWebClientPoToken] function which reports exceptions
     */
    private fun getWebClientPoTokenOrNull(videoId: String, sessionId: String?): PoTokenResult? {
        if (sessionId == null) {
            Log.d(TAG, "[$videoId] Session identifier is null")
            return null
        }
        try {
            return poTokenGenerator.getWebClientPoToken(videoId, sessionId)
        } catch (e: Exception) {
            reportException(e)
        }
        return null
    }
}