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
     * The main client is used for metadata and initial streams.
     * Do not use other clients for this because it can result in inconsistent metadata.
     * For example other clients can have different normalization targets (loudnessDb).
     *
     * [com.zionhuang.innertube.models.YouTubeClient.ANDROID_VR_NO_AUTH] Is temporally used as it is out only working client
     * [com.zionhuang.innertube.models.YouTubeClient.WEB_REMIX] should be preferred here because currently it is the only client which provides:
     * - the correct metadata (like loudnessDb)
     * - premium formats
     */
    private val MAIN_CLIENT: YouTubeClient = ANDROID_VR_NO_AUTH

    /**
     * Clients used for fallback streams in case the streams of the main client do not work.
     * Order matters: first match with a valid stream URL wins (last client skips validateStatus).
     */
    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        TVHTML5_SIMPLY_EMBEDDED_PLAYER, // requires login; stable streams via signature timestamp
        WEB_REMIX,                       // poToken-based; stable streams for non-logged-in users
        IOS,                             // last resort; 403 after ~30s due to recent API changes
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

        // Log the body fields we're about to send for MAIN_CLIENT
        Log.d(TAG, "[$videoId] MAIN_CLIENT request: client=${MAIN_CLIENT.clientName} v${MAIN_CLIENT.clientVersion}" +
                " sigTs=$signatureTimestamp usePoToken=${MAIN_CLIENT.useWebPoTokens} pot=${webPlayerPot?.take(20)}")

        val mainPlayerResponse =
            YouTube.player(videoId, playlistId, MAIN_CLIENT, signatureTimestamp, webPlayerPot)
                .getOrThrow()

        Log.d(TAG, "[$videoId] MAIN_CLIENT playabilityStatus=${mainPlayerResponse.playabilityStatus.status}" +
                " reason=${mainPlayerResponse.playabilityStatus.reason}" +
                " adaptiveFormats=${mainPlayerResponse.streamingData?.adaptiveFormats?.size ?: 0}")

        val audioConfig = mainPlayerResponse.playerConfig?.audioConfig
        val videoDetails = mainPlayerResponse.videoDetails
        val playbackTracking = mainPlayerResponse.playbackTracking

        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null

        var streamPlayerResponse: PlayerResponse? = null
        var winningClient: YouTubeClient? = null

        for (clientIndex in (-1 until STREAM_FALLBACK_CLIENTS.size)) {
            format = null
            streamUrl = null
            streamExpiresInSeconds = null

            val client: YouTubeClient
            if (clientIndex == -1) {
                client = MAIN_CLIENT
                streamPlayerResponse = mainPlayerResponse
                Log.d(TAG, "[$videoId] --- Trying MAIN_CLIENT: ${client.clientName} ---")
            } else {
                client = STREAM_FALLBACK_CLIENTS[clientIndex]
                Log.d(TAG, "[$videoId] --- Trying FALLBACK[$clientIndex]: ${client.clientName}" +
                        " loginRequired=${client.loginRequired} usePoToken=${client.useWebPoTokens} ---")

                if (client.loginRequired && !isLoggedIn) {
                    Log.d(TAG, "[$videoId] SKIP ${client.clientName}: loginRequired but not logged in")
                    continue
                }

                Log.d(TAG, "[$videoId] ${client.clientName} request body: client=${client.clientName} v${client.clientVersion}" +
                        " sigTs=$signatureTimestamp usePoToken=${client.useWebPoTokens}" +
                        " playerPot=${if (client.useWebPoTokens) webPlayerPot?.take(20) else "n/a"}")

                streamPlayerResponse =
                    YouTube.player(videoId, playlistId, client, signatureTimestamp, webPlayerPot)
                        .getOrNull()
            }

            val statusStr = streamPlayerResponse?.playabilityStatus?.let {
                "${it.status}${it.reason?.let { r -> " ($r)" } ?: ""}"
            } ?: "null"
            Log.d(TAG, "[$videoId] ${client.clientName} playabilityStatus=$statusStr")

            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                val formats = streamPlayerResponse.streamingData?.adaptiveFormats
                Log.d(TAG, "[$videoId] ${client.clientName} adaptiveFormats=${formats?.size ?: 0}" +
                        " audio=${formats?.count { it.isAudio } ?: 0}")

                format = findFormat(streamPlayerResponse, audioQuality, connectivityManager)
                if (format == null) {
                    Log.w(TAG, "[$videoId] ${client.clientName} SKIP: findFormat returned null")
                    continue
                }
                Log.d(TAG, "[$videoId] ${client.clientName} selected format: itag=${format.itag} mime=${format.mimeType} bitrate=${format.bitrate}")

                val rawUrl = findUrlOrNull(format, videoId)
                if (rawUrl == null) {
                    Log.w(TAG, "[$videoId] ${client.clientName} SKIP: findUrlOrNull returned null (NewPipe failed?)")
                    YTPlayerDebugInfo.newPipeOk = false
                    continue
                }
                YTPlayerDebugInfo.newPipeOk = true
                Log.d(TAG, "[$videoId] ${client.clientName} rawUrl[50]=${rawUrl.take(50)}")

                streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds
                if (streamExpiresInSeconds == null) {
                    Log.w(TAG, "[$videoId] ${client.clientName} SKIP: expiresInSeconds null")
                    continue
                }

                streamUrl = rawUrl
                if (client.useWebPoTokens && webStreamingPot != null) {
                    streamUrl += "&pot=$webStreamingPot"
                    Log.d(TAG, "[$videoId] ${client.clientName} appended &pot= streamUrl[50]=${streamUrl.take(50)}")
                } else if (client.useWebPoTokens && webStreamingPot == null) {
                    Log.w(TAG, "[$videoId] ${client.clientName} useWebPoTokens=true but streamPot=null — URL sent WITHOUT &pot=")
                }

                YTPlayerDebugInfo.clientUsed = client.clientName
                YTPlayerDebugInfo.urlPrefix = streamUrl.take(50)

                if (clientIndex == STREAM_FALLBACK_CLIENTS.size - 1) {
                    Log.w(TAG, "[$videoId] ${client.clientName} is LAST client — using without validateStatus (may 403)")
                    YTPlayerDebugInfo.validateLog += "${client.clientName}=LAST(no-check) "
                    winningClient = client
                    break
                }

                val valid = validateStatus(streamUrl)
                Log.d(TAG, "[$videoId] ${client.clientName} validateStatus=$valid expiresIn=${streamExpiresInSeconds}s")
                YTPlayerDebugInfo.validateLog += "${client.clientName}=${if (valid) "OK" else "FAIL"} "
                if (valid) {
                    Log.i(TAG, "[$videoId] WINNER: ${client.clientName} — stable stream confirmed")
                    winningClient = client
                    break
                } else {
                    Log.w(TAG, "[$videoId] ${client.clientName} validateStatus FAILED — trying next client")
                }
            }
        }

        Log.d(TAG, "[$videoId] ====== client selection done — winningClient=${winningClient?.clientName ?: "NONE"} ======")

        if (streamPlayerResponse == null) {
            throw Exception("Bad stream player response")
        }
        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            throw PlaybackException(
                streamPlayerResponse.playabilityStatus.reason,
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }
        if (streamExpiresInSeconds == null) throw Exception("Missing stream expire time")
        if (format == null) throw Exception("Could not find format")
        if (streamUrl == null) throw Exception("Could not find stream url")

        Log.i(TAG, "[$videoId] FINAL streamUrl[50]=${streamUrl.take(50)} expiresIn=${streamExpiresInSeconds}s")

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