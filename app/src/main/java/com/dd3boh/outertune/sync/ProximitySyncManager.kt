package com.dd3boh.outertune.sync

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import androidx.media3.common.PlaybackParameters
import com.dd3boh.outertune.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException

data class ProximityPositionSnapshot(
    val isPlaying: Boolean,
    val positionMs: Long,
    val hostNowMs: Long,
    val commandId: String
)

/**
 * LAN proximity sync using periodic beacons.
 * This is a minimal skeleton. It emulates ultra-low-latency behavior locally until
 * the networking beacons are fully implemented.
 */
class ProximitySyncManager(private val context: Context) {
    private var scope: CoroutineScope? = null
    private var job: Job? = null
    private var active: Boolean = false
    private var glideResetJob: Job? = null
    private var glideUntilMs: Long = 0L

    // Network
    private var wifiMulticastLock: WifiManager.MulticastLock? = null
    private var sendSocket: DatagramSocket? = null
    private var recvSocket: MulticastSocket? = null
    private var groupAddr: InetAddress? = null

    companion object {
        private const val TAG = "ProximitySync"
        // Use administratively scoped multicast address
        private const val MCAST_ADDR = "239.255.77.77"
        private const val MCAST_PORT = 48255
        private const val TX_INTERVAL_MS = 40L
        // Correction tuning
        private const val SEEK_THRESHOLD_MS = 25L
        private const val MINOR_DRIFT_MS = 5L
        private const val GLIDE_MAX_DELTA = 0.02f // +/-2%
        private const val GLIDE_WINDOW_MS = 250L
    }

    fun isActive(): Boolean = active

    fun startHost(
        partyCodeProvider: () -> String,
        positionProvider: () -> ProximityPositionSnapshot,
        connection: PlayerConnection
    ) {
        stop()
        active = true
        scope = CoroutineScope(Dispatchers.Default)
        // Prepare multicast group
        groupAddr = runCatching { InetAddress.getByName(MCAST_ADDR) }.getOrNull()
        sendSocket = runCatching { DatagramSocket().apply { broadcast = false } }.getOrNull()
        job = scope!!.launch {
            while (isActive) {
                try {
                    val code = partyCodeProvider()
                    val snap = positionProvider()
                    val payload = JSONObject().apply {
                        put("c", code)
                        put("cmd", snap.commandId)
                        put("t", snap.hostNowMs)
                        put("p", snap.positionMs)
                        put("pl", if (snap.isPlaying) 1 else 0)
                    }.toString().toByteArray(Charsets.UTF_8)
                    val pkt = DatagramPacket(payload, payload.size, groupAddr, MCAST_PORT)
                    sendSocket?.send(pkt)
                } catch (e: Exception) {
                    Log.w(TAG, "Host beacon send failed: ${e.message}")
                }
                delay(TX_INTERVAL_MS)
            }
        }
        Log.d(TAG, "Host proximity sync started for party ${partyCodeProvider()}")
    }

    fun startMember(
        partyCode: String,
        connection: PlayerConnection
    ) {
        stop()
        active = true
        scope = CoroutineScope(Dispatchers.Default)
        // Acquire multicast lock for receiving
        acquireMulticastLock()
        // Join multicast group
        groupAddr = runCatching { InetAddress.getByName(MCAST_ADDR) }.getOrNull()
        recvSocket = runCatching { MulticastSocket(MCAST_PORT).apply {
            soTimeout = 1000
            joinGroup(groupAddr)
        } }.getOrNull()
        job = scope!!.launch {
            while (isActive) {
                try {
                    val buf = ByteArray(512)
                    val pkt = DatagramPacket(buf, buf.size)
                    recvSocket?.receive(pkt)
                    val now = System.currentTimeMillis()
                    val json = JSONObject(String(pkt.data, 0, pkt.length, Charsets.UTF_8))
                    val code = json.optString("c", "")
                    if (code != partyCode) continue
                    val hostNow = json.optLong("t", 0L)
                    val pos = json.optLong("p", 0L)
                    val isPlaying = json.optInt("pl", 0) == 1
                    // Apply play/pause parity fast
                    val player = connection.player
                    if (isPlaying != (player.playWhenReady && connection.isPlaying.value)) {
                        if (isPlaying) player.play() else player.pause()
                    }
                    // Predict effective host position using naive offset (local now - hostNow)
                    val predictedPos = if (isPlaying && hostNow > 0L) (pos + (now - hostNow)).coerceAtLeast(0L) else pos
                    val localPos = player.currentPosition
                    val drift = predictedPos - localPos
                    val absDrift = kotlin.math.abs(drift)
                    if (absDrift > SEEK_THRESHOLD_MS) {
                        // Hard correction
                        player.seekTo(predictedPos)
                        normalizeSpeed(connection)
                    } else if (absDrift > MINOR_DRIFT_MS) {
                        val sign = if (drift > 0) 1f else -1f
                        val ratio = (kotlin.math.min(absDrift, SEEK_THRESHOLD_MS) / SEEK_THRESHOLD_MS.toFloat())
                        val delta = GLIDE_MAX_DELTA * ratio * sign
                        val target = (1f + delta).coerceIn(0.98f, 1.02f)
                        applyTemporarySpeed(connection, target, GLIDE_WINDOW_MS)
                    } else {
                        // Close enough
                        if (System.currentTimeMillis() >= glideUntilMs) normalizeSpeed(connection)
                    }
                } catch (to: SocketTimeoutException) {
                    // continue
                } catch (e: Exception) {
                    Log.w(TAG, "Member beacon recv failed: ${e.message}")
                    delay(20)
                }
            }
        }
        Log.d(TAG, "Member proximity sync started for party $partyCode")
    }

    fun stop() {
        active = false
        job?.cancel()
        job = null
        scope?.cancel()
        scope = null
        glideResetJob?.cancel()
        glideResetJob = null
        try {
            recvSocket?.leaveGroup(groupAddr)
        } catch (_: Exception) {}
        runCatching { recvSocket?.close() }
        recvSocket = null
        runCatching { sendSocket?.close() }
        sendSocket = null
        releaseMulticastLock()
    }

    private fun acquireMulticastLock() {
        if (wifiMulticastLock != null) return
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifiMulticastLock = wm?.createMulticastLock("OuterTuneProximity").apply {
            this?.setReferenceCounted(true)
            runCatching { this?.acquire() }
        }
    }

    private fun releaseMulticastLock() {
        runCatching { wifiMulticastLock?.release() }
        wifiMulticastLock = null
    }

    private fun applyTemporarySpeed(connection: PlayerConnection, speed: Float, durationMs: Long) {
        try {
            connection.player.playbackParameters = PlaybackParameters(speed)
            glideUntilMs = System.currentTimeMillis() + durationMs
            glideResetJob?.cancel()
            glideResetJob = scope?.launch(Dispatchers.Default) {
                delay(durationMs)
                if (System.currentTimeMillis() >= glideUntilMs) normalizeSpeed(connection)
            }
        } catch (_: Exception) {}
    }

    private fun normalizeSpeed(connection: PlayerConnection) {
        try {
            if (connection.player.playbackParameters.speed != 1.0f) {
                connection.player.playbackParameters = PlaybackParameters(1.0f)
            }
        } catch (_: Exception) {}
    }
}
