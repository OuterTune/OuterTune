package com.dd3boh.outertune.network

import android.util.Log
import com.dd3boh.outertune.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Dispatcher
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Thin HTTP client for the authoritative Node.js party service.
 *
 * Contract assumptions (adjust to match the real service when available):
 * - POST {baseUrl}/party/{partyId}/command with JSON body {
 *     "action": "play"|"pause"|"seek"|"next"|"prev",
 *     "start"?: Long,
 *     "song_id"?: String
 *   }
 *   Returns 200/202 on accepted; service will update RTDB itself.
 */
class PartyServiceClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .dispatcher(
            Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 16
            }
        )
        .build()
) {
    private val TAG = "PartyServiceClient"
    private val json = "application/json; charset=utf-8".toMediaType()

    private fun baseUrl(): String {
        // Prefer BuildConfig override; otherwise default to hosted service base URL
        val configured = BuildConfig.PARTY_SERVICE_BASE_URL
        val hostedDefault = "https://lonely-anabal-ra-l-ph-66d16853.koyeb.app/"
        return if (configured.isNotBlank()) configured else hostedDefault
    }

    fun isConfigured(): Boolean = baseUrl().isNotBlank()

    suspend fun sendCommand(
        partyId: String,
    type: String,
    positionMs: Long? = null,
        songId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val urlBase = baseUrl()
        if (urlBase.isBlank()) {
            Log.w(TAG, "PARTY_SERVICE_BASE_URL is not set. Skipping command $type for $partyId")
            return@withContext false
        }
        val url = if (urlBase.endsWith("/")) "${urlBase}party/$partyId/command" else "$urlBase/party/$partyId/command"
        val body = JSONObject().apply {
            put("action", type)
            positionMs?.let { put("start", it) }
            songId?.let { put("song_id", it) }
        }.toString().toRequestBody(json)

        val req = Request.Builder()
            .url(url)
            .post(body)
            .build()
        try {
            http.newCall(req).execute().use { resp ->
                val ok = resp.isSuccessful || resp.code == 202
                if (!ok) Log.w(TAG, "Service command failed: ${resp.code} ${resp.message}")
                ok
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call party service", e)
            false
        }
    }
}
