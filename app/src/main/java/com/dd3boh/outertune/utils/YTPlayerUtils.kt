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
     * [com.zionhuang.innertube.models.YouTubeClient.WEB_REMIX] should be preferred here because currently it is the only client which provides:
     * - the correct metadata (like loudnessDb)
     * - premium formats
     */
    private val MAIN_CLIENT: YouTubeClient = WEB_REMIX

    /**
     * Clients used for fallback streams in case the streams of the main client do not work.
     * Added more clients to handle various blocking scenarios and improve reliability.
     */
    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        IOS,
        WEB,
        WEB_CREATOR,
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
        validateStreamUrl: Boolean = true,
    ): Result<PlaybackData> = runCatching {
        Log.d(TAG, "Playback info requested: $videoId")

        /**
         * This is required for some clients to get working streams however
         * it should not be forced for the [MAIN_CLIENT] because the response of the [MAIN_CLIENT]
         * is required even if the streams won't work from this client.
         * This is why it is allowed to be null.
         */
        val signatureTimestamp = getSignatureTimestampOrNull(videoId)

        val isLoggedIn = YouTube.cookie != null
        val sessionId =
            if (isLoggedIn) {
                // signed in sessions use dataSyncId as identifier
                YouTube.dataSyncId
            } else {
                // signed out sessions use visitorData as identifier
                YouTube.visitorData
            }

        Log.d(TAG, "[$videoId] signatureTimestamp: $signatureTimestamp, isLoggedIn: $isLoggedIn, validateStreams: $validateStreamUrl")

        val (webPlayerPot, webStreamingPot) = getWebClientPoTokenOrNull(videoId, sessionId)?.let {
            Pair(it.playerRequestPoToken, it.streamingDataPoToken)
        } ?: Pair(null, null).also {
            Log.w(TAG, "[$videoId] No po token")
        }

        val mainPlayerResponse =
            YouTube.player(videoId, playlistId, MAIN_CLIENT, signatureTimestamp, webPlayerPot)
                .getOrThrow()

        val audioConfig = mainPlayerResponse.playerConfig?.audioConfig
        val videoDetails = mainPlayerResponse.videoDetails
        val playbackTracking = mainPlayerResponse.playbackTracking

        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        val failedClients = mutableListOf<String>()

        var streamPlayerResponse: PlayerResponse? = null
        for (clientIndex in (-1 until STREAM_FALLBACK_CLIENTS.size)) {
            // reset for each client
            format = null
            streamUrl = null
            streamExpiresInSeconds = null

            // decide which client to use for streams and load its player response
            val client: YouTubeClient
            if (clientIndex == -1) {
                Log.d(TAG, "Trying client: ${MAIN_CLIENT.clientName}")
                // try with streams from main client first
                client = MAIN_CLIENT
                streamPlayerResponse = mainPlayerResponse
            } else {
                Log.d(TAG, "Trying fallback client: ${STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                // after main client use fallback clients
                client = STREAM_FALLBACK_CLIENTS[clientIndex]

                if (client.loginRequired && !isLoggedIn) {
                    // skip client if it requires login but user is not logged in
                    continue
                }

                streamPlayerResponse =
                    YouTube.player(videoId, playlistId, client, signatureTimestamp, webPlayerPot)
                        .getOrNull()
            }

            Log.d(TAG, "[$videoId] stream client: ${client.clientName}, " +
                    "playabilityStatus: ${streamPlayerResponse?.playabilityStatus?.let {
                        it.status + (it.reason?.let { " - $it" } ?: "")
                    }}")

            // process current client response
            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                format =
                    findFormat(
                        streamPlayerResponse,
                        audioQuality,
                        connectivityManager,
                    ) ?: continue
                streamUrl = findUrlOrNull(format, videoId) ?: continue
                streamExpiresInSeconds =
                    streamPlayerResponse.streamingData?.expiresInSeconds ?: continue

                if (client.useWebPoTokens && webStreamingPot != null) {
                    streamUrl += "&pot=$webStreamingPot";
                }

                if (clientIndex == STREAM_FALLBACK_CLIENTS.size - 1) {
                    /** skip [validateStatus] for last client */
                    break
                }
                if (!validateStreamUrl || validateStatus(streamUrl)) {
                    // working stream found or validation disabled
                    Log.i(TAG, "[$videoId] [${client.clientName}] found working stream${if (!validateStreamUrl) " (validation disabled)" else ""}")
                    break
                } else {
                    Log.w(TAG, "[$videoId] [${client.clientName}] got bad http status code")
                    failedClients.add("${client.clientName} (bad status)")
                }
            } else {
                failedClients.add("${client.clientName} (${streamPlayerResponse?.playabilityStatus?.status ?: "null response"})")
            }
        }

        if (streamPlayerResponse == null) {
            throw PlaybackException(
                "All YouTube clients failed: ${failedClients.joinToString(", ")}",
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }
        
        // Handle specific YouTube error responses with better error messages and codes
        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            val status = streamPlayerResponse.playabilityStatus.status
            val reason = streamPlayerResponse.playabilityStatus.reason ?: "Unknown error"
            
            Log.w(TAG, "[$videoId] Playability error: $status - $reason")
            
            // Map specific YouTube errors to appropriate error codes
            when {
                reason.contains("Sign in to confirm you're not a bot", ignoreCase = true) ||
                reason.contains("bot", ignoreCase = true) -> {
                    throw PlaybackException(
                        "Bot detection triggered. Try again later or use VPN.",
                        null,
                        PlaybackException.ERROR_CODE_AUTHENTICATION_EXPIRED
                    )
                }
                reason.contains("private", ignoreCase = true) ||
                status == "LOGIN_REQUIRED" -> {
                    throw PlaybackException(
                        "This video is private or requires login",
                        null,
                        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED
                    )
                }
                reason.contains("inappropriate", ignoreCase = true) ||
                reason.contains("age", ignoreCase = true) -> {
                    throw PlaybackException(
                        "Content is age-restricted or inappropriate",
                        null,
                        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED
                    )
                }
                reason.contains("unavailable", ignoreCase = true) ||
                status == "VIDEO_UNAVAILABLE" -> {
                    throw PlaybackException(
                        "Video is unavailable",
                        null,
                        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
                    )
                }
                status == "UNPLAYABLE" -> {
                    throw PlaybackException(
                        "Video cannot be played (geo-blocked or removed)",
                        null,
                        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED  
                    )
                }
                else -> {
                    throw PlaybackException(
                        reason,
                        null,
                        PlaybackException.ERROR_CODE_REMOTE_ERROR
                    )
                }
            }
        }
        
        if (streamExpiresInSeconds == null) {
            throw PlaybackException(
                "Stream URL expiration time is missing",
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }
        if (format == null) {
            throw PlaybackException(
                "No suitable audio format found",
                null,
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
            )
        }
        if (streamUrl == null) {
            throw PlaybackException(
                "Could not extract stream URL",
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }

        Log.d(TAG, "[$videoId] stream url: $streamUrl")

        PlaybackData(
            audioConfig,
            videoDetails,
            playbackTracking,
            format,
            streamUrl,
            streamExpiresInSeconds,
        )
    }

    /**
     * Simple player response intended to use for metadata only.
     * Stream URLs of this response might not work so don't use them.
     */
    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> =
        YouTube.player(videoId, playlistId, client = MAIN_CLIENT)

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
     * Checks if the stream url returns a successful status with retry logic.
     * If this returns true the url is likely to work.
     * If this returns false the url might cause an error during playback.
     */
    private fun validateStatus(url: String): Boolean {
        val maxRetries = 2
        var delay = 1000L // Start with 1 second delay
        
        repeat(maxRetries) { attempt ->
            try {
                val requestBuilder = okhttp3.Request.Builder()
                    .head()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    
                val response = httpClient.newCall(requestBuilder.build()).execute()
                
                when (response.code) {
                    200 -> return true
                    403 -> {
                        Log.w(TAG, "Got 403 for stream URL (attempt ${attempt + 1})")
                        if (attempt < maxRetries - 1) {
                            Thread.sleep(delay)
                            delay *= 2 // Exponential backoff
                        }
                    }
                    429 -> {
                        Log.w(TAG, "Rate limited (429) for stream URL (attempt ${attempt + 1})")
                        if (attempt < maxRetries - 1) {
                            Thread.sleep(delay * 2) // Longer delay for rate limiting
                            delay *= 2
                        }
                    }
                    else -> {
                        Log.w(TAG, "Got HTTP ${response.code} for stream URL")
                        return false
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception validating stream URL (attempt ${attempt + 1}): ${e.message}")
                reportException(e)
                if (attempt < maxRetries - 1) {
                    Thread.sleep(delay)
                    delay *= 2
                }
            }
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